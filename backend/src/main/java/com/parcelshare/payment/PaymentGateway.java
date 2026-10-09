package com.parcelshare.payment;

/**
 * Port to whatever moves real money. The MVP uses {@link MockGateway}. To go live, add a RazorpayGateway
 * (Orders + Route "on hold" transfers so the gateway, not this platform, holds the funds) and mark it @Primary.
 * Domain code only ever talks to this interface.
 */
public interface PaymentGateway {

    String name();

    /** Collect the sender's money (held by the provider). Returns the provider reference. */
    String charge(long amountPaise, String reference);

    /** Return money to the sender. */
    void refund(String gatewayRef, long amountPaise);

    /** Pay out to the traveller once the booking is settled. */
    void payout(String reference, long amountPaise);
}
