package com.parcelshare.payment;

import com.parcelshare.common.ApiException;
import com.parcelshare.common.AuditService;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Escrow-like behaviour without the platform holding money: funds are "held" at the payment provider and a
 * ledger records every movement. Payment status is a small state machine:
 * HELD -> RELEASED | REFUNDED | PARTIALLY_RELEASED, or HELD -> ON_DISPUTE_HOLD -> (any settlement).
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository payments;
    private final LedgerRepository ledger;
    private final PaymentGateway gateway;
    private final AuditService audit;

    @Transactional
    public Payment hold(Long bookingId, long amountPaise, long commissionPaise) {
        Payment p = new Payment();
        p.setBookingId(bookingId);
        p.setAmountPaise(amountPaise);
        p.setCommissionPaise(commissionPaise);
        p.setGateway(gateway.name());
        p.setGatewayRef(gateway.charge(amountPaise, "booking-" + bookingId));
        p = payments.save(p);
        ledger.save(new LedgerEntry(p.getId(), "SENDER", "DEBIT", amountPaise, "Payment for booking " + bookingId));
        ledger.save(new LedgerEntry(p.getId(), "ESCROW", "CREDIT", amountPaise, "Held until delivery"));
        return p;
    }

    @Transactional(readOnly = true)
    public Payment forBooking(Long bookingId) {
        return payments.findByBookingId(bookingId).orElseThrow(() -> ApiException.notFound("Payment not found"));
    }

    @Transactional(readOnly = true)
    public List<LedgerEntry> ledgerFor(Long paymentId) {
        return ledger.findByPaymentIdOrderById(paymentId);
    }

    @Transactional
    public void scheduleRelease(Payment p, Instant at) {
        p.setReleaseAt(at);
        payments.save(p);
    }

    @Transactional
    public void freezeForDispute(Payment p) {
        if (p.getStatus() != Payment.Status.HELD) {
            throw ApiException.conflict("Payment has already been settled, so it can no longer be disputed");
        }
        p.setStatus(Payment.Status.ON_DISPUTE_HOLD);
        p.setReleaseAt(null);
        payments.save(p);
    }

    /**
     * Settles a held payment. {@code travellerShare} is the gross amount (before commission) that the traveller
     * earns; the rest goes back to the sender. Release = full amount, refund = 0, anything between = partial.
     */
    @Transactional
    public Payment settle(Payment p, long travellerShare, Long actorId, String reason) {
        if (!p.isOpen()) {
            throw ApiException.conflict("Payment is already settled (" + p.getStatus() + ")");
        }
        long share = Math.max(0, Math.min(travellerShare, p.getAmountPaise()));
        long commission = share == p.getAmountPaise() ? p.getCommissionPaise()
                : Math.round((double) share * p.getCommissionPaise() / p.getAmountPaise());
        long payout = share - commission;
        long refund = p.getAmountPaise() - share;

        if (payout > 0) {
            ledger.save(new LedgerEntry(p.getId(), "ESCROW", "DEBIT", payout, reason));
            ledger.save(new LedgerEntry(p.getId(), "TRAVELLER", "CREDIT", payout, reason));
            gateway.payout("booking-" + p.getBookingId(), payout);
        }
        if (commission > 0) {
            ledger.save(new LedgerEntry(p.getId(), "ESCROW", "DEBIT", commission, "Platform commission"));
            ledger.save(new LedgerEntry(p.getId(), "PLATFORM", "CREDIT", commission, "Platform commission"));
        }
        if (refund > 0) {
            ledger.save(new LedgerEntry(p.getId(), "ESCROW", "DEBIT", refund, reason));
            ledger.save(new LedgerEntry(p.getId(), "SENDER", "CREDIT", refund, reason));
            gateway.refund(p.getGatewayRef(), refund);
        }
        p.setStatus(share == p.getAmountPaise() ? Payment.Status.RELEASED
                : share == 0 ? Payment.Status.REFUNDED : Payment.Status.PARTIALLY_RELEASED);
        p.setReleaseAt(null);
        payments.save(p);
        audit.log(actorId, "PAYMENT_" + p.getStatus(), "payment", p.getId(),
                "booking=" + p.getBookingId() + " share=" + share + " payout=" + payout + " refund=" + refund
                        + " reason=" + reason);
        return p;
    }

    @Transactional(readOnly = true)
    public List<Payment> dueForRelease(Instant now) {
        return payments.findByStatusAndReleaseAtBefore(Payment.Status.HELD, now);
    }
}
