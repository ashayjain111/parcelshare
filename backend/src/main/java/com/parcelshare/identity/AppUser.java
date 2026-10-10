package com.parcelshare.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class AppUser {

    public enum Role { USER, ADMIN }

    public enum Status { ACTIVE, SUSPENDED, DELETED }

    public enum Verification { NONE, PENDING, APPROVED, REJECTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    private String phone;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.USER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ACTIVE;

    private boolean emailVerified;
    private String verifyToken;

    @Enumerated(EnumType.STRING)
    @Column(name = "traveller_verification", nullable = false)
    private Verification travellerVerification = Verification.NONE;

    private String verificationNote;

    private String resetTokenHash;
    private Instant resetExpiresAt;

    /** Bumped on password change/reset: every earlier login token carries the old number and stops working. */
    private int tokenVersion;

    private Instant deletedAt;

    /** Set after a DigiLocker (or demo) verification. Only a name and a pseudonymous reference id: never an Aadhaar number. */
    private String identityProvider;
    private String identityName;
    private String identityRef;
    private Instant identityVerifiedAt;

    private int ratingSum;
    private int ratingCount;
    private int completedDeliveries;
    private int cancelledCount;
    private int disputedCount;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public double ratingAvg() {
        return Reputation.smoothedRating(ratingSum, ratingCount);
    }

    public int reliabilityPercent() {
        return (int) Math.round(Reputation.reliability(completedDeliveries, cancelledCount, disputedCount) * 100);
    }
}
