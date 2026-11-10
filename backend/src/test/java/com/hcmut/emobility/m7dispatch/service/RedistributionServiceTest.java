package com.hcmut.emobility.m7dispatch.service;

import com.hcmut.emobility.m7dispatch.client.ExternalDtos;
import com.hcmut.emobility.m7dispatch.client.HubClient;
import com.hcmut.emobility.m7dispatch.client.RecommendationClient;
import com.hcmut.emobility.m7dispatch.client.VehicleClient;
import com.hcmut.emobility.m7dispatch.domain.RedistributionTask;
import com.hcmut.emobility.m7dispatch.domain.RedistributionTaskStatus;
import com.hcmut.emobility.m7dispatch.repository.RedistributionTaskRepository;
import com.hcmut.emobility.shared.common.BusinessException;
import com.hcmut.emobility.shared.events.DispatchCompletedEvent;
import com.hcmut.emobility.shared.events.EventPublisher;
import com.hcmut.emobility.shared.events.RecommendationApprovedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Test case TC-M7-01..10 theo muc 8 Template_M7.
 * Id dung Long de khop hop dong su kien (Phu luc A).
 */
@ExtendWith(MockitoExtension.class)
class RedistributionServiceTest {

    private static final long VEHICLE = 1L, HUB_A = 1L, HUB_B = 2L, TASK = 10L;

    @Mock private RedistributionTaskRepository repository;
    @Mock private VehicleClient vehicleClient;
    @Mock private HubClient hubClient;
    @Mock private RecommendationClient recommendationClient;
    @Mock private EventPublisher eventPublisher;

    @InjectMocks private RedistributionService service;

    private void vehicleIs(String state) {
        when(vehicleClient.checkVehicle(anyLong()))
                .thenReturn(Optional.of(new ExternalDtos.VehicleInfo(VEHICLE, state, HUB_A)));
    }

    private void destinationHas(int capacity, int vehicles) {
        when(hubClient.checkDestinationCapacity(anyLong()))
                .thenReturn(Optional.of(new ExternalDtos.HubInfo(HUB_B, capacity, vehicles)));
    }

    private RedistributionTask taskIn(RedistributionTaskStatus status) {
        RedistributionTask task = new RedistributionTask();
        task.setTaskId(TASK);
        task.setVehicleId(VEHICLE);
        task.setSourceHubId(HUB_A);
        task.setDestinationHubId(HUB_B);
        task.setStatus(status);
        when(repository.findById(TASK)).thenReturn(Optional.of(task));
        return task;
    }

    @Test
    @DisplayName("TC-M7-01 tao nhiem vu voi du lieu hop le")
    void tc01_createValidTask() {
        vehicleIs("AVAILABLE");
        when(repository.findActiveByVehicleId(anyLong())).thenReturn(Optional.empty());
        destinationHas(10, 5);
        when(repository.save(any(RedistributionTask.class))).thenAnswer(i -> i.getArgument(0));

        RedistributionTask task = service.createRedistributionTask(VEHICLE, HUB_A, HUB_B);

        assertEquals(RedistributionTaskStatus.CREATED, task.getStatus());
        verify(repository, times(1)).save(any(RedistributionTask.class));
    }

    @Test
    @DisplayName("TC-M7-02 chan dieu chuyen xe dang duoc dat")
    void tc02_blockReservedVehicle() {
        vehicleIs("RESERVED");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createRedistributionTask(VEHICLE, HUB_A, HUB_B));

        assertEquals("VEHICLE_RESERVED", ex.getCode());
        assertTrue(ex.getMessage().contains("BR-M7-01"));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("TC-M7-03 chan hai nhiem vu cung nham vao mot xe")
    void tc03_blockSecondActiveTask() {
        vehicleIs("AVAILABLE");
        when(repository.findActiveByVehicleId(anyLong())).thenReturn(Optional.of(new RedistributionTask()));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createRedistributionTask(VEHICLE, HUB_A, HUB_B));

        assertEquals("TASK_ALREADY_ACTIVE", ex.getCode());
        assertTrue(ex.getMessage().contains("BR-M7-02"));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("TC-M7-04 chan dieu chuyen den hub da day (gia tri bien: 10/10)")
    void tc04_blockFullDestination() {
        vehicleIs("AVAILABLE");
        when(repository.findActiveByVehicleId(anyLong())).thenReturn(Optional.empty());
        destinationHas(10, 10);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createRedistributionTask(VEHICLE, HUB_A, HUB_B));

        assertEquals("DESTINATION_FULL", ex.getCode());
        assertTrue(ex.getMessage().contains("BR-M7-03"));
    }

    @Test
    @DisplayName("TC-M7-05 duyet khuyen nghi hop le thi tao nhiem vu va phat su kien")
    void tc05_approveRecommendation() {
        when(recommendationClient.getRecommendation("REC-01")).thenReturn(Optional.of(
                new ExternalDtos.Recommendation("REC-01", VEHICLE, HUB_A, HUB_B)));
        vehicleIs("AVAILABLE");
        when(repository.findActiveByVehicleId(anyLong())).thenReturn(Optional.empty());
        destinationHas(10, 5);
        when(repository.save(any(RedistributionTask.class))).thenAnswer(i -> i.getArgument(0));

        RedistributionTask task = service.approveRecommendation("REC-01");

        assertEquals(RedistributionTaskStatus.CREATED, task.getStatus());
        assertEquals("REC-01", task.getRecommendationId());
        verify(eventPublisher, times(1)).publish(any(RecommendationApprovedEvent.class));
    }

    @Test
    @DisplayName("TC-M7-06 khuyen nghi khong ton tai thi khong tao nhiem vu")
    void tc06_unknownRecommendation() {
        when(recommendationClient.getRecommendation("REC-X")).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.approveRecommendation("REC-X"));

        assertEquals("RECOMMENDATION_NOT_FOUND", ex.getCode());
        verify(repository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("TC-M7-07 CREATED -> IN_PROGRESS khi bat dau dieu chuyen")
    void tc07_startTask() {
        RedistributionTask task = taskIn(RedistributionTaskStatus.CREATED);
        when(repository.save(task)).thenReturn(task);

        service.updateTaskStatus(TASK, RedistributionTaskStatus.IN_PROGRESS);

        assertEquals(RedistributionTaskStatus.IN_PROGRESS, task.getStatus());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("TC-M7-08 IN_PROGRESS -> DONE va phat DispatchCompletedEvent cho M3, M1")
    void tc08_completeTask() {
        RedistributionTask task = taskIn(RedistributionTaskStatus.IN_PROGRESS);
        when(repository.save(task)).thenReturn(task);

        service.updateTaskStatus(TASK, RedistributionTaskStatus.DONE);

        assertEquals(RedistributionTaskStatus.DONE, task.getStatus());
        ArgumentCaptor<DispatchCompletedEvent> captor = ArgumentCaptor.forClass(DispatchCompletedEvent.class);
        verify(eventPublisher).publish(captor.capture());
        assertEquals(VEHICLE, captor.getValue().getVehicleId());
        assertEquals(HUB_A, captor.getValue().getFromHubId());
        assertEquals(HUB_B, captor.getValue().getToHubId());
    }

    @Test
    @DisplayName("TC-M7-09 IN_PROGRESS -> FAILED khi dieu chuyen that bai")
    void tc09_failTask() {
        RedistributionTask task = taskIn(RedistributionTaskStatus.IN_PROGRESS);
        when(repository.save(task)).thenReturn(task);

        service.updateTaskStatus(TASK, RedistributionTaskStatus.FAILED);

        assertEquals(RedistributionTaskStatus.FAILED, task.getStatus());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("TC-M7-10 chan chuyen trang thai khong hop le (DONE -> IN_PROGRESS, CREATED -> DONE)")
    void tc10_rejectInvalidTransitions() {
        taskIn(RedistributionTaskStatus.DONE);
        BusinessException back = assertThrows(BusinessException.class,
                () -> service.updateTaskStatus(TASK, RedistributionTaskStatus.IN_PROGRESS));
        assertEquals("INVALID_STATE_TRANSITION", back.getCode());

        taskIn(RedistributionTaskStatus.CREATED);
        BusinessException skip = assertThrows(BusinessException.class,
                () -> service.updateTaskStatus(TASK, RedistributionTaskStatus.DONE));
        assertEquals("INVALID_STATE_TRANSITION", skip.getCode());

        verify(repository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }
}
