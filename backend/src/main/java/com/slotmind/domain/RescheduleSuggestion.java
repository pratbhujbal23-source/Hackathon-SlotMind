package com.slotmind.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity @Getter @NoArgsConstructor
public class RescheduleSuggestion {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private String id;
    @ManyToOne(optional = false) private Appointment appointment;
    @ManyToOne(optional = false) private Slot recommendedSlot;
    @ManyToOne private Provider alternateProvider;
    private double confidence;
    @Column(length = 2000) private String reasoning;
    @Enumerated(EnumType.STRING) private SuggestionStatus status;
    @Enumerated(EnumType.STRING) private TriggerReason triggerReason;
    public RescheduleSuggestion(Appointment appointment, Slot slot, Provider alternateProvider,
                                 double confidence, String reasoning, TriggerReason triggerReason) {
        this.appointment = appointment; this.recommendedSlot = slot; this.alternateProvider = alternateProvider;
        this.confidence = confidence; this.reasoning = reasoning; this.status = SuggestionStatus.PENDING;
        this.triggerReason = triggerReason;
    }
    public void accept() { status = SuggestionStatus.ACCEPTED; }
    public void reject() { status = SuggestionStatus.REJECTED; }
}
