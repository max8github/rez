package com.rezhub.reservation.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpClientProvider;
import com.rezhub.reservation.agent.BookingAgent;
import com.rezhub.reservation.infrastructure.IdentityClient;
import com.rezhub.reservation.infrastructure.TelegramClient;
import com.rezhub.reservation.orchestration.OriginRequestContext;
import com.rezhub.reservation.spi.NotificationSender;
import com.rezhub.reservation.view.FacilityByBotTokenView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Receives Telegram webhook updates and dispatches them to the BookingAgent.
 *
 * Setup (one-time after deploy, per bot):
 *   curl "https://api.telegram.org/bot{TOKEN}/setWebhook?url=https://{your-service}/telegram/{TOKEN}/webhook"
 *
 * The bot token in the path is looked up in FacilityByBotTokenView to validate that this
 * bot is configured. One deployment can serve N facilities — no FACILITY_ID env var needed.
 */
@HttpEndpoint("/telegram")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class TelegramEndpoint {

    private static final Logger log = LoggerFactory.getLogger(TelegramEndpoint.class);

    private final ComponentClient componentClient;
    private final NotificationSender notificationSender;
    private final IdentityClient identityClient;
    private final TelegramClient telegramClient;

    public TelegramEndpoint(ComponentClient componentClient, NotificationSender notificationSender,
                            HttpClientProvider httpClientProvider) {
        this.componentClient = componentClient;
        this.notificationSender = notificationSender;
        this.identityClient = new IdentityClient(httpClientProvider);
        this.telegramClient = new TelegramClient(httpClientProvider);
    }

    public record Update(Message message) {}
    public record Message(long message_id, From from, Chat chat, String text) {}
    public record From(long id, String first_name, String username) {}
    public record Chat(long id, String type) {}

    /**
     * Telegram webhook receiver. Telegram POSTs one Update per incoming message.
     * Returns 200 OK immediately while the agent reply and booking outcome continue asynchronously.
     */
    @Post("/{botToken}/webhook")
    public void onUpdate(String botToken, Update update) {
        if (update.message() == null || update.message().text() == null) {
            log.debug("Ignoring Telegram update without text (e.g. join/leave event)");
            return;
        }

        Optional<FacilityByBotTokenView.Entry> facilityOpt = componentClient.forView()
            .method(FacilityByBotTokenView::getByBotToken)
            .invoke(botToken);

        if (facilityOpt.isEmpty()) {
            log.warn("No facility found for bot token (first 8 chars): {}...", botToken.substring(0, Math.min(8, botToken.length())));
            return;
        }

        FacilityByBotTokenView.Entry facility = facilityOpt.get();
        String facilityId = facility.facilityId();
        String timezone = facility.timezone() != null ? facility.timezone() : "Europe/Berlin";

        var msg = update.message();
        long chatId = msg.chat().id();
        String senderExternalId = msg.from() != null ? String.valueOf(msg.from().id()) : "";
        String senderDisplayName = msg.from() != null && msg.from().first_name() != null
            ? msg.from().first_name() : "Player";
        String recipientId = Base64.getUrlEncoder().withoutPadding()
                .encodeToString((botToken + ":" + chatId).getBytes());
        String conversationId = sanitize(botToken + "-" + chatId);

        log.info("Telegram message from {} (chat {}) for facility {}: {}", senderDisplayName, chatId, facilityId, msg.text());

        Optional<String> identityUserId = senderExternalId.isBlank()
            ? Optional.empty()
            : identityClient.resolveOrCreate("TELEGRAM", senderExternalId, Optional.empty());

        // A bot-specific deep link (https://t.me/<username>) gives the OS something concrete to hand
        // off to once an external flow (e.g. Stripe Checkout) redirects back — the generic
        // https://t.me/ homepage doesn't. Falls back to the generic link if getMe fails.
        String returnUrl = telegramClient.resolveUsername(botToken)
            .map(username -> "https://t.me/" + username)
            .orElse("https://t.me/");

        OriginRequestContext origin = new OriginRequestContext(
            "telegram",
            senderExternalId,
            senderDisplayName,
            recipientId,
            conversationId,
            Map.of("botToken", botToken, "facilityId", facilityId, "timezone", timezone, "returnUrl", returnUrl),
            identityUserId
        );

        componentClient
            .forAgent()
            .inSession(conversationId)
            .method(BookingAgent::chat)
            .invokeAsync(new BookingAgent.AgentRequest(origin, msg.text()))
            .thenAccept(reply -> {
                if (shouldSendAgentReply(reply)) {
                    notificationSender.send(recipientId, reply);
                }
            })
            .whenComplete((v, error) -> {
                if (error != null) log.error("Agent error for chat {}: {}", chatId, error.getMessage());
            });
    }

    private static String sanitize(String s) {
        return s.replaceAll("[^a-zA-Z0-9_\\-.]", "-");
    }

    private static boolean shouldSendAgentReply(String reply) {
        return reply != null && !reply.isBlank() && !isInterimBookingAcknowledgement(reply);
    }

    private static boolean isInterimBookingAcknowledgement(String reply) {
        String normalized = reply.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("__silent_booking_ack__")
            || normalized.equals("checking availability. i'll message you shortly.")
            || normalized.startsWith("booking request queued (id:")
            || normalized.startsWith("request submitted")
            || normalized.startsWith("booking_submitted:");
    }
}
