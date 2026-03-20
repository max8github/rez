package com.rezhub.reservation.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import com.rezhub.reservation.agent.BookingAgent;
import com.rezhub.reservation.spi.NotificationSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Receives Telegram webhook updates and dispatches them to the BookingAgent.
 *
 * Setup (one-time after deploy):
 *   curl "https://api.telegram.org/bot{TOKEN}/setWebhook?url=https://{your-service}/telegram/webhook"
 *
 * Each incoming message is acknowledged immediately (HTTP 200) and the agent
 * is invoked asynchronously. All replies — both conversational and final booking
 * outcomes — are sent back to the chat via NotificationSender (TelegramNotifier).
 *
 * facilityId is read from the FACILITY_ID env var (one club per bot token for MVP).
 * Session is scoped per chat: private DMs get their own session, group chats share one.
 */
@HttpEndpoint("/telegram")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class TelegramEndpoint {

    private static final Logger log = LoggerFactory.getLogger(TelegramEndpoint.class);

    private final ComponentClient componentClient;
    private final NotificationSender notificationSender;
    private final String facilityId;

    public TelegramEndpoint(ComponentClient componentClient, NotificationSender notificationSender) {
        this.componentClient = componentClient;
        this.notificationSender = notificationSender;
        this.facilityId = System.getenv("FACILITY_ID");
    }

    // ---- Telegram Update payload (subset of fields we need) ----

    public record Update(Message message) {}

    public record Message(long message_id, From from, Chat chat, String text) {}

    public record From(long id, String first_name, String username) {}

    public record Chat(long id, String type) {}

    /**
     * Telegram webhook receiver. Telegram POSTs one Update per incoming message.
     * Returns 200 OK immediately; the agent reply is sent back asynchronously
     * via NotificationSender so Telegram never has to wait for the LLM.
     */
    @Post("/webhook")
    public void onUpdate(Update update) {
        if (update.message() == null || update.message().text() == null) {
            log.debug("Ignoring Telegram update without text (e.g. join/leave event)");
            return;
        }

        var msg = update.message();
        long chatId = msg.chat().id();
        String recipientId = String.valueOf(chatId);
        String senderName = msg.from() != null && msg.from().first_name() != null
                ? msg.from().first_name()
                : "Player";

        log.info("Telegram message from {} (chat {}): {}", senderName, chatId, msg.text());

        String sessionId = sanitize(facilityId + ":" + chatId);

        componentClient
                .forAgent()
                .inSession(sessionId)
                .method(BookingAgent::chat)
                .invokeAsync(new BookingAgent.BookingRequest(facilityId, senderName, recipientId, msg.text()))
                .thenAccept(reply -> notificationSender.send(recipientId, reply))
                .whenComplete((v, error) -> {
                    if (error != null) log.error("Agent error for chat {}: {}", chatId, error.getMessage());
                });
    }

    /** Strip characters that are invalid in Akka entity/session IDs. */
    private static String sanitize(String s) {
        return s.replaceAll("[^a-zA-Z0-9_\\-.]", "-");
    }
}
