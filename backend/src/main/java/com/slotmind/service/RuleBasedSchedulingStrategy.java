package com.slotmind.service;

import com.slotmind.domain.*;
import com.slotmind.repository.SlotRepository;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Component
public class RuleBasedSchedulingStrategy implements SchedulingStrategy {
    private final SlotRepository slots;

    public RuleBasedSchedulingStrategy(SlotRepository slots) {
        this.slots = slots;
    }

    @Override
    public List<SchedulingRecommendation> recommend(Appointment appointment, TriggerReason reason) {
        Provider original = appointment.getProvider();
        List<Slot> candidates = slots.findByStatusAndStartTimeAfterOrderByStartTimeAsc(
                SlotStatus.OPEN, LocalDateTime.now()).stream()
                .filter(slot -> reason != TriggerReason.PROVIDER_OFFLINE
                        || (!slot.getProvider().getId().equals(original.getId())
                        && slot.getProvider().getStatus() != ProviderStatus.OFFLINE))
                .sorted(Comparator
                        .comparing((Slot slot) -> reason != TriggerReason.PROVIDER_OFFLINE
                                && slot.getProvider().getId().equals(original.getId()) ? 0 : 1)
                        .thenComparing((Slot slot) -> slot.getProvider().getSpecialty() == original.getSpecialty() ? 0 : 1)
                        .thenComparing(Slot::getStartTime))
                .toList();

        return candidates.stream()
                .map(slot -> new SchedulingRecommendation(slot, confidence(slot, original, reason),
                        reasoning(slot, original, reason), SuggestionSource.RULE))
                .toList();
    }

    private double confidence(Slot slot, Provider original, TriggerReason reason) {
        if (slot.getProvider().getId().equals(original.getId())) return 0.95;
        if (slot.getProvider().getSpecialty() == original.getSpecialty()) return 0.85;
        return reason == TriggerReason.PROVIDER_OFFLINE ? 0.70 : 0.65;
    }

    private String reasoning(Slot slot, Provider original, TriggerReason reason) {
        if (slot.getProvider().getId().equals(original.getId()))
            return "Nearest open slot with the same provider.";
        if (slot.getProvider().getSpecialty() == original.getSpecialty())
            return "Nearest open slot with a provider matching the appointment specialty.";
        return reason == TriggerReason.PROVIDER_OFFLINE
                ? "Nearest open slot with an available alternate provider."
                : "Nearest available open slot.";
    }
}
