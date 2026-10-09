package com.parcelshare.rating;

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
@Table(name = "rating")
@Getter
@NoArgsConstructor
public class Rating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long bookingId;
    private Long raterId;
    private Long rateeId;
    private int score;
    private String comment;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public Rating(Long bookingId, Long raterId, Long rateeId, int score, String comment) {
        this.bookingId = bookingId;
        this.raterId = raterId;
        this.rateeId = rateeId;
        this.score = score;
        this.comment = comment;
    }
}
