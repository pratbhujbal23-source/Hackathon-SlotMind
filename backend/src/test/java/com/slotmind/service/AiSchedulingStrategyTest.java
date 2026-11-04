package com.slotmind.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.slotmind.domain.*;
import com.slotmind.repository.ProviderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AiSchedulingStrategyTest {
    @Test
    void fallsBackWhenModelReturnsUnknownSlot() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RuleBasedSchedulingStrategy rules = org.mockito.Mockito.mock(RuleBasedSchedulingStrategy.class);
        ProviderRepository providers = org.mockito.Mockito.mock(ProviderRepository.class);
        Provider provider = new Provider("P1", "Provider", ProviderSpecialty.GENERAL, ProviderStatus.BUSY);
        Slot slot = new Slot("S1", provider, LocalDateTime.now().plusHours(1), 30, SlotStatus.OPEN);
        Appointment appointment = new Appointment("A1", "Patient", AppointmentType.ROUTINE, provider,
                new Slot("OLD", provider, LocalDateTime.now(), 30, SlotStatus.BOOKED), AppointmentStatus.CONFIRMED);
        var candidate = new SchedulingStrategy.SchedulingRecommendation(slot, 0.8, "Rule reason", SuggestionSource.RULE);
        org.mockito.Mockito.when(rules.recommend(appointment, TriggerReason.PROVIDER_OFFLINE)).thenReturn(List.of(candidate));
        org.mockito.Mockito.when(providers.findAll()).thenReturn(List.of(provider));

        server.expect(requestTo("http://localhost/chat/completions")).andRespond(withSuccess(
                "{\"choices\":[{\"message\":{\"content\":\"{\\\"recommendations\\\":[{\\\"slotId\\\":\\\"UNKNOWN\\\",\\\"confidence\\\":0.99,\\\"reasoning\\\":\\\"bad\\\"}]}\"}}]}",
                org.springframework.http.MediaType.APPLICATION_JSON));
        AiSchedulingStrategy ai = new AiSchedulingStrategy(builder, new ObjectMapper(), rules, providers,
                "http://localhost", "test-key", "test-model");

        var result = ai.recommend(appointment, TriggerReason.PROVIDER_OFFLINE);

        assertEquals(SuggestionSource.AI_FALLBACK, result.get(0).source());
        server.verify();
    }
}
