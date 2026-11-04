package com.slotmind.repository;

import com.slotmind.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface AppointmentRepository extends JpaRepository<Appointment, String> {
    List<Appointment> findByStatus(AppointmentStatus status);

    List<Appointment> findByProviderAndStatus(Provider provider, AppointmentStatus status);
}
