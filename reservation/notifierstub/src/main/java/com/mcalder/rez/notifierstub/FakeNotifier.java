package com.mcalder.rez.notifierstub;

import com.mcalder.rez.spi.NotificationSender;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.concurrent.CompletableFuture;

@Component
public class FakeNotifier implements NotificationSender {
    @Override
    public CompletableFuture<String> messageTwist(WebClient webClient, String body) {
        NotificationSender.log.info("fake send to Twist with body:\n\t{}", body);
        return CompletableFuture.completedFuture(body);
    }
}
