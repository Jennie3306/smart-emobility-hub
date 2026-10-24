package com.hcmut.emobility.m1monitoring.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Cac nguong cua M1, doc tu application.yml (khoi app.monitoring).
 * De o day thay vi viet cung trong code: luc quay demo co the ha chu ky
 * xuong 10 giay ma khong phai bien dich lai.
 */
@Component
@ConfigurationProperties(prefix = "app.monitoring")
public class MonitoringProperties {

    private int capacityRaiseThreshold = 80;   // BR-M1-03 (%)
    private int capacityClearThreshold = 75;   // BR-M1-07 (%)
    private int lowVehicleThreshold = 2;       // BR-M1-04 (xe)
    private int lowVehicleClearThreshold = 4;  // BR-M1-07 (xe)

    public int getCapacityRaiseThreshold() { return capacityRaiseThreshold; }
    public void setCapacityRaiseThreshold(int v) { this.capacityRaiseThreshold = v; }

    public int getCapacityClearThreshold() { return capacityClearThreshold; }
    public void setCapacityClearThreshold(int v) { this.capacityClearThreshold = v; }

    public int getLowVehicleThreshold() { return lowVehicleThreshold; }
    public void setLowVehicleThreshold(int v) { this.lowVehicleThreshold = v; }

    public int getLowVehicleClearThreshold() { return lowVehicleClearThreshold; }
    public void setLowVehicleClearThreshold(int v) { this.lowVehicleClearThreshold = v; }
}
