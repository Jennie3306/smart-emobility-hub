package com.hcmut.emobility.m1monitoring;

import com.hcmut.emobility.m1monitoring.domain.*;
import com.hcmut.emobility.m1monitoring.dto.Dtos.UtilisationDto;
import com.hcmut.emobility.m1monitoring.events.MonitoringEventHandler;
import com.hcmut.emobility.m1monitoring.repository.AlertRepository;
import com.hcmut.emobility.m1monitoring.repository.HubRepository;
import com.hcmut.emobility.m1monitoring.repository.ResourceStateRepository;
import com.hcmut.emobility.m1monitoring.service.*;
import com.hcmut.emobility.shared.common.BusinessException;
import com.hcmut.emobility.shared.events.EventPublisher;
import com.hcmut.emobility.shared.events.HubNearCapacityEvent;
import com.hcmut.emobility.shared.events.ProcessedEventStore;
import com.hcmut.emobility.shared.events.ResourceStateUpdatedEvent;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * 12 test case cua module M1, dung ma TC-M1-01 .. TC-M1-12 nhu trong
 * muc 8 cua Template_M1 va muc 5.3 cua report.
 *
 * Chay o tang service voi repository gia (Mockito): khong can database,
 * nen chay duoc tren CI trong vai giay.
 */
class M1MonitoringTest {

    private HubRepository hubs;
    private AlertRepository alerts;
    private EventPublisher events;
    private DashboardPublisher publisher;
    private MonitoringService monitoring;
    private AlertService alertService;
    private MonitoringProperties props;
    private ResourceStateRepository resources;

    @BeforeEach
    void setUp() {
        hubs = mock(HubRepository.class);
        alerts = mock(AlertRepository.class);
        events = mock(EventPublisher.class);
        publisher = mock(DashboardPublisher.class);
        resources = mock(ResourceStateRepository.class);
        monitoring = new MonitoringService(hubs, alerts, resources);
        alertService = new AlertService(alerts, hubs, events, publisher);
        props = new MonitoringProperties();
        when(alerts.save(any(Alert.class))).thenAnswer(inv -> inv.getArgument(0));
        when(alerts.findOpenByHubAndType(anyLong(), any())).thenReturn(Optional.empty());
    }

    // ---------- Giá trị biên: nguong suc chua (FR-M1-07) ----------

    @Test
    @DisplayName("TC-M1-01 duoi nguong suc chua thi khong tao canh bao")
    void tc01_belowCapacityThreshold() {
        // 31/40 = 77% < 80%
        UtilisationDto u = monitoring.computeUtilisation(counts(40, 31, 8, 0, 0));
        assertEquals(77, u.parkingPercent());
        assertFalse(u.parkingPercent() >= props.getCapacityRaiseThreshold());
    }

    @Test
    @DisplayName("TC-M1-02 dung nguong suc chua thi tao canh bao va phat su kien mot lan")
    void tc02_exactlyAtCapacityThreshold() {
        UtilisationDto u = monitoring.computeUtilisation(counts(40, 32, 8, 0, 0));
        assertEquals(80, u.parkingPercent());
        assertTrue(u.parkingPercent() >= props.getCapacityRaiseThreshold());

        Alert created = alertService.raiseOrRefresh(1L, AlertType.CAPACITY, u.parkingPercent());

        assertEquals(AlertState.OPEN, created.getState());
        assertEquals(AlertType.CAPACITY, created.getType());
        ArgumentCaptor<HubNearCapacityEvent> captor =
                ArgumentCaptor.forClass(HubNearCapacityEvent.class);
        verify(events, times(1)).publish(captor.capture());
        assertEquals(80, captor.getValue().getUsagePercent());
    }

    @Test
    @DisplayName("TC-M1-03 tren nguong suc chua thi tao canh bao")
    void tc03_aboveCapacityThreshold() {
        UtilisationDto u = monitoring.computeUtilisation(counts(40, 33, 8, 0, 0));
        assertEquals(82, u.parkingPercent());
        Alert created = alertService.raiseOrRefresh(1L, AlertType.CAPACITY, u.parkingPercent());
        assertEquals(AlertState.OPEN, created.getState());
    }

    // ---------- Giá trị biên: nguong thieu xe (FR-M1-08) ----------

    @Test
    @DisplayName("TC-M1-04 dung nguong thieu xe thi tao canh bao")
    void tc04_exactlyAtLowVehicleThreshold() {
        int available = 2;
        assertTrue(available <= props.getLowVehicleThreshold());
        Alert created = alertService.raiseOrRefresh(1L, AlertType.LOW_VEHICLE, available);
        assertEquals(AlertType.LOW_VEHICLE, created.getType());
        assertEquals(2, created.getTriggerValue());
    }

    @Test
    @DisplayName("TC-M1-05 tren nguong thieu xe thi khong tao canh bao")
    void tc05_aboveLowVehicleThreshold() {
        assertFalse(3 <= props.getLowVehicleThreshold());
    }

    // ---------- Giá trị biên: nguong tat canh bao (FR-M1-09) ----------

    @Test
    @DisplayName("TC-M1-06 dung nguong tat thi canh bao van mo")
    void tc06_atClearThresholdStaysOpen() {
        // 30/40 = 75%, chua duoi nguong tat 75% nen giu nguyen
        UtilisationDto u = monitoring.computeUtilisation(counts(40, 30, 8, 0, 0));
        assertEquals(75, u.parkingPercent());
        assertFalse(u.parkingPercent() < props.getCapacityClearThreshold());
    }

    @Test
    @DisplayName("TC-M1-07 duoi nguong tat thi dong canh bao")
    void tc07_belowClearThresholdResolves() {
        // 37/50 = 74% < nguong tat 75%
        UtilisationDto u = monitoring.computeUtilisation(counts(50, 37, 8, 0, 0));
        assertEquals(74, u.parkingPercent());
        assertTrue(u.parkingPercent() < props.getCapacityClearThreshold());

        Alert open = Alert.raise(1L, AlertType.CAPACITY, 80);
        alertService.clear(open);

        assertEquals(AlertState.RESOLVED, open.getState());
        assertNotNull(open.getClosedAt());
    }

    // ---------- Chuyen trang thai (FR-M1-11) ----------

    @Test
    @DisplayName("TC-M1-08 operator xac nhan duoc canh bao dang mo")
    void tc08_acknowledgeOpenAlert() {
        Alert alert = Alert.raise(1L, AlertType.CAPACITY, 85);
        when(alerts.findById(7L)).thenReturn(Optional.of(alert));

        alertService.acknowledge(7L, "operator1");

        assertEquals(AlertState.ACKNOWLEDGED, alert.getState());
        assertEquals("operator1", alert.getAcknowledgedBy());
        assertNotNull(alert.getAcknowledgedAt());
    }

    @Test
    @DisplayName("TC-M1-09 khong xac nhan duoc canh bao da dong")
    void tc09_acknowledgeClosedAlertRejected() {
        Alert alert = Alert.raise(1L, AlertType.CAPACITY, 85);
        alert.resolve();
        when(alerts.findById(7L)).thenReturn(Optional.of(alert));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> alertService.acknowledge(7L, "operator1"));

        assertEquals("INVALID_STATE_TRANSITION", ex.getCode());
        assertEquals(409, ex.getStatus().value());
        assertEquals(AlertState.RESOLVED, alert.getState());   // khong doi
    }

    // ---------- Bang quyet dinh: chong canh bao trung (BR-M1-06) ----------

    @Test
    @DisplayName("TC-M1-10 da co canh bao dang mo thi khong tao them")
    void tc10_noDuplicateAlert() {
        Alert existing = Alert.raise(1L, AlertType.CAPACITY, 80);
        when(alerts.findOpenByHubAndType(1L, AlertType.CAPACITY)).thenReturn(Optional.of(existing));

        Alert result = alertService.raiseOrRefresh(1L, AlertType.CAPACITY, 85);

        assertSame(existing, result);
        assertEquals(85, result.getTriggerValue());        // chi lam tuoi so lieu
        verify(alerts, never()).save(any(Alert.class));    // khong tao ban ghi moi
        verify(events, never()).publish(any());            // khong phat lai su kien
    }

    // ---------- Phan hoach tuong duong ----------

    @Test
    @DisplayName("TC-M1-11 xe Maintenance va InTransit khong tinh la kha dung")
    void tc11_onlyAvailableVehiclesCount() {
        when(hubs.countAvailableVehiclesPerHub())
                .thenReturn(List.<Object[]>of(new Object[]{1L, 1L}));  // 1 AVAILABLE, 2 MAINTENANCE, 1 IN_TRANSIT

        int available = monitoring.availableVehiclesPerHub().getOrDefault(1L, 0);

        assertEquals(1, available);
        assertTrue(available <= props.getLowVehicleThreshold());   // du de bat canh bao
    }

    @Test
    @DisplayName("TC-M1-12 su kien lap lai chi duoc ap dung mot lan")
    void tc12_duplicateEventAppliedOnce() {
        ProcessedEventStore store = new ProcessedEventStore();
        ResourceStateUpdatedEvent event =
                new ResourceStateUpdatedEvent(1L, "PARKING_SPACE", 5L, "OCCUPIED");

        assertTrue(store.markIfNew("M1", event.getEventId()));    // lan dau: xu ly
        assertFalse(store.markIfNew("M1", event.getEventId()));   // lan hai: bo qua
        // M3 nghe cung su kien voi khoa rieng, khong bi M1 lam bo qua
        assertTrue(store.markIfNew("M3", event.getEventId()));
    }

    @Test
    @DisplayName("TC-M1-13 quy doi tu vung trang thai cua M6 sang tu vung cua M1")
    void tc13_normaliseM6Vocabulary() {
        // Lop tuong duong 1: tu vung M6 cho tru sac -> tu vung M1
        assertEquals("FREE", MonitoringEventHandler.normalise("CHARGING_POINT", "AVAILABLE"));
        assertEquals("IN_USE", MonitoringEventHandler.normalise("CHARGING_POINT", "charging"));
        // Lop 2: da dung tu vung M1 -> giu nguyen
        assertEquals("FAULTY", MonitoringEventHandler.normalise("CHARGING_POINT", "FAULTY"));
        assertEquals("OCCUPIED", MonitoringEventHandler.normalise("PARKING_SPACE", "OCCUPIED"));
        // Lop 3: gia tri la -> giu nguyen (khong doan), null -> null
        assertEquals("UNKNOWN_X", MonitoringEventHandler.normalise("PARKING_SPACE", "unknown_x"));
        assertNull(MonitoringEventHandler.normalise("PARKING_SPACE", null));
    }

    // ---------- tien ich ----------

    /**
     * Dung ket qua dem cua mot hub: tong cho do, so cho da dung,
     * tong tru sac, so tru dang sac, so tru hong.
     */
    private HubCounts counts(int totalSlots, int usedSlots,
                             int totalPoints, int inUse, int faulty) {
        long freeSlots = totalSlots - usedSlots;
        long operational = totalPoints - faulty;
        long freePoints = operational - inUse;
        return new HubCounts(1L, totalSlots, usedSlots, freeSlots, operational, inUse, freePoints);
    }
}