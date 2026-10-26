package com.hcmut.emobility.m1monitoring.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

/**
 * Hub thuoc so huu cua M6/M4; M1 anh xa lai o dang chi doc de lay ten va vi tri.
 * Khong co setter va danh dau @Immutable de Hibernate khong bao gio ghi.
 */
@Entity
@Table(name = "hub")
@Immutable
public class Hub {

    @Id
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 200)
    private String location;

    protected Hub() { }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getLocation() { return location; }
}
