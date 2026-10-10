package com.parcelshare.identity.idp;

/**
 * A government/third-party identity source. The flow is OAuth2 authorisation-code with PKCE: we send the user to the
 * provider, they consent there, and we get back only a verified name and a pseudonymous reference id.
 * We never see (or ask for) an Aadhaar number.
 */
public interface IdentityProvider {

    /** Short stable name stored with the verification, e.g. DIGILOCKER or DEMO. */
    String name();

    /** Where to send the user to give consent. */
    String authorizeUrl(String state, String codeChallenge, String redirectUri);

    /** Trades the one-time code for the user's verified details. Throws ApiException on any provider failure. */
    VerifiedIdentity exchange(String code, String codeVerifier, String redirectUri);

    record VerifiedIdentity(String provider, String name, String reference) {
    }
}
