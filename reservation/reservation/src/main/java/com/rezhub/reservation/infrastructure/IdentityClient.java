package com.rezhub.reservation.infrastructure;

import akka.javasdk.http.HttpClient;
import akka.javasdk.http.HttpClientProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Fail-open HTTP client for the shared `identity` service.
 *
 * Every method swallows any error (network failure, timeout, non-2xx, deserialization failure) and
 * logs it rather than throwing — a broken or unreachable `identity` must never affect Rez's own
 * Telegram/booking flow. Callers get a simple "did it work" signal instead of an exception to handle.
 *
 * Mirrors hit-backend's `hit.infrastructure.IdentityClient` (spec 006-identity-integration).
 */
public class IdentityClient {

    private static final Logger log = LoggerFactory.getLogger(IdentityClient.class);

    private final HttpClient http;

    public IdentityClient(HttpClientProvider httpClientProvider) {
        this.http = httpClientProvider.httpClientFor("identity");
    }

    public record Claims(Optional<String> email) {}

    public record ResolveRequest(String provider, String externalId, Optional<Claims> claims) {}

    public record ResolveResponse(String userId, boolean isNew) {}

    /**
     * Resolves (or creates) the `identity` userId for a verified external identity.
     * Returns empty on any failure — never throws.
     */
    public Optional<String> resolveOrCreate(String provider, String externalId, Optional<String> email) {
        try {
            var request = new ResolveRequest(provider, externalId, Optional.of(new Claims(email)));
            var response = http.POST("/internal/identities/resolve")
                    .withRequestBody(request)
                    .responseBodyAs(ResolveResponse.class)
                    .invoke();
            if (!response.status().isSuccess()) {
                log.warn("identity resolveOrCreate({}, {}) returned {}", provider, externalId, response.status());
                return Optional.empty();
            }
            return Optional.of(response.body().userId());
        } catch (Exception e) {
            log.warn("identity resolveOrCreate({}, {}) failed: {}", provider, externalId, e.getMessage());
            return Optional.empty();
        }
    }

    public record LinkRequest(String userId, String kind, String provider, String externalId) {}

    public record LinkResponse(String userId, boolean linked) {}

    /**
     * Links an additional external identity to an already-resolved `identity` user.
     * Best-effort — logs and returns on any failure, never throws.
     */
    public void link(String identityUserId, String kind, String provider, String externalId) {
        try {
            var request = new LinkRequest(identityUserId, kind, provider, externalId);
            var response = http.POST("/internal/identities/link")
                    .withRequestBody(request)
                    .responseBodyAs(LinkResponse.class)
                    .invoke();
            if (!response.status().isSuccess()) {
                log.warn("identity link({}, {}, {}, {}) returned {}",
                        identityUserId, kind, provider, externalId, response.status());
            }
        } catch (Exception e) {
            log.warn("identity link({}, {}, {}, {}) failed: {}",
                    identityUserId, kind, provider, externalId, e.getMessage());
        }
    }
}
