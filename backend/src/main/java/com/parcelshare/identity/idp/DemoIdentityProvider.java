package com.parcelshare.identity.idp;

import com.parcelshare.common.ApiException;
import com.parcelshare.common.AppProps;
import com.parcelshare.identity.Tokens;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * A pretend DigiLocker for demos: the "consent screen" is a page in our own frontend that lets you choose the name to
 * approve. It exists only when demo mode is on, so it can never grant real verification in production.
 */
@Component
@ConditionalOnProperty(name = "parcelshare.demo-mode", havingValue = "true")
public class DemoIdentityProvider implements IdentityProvider {

    private final AppProps props;

    public DemoIdentityProvider(AppProps props) {
        this.props = props;
    }

    @Override
    public String name() {
        return "DEMO";
    }

    @Override
    public String authorizeUrl(String state, String codeChallenge, String redirectUri) {
        return props.frontendUrl() + "/digilocker-demo?state=" + URLEncoder.encode(state, StandardCharsets.UTF_8);
    }

    @Override
    public VerifiedIdentity exchange(String code, String codeVerifier, String redirectUri) {
        if (code == null || !code.startsWith("demo:") || code.substring(5).isBlank()) {
            throw ApiException.bad("That demo sign-in code is not valid.");
        }
        String name = code.substring(5).trim();
        return new VerifiedIdentity(name(), name, "demo-" + Tokens.sha256(name.toLowerCase(Locale.ROOT)).substring(0, 12));
    }
}
