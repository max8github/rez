package com.rezhub.reservation.api;

import akka.http.javadsl.model.HttpEntity;
import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.AbstractHttpEndpoint;
import akka.javasdk.http.HttpResponses;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rezhub.reservation.infrastructure.StripeService;
import com.rezhub.reservation.payment.PaymentEntity;
import com.rezhub.reservation.payment.PaymentState;
import com.rezhub.reservation.payment.PlayerPaymentProfileEntity;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Receives Stripe webhook events (FR-006). Structurally mirrors {@code hit-backend}'s
 * {@code StripeWebhookEndpoint} — signature verification, raw-JSON parsing (dodges Stripe-CLI/SDK
 * API-version skew), {@code 200 OK} in no-op mode when {@code STRIPE_WEBHOOK_SECRET} is unset.
 *
 * <p>Handles:
 * <ul>
 *   <li>{@code setup_intent.succeeded} / {@code payment_method.attached} → populate
 *       {@code PlayerPaymentProfile} (FR-005's card-collection flow).</li>
 *   <li>{@code payment_intent.succeeded} / {@code payment_intent.payment_failed} → idempotent
 *       {@code PaymentEntity} reconciliation. In this feature the primary state changes come from
 *       {@code CommitmentCutoffTimedAction}'s direct Stripe calls, not these webhooks — they exist as
 *       a reconciliation safety net, and are naturally idempotent because {@code PaymentEntity}'s own
 *       command handlers reject a same-or-later-state transition (FR-006/SC-006).</li>
 * </ul>
 */
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/webhooks")
public class StripeWebhookEndpoint extends AbstractHttpEndpoint {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookEndpoint.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ComponentClient componentClient;
    private final StripeService stripeService;

    public StripeWebhookEndpoint(ComponentClient componentClient, StripeService stripeService) {
        this.componentClient = componentClient;
        this.stripeService = stripeService;
    }

    @Post("/stripe")
    public HttpResponse handleStripeWebhook(HttpEntity.Strict body) {
        String payload = body.getData().utf8String();
        String webhookSecret = stripeService.getWebhookSecret();

        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.warn("STRIPE_WEBHOOK_SECRET not configured — ignoring webhook call in no-op mode");
            return HttpResponses.ok();
        }

        String sigHeader = requestContext().requestHeader("Stripe-Signature").map(h -> h.value()).orElse(null);
        if (sigHeader == null) {
            log.warn("Missing Stripe-Signature header — rejecting webhook");
            return HttpResponses.badRequest("Missing Stripe-Signature header");
        }

        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            log.warn("Invalid Stripe webhook signature: {}", e.getMessage());
            return HttpResponses.badRequest("Invalid signature");
        }

        log.info("Received Stripe event: type={} id={}", event.getType(), event.getId());
        routeEvent(event);
        return HttpResponses.ok();
    }

    private void routeEvent(Event event) {
        String rawJson = event.getDataObjectDeserializer().getRawJson();
        if (rawJson == null || rawJson.isBlank()) {
            log.warn("No raw JSON available for event {} — skipping", event.getId());
            return;
        }
        try {
            JsonNode data = MAPPER.readTree(rawJson);
            switch (event.getType()) {
                case "setup_intent.succeeded" -> handleSetupIntentSucceeded(data);
                case "payment_method.attached" -> handlePaymentMethodAttached(data);
                case "payment_intent.succeeded" -> handlePaymentIntentSucceeded(data);
                case "payment_intent.payment_failed" -> handlePaymentIntentFailed(data);
                default -> log.debug("Unhandled Stripe event type: {}", event.getType());
            }
        } catch (Exception e) {
            log.warn("Failed to process Stripe event {}: {}", event.getId(), e.getMessage());
        }
    }

    /**
     * A SetupIntent succeeded → the customer and, if present, its default payment method get linked
     * to the {@code userId} carried in metadata (set when {@code StripeService.createCardSetupLink}
     * created the Checkout Session).
     */
    private void handleSetupIntentSucceeded(JsonNode setupIntent) {
        String userId = setupIntent.path("metadata").path("userId").asText(null);
        String customerId = setupIntent.path("customer").asText(null);
        String paymentMethodId = setupIntent.path("payment_method").asText(null);
        if (userId == null || userId.isBlank()) {
            log.debug("setup_intent.succeeded without userId metadata — ignoring: {}", setupIntent.path("id").asText());
            return;
        }
        if (customerId != null && !customerId.isBlank()) {
            log.info("Linking Stripe customer {} to player {}", customerId, userId);
            componentClient.forKeyValueEntity(userId).method(PlayerPaymentProfileEntity::linkCustomer).invoke(customerId);
        }
        if (paymentMethodId != null && !paymentMethodId.isBlank()) {
            componentClient.forKeyValueEntity(userId).method(PlayerPaymentProfileEntity::setDefaultPaymentMethod).invoke(paymentMethodId);
        }
    }

    /**
     * A PaymentMethod was attached to a Customer directly (e.g. via a Checkout Session's Customer
     * portal rather than a SetupIntent) — same {@code userId}-in-metadata resolution as above, read
     * from the Customer's own metadata since PaymentMethod objects don't carry it themselves.
     */
    private void handlePaymentMethodAttached(JsonNode paymentMethod) {
        String paymentMethodId = paymentMethod.path("id").asText(null);
        String customerId = paymentMethod.path("customer").asText(null);
        if (customerId == null || customerId.isBlank() || paymentMethodId == null) {
            log.debug("payment_method.attached without a customer — ignoring: {}", paymentMethodId);
            return;
        }
        try {
            com.stripe.model.Customer customer = com.stripe.model.Customer.retrieve(customerId);
            String userId = customer.getMetadata().get("userId");
            if (userId == null || userId.isBlank()) {
                log.debug("Customer {} has no userId metadata — ignoring", customerId);
                return;
            }
            componentClient.forKeyValueEntity(userId).method(PlayerPaymentProfileEntity::setDefaultPaymentMethod).invoke(paymentMethodId);
        } catch (Exception e) {
            log.warn("Could not resolve customer {} for payment_method.attached: {}", customerId, e.getMessage());
        }
    }

    /**
     * Reconciliation safety net (FR-006): if this PaymentIntent maps to a known reservationId (set as
     * metadata by {@code StripeService.createAndConfirmHold}) and the entity isn't already CAPTURED,
     * nudges it forward. Idempotent by construction — {@code PaymentEntity.capture} rejects a non-
     * AUTHORIZED source state, so a duplicate/replayed event is a safe no-op.
     */
    private void handlePaymentIntentSucceeded(JsonNode paymentIntent) {
        String reservationId = paymentIntent.path("metadata").path("reservationId").asText(null);
        if (reservationId == null || reservationId.isBlank()) {
            return;
        }
        PaymentState current = componentClient.forEventSourcedEntity(reservationId).method(PaymentEntity::getPayment).invoke();
        if (current.state() == PaymentState.State.CAPTURED) {
            log.debug("Payment {} already CAPTURED — ignoring duplicate payment_intent.succeeded", reservationId);
        }
        // Capture itself is driven by CommitmentCutoffTimedAction.captureHold at the resolution point,
        // not by this webhook — this handler exists to observe/log, not to independently trigger capture.
    }

    private void handlePaymentIntentFailed(JsonNode paymentIntent) {
        String reservationId = paymentIntent.path("metadata").path("reservationId").asText(null);
        String errorMessage = paymentIntent.path("last_payment_error").path("message").asText("unknown");
        log.warn("PaymentIntent failed for reservation {}: {}", reservationId, errorMessage);
        // Failure handling (retry classification, notify-and-cancel) is driven synchronously by
        // CommitmentCutoffTimedAction.attemptHold's own try/catch (FR-016/FR-010), not by this webhook.
    }
}
