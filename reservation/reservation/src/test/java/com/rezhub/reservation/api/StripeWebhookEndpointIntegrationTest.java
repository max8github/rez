package com.rezhub.reservation.api;

import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpEntities;
import akka.http.javadsl.model.HttpEntity;
import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.headers.RawHeader;
import akka.javasdk.JwtClaims;
import akka.javasdk.Principals;
import akka.javasdk.Tracing;
import akka.javasdk.http.QueryParams;
import akka.javasdk.http.RequestContext;
import akka.javasdk.testkit.TestKitSupport;
import com.rezhub.reservation.infrastructure.StripeService;
import com.rezhub.reservation.payment.PaymentEntity;
import com.rezhub.reservation.payment.PaymentState;
import com.rezhub.reservation.payment.PlayerPaymentProfileEntity;
import com.stripe.net.Webhook;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * User Story 2 (T035, T036) — StripeWebhookEndpoint's card-collection path and its idempotency
 * guarantee (FR-006/SC-006). Uses a validly HMAC-signed payload against a locally-constructed
 * endpoint whose StripeService override supplies a fixed test webhook secret — same pattern as
 * hit-backend's IdentityStripeWebhookIntegrationTest.
 */
class StripeWebhookEndpointIntegrationTest extends TestKitSupport {

    private static final String TEST_WEBHOOK_SECRET = "whsec_test_payment_core";

    private final StripeService testStripeService = new StripeService() {
        @Override
        public String getWebhookSecret() {
            return TEST_WEBHOOK_SECRET;
        }
    };

    private String signedHeaderPlaceholder;

    private StripeWebhookEndpoint endpoint() {
        var endpoint = new StripeWebhookEndpoint(componentClient, testStripeService);
        endpoint._internalSetRequestContext(new FakeRequestContext(signedHeaderPlaceholder));
        return endpoint;
    }

    private void sendWebhook(String payload) {
        long timestamp = System.currentTimeMillis() / 1000;
        String signedPayload = timestamp + "." + payload;
        String signature;
        try {
            signature = Webhook.Util.computeHmacSha256(TEST_WEBHOOK_SECRET, signedPayload);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        signedHeaderPlaceholder = "t=" + timestamp + ",v1=" + signature;

        HttpEntity.Strict body = HttpEntities.create(ContentTypes.APPLICATION_JSON, payload.getBytes(StandardCharsets.UTF_8));
        var response = endpoint().handleStripeWebhook(body);
        assertThat(response.status().isSuccess()).isTrue();
    }

    @Test
    public void setupIntentSucceeded_populatesPlayerPaymentProfile() {
        String userId = "user-" + shortId();
        String customerId = "cus_test_" + shortId();
        String paymentMethodId = "pm_test_" + shortId();

        sendWebhook("""
            {
              "id": "evt_test_%s",
              "object": "event",
              "type": "setup_intent.succeeded",
              "data": {
                "object": {
                  "id": "seti_test_%s",
                  "object": "setup_intent",
                  "customer": "%s",
                  "payment_method": "%s",
                  "metadata": { "userId": "%s" }
                }
              }
            }
            """.formatted(UUID.randomUUID(), shortId(), customerId, paymentMethodId, userId));

        var profile = componentClient.forKeyValueEntity(userId).method(PlayerPaymentProfileEntity::getProfile).invoke();
        assertThat(profile.stripeCustomerId()).contains(customerId);
        assertThat(profile.defaultPaymentMethodId()).contains(paymentMethodId);
        assertThat(profile.hasPaymentMethod()).isTrue();
    }

    @Test
    public void setupIntentSucceeded_withoutUserIdMetadata_isIgnored() {
        sendWebhook("""
            {
              "id": "evt_test_%s",
              "object": "event",
              "type": "setup_intent.succeeded",
              "data": {
                "object": {
                  "id": "seti_test_%s",
                  "object": "setup_intent",
                  "customer": "cus_orphan",
                  "payment_method": "pm_orphan",
                  "metadata": {}
                }
              }
            }
            """.formatted(UUID.randomUUID(), shortId()));
        // No assertion beyond "did not throw" — there's no userId to check a profile against.
    }

    @Test
    public void duplicatePaymentIntentSucceeded_doesNotCorruptAlreadyCapturedPayment() {
        String reservationId = "reservation-" + shortId();
        componentClient.forEventSourcedEntity(reservationId).method(PaymentEntity::authorize)
            .invoke(new PaymentEntity.Authorize(reservationId, "court-1", LocalDateTime.now().plusHours(1),
                "pi_test", 5000, "eur", "acct_1", 500));
        componentClient.forEventSourcedEntity(reservationId).method(PaymentEntity::capture)
            .invoke(new PaymentEntity.Capture("ch_test"));

        String payload = """
            {
              "id": "evt_test_%s",
              "object": "event",
              "type": "payment_intent.succeeded",
              "data": {
                "object": {
                  "id": "pi_test",
                  "object": "payment_intent",
                  "metadata": { "reservationId": "%s" }
                }
              }
            }
            """.formatted(UUID.randomUUID(), reservationId);

        // Deliver the same event twice, as Stripe's at-least-once delivery can.
        sendWebhook(payload);
        sendWebhook(payload);

        var payment = componentClient.forEventSourcedEntity(reservationId).method(PaymentEntity::getPayment).invoke();
        assertThat(payment.state()).isEqualTo(PaymentState.State.CAPTURED);
        assertThat(payment.stripeChargeId()).contains("ch_test");
    }

    private static String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private static class FakeRequestContext implements RequestContext {
        private final String stripeSignatureValue;

        FakeRequestContext(String stripeSignatureValue) {
            this.stripeSignatureValue = stripeSignatureValue;
        }

        @Override
        public Principals getPrincipals() { throw new UnsupportedOperationException(); }

        @Override
        public JwtClaims getJwtClaims() { throw new UnsupportedOperationException(); }

        @Override
        public Optional<HttpHeader> requestHeader(String headerName) {
            if (!"Stripe-Signature".equalsIgnoreCase(headerName)) return Optional.empty();
            return Optional.of(RawHeader.create("Stripe-Signature", stripeSignatureValue));
        }

        @Override
        public List<HttpHeader> allRequestHeaders() { return List.of(); }

        @Override
        public Tracing tracing() { throw new UnsupportedOperationException(); }

        @Override
        public QueryParams queryParams() { throw new UnsupportedOperationException(); }

        @Override
        public Optional<String> lastSeenSseEventId() { return Optional.empty(); }

        @Override
        public String selfRegion() { return "test-region"; }
    }
}
