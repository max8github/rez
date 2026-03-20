package com.rezhub.reservation.spi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

public interface NotificationSender {

    Logger log = LoggerFactory.getLogger(NotificationSender.class);

    /**
     * Send a message back to the user on their messaging service.
     *
     * @param recipientId  service-specific destination (e.g. Telegram chat_id, Twist thread_id)
     * @param text         the message text to deliver
     */
    CompletableFuture<String> send(String recipientId, String text);
}
