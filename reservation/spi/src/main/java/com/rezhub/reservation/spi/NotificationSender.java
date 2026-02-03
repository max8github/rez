package com.rezhub.reservation.spi;

import akka.javasdk.http.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

public interface NotificationSender {

    Logger log = LoggerFactory.getLogger(NotificationSender.class);

    CompletableFuture<String> messageTwist(HttpClient httpClient, String body);
}
