package com.slotmind.service;

import com.slotmind.domain.Appointment;
import com.slotmind.domain.Slot;
import com.slotmind.domain.TriggerReason;
import java.util.List;

/**
 * Produces replacement slots in preference order for a reschedule event.
 */
public interface SchedulingStrategy {
    List<SchedulingRecommendation> recommend(Appointment appointment, TriggerReason triggerReason);

    record SchedulingRecommendation(Slot slot, double confidence, String reasoning) {
    }
}
