package com.slotmind.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
public class Appointment {
    @Id
    private String id;
    private String patientName;
    @Enumerated(EnumType.STRING)
    private AppointmentType appointmentType;
    @ManyToOne(optional = false)
    private Provider provider;
    @ManyToOne(optional = false)
    private Slot slot;
    @Enumerated(EnumType.STRING)
    private AppointmentStatus status;

    public Appointment(String id, String patientName, AppointmentType type, Provider provider, Slot slot,
            AppointmentStatus status) {
        this.id = id;
        this.patientName = patientName;
        this.appointmentType = type;
        this.provider = provider;
        this.slot = slot;
        this.status = status;
    }

    public void markReschedulePending() {
        this.status = AppointmentStatus.RESCHEDULE_PENDING;
    }

    public void reschedule(Provider provider, Slot slot) {
        this.provider = provider;
        this.slot = slot;
        this.status = AppointmentStatus.RESCHEDULED;
    }
}
