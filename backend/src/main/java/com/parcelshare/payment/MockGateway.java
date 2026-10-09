package com.parcelshare.payment;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Pretend payment provider for the MVP/demo: always succeeds, moves no real money. */
@Component
public class MockGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(MockGateway.class);

    @Override
    public String name() {
        return "MOCK";
    }

    @Override
    public String charge(long amountPaise, String reference) {
        String ref = "mock_" + UUID.randomUUID().toString().substring(0, 12);
        log.info("[mock] charged {} paise ({}) -> {}", amountPaise, reference, ref);
        return ref;
    }

    @Override
    public void refund(String gatewayRef, long amountPaise) {
        log.info("[mock] refunded {} paise on {}", amountPaise, gatewayRef);
    }

    @Override
    public void payout(String reference, long amountPaise) {
        log.info("[mock] paid out {} paise ({})", amountPaise, reference);
    }
}
