package com.rezhub.reservation.infrastructure;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Account;
import com.stripe.model.BalanceTransaction;
import com.stripe.model.Charge;
import com.stripe.model.Customer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Transfer;
import com.stripe.net.RequestOptions;
import com.stripe.param.AccountCreateParams;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.PaymentIntentCaptureParams;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.TransferCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stripe Java SDK wrapper for Rez's payment core, modeled on {@code hit-backend}'s
 * {@code hit.infrastructure.StripeService} (research.md #7) but independently reimplemented — the two
 * services are separate repos with, potentially, separate Stripe accounts.
 *
 * <p>No-op mode: if {@code STRIPE_SECRET_KEY} is absent or blank, methods log a warning and return
 * mock values, so tests can run without a live Stripe account.
 *
 * <p>Unlike hit-backend's {@code createPaymentIntent} (client-confirmed via a mobile PaymentSheet),
 * {@link #createAndConfirmHold} confirms off-session in the same server-side call — Rez has no mobile
 * client in this flow at all (research.md #7).
 *
 * <p>Deliberately does <b>not</b> catch-and-wrap {@link StripeException} the way hit-backend's
 * equivalent methods do: {@code CommitmentCutoffTimedAction} needs to distinguish a transient failure
 * (network/API availability) from a card-specific one ({@code CardException},
 * {@code authentication_required}) to implement FR-016's retry-vs-notify branching, which requires the
 * original exception type to survive.
 */
public class StripeService {

    private static final Logger log = LoggerFactory.getLogger(StripeService.class);

    private final boolean enabled;
    private final String webhookSecret;

    private final Map<String, Long> mockPaymentIntentAmounts = new ConcurrentHashMap<>();

    public StripeService() {
        String apiKey = System.getenv("STRIPE_SECRET_KEY");
        this.enabled = apiKey != null && !apiKey.isBlank();
        if (enabled) {
            Stripe.apiKey = apiKey;
            log.info("StripeService initialised in LIVE mode");
        } else {
            log.warn("STRIPE_SECRET_KEY not set — StripeService running in NO-OP mode (mock ids returned)");
        }
        this.webhookSecret = System.getenv("STRIPE_WEBHOOK_SECRET");
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    // -------------------------------------------------------------------------
    // PaymentIntent — commitment-cutoff hold (manual capture, off-session)
    // -------------------------------------------------------------------------

    public record HoldResult(String paymentIntentId) {}

    /**
     * Creates and confirms, in one server-side call, a manual-capture {@code PaymentIntent} against
     * the player's saved payment method — fully unattended, no client confirmation step (FR-007).
     */
    public HoldResult createAndConfirmHold(long amountCents, String currency, String stripeCustomerId,
                                           String paymentMethodId, String idempotencyKey, String description) throws StripeException {
        if (!enabled) {
            String mockId = "pi_mock_" + idempotencyKey;
            mockPaymentIntentAmounts.put(mockId, amountCents);
            log.debug("NO-OP createAndConfirmHold: returning {}", mockId);
            return new HoldResult(mockId);
        }
        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(amountCents)
                .setCurrency(currency)
                .setCustomer(stripeCustomerId)
                .setPaymentMethod(paymentMethodId)
                .setCaptureMethod(PaymentIntentCreateParams.CaptureMethod.MANUAL)
                .setOffSession(true)
                .setConfirm(true)
                .setDescription(description)
                .putMetadata("reservationId", idempotencyKey)
                .build();
        RequestOptions opts = RequestOptions.builder()
                .setIdempotencyKey("create-hold-" + idempotencyKey)
                .build();
        PaymentIntent pi = PaymentIntent.create(params, opts);
        log.info("Created and confirmed hold {} for reservation {}", pi.getId(), idempotencyKey);
        return new HoldResult(pi.getId());
    }

    /**
     * Captures a previously authorized hold (resolution point, FR-008). Returns the resulting charge
     * id, needed by {@link #createTransferFromCharge}.
     */
    public String capturePaymentIntent(String paymentIntentId, String idempotencyKey) throws StripeException {
        if (!enabled) {
            log.debug("NO-OP capturePaymentIntent: {}", paymentIntentId);
            return "ch_mock_" + idempotencyKey;
        }
        PaymentIntent pi = PaymentIntent.retrieve(paymentIntentId);
        PaymentIntent captured = pi.capture(
                PaymentIntentCaptureParams.builder().build(),
                RequestOptions.builder().setIdempotencyKey("capture-hold-" + idempotencyKey).build());
        log.info("Captured hold {} for reservation {}", paymentIntentId, idempotencyKey);
        return captured.getLatestCharge();
    }

    // -------------------------------------------------------------------------
    // Transfer — pay the facility's connected account (destination-charge split)
    // -------------------------------------------------------------------------

    /**
     * Transfers {@code facilityFraction} (1 - Rez's commission) of the captured charge to the
     * facility's connected account. Mirrors hit-backend's teacher-payout mechanics — same shape,
     * different payee.
     */
    public void createTransferFromCharge(String chargeId, double facilityFraction,
                                         String destinationConnectAccountId, String idempotencyKey, String description) throws StripeException {
        if (!enabled) {
            log.debug("NO-OP createTransferFromCharge: {}% to {}", (int) (facilityFraction * 100), destinationConnectAccountId);
            return;
        }
        Charge charge = Charge.retrieve(chargeId);
        BalanceTransaction bt = BalanceTransaction.retrieve(charge.getBalanceTransaction());
        long transferAmount = Math.round(bt.getAmount() * facilityFraction);
        String currency = bt.getCurrency();

        TransferCreateParams params = TransferCreateParams.builder()
                .setAmount(transferAmount)
                .setCurrency(currency)
                .setDestination(destinationConnectAccountId)
                .setSourceTransaction(chargeId)
                .setDescription(description)
                .putMetadata("reservationId", idempotencyKey)
                .build();
        Transfer.create(params, RequestOptions.builder().setIdempotencyKey("transfer-" + idempotencyKey).build());
        log.info("Transfer of {} {} ({}% of charge {}) to {} for reservation {}",
                transferAmount, currency, (int) (facilityFraction * 100), chargeId, destinationConnectAccountId, idempotencyKey);
    }

    // -------------------------------------------------------------------------
    // Customer — create a Stripe customer for a new player
    // -------------------------------------------------------------------------

    public String createCustomer(String userId, String email) throws StripeException {
        if (!enabled) {
            String mockId = "cus_mock_" + userId;
            log.debug("NO-OP createCustomer: returning {}", mockId);
            return mockId;
        }
        CustomerCreateParams.Builder builder = CustomerCreateParams.builder().putMetadata("userId", userId);
        if (email != null && !email.isBlank()) {
            builder.setEmail(email);
        }
        RequestOptions opts = RequestOptions.builder().setIdempotencyKey("create-customer-" + userId).build();
        Customer customer = Customer.create(builder.build(), opts);
        log.info("Created Stripe customer {} for player {}", customer.getId(), userId);
        return customer.getId();
    }

    /**
     * Creates a Stripe-hosted Checkout Session in setup mode, so a first-time player can put a card
     * on file without any native card form in Telegram (FR-005). {@code userId} travels in
     * {@code setup_intent_data.metadata} — not just the Checkout Session's own metadata, which Stripe
     * does not propagate onto the SetupIntent it creates — so {@code StripeWebhookEndpoint} can
     * resolve {@code setup_intent.succeeded} back to a {@code PlayerPaymentProfile} without the player
     * having authenticated to anything Rez-side.
     */
    public String createCardSetupLink(String stripeCustomerIdOrNull, String userId, String returnUrl) throws StripeException {
        if (!enabled) {
            String mockUrl = "https://checkout.stripe.com/mock/setup/" + userId;
            log.debug("NO-OP createCardSetupLink: returning {}", mockUrl);
            return mockUrl;
        }
        com.stripe.param.checkout.SessionCreateParams.Builder builder = com.stripe.param.checkout.SessionCreateParams.builder()
                .setMode(com.stripe.param.checkout.SessionCreateParams.Mode.SETUP)
                .addPaymentMethodType(com.stripe.param.checkout.SessionCreateParams.PaymentMethodType.CARD)
                .setSuccessUrl(returnUrl)
                .setCancelUrl(returnUrl)
                .putMetadata("userId", userId)
                .setSetupIntentData(com.stripe.param.checkout.SessionCreateParams.SetupIntentData.builder()
                        .putMetadata("userId", userId)
                        .build());
        if (stripeCustomerIdOrNull != null && !stripeCustomerIdOrNull.isBlank()) {
            builder.setCustomer(stripeCustomerIdOrNull);
        } else {
            builder.setCustomerCreation(com.stripe.param.checkout.SessionCreateParams.CustomerCreation.IF_REQUIRED);
        }
        com.stripe.model.checkout.Session session = com.stripe.model.checkout.Session.create(builder.build());
        log.info("Created card-setup Checkout Session {} for user {}", session.getId(), userId);
        return session.getUrl();
    }

    // -------------------------------------------------------------------------
    // Connect — facility onboarding
    // -------------------------------------------------------------------------

    public String createConnectAccount(String facilityId) throws StripeException {
        if (!enabled) {
            String mockId = "acct_mock_" + facilityId;
            log.debug("NO-OP createConnectAccount: returning {}", mockId);
            return mockId;
        }
        AccountCreateParams params = AccountCreateParams.builder()
                .setType(AccountCreateParams.Type.EXPRESS)
                .putMetadata("facilityId", facilityId)
                .build();
        Account account = Account.create(params);
        log.info("Created Connect account {} for facility {}", account.getId(), facilityId);
        return account.getId();
    }

    /**
     * Returns true if the Connect account has {@code charges_enabled=true} on Stripe. Used by
     * {@code PaymentGate.isFacilityPayable} (FR-012).
     */
    public boolean isConnectAccountChargesEnabled(String accountId) {
        if (!enabled) return true; // no-op mode: treat every account as immediately activated
        try {
            return Boolean.TRUE.equals(Account.retrieve(accountId).getChargesEnabled());
        } catch (StripeException e) {
            log.warn("Could not check Connect account {}: {}", accountId, e.getMessage());
            return false;
        }
    }
}
