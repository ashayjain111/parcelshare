package com.parcelshare.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Double-entry style record: every movement shows money leaving one account (DEBIT) and entering another (CREDIT). */
@Entity
@Table(name = "ledger_entry")
@Getter
@NoArgsConstructor
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long paymentId;
    private String account;
    private String direction;
    private long amountPaise;
    private String memo;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public LedgerEntry(Long paymentId, String account, String direction, long amountPaise, String memo) {
        this.paymentId = paymentId;
        this.account = account;
        this.direction = direction;
        this.amountPaise = amountPaise;
        this.memo = memo;
    }
}
