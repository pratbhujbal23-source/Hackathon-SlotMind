package com.slotmind.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
public class Slot {
    @Id
    private String id;
    @ManyToOne(optional = false)
    private Provider provider;
    private LocalDateTime startTime;
    private int durationMinutes;
    @Enumerated(EnumType.STRING)
    private SlotStatus status;

    public Slot(String id, Provider provider, LocalDateTime startTime, int durationMinutes, SlotStatus status) {
        this.id = id;
        this.provider = provider;
        this.startTime = startTime;
        this.durationMinutes = durationMinutes;
        this.status = status;
    }

    public void setStatus(SlotStatus status) {
        this.status = status;
    }
}
