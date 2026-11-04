package com.slotmind.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slotmind.domain.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.util.*;

@Component
public class AiSchedulingStrategy implements SchedulingStrategy {
    private final RestClient client;
    private final ObjectMapper objectMapper;
    private final RuleBasedSchedulingStrategy ruleStrategy;
    private final String model;

    public AiSchedulingStrategy(RestClient.Builder builder, ObjectMapper objectMapper,
            RuleBasedSchedulingStrategy ruleStrategy,
            @Value("${llm.base-url}") String baseUrl, @Value("${llm.api-key:}") String apiKey,
            @Value("${llm.model}") String model) {
        this.client = builder.baseUrl(baseUrl).defaultHeaders(headers -> {
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (!apiKey.isBlank()) headers.setBearerAuth(apiKey);
        }).build();
        this.objectMapper = objectMapper;
        this.ruleStrategy = ruleStrategy;
        this.model = model;
    }

    @Override
    public List<SchedulingRecommendation> recommend(Appointment appointment, TriggerReason reason) {
        List<SchedulingRecommendation> fallback = ruleStrategy.recommend(appointment, reason);
        if (fallback.isEmpty()) return fallback;
        try {
            Map<String, Object> request = Map.of(
                    "model", model, "temperature", 0,
                    "messages", List.of(Map.of("role", "system", "content",
                            "Return only JSON: {\"recommendations\":[{\"slotId\":\"...\",\"confidence\":0.0,\"reasoning\":\"...\"}]}"),
                            Map.of("role", "user", "content", prompt(appointment, reason, fallback))));
            String response = client.post().uri("/chat/completions").body(request).retrieve()
                    .body(String.class);
            List<SchedulingRecommendation> parsed = parse(response, fallback, appointment, reason);
            return parsed.isEmpty() ? fallback : parsed;
        } catch (RuntimeException | java.io.IOException ignored) {
            return fallback;
        }
    }

    private String prompt(Appointment appointment, TriggerReason reason, List<SchedulingRecommendation> candidates)
            throws RuntimeException {
        try {
            return objectMapper.writeValueAsString(Map.of("triggerReason", reason.name(),
                    "appointmentType", appointment.getAppointmentType().name(),
                    "providerId", appointment.getProvider().getId(),
                    "providerSpecialty", appointment.getProvider().getSpecialty().name(),
                    "candidates", candidates.stream().map(c -> Map.of("slotId", c.slot().getId(),
                            "providerId", c.slot().getProvider().getId(),
                            "specialty", c.slot().getProvider().getSpecialty().name(),
                            "startTime", c.slot().getStartTime().toString())).toList()));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to create LLM request", e);
        }
    }

    private List<SchedulingRecommendation> parse(String response, List<SchedulingRecommendation> fallback,
            Appointment appointment, TriggerReason reason) throws java.io.IOException {
        JsonNode root = objectMapper.readTree(response);
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        if (content.isTextual()) root = objectMapper.readTree(content.asText());
        JsonNode recommendations = root.path("recommendations");
        if (!recommendations.isArray()) return List.of();
        Map<String, SchedulingRecommendation> bySlot = new HashMap<>();
        fallback.forEach(c -> bySlot.put(c.slot().getId(), c));
        Set<String> seen = new HashSet<>();
        List<SchedulingRecommendation> result = new ArrayList<>();
        for (JsonNode item : recommendations) {
            String id = item.path("slotId").asText("");
            SchedulingRecommendation candidate = bySlot.get(id);
            if (candidate == null || !seen.add(id)) continue;
            double confidence = item.path("confidence").isNumber()
                    ? Math.max(0, Math.min(1, item.path("confidence").asDouble())) : candidate.confidence();
            String reasoning = item.path("reasoning").asText("");
            result.add(new SchedulingRecommendation(candidate.slot(), confidence,
                    reasoning.isBlank() ? candidate.reasoning() : reasoning));
        }
        if (reason == TriggerReason.SLOT_CANCELLED) {
            result.sort(Comparator.comparing(c -> c.slot().getProvider().getId().equals(appointment.getProvider().getId()) ? 0 : 1));
        }
        return result;
    }
}
