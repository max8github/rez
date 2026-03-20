package com.rezhub.reservation.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpClient;
import akka.javasdk.http.HttpClientProvider;
import com.rezhub.reservation.agent.BookingAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Receives Telegram webhook updates and dispatches them to the BookingAgent.
 *
 * Setup (one-time after deploy):
 *   curl "https://api.telegram.org/bot{TOKEN}/setWebhook?url=https://{your-service}/telegram/webhook"
 *
 * Each incoming message is handled synchronously: the agent produces a reply,
 * which is sent back to the same chat via the Telegram sendMessage API.
 *
 * facilityId is read from the FACILITY_ID env var (one club per bot token for MVP).
 * Session is scoped per chat: private DMs get their own session, group chats share one.
 */
@HttpEndpoint("/telegram")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class TelegramEndpoint {

    private static final Logger log = LoggerFactory.getLogger(TelegramEndpoint.class);

    private final ComponentClient componentClient;
    private final HttpClient telegramClient;
    private final String botToken;
    private final String facilityId;

    public TelegramEndpoint(ComponentClient componentClient, HttpClientProvider httpClientProvider) {
        this.componentClient = componentClient;
        this.telegramClient = httpClientProvider.httpClientFor("https://api.telegram.org");
        this.botToken = System.getenv("TELEGRAM_BOT_TOKEN");
        this.facilityId = System.getenv("FACILITY_ID");
    }

    // ---- Telegram Update payload (subset of fields we need) ----

    public record Update(Message message) {}

    public record Message(long message_id, From from, Chat chat, String text) {}

    public record From(long id, String first_name, String username) {}

    public record Chat(long id, String type) {}

    // ---- Telegram sendMessage request ----

    record SendMessage(long chat_id, String text) {}

    /**
     * Telegram webhook receiver. Telegram POSTs one Update per incoming message.
     * Returns 200 OK to acknowledge; Telegram retries if it doesn't get 200.
     */
    @Post("/webhook")
    public void onUpdate(Update update) {
        if (update.message() == null || update.message().text() == null) {
            log.debug("Ignoring Telegram update without text (e.g. join/leave event)");
            return;
        }

        var msg = update.message();
        long chatId = msg.chat().id();
        String senderName = msg.from() != null && msg.from().first_name() != null
                ? msg.from().first_name()
                : "Player";

        log.info("Telegram message from {} (chat {}): {}", senderName, chatId, msg.text());

        String sessionId = sanitize(facilityId + ":" + chatId);

        String reply = componentClient
                .forAgent()
                .inSession(sessionId)
                .method(BookingAgent::chat)
                .invoke(new BookingAgent.BookingRequest(facilityId, senderName, String.valueOf(chatId), msg.text()));

        telegramClient
                .POST("/bot" + botToken + "/sendMessage")
                .withRequestBody(new SendMessage(chatId, reply))
                .responseBodyAs(String.class)
                .invoke();
    }

    /** Strip characters that are invalid in Akka entity/session IDs. */
    private static String sanitize(String s) {
        return s.replaceAll("[^a-zA-Z0-9_\\-.]", "-");
    }
}
