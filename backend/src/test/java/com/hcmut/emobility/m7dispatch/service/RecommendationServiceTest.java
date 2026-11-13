package com.hcmut.emobility.m7dispatch.service;

import com.hcmut.emobility.m7dispatch.client.ExternalDtos;
import com.hcmut.emobility.m7dispatch.client.HubClient;
import com.hcmut.emobility.m7dispatch.client.VehicleClient;
import com.hcmut.emobility.m7dispatch.domain.RecommendationStatus;
import com.hcmut.emobility.m7dispatch.domain.RedistributionRecommendation;
import com.hcmut.emobility.m7dispatch.domain.RedistributionTask;
import com.hcmut.emobility.m7dispatch.repository.RedistributionRecommendationRepository;
import com.hcmut.emobility.m7dispatch.repository.RedistributionTaskRepository;
import com.hcmut.emobility.shared.common.BusinessException;
import com.hcmut.emobility.shared.contracts.ParkingDock;
import com.hcmut.emobility.shared.contracts.RedistributionRecommendations.ApprovalResult;
import com.hcmut.emobility.shared.events.EventPublisher;
import com.hcmut.emobility.shared.events.RecommendationApprovedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * FR-M7-02 (approve / reject a proposal) and FR-M7-04 (proposal from M1's HubNearCapacityEvent).
 * Hubs: METRO short of vehicles / nearly full, DORM with spare vehicles, BK with free spaces.
 * Defaults: a donor keeps 4 available vehicles, a short Hub should reach 4, a full Hub should fall below 75%.
 */
class RecommendationServiceTest {

    static final long METRO = 1L, DORM = 2L, BK = 4L;

    RedistributionRecommendationRepository recommendations = mock(RedistributionRecommendationRepository.class);
    RedistributionTaskRepository tasks = mock(RedistributionTaskRepository.class);
    RedistributionService dispatch = mock(RedistributionService.class);
    VehicleClient vehicles = mock(VehicleClient.class);
    HubClient hubs = mock(HubClient.class);
    ParkingDock parkingDock = mock(ParkingDock.class);
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    EventPublisher events = mock(EventPublisher.class);
    RecommendationService service;
    AtomicLong ids = new AtomicLong(100);

    @BeforeEach
    void setUp() {
        service = new RecommendationService(recommendations, tasks, dispatch, vehicles, hubs, parkingDock,
                jdbc, events, 4, 4, 75);
        when(recommendations.save(any())).thenAnswer(i -> {
            RedistributionRecommendation r = i.getArgument(0);
            if (r.getId() == null) r.setId(ids.incrementAndGet());
            return r;
        });
        when(tasks.save(any())).thenAnswer(i -> i.getArgument(0));
        when(hubs.checkDestinationCapacity(anyLong()))
                .thenAnswer(i -> Optional.of(new ExternalDtos.HubInfo(i.getArgument(0), 9, 0)));
    }

    private RedistributionRecommendation stored(RecommendationStatus status, int amount, int taskCount) {
        RedistributionRecommendation r = RedistributionRecommendation.pending("M8_SIMULATION", "R-1", DORM, METRO,
                amount, "test", LocalDateTime.now());
        r.setId(7L);
        r.setStatus(status);
        r.setTaskCount(taskCount);
        when(recommendations.findById(7L)).thenReturn(Optional.of(r));
        return r;
    }

    private void taskCreatedFor(long vehicleId) {
        RedistributionTask t = new RedistributionTask();
        t.setTaskId(1000 + vehicleId);
        t.setVehicleId(vehicleId);
        when(dispatch.createRedistributionTask(vehicleId, DORM, METRO)).thenReturn(t);
    }

    @Test
    @DisplayName("TC-M7-15 VEHICLE alert: bring vehicles from the Hub with the most spare ones")
    void tc15_shortageProposal() {
        when(vehicles.availableCountPerHub()).thenReturn(Map.of(METRO, 1, DORM, 10, BK, 5));

        RedistributionRecommendation r = service.proposeForAlert(METRO, "VEHICLE", "evt-1").orElseThrow();

        assertEquals(DORM, r.getSourceHubId());       // DORM spares 10-4 = 6, BK only 1
        assertEquals(METRO, r.getDestinationHubId());
        assertEquals(3, r.getAmount());               // 1 available -> target 4
        assertEquals(RecommendationStatus.PENDING, r.getStatus());
        assertEquals("M1_ALERT", r.getSource());
    }

    @Test
    @DisplayName("TC-M7-16 VEHICLE alert: no proposal when every other Hub would fall into a shortage")
    void tc16_noDonor() {
        when(vehicles.availableCountPerHub()).thenReturn(Map.of(METRO, 1, DORM, 4, BK, 3));

        assertTrue(service.proposeForAlert(METRO, "VEHICLE", "evt-2").isEmpty());
        verify(recommendations, never()).save(any());
    }

    @Test
    @DisplayName("TC-M7-17 PARKING alert: move rental vehicles out, but keep the crowded Hub supplied")
    @SuppressWarnings("unchecked")
    void tc17_crowdingProposal() {
        // DORM is 80% full (24 of 30) and has 9 available vehicles; BK has the most room.
        when(jdbc.queryForObject(anyString(), any(RowMapper.class), eq(DORM))).thenReturn(new int[]{30, 24});
        when(vehicles.availableCountPerHub()).thenReturn(Map.of(DORM, 9, METRO, 1));
        when(parkingDock.vacancies()).thenReturn(List.of(new ParkingDock.HubVacancy(METRO, "Metro", 2),
                new ParkingDock.HubVacancy(DORM, "Dorm", 6), new ParkingDock.HubVacancy(BK, "BK", 12)));
        when(hubs.checkDestinationCapacity(METRO)).thenReturn(Optional.of(new ExternalDtos.HubInfo(METRO, 2, 0)));
        when(hubs.checkDestinationCapacity(BK)).thenReturn(Optional.of(new ExternalDtos.HubInfo(BK, 12, 0)));

        RedistributionRecommendation r = service.proposeForAlert(DORM, "PARKING", "evt-3").orElseThrow();

        assertEquals(DORM, r.getSourceHubId());
        assertEquals(BK, r.getDestinationHubId());
        assertEquals(2, r.getAmount());               // below 75% of 30 means at most 22 used: 24 - 22 = 2
    }

    @Test
    @DisplayName("TC-M7-18 a second alert while a proposal for that Hub is open creates nothing")
    void tc18_openProposalExists() {
        when(recommendations.existsByDestinationHubIdAndStatusIn(eq(METRO), any())).thenReturn(true);

        assertTrue(service.proposeForAlert(METRO, "VEHICLE", "evt-4").isEmpty());
        verifyNoInteractions(vehicles);
    }

    @Test
    @DisplayName("TC-M7-05 approve: one task per vehicle, highest battery first, RecommendationApproved once")
    void tc05_approveCreatesTasks() {
        stored(RecommendationStatus.PENDING, 2, 0);
        when(tasks.findActiveVehicleIds()).thenReturn(List.of());
        when(vehicles.availableVehiclesAt(DORM)).thenReturn(List.of(11L, 12L, 13L, 14L));
        taskCreatedFor(11L);
        taskCreatedFor(12L);

        ApprovalResult result = service.approve("7", "operator1");

        assertEquals("APPROVED", result.status());
        assertEquals(List.of(1011L, 1012L), result.taskIds());
        ArgumentCaptor<RecommendationApprovedEvent> captor = ArgumentCaptor.forClass(RecommendationApprovedEvent.class);
        verify(events, times(1)).publish(captor.capture());
        assertEquals("7", captor.getValue().getRecommendationId());
    }

    @Test
    @DisplayName("TC-M7-19 approve with too few vehicles: PARTIAL, the source keeps one, busy vehicles skipped")
    void tc19_partialApproval() {
        RedistributionRecommendation r = stored(RecommendationStatus.PENDING, 3, 0);
        when(tasks.findActiveVehicleIds()).thenReturn(List.of(12L));
        when(vehicles.availableVehiclesAt(DORM)).thenReturn(List.of(11L, 12L, 13L));
        taskCreatedFor(11L);

        ApprovalResult result = service.approve("7", "operator1");

        assertEquals("PARTIAL", result.status());
        assertEquals(List.of(1011L), result.taskIds());   // 11 and 13 are free; 13 stays at the source
        assertEquals(1, r.getTaskCount());
        assertNotNull(result.message());
        verify(dispatch, never()).createRedistributionTask(eq(12L), anyLong(), anyLong());
    }

    @Test
    @DisplayName("TC-M7-20 a destination that fills up stops the approval with its reason")
    void tc20_destinationFull() {
        stored(RecommendationStatus.PENDING, 2, 0);
        when(tasks.findActiveVehicleIds()).thenReturn(List.of());
        when(vehicles.availableVehiclesAt(DORM)).thenReturn(List.of(11L, 12L, 13L));
        when(dispatch.createRedistributionTask(11L, DORM, METRO))
                .thenThrow(new BusinessException("DESTINATION_FULL", "Destination hub 1 is full (BR-M7-03)"));

        ApprovalResult result = service.approve("7", "operator1");

        assertEquals("PENDING", result.status());
        assertTrue(result.taskIds().isEmpty());
        assertTrue(result.message().contains("BR-M7-03"));
        verify(events, never()).publish(any());
    }

    @Test
    @DisplayName("TC-M7-06 reject: REJECTED, no task, no event; a resolved proposal cannot be decided again")
    void tc06_reject() {
        RedistributionRecommendation r = stored(RecommendationStatus.PENDING, 2, 0);

        service.reject("7", "operator1", "Weekend, demand is low");

        assertEquals(RecommendationStatus.REJECTED, r.getStatus());
        assertEquals("Weekend, demand is low", r.getNote());
        verifyNoInteractions(dispatch, events);
        assertEquals("RECOMMENDATION_ALREADY_RESOLVED",
                assertThrows(BusinessException.class, () -> service.approve("7", "operator1")).getCode());
        assertEquals("RECOMMENDATION_NOT_FOUND",
                assertThrows(BusinessException.class, () -> service.approve("abc", "operator1")).getCode());
    }

    @Test
    @DisplayName("propose() from M8 is idempotent per (source, externalRef) and validates its input")
    void proposeIsIdempotent() {
        RedistributionRecommendation existing = stored(RecommendationStatus.PENDING, 2, 0);
        when(recommendations.findBySourceAndExternalRef("M8_SIMULATION", "R-1")).thenReturn(Optional.of(existing));

        assertEquals("7", service.propose("M8_SIMULATION", "R-1", DORM, METRO, 2, "again"));
        verify(recommendations, never()).save(any());
        assertEquals("VALIDATION_FAILED", assertThrows(BusinessException.class,
                () -> service.propose("M8_SIMULATION", "R-2", DORM, DORM, 2, "same hub")).getCode());

        String id = service.propose("M8_SIMULATION", "R-3", DORM, METRO, 2, "new");
        assertEquals("101", id);
    }
}
