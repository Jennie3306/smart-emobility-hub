package com.hcmut.emobility.m6simulator.service;

public class SimulationAlreadyRunningException extends RuntimeException {
    public SimulationAlreadyRunningException() {
        super("SIMULATOR_ALREADY_RUNNING");
    }
}
