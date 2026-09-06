package com.rezhub.reservation.infrastructure;

import akka.javasdk.http.HttpClient;
import akka.javasdk.http.HttpClientProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fail-open client for the Telegram Bot API's {@code getMe} call, used to resolve a bot's own
 * {@code @username} from its token — needed to build a {@code https://t.me/<username>} deep link that
 * actually reopens the right chat, rather than Telegram's generic homepage (research note: a bare
 * {@code https://t.me/} return URL from Stripe Checkout gives the OS nothing bot-specific to hand off
 * to).
 *
 * <p>A bot's username never changes for a given token, so results are cached for the process
 * lifetime — avoids a Telegram API round trip on every card-setup link.
 */
public class TelegramClient {

    private static final Logger log = LoggerFactory.getLogger(TelegramClient.class);
    private static final Map<String, String> usernameCache = new ConcurrentHashMap<>();

    private final HttpClient http;

    public TelegramClient(HttpClientProvider httpClientProvider) {
        this.http = httpClientProvider.httpClientFor("https://api.telegram.org");
    }

    public record User(String username) {}

    public record GetMeResponse(boolean ok, User result) {}

    /**
     * Resolves the bot's {@code @username} for the given token. Returns empty on any failure —
     * callers should fall back to a generic link rather than propagate the error.
     */
    public Optional<String> resolveUsername(String botToken) {
        String cached = usernameCache.get(botToken);
        if (cached != null) {
            return Optional.of(cached);
        }
        try {
            var response = http.GET("/bot" + botToken + "/getMe")
                    .responseBodyAs(GetMeResponse.class)
                    .invoke();
            if (!response.status().isSuccess() || !response.body().ok() || response.body().result() == null) {
                log.warn("Telegram getMe returned {}", response.status());
                return Optional.empty();
            }
            String username = response.body().result().username();
            if (username == null || username.isBlank()) {
                return Optional.empty();
            }
            usernameCache.put(botToken, username);
            return Optional.of(username);
        } catch (Exception e) {
            log.warn("Telegram getMe failed: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
