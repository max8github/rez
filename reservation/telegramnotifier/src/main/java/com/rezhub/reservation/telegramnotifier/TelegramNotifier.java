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
 * recipientId must be in the format "<botToken>:<chatId>", where botToken is the
 * Telegram bot token and chatId is the numeric chat ID from the original Update.
 */
public class TelegramNotifier implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(TelegramNotifier.class);
    private static final String API_BASE = "https://api.telegram.org";

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Override
    public CompletableFuture<String> send(String recipientId, String text) {
        // Format: <botToken>:<chatId> where botToken itself contains a colon (e.g. 12345:ABCdef)
        // Split on the LAST colon to separate chatId from botToken
        int lastColon = recipientId.lastIndexOf(':');
        String botToken = recipientId.substring(0, lastColon);
        String chatId = recipientId.substring(lastColon + 1);
        log.info("Sending to Telegram chat {}: {}", chatId, text);
        String body = "{\"chat_id\": " + chatId + ", \"text\": \"" + escape(text) + "\", \"parse_mode\": \"HTML\"}";
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
