package com.rezhub.reservation.telegramnotifier;

import com.rezhub.reservation.spi.NotificationSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;

/**
 * Sends notifications back to a Telegram chat via the Bot API.
 * recipientId is a base64url-encoded string of "<botToken>:<chatId>", making it
 * opaque to the LLM so it passes through unchanged.
 */
public class TelegramNotifier implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(TelegramNotifier.class);
    private static final String API_BASE = "https://api.telegram.org";

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Override
    public CompletableFuture<String> send(String recipientId, String text) {
        // recipientId is base64url("<botToken>:<chatId>") — decode, then split on last colon
        String decoded;
        try {
            decoded = new String(Base64.getUrlDecoder().decode(recipientId));
        } catch (IllegalArgumentException e) {
            log.warn("TelegramNotifier: recipientId '{}' is not valid base64url, skipping send", recipientId);
            return CompletableFuture.completedFuture("SKIPPED");
        }
        int lastColon = decoded.lastIndexOf(':');
        if (lastColon <= 0) {
            log.warn("TelegramNotifier: decoded recipientId '{}' has no colon, skipping send", decoded);
            return CompletableFuture.completedFuture("SKIPPED");
        }
        String botToken = decoded.substring(0, lastColon);
        String chatId = decoded.substring(lastColon + 1);
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
