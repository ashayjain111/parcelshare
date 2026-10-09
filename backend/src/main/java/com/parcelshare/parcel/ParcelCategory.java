package com.parcelshare.parcel;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "parcel_category")
@Getter
@Setter
@NoArgsConstructor
public class ParcelCategory {

    public enum Tier { ALLOWED, RESTRICTED, PROHIBITED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Enumerated(EnumType.STRING)
    private Tier tier;

    private int maxGrams;
    private long maxValuePaise;
    private boolean active = true;
}
