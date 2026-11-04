package com.slotmind.service;

import com.slotmind.domain.*;
import com.slotmind.repository.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RescheduleServiceTest {
    @Test
    void acceptingSuggestionUpdatesAppointmentAndSlots() {
        ProviderRepository providers = mock(ProviderRepository.class);
        SlotRepository slots = mock(SlotRepository.class);
        AppointmentRepository appointments = mock(AppointmentRepository.class);
        SuggestionRepository suggestions = mock(SuggestionRepository.class);
        Provider oldProvider = new Provider("P1", "Old", ProviderSpecialty.GENERAL, ProviderStatus.OFFLINE);
        Provider newProvider = new Provider("P2", "New", ProviderSpecialty.GENERAL, ProviderStatus.AVAILABLE);
        Slot old = new Slot("OLD", oldProvider, LocalDateTime.now(), 30, SlotStatus.BOOKED);
        Slot replacement = new Slot("NEW", newProvider, LocalDateTime.now().plusHours(1), 30, SlotStatus.OPEN);
        Appointment appointment = new Appointment("A1", "Patient", AppointmentType.ROUTINE, oldProvider, old,
                AppointmentStatus.RESCHEDULE_PENDING);
        RescheduleSuggestion suggestion = new RescheduleSuggestion(appointment, replacement, newProvider, 0.9,
                "AI reason", TriggerReason.PROVIDER_OFFLINE, SuggestionSource.AI);
        when(suggestions.findById("S1")).thenReturn(Optional.of(suggestion));
        RescheduleService service = new RescheduleService(providers, slots, appointments, suggestions, mock(SchedulingStrategy.class));

        service.decide("S1", SuggestionStatus.ACCEPTED);

        assertEquals(SlotStatus.BOOKED, replacement.getStatus());
        assertEquals(SlotStatus.OPEN, old.getStatus());
        assertEquals(AppointmentStatus.RESCHEDULED, appointment.getStatus());
        assertEquals(SuggestionStatus.ACCEPTED, suggestion.getStatus());
    }

    @Test
    void rejectsSecondSuggestionForAlreadyBookedReplacement() {
        Provider provider = new Provider("P1", "Provider", ProviderSpecialty.GENERAL, ProviderStatus.AVAILABLE);
        Slot replacement = new Slot("NEW", provider, LocalDateTime.now().plusHours(1), 30, SlotStatus.OPEN);
        Appointment firstAppointment = new Appointment("A1", "First", AppointmentType.ROUTINE, provider,
                new Slot("OLD1", provider, LocalDateTime.now(), 30, SlotStatus.BOOKED), AppointmentStatus.RESCHEDULE_PENDING);
        Appointment secondAppointment = new Appointment("A2", "Second", AppointmentType.ROUTINE, provider,
                new Slot("OLD2", provider, LocalDateTime.now(), 30, SlotStatus.BOOKED), AppointmentStatus.RESCHEDULE_PENDING);
        RescheduleSuggestion first = new RescheduleSuggestion(firstAppointment, replacement, provider, 0.9,
                "first", TriggerReason.PROVIDER_OFFLINE, SuggestionSource.RULE);
        RescheduleSuggestion second = new RescheduleSuggestion(secondAppointment, replacement, provider, 0.9,
                "second", TriggerReason.PROVIDER_OFFLINE, SuggestionSource.RULE);
        SuggestionRepository suggestions = mock(SuggestionRepository.class);
        when(suggestions.findById("S1")).thenReturn(Optional.of(first));
        when(suggestions.findById("S2")).thenReturn(Optional.of(second));
        RescheduleService service = new RescheduleService(mock(ProviderRepository.class), mock(SlotRepository.class),
                mock(AppointmentRepository.class), suggestions, mock(SchedulingStrategy.class));

        service.decide("S1", SuggestionStatus.ACCEPTED);

        assertThrows(IllegalStateException.class, () -> service.decide("S2", SuggestionStatus.ACCEPTED));
        assertEquals(SuggestionStatus.PENDING, second.getStatus());
    }

    @Test
    void rejectionDoesNotChangeAppointmentOrSlot() {
        Provider provider = new Provider("P1", "Provider", ProviderSpecialty.GENERAL, ProviderStatus.AVAILABLE);
        Slot old = new Slot("OLD", provider, LocalDateTime.now(), 30, SlotStatus.BOOKED);
        Slot replacement = new Slot("NEW", provider, LocalDateTime.now().plusHours(1), 30, SlotStatus.OPEN);
        Appointment appointment = new Appointment("A1", "Patient", AppointmentType.ROUTINE, provider, old,
                AppointmentStatus.RESCHEDULE_PENDING);
        RescheduleSuggestion suggestion = new RescheduleSuggestion(appointment, replacement, provider, 0.8,
                "reason", TriggerReason.SLOT_CANCELLED, SuggestionSource.RULE);
        SuggestionRepository suggestions = mock(SuggestionRepository.class);
        when(suggestions.findById("S1")).thenReturn(Optional.of(suggestion));
        RescheduleService service = new RescheduleService(mock(ProviderRepository.class), mock(SlotRepository.class),
                mock(AppointmentRepository.class), suggestions, mock(SchedulingStrategy.class));

        service.decide("S1", SuggestionStatus.REJECTED);

        assertEquals(SuggestionStatus.REJECTED, suggestion.getStatus());
        assertEquals(AppointmentStatus.RESCHEDULE_PENDING, appointment.getStatus());
        assertEquals(SlotStatus.OPEN, replacement.getStatus());
    }
}
