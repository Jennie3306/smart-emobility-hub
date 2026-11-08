package com.hcmut.emobility.m7dispatch.service;

import com.hcmut.emobility.m7dispatch.domain.*;
import com.hcmut.emobility.m7dispatch.repository.RedistributionTaskRepository;
import com.hcmut.emobility.m7dispatch.client.*;
import com.hcmut.emobility.m7dispatch.events.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedistributionServiceTest {

    @Mock private RedistributionTaskRepository repository;
    @Mock private VehicleClient vehicleClient;
    @Mock private HubClient hubClient;
    @Mock private RecommendationClient recommendationClient;
    @Mock private ApplicationEventPublisher eventPublisher;
    
    @InjectMocks private RedistributionService service;

    @Test
    void test_TCM701_TaoNhiemVuHopLe() {
        ExternalDtos.VehicleInfo xeRanh = new ExternalDtos.VehicleInfo("V01", "available");
        when(vehicleClient.checkVehicle(anyString())).thenReturn(xeRanh);
        when(repository.findActiveByVehicleId(anyString())).thenReturn(Optional.empty());
        
        ExternalDtos.HubInfo hubConCho = new ExternalDtos.HubInfo("HUB_B", 10, 5);
        when(hubClient.checkDestinationCapacity(anyString())).thenReturn(hubConCho);
        when(repository.save(any(RedistributionTask.class))).thenAnswer(i -> i.getArguments()[0]);

        RedistributionTask task = service.createRedistributionTask("V01", "HUB_A", "HUB_B");
        assertNotNull(task);
        assertEquals(RedistributionTaskStatus.CREATED, task.getStatus());
        verify(repository, times(1)).save(any(RedistributionTask.class));
    }

    @Test
    void test_TCM702_KhongDuocDieuChuyenXeDangCoNguoiDat() {
        ExternalDtos.VehicleInfo xeDangDat = new ExternalDtos.VehicleInfo("V01", "booked");
        when(vehicleClient.checkVehicle(anyString())).thenReturn(xeDangDat);

        Exception loi = assertThrows(RuntimeException.class, () -> {
            service.createRedistributionTask("V01", "HUB_A", "HUB_B");
        });
        assertTrue(loi.getMessage().contains("BR-M7-01"));
    }

    @Test
    void test_TCM703_KhongTaoHaiNhiemVuChoMotXe() {
        when(vehicleClient.checkVehicle(anyString())).thenReturn(new ExternalDtos.VehicleInfo("V01", "available"));
        RedistributionTask taskCu = new RedistributionTask();
        when(repository.findActiveByVehicleId(anyString())).thenReturn(Optional.of(taskCu));

        Exception loi = assertThrows(RuntimeException.class, () -> {
            service.createRedistributionTask("V01", "HUB_A", "HUB_B");
        });
        assertTrue(loi.getMessage().contains("BR-M7-02"));
    }

    @Test
    void test_TCM704_KhongDuocDieuChuyenNeuHubDichDaDay() {
        when(vehicleClient.checkVehicle(anyString())).thenReturn(new ExternalDtos.VehicleInfo("V01", "available"));
        when(repository.findActiveByVehicleId(anyString())).thenReturn(Optional.empty());
        ExternalDtos.HubInfo hubDay = new ExternalDtos.HubInfo("HUB_B", 10, 10);
        when(hubClient.checkDestinationCapacity(anyString())).thenReturn(hubDay);
        
        Exception loi = assertThrows(RuntimeException.class, () -> {
            service.createRedistributionTask("V01", "HUB_A", "HUB_B");
        });
        assertTrue(loi.getMessage().contains("BR-M7-03"));
    }

    @Test
    void test_TCM705_DuyetKhuyenNghiHopLe() {
        ExternalDtos.Recommendation khuyenNghi = new ExternalDtos.Recommendation("REC-01", "V01", "HUB_A", "HUB_B");
        when(recommendationClient.getRecommendation("REC-01")).thenReturn(khuyenNghi);
        when(vehicleClient.checkVehicle(anyString())).thenReturn(new ExternalDtos.VehicleInfo("V01", "available"));
        when(repository.findActiveByVehicleId(anyString())).thenReturn(Optional.empty());
        when(hubClient.checkDestinationCapacity(anyString())).thenReturn(new ExternalDtos.HubInfo("HUB_B", 10, 5));
        when(repository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        RedistributionTask task = service.approveRecommendation("REC-01");

        assertEquals(RedistributionTaskStatus.CREATED, task.getStatus());
        // Fix đỏ: Thêm Events. vào trước class
        verify(eventPublisher, times(1)).publishEvent(any(Events.RecommendationApprovedEvent.class));
    }

    @Test
    void test_TCM707_BatDauDieuChuyen() {
        RedistributionTask task = new RedistributionTask();
        task.setStatus(RedistributionTaskStatus.CREATED);
        when(repository.findById("TASK-01")).thenReturn(Optional.of(task));

        service.updateTaskStatus("TASK-01", RedistributionTaskStatus.IN_PROGRESS);
        assertEquals(RedistributionTaskStatus.IN_PROGRESS, task.getStatus());
        verify(repository, times(1)).save(task);
    }

    @Test
    void test_TCM708_HoanThanhNhiemVu_VaPhatSuKien() {
        RedistributionTask task = new RedistributionTask();
        task.setTaskId("TASK-01");
        task.setVehicleId("V01");
        task.setDestinationHubId("HUB_B");
        task.setStatus(RedistributionTaskStatus.IN_PROGRESS);
        when(repository.findById("TASK-01")).thenReturn(Optional.of(task));

        service.updateTaskStatus("TASK-01", RedistributionTaskStatus.DONE);
        assertEquals(RedistributionTaskStatus.DONE, task.getStatus());
        
        // Fix đỏ: Thêm Events. vào trước class
        verify(eventPublisher, times(1)).publishEvent(any(Events.DispatchCompletedEvent.class));
    }

    @Test
    void test_TCM709_DieuChuyenThatBai() {
        RedistributionTask task = new RedistributionTask();
        task.setStatus(RedistributionTaskStatus.IN_PROGRESS);
        when(repository.findById("TASK-01")).thenReturn(Optional.of(task));

        service.updateTaskStatus("TASK-01", RedistributionTaskStatus.FAILED);
        assertEquals(RedistributionTaskStatus.FAILED, task.getStatus());
    }

    @Test
    void test_TCM710_KhongChoQuayVeTrangThaiTruoc() {
        RedistributionTask taskDaXong = new RedistributionTask();
        taskDaXong.setStatus(RedistributionTaskStatus.DONE);
        when(repository.findById("TASK-01")).thenReturn(Optional.of(taskDaXong));

        Exception loi = assertThrows(RuntimeException.class, () -> {
            service.updateTaskStatus("TASK-01", RedistributionTaskStatus.IN_PROGRESS);
        });
        
        assertEquals("Không thể chuyển trạng thái ngược về InProgress", loi.getMessage());
    }
}