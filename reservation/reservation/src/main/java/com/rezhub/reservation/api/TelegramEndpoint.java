package com.rezhub.reservation.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import com.rezhub.reservation.agent.BookingAgent;
import com.rezhub.reservation.orchestration.OriginRequestContext;
import com.rezhub.reservation.spi.NotificationSender;
import com.rezhub.reservation.view.FacilityByBotTokenView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final String IMMEDIATE_ACK =
        "Request submitted — you'll receive a notification shortly with the outcome.";

    private final ComponentClient componentClient;
    private final NotificationSender notificationSender;

    public TelegramEndpoint(ComponentClient componentClient, NotificationSender notificationSender) {
        this.componentClient = componentClient;
        this.notificationSender = notificationSender;
    }

    public record Update(Message message) {}
    public record Message(long message_id, From from, Chat chat, String text) {}
    public record From(long id, String first_name, String username) {}
    public record Chat(long id, String type) {}

    /**
     * Telegram webhook receiver. Telegram POSTs one Update per incoming message.
     * Returns 200 OK immediately; an acknowledgement is sent to the chat right away,
     * while the agent reply and booking outcome continue asynchronously.
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
        String recipientId = botToken + ":" + chatId;
        String conversationId = sanitize(botToken + ":" + chatId);

        log.info("Telegram message from {} (chat {}) for facility {}: {}", senderDisplayName, chatId, facilityId, msg.text());
        notificationSender.send(recipientId, IMMEDIATE_ACK);

        OriginRequestContext origin = new OriginRequestContext(
            "telegram",
            senderExternalId,
            senderDisplayName,
            recipientId,
            conversationId,
            Map.of("botToken", botToken, "facilityId", facilityId, "timezone", timezone)
        );

        componentClient
            .forAgent()
            .inSession(conversationId)
            .method(BookingAgent::chat)
            .invokeAsync(new BookingAgent.AgentRequest(origin, msg.text()))
            .thenAccept(reply -> {
                if (reply != null && !reply.isBlank() && !IMMEDIATE_ACK.equals(reply)) {
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
}
