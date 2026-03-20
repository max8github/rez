package com.rezhub.reservation.twistnotifier;

import com.rezhub.reservation.spi.NotificationSender;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

public class TwistNotifier implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(TwistNotifier.class);

    private final String twistPostDataUri;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public TwistNotifier() {
        Config twistConfig = ConfigFactory.defaultApplication().getConfig("twist");
        String url = twistConfig.getString("url");
        String install_id = twistConfig.getString("install_id");
        String install_token = System.getenv("INSTALL_TOKEN");
        twistPostDataUri = url + "?install_id=" + install_id + "&install_token=" + install_token;
    }

    /**
     * Send a notification back to a Twist thread.
     * recipientId is the Twist thread_id (used as the post target URL suffix).
     */
    @Override
    public CompletableFuture<String> send(String recipientId, String text) {
        log.info("Sending to Twist thread {}: {}", recipientId, text);
        String body = "{\"content\": \"" + text.replace("\"", "\\\"") + "\"}";
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(twistPostDataUri))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(HttpResponse::body);
    }
}
