package com.hcmut.emobility.m1monitoring.service;

import com.hcmut.emobility.m1monitoring.domain.Alert;
import com.hcmut.emobility.m1monitoring.domain.AlertType;
import com.hcmut.emobility.m1monitoring.domain.Hub;
import com.hcmut.emobility.m1monitoring.domain.HubCounts;
import com.hcmut.emobility.m1monitoring.dto.Dtos.UtilisationDto;
import com.hcmut.emobility.m1monitoring.repository.AlertRepository;
import com.hcmut.emobility.m1monitoring.repository.HubRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

/**
 * UC-M1-07 - FR non-interactive, tac nhan la Timer chu khong phai nguoi dung.
 *
 * Dung fixedDelay chu khong phai fixedRate: lan chay sau chi bat dau khi lan
 * truoc da xong, nen hai chu ky khong bao gio chay chong len nhau va sinh
 * canh bao trung. Day chinh la nhanh "Previous cycle finished?" tren activity diagram.
 */
@Component
public class ThresholdEvaluationTask {

    private static final Logger log = LoggerFactory.getLogger(ThresholdEvaluationTask.class);

    private final HubRepository hubs;
    private final AlertRepository alerts;
    private final MonitoringService monitoring;
    private final AlertService alertService;
    private final DashboardPublisher publisher;
    private final MonitoringProperties props;

    public ThresholdEvaluationTask(HubRepository hubs, AlertRepository alerts,
                                   MonitoringService monitoring, AlertService alertService,
                                   DashboardPublisher publisher, MonitoringProperties props) {
        this.hubs = hubs;
        this.alerts = alerts;
        this.monitoring = monitoring;
        this.alertService = alertService;
        this.publisher = publisher;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "${app.monitoring.evaluation-interval-ms:30000}",
               initialDelayString = "${app.monitoring.initial-delay-ms:10000}")
    @Transactional
    public void evaluateAll() {
        Map<Long, Integer> availableVehicles = monitoring.availableVehiclesPerHub();
        Map<Long, HubCounts> counts = monitoring.countsPerHub();

        for (Hub hub : hubs.findAllByOrderByIdAsc()) {
            try {
                evaluateHub(hub,
                        counts.getOrDefault(hub.getId(), HubCounts.empty(hub.getId())),
                        availableVehicles.getOrDefault(hub.getId(), 0));
            } catch (Exception ex) {
                // Mot hub loi khong duoc lam hong ca chu ky.
                log.error("[M1] Bo qua hub {} do loi: {}", hub.getId(), ex.getMessage(), ex);
            }
        }
    }

    private void evaluateHub(Hub hub, HubCounts counts, int availableVehicles) {
        UtilisationDto u = monitoring.computeUtilisation(counts);

        if (u.parkingPercent() != null) {
            evaluate(hub.getId(), AlertType.CAPACITY, u.parkingPercent(),
                    u.parkingPercent() >= props.getCapacityRaiseThreshold(),
                    u.parkingPercent() <  props.getCapacityClearThreshold());
        }

        evaluate(hub.getId(), AlertType.LOW_VEHICLE, availableVehicles,
                availableVehicles <= props.getLowVehicleThreshold(),
                availableVehicles >= props.getLowVehicleClearThreshold());

        monitoring.buildHubStatus(hub.getId()).ifPresent(publisher::publishHubStatus);
    }

    /**
     * Ba ket cuc co the cho moi loai canh bao:
     *   vuot nguong + chua co canh bao  -> tao moi va phat su kien
     *   vuot nguong + da co canh bao    -> chi lam tuoi so lieu (BR-M1-06)
     *   khong vuot + da co canh bao + da duoi nguong tat -> dong canh bao
     */
    private void evaluate(Long hubId, AlertType type, int value, boolean breached, boolean cleared) {
        Optional<Alert> open = alerts.findOpenByHubAndType(hubId, type);

        if (breached) {
            alertService.raiseOrRefresh(hubId, type, value);
        } else if (open.isPresent() && cleared) {
            alertService.clear(open.get());
        }
    }
}
