package com.slotmind.service;

import com.slotmind.api.ApiDtos;
import com.slotmind.domain.*;
import com.slotmind.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class RescheduleService {
    private final ProviderRepository providers;
    private final SlotRepository slots;
    private final AppointmentRepository appointments;
    private final SuggestionRepository suggestions;
    private final SchedulingStrategy schedulingStrategy;
    private RescheduleService asyncService;

    public RescheduleService(ProviderRepository providers, SlotRepository slots, AppointmentRepository appointments,
            SuggestionRepository suggestions, SchedulingStrategy schedulingStrategy) {
        this.providers = providers;
        this.slots = slots;
        this.appointments = appointments;
        this.suggestions = suggestions;
        this.schedulingStrategy = schedulingStrategy;
    }

    @Autowired
    void setAsyncService(@Lazy RescheduleService asyncService) {
        this.asyncService = asyncService;
    }

    @Transactional
    public Appointment book(ApiDtos.CreateAppointmentRequest r) {
        Provider p = providers.findById(r.providerId())
                .orElseThrow(() -> new EntityNotFoundException("Provider not found"));
        Slot s = slots.findById(r.slotId()).orElseThrow(() -> new EntityNotFoundException("Slot not found"));
        if (s.getStatus() != SlotStatus.OPEN || !s.getProvider().getId().equals(p.getId()))
            throw new IllegalStateException("Slot is not open for this provider");
        s.setStatus(SlotStatus.BOOKED);
        Appointment a = appointments.save(new Appointment("APT-" + System.currentTimeMillis(), r.patientName(),
                r.appointmentType(), p, s, AppointmentStatus.CONFIRMED));
        return a;
    }

    @Transactional
    public void providerStatus(String id, ProviderStatus status) {
        Provider p = providers.findById(id).orElseThrow(() -> new EntityNotFoundException("Provider not found"));
        p.setStatus(status);
        providers.save(p);
        if (status == ProviderStatus.OFFLINE)
            CompletableFuture.runAsync(() -> asyncTarget().generateForProvider(p, TriggerReason.PROVIDER_OFFLINE));
    }

    @Transactional
    public void slotStatus(String id, SlotStatus status) {
        Slot s = slots.findById(id).orElseThrow(() -> new EntityNotFoundException("Slot not found"));
        s.setStatus(status);
        slots.save(s);
        if (status == SlotStatus.CANCELLED)
            CompletableFuture.runAsync(() -> asyncTarget().generateForAppointment(s, TriggerReason.SLOT_CANCELLED));
    }

    private RescheduleService asyncTarget() {
        return asyncService == null ? this : asyncService;
    }

    @Async
    @Transactional
    public void generateForProvider(Provider p, TriggerReason reason) {
        appointments.findByProviderAndStatus(p, AppointmentStatus.CONFIRMED).forEach(a -> generate(a, reason));
    }

    @Async
    @Transactional
    public void generateForAppointment(Slot s, TriggerReason reason) {
        appointments.findByProviderAndStatus(s.getProvider(), AppointmentStatus.CONFIRMED).stream()
                .filter(a -> a.getSlot().getId().equals(s.getId())).forEach(a -> generate(a, reason));
    }

    @Transactional
    public synchronized RescheduleSuggestion generate(Appointment a, TriggerReason reason) {
        var existing = suggestions.findByAppointmentAndTriggerReasonAndStatus(a, reason, SuggestionStatus.PENDING);
        if (existing.isPresent())
            return existing.get();
        a.markReschedulePending();
        var recommendations = schedulingStrategy.recommend(a, reason);
        var reservedSlotIds = suggestions.findByStatus(SuggestionStatus.PENDING).stream()
                .map(suggestion -> suggestion.getRecommendedSlot().getId())
                .collect(java.util.stream.Collectors.toSet());
        var recommendation = recommendations.stream()
                .filter(candidate -> !reservedSlotIds.contains(candidate.slot().getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No replacement slot available"));
        Slot candidate = recommendation.slot();
        return suggestions.save(new RescheduleSuggestion(a, candidate, candidate.getProvider(),
                recommendation.confidence(), recommendation.reasoning(), reason));
    }

    @Transactional
    public void decide(String id, SuggestionStatus decision) {
        RescheduleSuggestion s = suggestions.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Suggestion not found"));
        if (s.getStatus() != SuggestionStatus.PENDING)
            throw new IllegalStateException("Suggestion already decided");
        if (decision == SuggestionStatus.REJECTED) {
            s.reject();
            return;
        }
        Appointment a = s.getAppointment();
        Slot replacement = s.getRecommendedSlot();
        if (replacement.getStatus() != SlotStatus.OPEN)
            throw new IllegalStateException("Replacement slot is no longer available");
        Slot old = a.getSlot();
        replacement.setStatus(SlotStatus.BOOKED);
        old.setStatus(SlotStatus.OPEN);
        a.reschedule(replacement.getProvider(), replacement);
        s.accept();
    }

    public List<Appointment> appointments(AppointmentStatus status) {
        return status == null ? appointments.findAll() : appointments.findByStatus(status);
    }

    public List<RescheduleSuggestion> suggestions(SuggestionStatus status) {
        return status == null ? suggestions.findAll() : suggestions.findByStatus(status);
    }
}
