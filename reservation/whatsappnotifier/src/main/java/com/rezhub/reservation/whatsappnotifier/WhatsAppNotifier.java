package com.rezhub.reservation.whatsappnotifier;

import com.rezhub.reservation.spi.NotificationSender;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.concurrent.CompletableFuture;

@Component
public class WhatsAppNotifier implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppNotifier.class);

    private final String postDataUri;

    public WhatsAppNotifier() {
        Config whatsappConfig = ConfigFactory.defaultApplication().getConfig("whatsapp");
        String url = whatsappConfig.getString("url");//todo: validate the url here or else call will fail (painful)
        String install_id = whatsappConfig.getString("install_id");
        String install_token = System.getenv("INSTALL_TOKEN");
        postDataUri = url + "?install_id=" + install_id + "&install_token=" + install_token;
    }

    /**
     * This is the incoming webhook: Kalix -> whatsapp.<br>
     * It is used for posting a confirmation to whatsapp that something happened.
     */
    @Override
    public CompletableFuture<String> messageTwist(WebClient webClient, String body) {
        log.info("Received message from Twist");
        return webClient
                .post().uri(postDataUri)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class).toFuture();
    }
}
