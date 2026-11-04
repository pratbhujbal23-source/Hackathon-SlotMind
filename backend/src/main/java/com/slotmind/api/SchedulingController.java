package com.slotmind.api;

import com.slotmind.api.ApiDtos.*;
import com.slotmind.domain.*;
import com.slotmind.service.RescheduleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping
public class SchedulingController {
    private final RescheduleService service;

    public SchedulingController(RescheduleService service) {
        this.service = service;
    }

    @PostMapping("/appointments")
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentResponse book(@Valid @RequestBody CreateAppointmentRequest r) {
        return toResponse(service.book(r));
    }

    @GetMapping("/appointments")
    public List<AppointmentResponse> appointments(@RequestParam(required = false) AppointmentStatus status) {
        return service.appointments(status).stream().map(this::toResponse).toList();
    }

    @GetMapping("/providers")
    public List<ProviderResponse> providers() {
        return service.providers().stream()
                .map(p -> new ProviderResponse(p.getId(), p.getName(), p.getSpecialty(), p.getStatus()))
                .toList();
    }

    @PatchMapping("/providers/{id}/status")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void provider(@PathVariable String id, @Valid @RequestBody StatusRequest r) {
        service.providerStatus(id, r.status());
    }

    @PatchMapping("/slots/{id}/status")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void slot(@PathVariable String id, @Valid @RequestBody SlotStatusRequest r) {
        service.slotStatus(id, r.status());
    }

    @PostMapping("/appointments/{id}/suggest")
    @ResponseStatus(HttpStatus.CREATED)
    public SuggestionResponse suggest(@PathVariable String id) {
        Appointment a = service.appointments(null).stream().filter(x -> x.getId().equals(id)).findFirst().orElseThrow();
        return toSuggestion(service.generate(a, TriggerReason.INITIAL));
    }

    @GetMapping("/suggestions")
    public List<SuggestionResponse> suggestions(@RequestParam(required = false) SuggestionStatus status) {
        return service.suggestions(status).stream().map(this::toSuggestion).toList();
    }

    @PatchMapping("/suggestions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void decide(@PathVariable String id, @Valid @RequestBody SuggestionDecision d) {
        service.decide(id, d.status());
    }

    private AppointmentResponse toResponse(Appointment a) {
        return new AppointmentResponse(a.getId(), a.getPatientName(), a.getAppointmentType(), a.getProvider().getId(),
                a.getSlot().getId(), a.getSlot().getStartTime(), a.getStatus());
    }

    private SuggestionResponse toSuggestion(RescheduleSuggestion s) {
        return new SuggestionResponse(s.getId(), s.getAppointment().getId(), s.getRecommendedSlot().getId(),
                s.getAlternateProvider() == null ? null : s.getAlternateProvider().getId(), s.getConfidence(),
                s.getReasoning(), s.getStatus(), s.getTriggerReason(), s.getSource());
    }
}
