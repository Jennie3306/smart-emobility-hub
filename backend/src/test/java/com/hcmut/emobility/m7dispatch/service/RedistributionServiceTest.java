package com.hcmut.emobility.m7dispatch.service;

import com.hcmut.emobility.m7dispatch.client.ExternalDtos;
import com.hcmut.emobility.m7dispatch.client.HubClient;
import com.hcmut.emobility.m7dispatch.client.VehicleClient;
import com.hcmut.emobility.m7dispatch.domain.RedistributionTask;
import com.hcmut.emobility.m7dispatch.domain.RedistributionTaskStatus;
import com.hcmut.emobility.m7dispatch.repository.RedistributionTaskRepository;
import com.hcmut.emobility.shared.common.BusinessException;
import com.hcmut.emobility.shared.contracts.ParkingDock;
import com.hcmut.emobility.shared.events.DispatchCompletedEvent;
import com.hcmut.emobility.shared.events.DispatchFailedEvent;
import com.hcmut.emobility.shared.events.DispatchStartedEvent;
import com.hcmut.emobility.shared.events.EventPublisher;
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
 * Test case TC-M7-01..04, 07..12 theo muc 8 Template_M7 (TC-M7-05, 06 va 15..20: RecommendationServiceTest).
 * Id dung Long de khop hop dong su kien (Phu luc A). Suc chua Hub dich = cho trong trong quy cho do chung
 * cua M4 tru so xe dang duoc dieu toi (BR-M7-03, Appendix A v2).
 */
@ExtendWith(MockitoExtension.class)
class RedistributionServiceTest {

    private static final long VEHICLE = 1L, HUB_A = 1L, HUB_B = 2L, TASK = 10L;

    @Mock private RedistributionTaskRepository repository;
    @Mock private VehicleClient vehicleClient;
    @Mock private HubClient hubClient;
    @Mock private EventPublisher eventPublisher;
    @Mock private ParkingDock parkingDock;

    @InjectMocks private RedistributionService service;

    private void vehicleIs(String state) {
        when(vehicleClient.checkVehicle(anyLong()))
                .thenReturn(Optional.of(new ExternalDtos.VehicleInfo(VEHICLE, state, HUB_A)));
    }

    private void destinationHas(int freeSpaces, int incomingVehicles) {
        when(hubClient.checkDestinationCapacity(anyLong()))
                .thenReturn(Optional.of(new ExternalDtos.HubInfo(HUB_B, freeSpaces, incomingVehicles)));
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
    @DisplayName("TC-M7-04 chan dieu chuyen den hub da day (bien: 10 cho trong, 10 xe dang toi)")
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
    @DisplayName("TC-M7-07 CREATED -> IN_PROGRESS: xe roi cho o Hub nguon, phat DispatchStartedEvent cho M3")
    void tc07_startTask() {
        RedistributionTask task = taskIn(RedistributionTaskStatus.CREATED);
        vehicleIs("AVAILABLE");
        when(repository.save(task)).thenReturn(task);

        service.updateTaskStatus(TASK, RedistributionTaskStatus.IN_PROGRESS);

        assertEquals(RedistributionTaskStatus.IN_PROGRESS, task.getStatus());
        verify(parkingDock).undockRentalVehicle(VEHICLE);
        ArgumentCaptor<DispatchStartedEvent> captor = ArgumentCaptor.forClass(DispatchStartedEvent.class);
        verify(eventPublisher).publish(captor.capture());
        assertEquals(HUB_A, captor.getValue().getFromHubId());
    }

    @Test
    @DisplayName("TC-M7-08 IN_PROGRESS -> DONE va phat DispatchCompletedEvent cho M3, M1")
    void tc08_completeTask() {
        RedistributionTask task = taskIn(RedistributionTaskStatus.IN_PROGRESS);
        when(repository.save(task)).thenReturn(task);

        service.updateTaskStatus(TASK, RedistributionTaskStatus.DONE);

        assertEquals(RedistributionTaskStatus.DONE, task.getStatus());
        verify(parkingDock).dockRentalVehicle(VEHICLE, HUB_B);
        ArgumentCaptor<DispatchCompletedEvent> captor = ArgumentCaptor.forClass(DispatchCompletedEvent.class);
        verify(eventPublisher).publish(captor.capture());
        assertEquals(VEHICLE, captor.getValue().getVehicleId());
        assertEquals(HUB_A, captor.getValue().getFromHubId());
        assertEquals(HUB_B, captor.getValue().getToHubId());
    }

    @Test
    @DisplayName("TC-M7-09 IN_PROGRESS -> FAILED: xe quay ve Hub nguon, phat DispatchFailedEvent cho M3")
    void tc09_failTask() {
        RedistributionTask task = taskIn(RedistributionTaskStatus.IN_PROGRESS);
        when(repository.save(task)).thenReturn(task);

        service.updateTaskStatus(TASK, RedistributionTaskStatus.FAILED);

        assertEquals(RedistributionTaskStatus.FAILED, task.getStatus());
        verify(parkingDock).dockRentalVehicle(VEHICLE, HUB_A);
        verify(eventPublisher).publish(any(DispatchFailedEvent.class));
    }

    @Test
    @DisplayName("TC-M7-11 IN_PROGRESS -> DONE khi Hub dich vua het cho: DESTINATION_FULL, nhiem vu van IN_PROGRESS")
    void tc11_destinationFilledUpBeforeArrival() {
        RedistributionTask task = taskIn(RedistributionTaskStatus.IN_PROGRESS);
        when(parkingDock.dockRentalVehicle(VEHICLE, HUB_B))
                .thenThrow(new BusinessException("HUB_FULL", "No free parking space is left at this Hub"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateTaskStatus(TASK, RedistributionTaskStatus.DONE));

        assertEquals("DESTINATION_FULL", ex.getCode());
        assertEquals(RedistributionTaskStatus.IN_PROGRESS, task.getStatus());
        verify(repository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("TC-M7-12 xe vua bi sinh vien dat sau khi tao nhiem vu: khong cho khoi hanh (BR-M7-01, tranh chap voi M2)")
    void tc12_vehicleReservedBeforeDeparture() {
        RedistributionTask task = taskIn(RedistributionTaskStatus.CREATED);
        vehicleIs("RESERVED");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateTaskStatus(TASK, RedistributionTaskStatus.IN_PROGRESS));

        assertEquals("VEHICLE_RESERVED", ex.getCode());
        assertEquals(RedistributionTaskStatus.CREATED, task.getStatus());
        verify(parkingDock, never()).undockRentalVehicle(anyLong());
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
