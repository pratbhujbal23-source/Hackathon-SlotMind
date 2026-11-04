package com.slotmind.service;

import com.slotmind.domain.*;
import com.slotmind.repository.SlotRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RuleBasedSchedulingStrategyTest {
    @Test
    void prefersSameProviderBeforeMatchingSpecialty() {
        SlotRepository repository = Mockito.mock(SlotRepository.class);
        Provider original = new Provider("P1", "Original", ProviderSpecialty.GENERAL, ProviderStatus.BUSY);
        Provider sameSpecialty = new Provider("P2", "General", ProviderSpecialty.GENERAL, ProviderStatus.AVAILABLE);
        Slot originalProviderSlot = new Slot("S1", original, LocalDateTime.now().plusHours(1), 30, SlotStatus.OPEN);
        Slot specialtySlot = new Slot("S2", sameSpecialty, LocalDateTime.now().plusMinutes(15), 30, SlotStatus.OPEN);
        Appointment appointment = new Appointment("A1", "Patient", AppointmentType.ROUTINE, original,
                new Slot("OLD", original, LocalDateTime.now(), 30, SlotStatus.BOOKED), AppointmentStatus.CONFIRMED);
        Mockito.when(repository.findByStatusAndStartTimeAfterOrderByStartTimeAsc(
                Mockito.eq(SlotStatus.OPEN), Mockito.any())).thenReturn(List.of(specialtySlot, originalProviderSlot));

        List<SchedulingStrategy.SchedulingRecommendation> result =
                new RuleBasedSchedulingStrategy(repository).recommend(appointment, TriggerReason.INITIAL);

        assertEquals("S1", result.get(0).slot().getId());
        assertEquals(SuggestionSource.RULE, result.get(0).source());
    }

    @Test
    void excludesOfflineProviderForProviderOfflineTrigger() {
        SlotRepository repository = Mockito.mock(SlotRepository.class);
        Provider offline = new Provider("P1", "Offline", ProviderSpecialty.GENERAL, ProviderStatus.OFFLINE);
        Provider available = new Provider("P2", "Available", ProviderSpecialty.GENERAL, ProviderStatus.AVAILABLE);
        Slot unavailable = new Slot("S1", offline, LocalDateTime.now().plusHours(1), 30, SlotStatus.OPEN);
        Slot replacement = new Slot("S2", available, LocalDateTime.now().plusHours(2), 30, SlotStatus.OPEN);
        Appointment appointment = new Appointment("A1", "Patient", AppointmentType.URGENT, offline,
                new Slot("OLD", offline, LocalDateTime.now(), 30, SlotStatus.BOOKED), AppointmentStatus.CONFIRMED);
        Mockito.when(repository.findByStatusAndStartTimeAfterOrderByStartTimeAsc(
                Mockito.eq(SlotStatus.OPEN), Mockito.any())).thenReturn(List.of(unavailable, replacement));

        var result = new RuleBasedSchedulingStrategy(repository).recommend(appointment, TriggerReason.PROVIDER_OFFLINE);

        assertEquals(List.of("S2"), result.stream().map(r -> r.slot().getId()).toList());
    }
}
