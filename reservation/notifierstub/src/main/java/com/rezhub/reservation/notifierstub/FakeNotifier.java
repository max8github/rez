package com.rezhub.reservation.notifierstub;

import akka.javasdk.http.HttpClient;
import com.rezhub.reservation.spi.NotificationSender;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class FakeNotifier implements NotificationSender {
    @Override
    public CompletableFuture<String> messageTwist(HttpClient httpClient, String body) {
        NotificationSender.log.info("fake send to Twist with body:\n\t{}", body);
        return CompletableFuture.completedFuture(body);
    }
}
