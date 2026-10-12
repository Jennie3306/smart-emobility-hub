package com.hcmut.emobility.m3vehicle.config;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
/** Separate persistence configuration so MVC-only tests do not require a JPA metamodel. */
@Configuration
@EnableJpaAuditing
public class VehicleAuditConfig {}
