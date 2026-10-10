package com.parcelshare.identity.idp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parcelshare.common.ApiException;
import com.parcelshare.common.AppProps;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * DigiLocker (MeitY / API Setu) sign-in. Only active when DIGILOCKER_CLIENT_ID is set.
 * Getting a client id and secret means registering as a DigiLocker requester through API Setu and being approved;
 * see DEPLOY.md. The endpoint URLs are configurable because they are controlled by the government, not by us:
 * confirm them against the current API Setu documentation when you receive your credentials.
 */
@Component
@ConditionalOnExpression("'${parcelshare.digilocker-client-id:}' != ''")
public class DigiLockerProvider implements IdentityProvider {

    private static final Logger log = LoggerFactory.getLogger(DigiLockerProvider.class);

    private final AppProps props;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper json = new ObjectMapper();

    public DigiLockerProvider(AppProps props) {
        this.props = props;
    }

    @Override
    public String name() {
        return "DIGILOCKER";
    }

    @Override
    public String authorizeUrl(String state, String codeChallenge, String redirectUri) {
        return props.digilockerAuthorizeUrl()
                + "?response_type=code"
                + "&client_id=" + enc(props.digilockerClientId())
                + "&redirect_uri=" + enc(redirectUri)
                + "&state=" + enc(state)
                + "&code_challenge=" + enc(codeChallenge)
                + "&code_challenge_method=S256";
    }

    @Override
    public VerifiedIdentity exchange(String code, String codeVerifier, String redirectUri) {
        String form = "grant_type=authorization_code"
                + "&code=" + enc(code)
                + "&client_id=" + enc(props.digilockerClientId())
                + "&client_secret=" + enc(props.digilockerClientSecret())
                + "&redirect_uri=" + enc(redirectUri)
                + "&code_verifier=" + enc(codeVerifier);
        JsonNode token = send(HttpRequest.newBuilder(URI.create(props.digilockerTokenUrl()))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build());
        String accessToken = token.path("access_token").asText("");
        if (accessToken.isBlank()) {
            throw ApiException.bad("DigiLocker did not accept the sign-in. Please try again.");
        }
        JsonNode user = send(HttpRequest.newBuilder(URI.create(props.digilockerUserUrl()))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + accessToken)
                .GET().build());
        String name = user.path("name").asText("").trim();
        String reference = user.path("digilockerid").asText("").trim();
        if (name.isEmpty() || reference.isEmpty()) {
            throw ApiException.bad("DigiLocker did not return a verified name. Please try again.");
        }
        return new VerifiedIdentity(name(), name, reference);
    }

    private JsonNode send(HttpRequest request) {
        try {
            HttpResponse<String> res = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() / 100 != 2) {
                log.warn("DigiLocker returned HTTP {} for {}", res.statusCode(), request.uri().getPath());
                throw ApiException.bad("DigiLocker did not accept the sign-in. Please try again.");
            }
            return json.readTree(res.body());
        } catch (IOException e) {
            log.warn("DigiLocker request failed: {}", e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Could not reach DigiLocker. Please try again in a moment.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.BAD_GATEWAY, "DigiLocker verification was interrupted. Please try again.");
        }
    }

    private static String enc(String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8);
    }
}
