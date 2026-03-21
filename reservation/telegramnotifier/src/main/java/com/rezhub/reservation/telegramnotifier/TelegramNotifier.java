package com.rezhub.reservation.telegramnotifier;

import com.rezhub.reservation.spi.NotificationSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

/**
 * Sends notifications back to a Telegram chat via the Bot API.
 * recipientId must be the numeric chat_id (as a String) from the original Update.
 * Bot token is read from the TELEGRAM_BOT_TOKEN environment variable.
 */
public class TelegramNotifier implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(TelegramNotifier.class);
    private static final String API_BASE = "https://api.telegram.org";

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final String botToken;

    public TelegramNotifier() {
        this.botToken = System.getenv("TELEGRAM_BOT_TOKEN");
        if (botToken == null || botToken.isBlank()) {
            log.warn("TELEGRAM_BOT_TOKEN is not set — TelegramNotifier will fail at runtime");
        }
    }

    @Override
    public CompletableFuture<String> send(String recipientId, String text) {
        log.info("Sending to Telegram chat {}: {}", recipientId, text);
        String body = "{\"chat_id\": " + recipientId + ", \"text\": \"" + escape(text) + "\", \"parse_mode\": \"HTML\"}";
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(API_BASE + "/bot" + botToken + "/sendMessage"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(HttpResponse::body);
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n");
    }
}
