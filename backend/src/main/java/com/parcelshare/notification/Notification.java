package com.parcelshare.notification;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** An in-app notification (the bell). Written alongside the email for every milestone. */
@Entity
@Table(name = "notification")
@Getter
@Setter
@NoArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private String type;
    private String title;
    private String body;
    private String link;
    private Instant readAt;
    private Instant createdAt = Instant.now();

    public Notification(Long userId, String type, String title, String body, String link) {
        this.userId = userId;
        this.type = type;
        this.title = title.length() > 160 ? title.substring(0, 160) : title;
        this.body = body != null && body.length() > 500 ? body.substring(0, 500) : body;
        this.link = link;
    }
}
