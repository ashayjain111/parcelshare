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
 * DigiLocker (MeitY / API Setu) sign-in, written against the DigiLocker Requester API Specification v1.12.
 * Only active when DIGILOCKER_CLIENT_ID is set. The client id and secret come from the API Setu partner portal after approval;
 * see DEPLOY.md. The endpoint URLs default to the production paths in the specification and can be overridden.
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
        // API Setu / DigiLocker Requester API: the client authenticates with HTTP Basic, and PKCE proves the same browser started the flow.
        String form = "grant_type=authorization_code"
                + "&code=" + enc(code)
                + "&client_id=" + enc(props.digilockerClientId())
                + "&redirect_uri=" + enc(redirectUri)
                + "&code_verifier=" + enc(codeVerifier);
        JsonNode token = send(HttpRequest.newBuilder(URI.create(props.digilockerTokenUrl()))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Authorization", basic())
                .POST(HttpRequest.BodyPublishers.ofString(form)).build());
        String accessToken = token.path("access_token").asText("");
        if (accessToken.isBlank()) {
            throw ApiException.bad("DigiLocker did not accept the sign-in. Please try again.");
        }
        try {
            // Since spec v1.6 the token response already carries the verified name and DigiLocker id; only ask again if it does not.
            JsonNode who = token;
            if (who.path("name").asText("").isBlank() || who.path("digilockerid").asText("").isBlank()) {
                who = send(HttpRequest.newBuilder(URI.create(props.digilockerUserUrl()))
                        .timeout(Duration.ofSeconds(15))
                        .header("Authorization", "Bearer " + accessToken)
                        .GET().build());
            }
            String name = who.path("name").asText("").trim();
            String reference = who.path("digilockerid").asText("").trim();
            if (name.isEmpty() || reference.isEmpty()) {
                throw ApiException.bad("DigiLocker did not return a verified name. Please try again.");
            }
            // Only the name and reference id are kept: date of birth, gender and documents are never read or stored.
            return new VerifiedIdentity(name(), name, reference);
        } finally {
            revoke(accessToken);
        }
    }

    /** We only needed to know who the person is, so give the access back straight away. Best effort: a failure here never blocks sign-in. */
    private void revoke(String accessToken) {
        try {
            String url = props.digilockerTokenUrl().replaceAll("/token/?$", "/revoke");
            http.send(HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Authorization", basic())
                    .POST(HttpRequest.BodyPublishers.ofString("token=" + enc(accessToken) + "&token_type_hint=access_token")).build(),
                    HttpResponse.BodyHandlers.discarding());
        } catch (IOException e) {
            log.debug("DigiLocker token revoke failed: {}", e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String basic() {
        String raw = props.digilockerClientId() + ":" + props.digilockerClientSecret();
        return "Basic " + java.util.Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
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
