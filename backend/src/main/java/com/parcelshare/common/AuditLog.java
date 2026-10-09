package com.parcelshare.common;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "audit_log")
@Getter
@NoArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long actorId;

    @Column(nullable = false)
    private String action;

    private String entityType;
    private Long entityId;
    private String detail;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public AuditLog(Long actorId, String action, String entityType, Long entityId, String detail) {
        this.actorId = actorId;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.detail = detail != null && detail.length() > 1000 ? detail.substring(0, 1000) : detail;
    }
}
