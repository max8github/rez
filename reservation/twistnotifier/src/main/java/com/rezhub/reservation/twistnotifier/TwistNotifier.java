package com.rezhub.reservation.twistnotifier;

import akka.javasdk.http.HttpClient;
import com.rezhub.reservation.spi.NotificationSender;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

public class TwistNotifier implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(TwistNotifier.class);

    private final String twistPostDataUri;

    public TwistNotifier() {
        Config twistConfig = ConfigFactory.defaultApplication().getConfig("twist");
        String url = twistConfig.getString("url");
        String install_id = twistConfig.getString("install_id");
        String install_token = System.getenv("INSTALL_TOKEN");
        twistPostDataUri = url + "?install_id=" + install_id + "&install_token=" + install_token;
    }

    /**
     * This is the incoming webhook: Akka -> Twist.
     * It is used for posting a confirmation to Twist that something happened.
     */
    @Override
    public CompletableFuture<String> messageTwist(HttpClient httpClient, String body) {
        log.info("Received message from Twist");
        return httpClient
            .POST(twistPostDataUri)
            .withRequestBody(body)
            .invokeAsync()
            .thenApply(response -> response.body().utf8String())
            .toCompletableFuture();
    }
}
