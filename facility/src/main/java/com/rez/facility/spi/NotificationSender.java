package com.rez.facility.spi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.concurrent.CompletableFuture;

public interface NotificationSender {

    Logger log = LoggerFactory.getLogger(NotificationSender.class);

    CompletableFuture<String> messageTwist(WebClient webClient, String body);
}
