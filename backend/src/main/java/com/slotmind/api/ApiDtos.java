package com.slotmind.api;

import com.slotmind.domain.*;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

public final class ApiDtos {
    private ApiDtos() {
    }

    public record CreateAppointmentRequest(@NotBlank String patientName, @NotNull AppointmentType appointmentType,
            @NotBlank String providerId, @NotBlank String slotId) {
    }

    public record StatusRequest(@NotNull ProviderStatus status) {
    }

    public record SlotStatusRequest(@NotNull SlotStatus status) {
    }

    public record SuggestionDecision(@NotNull SuggestionStatus status) {
    }

    public record AppointmentResponse(String id, String patientName, AppointmentType appointmentType,
            String providerId, String slotId, LocalDateTime startTime, AppointmentStatus status) {
    }

    public record ProviderResponse(String id, String name, ProviderSpecialty specialty, ProviderStatus status) {
    }

    public record SuggestionResponse(String id, String appointmentId, String recommendedSlotId,
            String alternateProviderId, double confidence, String reasoning,
            SuggestionStatus status, TriggerReason triggerReason, SuggestionSource source) {
    }
}
