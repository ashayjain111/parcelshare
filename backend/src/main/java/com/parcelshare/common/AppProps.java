package com.parcelshare.common;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("parcelshare")
public record AppProps(
        @DefaultValue("dev-only-secret-change-me-dev-only-secret-change-me") String jwtSecret,
        @DefaultValue("12") int jwtHours,
        @DefaultValue("10") int commissionPercent,
        @DefaultValue("10") int cancellationFeePercent,
        @DefaultValue("48") int releaseWindowHours,
        @DefaultValue("24") int requestExpiryHours,
        @DefaultValue("30") int rateLimitPerMinute,
        @DefaultValue("false") boolean requireEmailVerification,
        @DefaultValue("http://localhost:5180") List<String> corsOrigins,
        @DefaultValue("admin@parcelshare.local") String adminEmail,
        @DefaultValue("") String adminPassword,
        @DefaultValue("false") boolean seedDemo,
        @DefaultValue("false") boolean demoMode,
        @DefaultValue("") String mailFrom,
        @DefaultValue("http://localhost:5180") String frontendUrl) {
}
