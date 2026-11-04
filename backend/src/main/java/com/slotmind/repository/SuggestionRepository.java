package com.slotmind.repository;
import com.slotmind.domain.*; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface SuggestionRepository extends JpaRepository<RescheduleSuggestion, String> {
    List<RescheduleSuggestion> findByStatus(SuggestionStatus status);
    Optional<RescheduleSuggestion> findByAppointmentAndTriggerReasonAndStatus(
            Appointment appointment, TriggerReason triggerReason, SuggestionStatus status);
}
