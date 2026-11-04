package com.slotmind.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
public class Provider {
    @Id
    private String id;
    private String name;
    @Enumerated(EnumType.STRING)
    private ProviderSpecialty specialty;
    @Enumerated(EnumType.STRING)
    private ProviderStatus status;

    public Provider(String id, String name, ProviderSpecialty specialty, ProviderStatus status) {
        this.id = id;
        this.name = name;
        this.specialty = specialty;
        this.status = status;
    }

    public void setStatus(ProviderStatus status) {
        this.status = status;
    }
}
