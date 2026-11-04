package com.slotmind.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slotmind.domain.*;
import com.slotmind.repository.ProviderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import java.util.*;

@Component
public class AiSchedulingStrategy implements SchedulingStrategy {
    private static final Logger log = LoggerFactory.getLogger(AiSchedulingStrategy.class);
    private final RestClient client;
    private final ObjectMapper objectMapper;
    private final RuleBasedSchedulingStrategy ruleStrategy;
    private final ProviderRepository providers;
    private final String baseUrl;
    private final String model;

    public AiSchedulingStrategy(RestClient.Builder builder, ObjectMapper objectMapper,
            RuleBasedSchedulingStrategy ruleStrategy, ProviderRepository providers,
            @Value("${llm.base-url}") String baseUrl, @Value("${llm.api-key:}") String apiKey,
            @Value("${llm.model}") String model) {
        this.client = builder.baseUrl(baseUrl).defaultHeaders(headers -> {
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (!apiKey.isBlank()) headers.setBearerAuth(apiKey);
        }).build();
        this.objectMapper = objectMapper;
        this.ruleStrategy = ruleStrategy;
        this.providers = providers;
        this.baseUrl = baseUrl;
        this.model = model;
    }

    @Override
    public List<SchedulingRecommendation> recommend(Appointment appointment, TriggerReason reason) {
        List<SchedulingRecommendation> ruleCandidates = ruleStrategy.recommend(appointment, reason);
        if (ruleCandidates.isEmpty()) return ruleCandidates;
        if (model.isBlank()) {
            log.warn("AI scheduling skipped: LLM model is not configured; using rule fallback");
            return fallback(ruleCandidates);
        }
        long started = System.nanoTime();
        try {
            log.info("Calling LLM scheduling endpoint={} model={} appointment={} trigger={}",
                    baseUrl + "/chat/completions", model, appointment.getId(), reason);
            Map<String, Object> request = Map.of(
                    "model", model,
                    "temperature", 0,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt(reason)),
                            Map.of("role", "user", "content", prompt(appointment, reason, ruleCandidates))));
            String response = client.post().uri("/chat/completions").body(request).retrieve().body(String.class);
            List<SchedulingRecommendation> parsed = parse(response, ruleCandidates, appointment, reason);
            if (parsed.isEmpty()) {
                log.warn("AI returned no valid slot recommendation for appointment={} trigger={}; using rule fallback",
                        appointment.getId(), reason);
                return fallback(ruleCandidates);
            }
            log.info("AI scheduling request completed model={} appointment={} trigger={} latencyMs={} recommendations={}",
                    model, appointment.getId(), reason, (System.nanoTime() - started) / 1_000_000, parsed.size());
            return parsed;
        } catch (RestClientResponseException ex) {
            log.warn("AI scheduling HTTP failure status={} appointment={} trigger={} latencyMs={}; using rule fallback",
                    ex.getStatusCode().value(), appointment.getId(), reason,
                    (System.nanoTime() - started) / 1_000_000);
            return fallback(ruleCandidates);
        } catch (ResourceAccessException ex) {
            log.warn("AI scheduling transport failure appointment={} trigger={} latencyMs={}; using rule fallback",
                    appointment.getId(), reason, (System.nanoTime() - started) / 1_000_000);
            return fallback(ruleCandidates);
        } catch (java.io.IOException | IllegalStateException ex) {
            log.warn("AI scheduling response or prompt failure type={} appointment={} trigger={} latencyMs={}; using rule fallback",
                    ex.getClass().getSimpleName(), appointment.getId(), reason,
                    (System.nanoTime() - started) / 1_000_000);
            return fallback(ruleCandidates);
        } catch (RuntimeException ex) {
            log.warn("AI scheduling unexpected failure type={} appointment={} trigger={} latencyMs={}; using rule fallback",
                    ex.getClass().getSimpleName(), appointment.getId(), reason,
                    (System.nanoTime() - started) / 1_000_000);
            return fallback(ruleCandidates);
        }
    }

    private List<SchedulingRecommendation> fallback(List<SchedulingRecommendation> candidates) {
        return candidates.stream()
                .map(c -> new SchedulingRecommendation(c.slot(), c.confidence(),
                        "AI unavailable; " + c.reasoning(), SuggestionSource.AI_FALLBACK))
                .toList();
    }

    private String systemPrompt(TriggerReason reason) {
        return switch (reason) {
            case PROVIDER_OFFLINE -> """
                    You are a clinic recovery advisor. A provider is offline, so prioritize an available alternate
                    provider with the same specialty, then the earliest suitable slot. Return only JSON in the form
                    {"recommendations":[{"slotId":"...","alternateProviderId":"...","confidence":0.0,"reasoning":"..."}]}.
                    """;
            case SLOT_CANCELLED -> """
                    You are a clinic recovery advisor. A booked slot was cancelled, but the original provider may
                    still be available. Prioritize the earliest open slot with that same provider, then matching
                    specialty. Return only JSON in the form
                    {"recommendations":[{"slotId":"...","alternateProviderId":"...","confidence":0.0,"reasoning":"..."}]}.
                    """;
            case INITIAL -> """
                    You are a clinic scheduling advisor. Recommend the best available replacement for the appointment
                    using urgency, duration, specialty, and time. Return only JSON in the form
                    {"recommendations":[{"slotId":"...","alternateProviderId":"...","confidence":0.0,"reasoning":"..."}]}.
                    """;
        };
    }

    private String prompt(Appointment appointment, TriggerReason reason,
            List<SchedulingRecommendation> candidates) {
        try {
            Map<String, Object> context = new LinkedHashMap<>();
            context.put("triggerReason", reason.name());
            context.put("patientName", appointment.getPatientName());
            context.put("appointmentType", appointment.getAppointmentType().name());
            context.put("appointmentId", appointment.getId());
            context.put("originalProvider", Map.of(
                    "id", appointment.getProvider().getId(),
                    "specialty", appointment.getProvider().getSpecialty().name(),
                    "status", appointment.getProvider().getStatus().name()));
            context.put("originalSlot", Map.of(
                    "id", appointment.getSlot().getId(),
                    "startTime", appointment.getSlot().getStartTime().toString(),
                    "durationMinutes", appointment.getSlot().getDurationMinutes()));
            context.put("providerRoster", providers.findAll().stream().map(provider -> Map.of(
                    "id", provider.getId(), "name", provider.getName(),
                    "specialty", provider.getSpecialty().name(), "status", provider.getStatus().name())).toList());
            context.put("availableCandidates", candidates.stream().map(c -> Map.of(
                    "slotId", c.slot().getId(), "providerId", c.slot().getProvider().getId(),
                    "providerSpecialty", c.slot().getProvider().getSpecialty().name(),
                    "providerStatus", c.slot().getProvider().getStatus().name(),
                    "startTime", c.slot().getStartTime().toString(),
                    "durationMinutes", c.slot().getDurationMinutes())).toList());
            return objectMapper.writeValueAsString(context);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to create LLM request context", ex);
        }
    }

    private List<SchedulingRecommendation> parse(String response, List<SchedulingRecommendation> candidates,
            Appointment appointment, TriggerReason reason) throws java.io.IOException {
        JsonNode root = objectMapper.readTree(response);
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        if (content.isTextual()) root = objectMapper.readTree(content.asText());
        JsonNode recommendations = root.path("recommendations");
        if (!recommendations.isArray()) return List.of();
        Map<String, SchedulingRecommendation> bySlot = new HashMap<>();
        candidates.forEach(c -> bySlot.put(c.slot().getId(), c));
        Set<String> seen = new HashSet<>();
        List<SchedulingRecommendation> result = new ArrayList<>();
        for (JsonNode item : recommendations) {
            String slotId = item.path("slotId").asText("");
            SchedulingRecommendation candidate = bySlot.get(slotId);
            String alternateProviderId = item.path("alternateProviderId").asText("");
            if (candidate == null || !seen.add(slotId)) continue;
            if (!alternateProviderId.isBlank()
                    && !candidate.slot().getProvider().getId().equals(alternateProviderId)) continue;
            double confidence = item.path("confidence").isNumber()
                    ? Math.max(0, Math.min(1, item.path("confidence").asDouble())) : candidate.confidence();
            String reasoning = item.path("reasoning").asText("");
            result.add(new SchedulingRecommendation(candidate.slot(), confidence,
                    reasoning.isBlank() ? candidate.reasoning() : reasoning, SuggestionSource.AI));
        }
        if (reason == TriggerReason.SLOT_CANCELLED) {
            result.sort(Comparator.comparing(c -> c.slot().getProvider().getId()
                    .equals(appointment.getProvider().getId()) ? 0 : 1));
        }
        return result;
    }
}
