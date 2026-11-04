package com.slotmind.config;

import com.slotmind.domain.*;
import com.slotmind.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.LocalDateTime;
import java.util.List;

@Configuration
public class SeedData {
    @Bean
    CommandLineRunner seed(ProviderRepository ps, SlotRepository ss, AppointmentRepository as) {
        return args -> {
            Provider p1 = new Provider("PRV-001", "Dr. Sarah Chen", ProviderSpecialty.GENERAL, ProviderStatus.BUSY),
                    p2 = new Provider("PRV-002", "Dr. James Okonkwo", ProviderSpecialty.SPECIALIST,
                            ProviderStatus.AVAILABLE),
                    p3 = new Provider("PRV-003", "Dr. Priya Sharma", ProviderSpecialty.PEDIATRIC, ProviderStatus.BUSY),
                    p4 = new Provider("PRV-004", "Dr. Rahul Verma", ProviderSpecialty.GENERAL,
                            ProviderStatus.AVAILABLE),
                    p5 = new Provider("PRV-005", "Dr. Ananya Iyer", ProviderSpecialty.SPECIALIST, ProviderStatus.BUSY);
            ps.saveAll(List.of(p1, p2, p3, p4, p5));
            LocalDateTime now = LocalDateTime.now();
            Slot s1 = new Slot("SLT-001", p1, now.plusMinutes(60), 30, SlotStatus.BOOKED),
                    s2 = new Slot("SLT-002", p1, now.plusMinutes(120), 30, SlotStatus.BOOKED),
                    s3 = new Slot("SLT-003", p3, now.plusMinutes(90), 45, SlotStatus.BOOKED),
                    s4 = new Slot("SLT-004", p2, now.plusMinutes(180), 30, SlotStatus.OPEN),
                    s5 = new Slot("SLT-005", p4, now.plusMinutes(150), 30, SlotStatus.OPEN),
                    s6 = new Slot("SLT-006", p5, now.plusMinutes(240), 60, SlotStatus.BOOKED);
            ss.saveAll(List.of(s1, s2, s3, s4, s5, s6));
            as.saveAll(List.of(
                    new Appointment("APT-001", "John Martinez", AppointmentType.ROUTINE, p1, s1,
                            AppointmentStatus.CONFIRMED),
                    new Appointment("APT-002", "Emily Watson", AppointmentType.FOLLOW_UP, p1, s2,
                            AppointmentStatus.CONFIRMED),
                    new Appointment("APT-003", "Aisha Patel", AppointmentType.URGENT, p3, s3,
                            AppointmentStatus.CONFIRMED),
                    new Appointment("APT-004", "Robert Kim", AppointmentType.ROUTINE, p5, s6,
                            AppointmentStatus.CONFIRMED)));
        };
    }
}
