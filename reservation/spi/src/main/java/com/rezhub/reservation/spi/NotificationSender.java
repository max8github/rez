package com.rezhub.reservation.spi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.concurrent.CompletableFuture;

public interface NotificationSender {

    Logger log = LoggerFactory.getLogger(NotificationSender.class);

    /**
     * This sends a message to the chosen Instant Messenger app implementor.
     * This is the incoming webhook: Kalix -> Instant Messenger app.<br>
     * It is used for posting a confirmation to the Instant Messenger app that something happened.
     *
     * @param webClient
     * @param body
     * @return
     */
    CompletableFuture<String> messageTwist(WebClient webClient, String body);//TODO: rename to notifyInstantMessenger()
}
