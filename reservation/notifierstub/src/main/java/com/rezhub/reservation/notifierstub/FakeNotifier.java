package com.rezhub.reservation.notifierstub;

import com.rezhub.reservation.spi.NotificationSender;

import java.util.concurrent.CompletableFuture;

public class FakeNotifier implements NotificationSender {
    @Override
    public CompletableFuture<String> send(String recipientId, String text) {
        NotificationSender.log.info("fake send to {} : {}", recipientId, text);
        return CompletableFuture.completedFuture(text);
    }
}
