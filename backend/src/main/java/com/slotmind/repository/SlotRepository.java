package com.slotmind.repository;

import com.slotmind.domain.*;
import org.springframework.data.jpa.repository.*;
import java.util.*;

public interface SlotRepository extends JpaRepository<Slot, String> {
    List<Slot> findByStatusAndStartTimeAfterOrderByStartTimeAsc(SlotStatus status, java.time.LocalDateTime time);
}
