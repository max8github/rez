package com.rezhub.reservation.twistnotifier;

import com.rezhub.reservation.spi.NotificationSender;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.concurrent.CompletableFuture;

@Component
public class TwistNotifier implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(TwistNotifier.class);

    private final String twistPostDataUri;

    public TwistNotifier() {
        Config twistConfig = ConfigFactory.defaultApplication().getConfig("twist");
        String url = twistConfig.getString("url");//todo: validate the url here or else call will fail (painful)
        String install_id = twistConfig.getString("install_id");
        String install_token = System.getenv("INSTALL_TOKEN");
        twistPostDataUri = url + "?install_id=" + install_id + "&install_token=" + install_token;
    }

    /**
     * This is the incoming webhook: Kalix -> Twist.<br>
     * It is used for posting a confirmation to Twist that something happened.
     */
    @Override
    public CompletableFuture<String> messageTwist(WebClient webClient, String body) {
        log.info("Received message from Twist");
        return webClient
                .post().uri(twistPostDataUri)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class).toFuture();
    }
}
