package com.rez.facility;

import com.rez.facility.api.Mod;
import kalix.spring.testkit.KalixIntegrationTestKitSupport;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Collections;

import static java.time.temporal.ChronoUnit.SECONDS;


/**
 * This is a skeleton for implementing integration tests for a Kalix application built with the Java SDK.
 * This test will initiate a Kalix Proxy using testcontainers and therefore it's required to have Docker installed
 * on your machine. This test will also start your Spring Boot application.
 * Since this is an integration tests, it interacts with the application using a WebClient
 * (already configured and provided automatically through injection).
 */
@SpringBootTest(classes = Main.class)
public class IntegrationTest extends KalixIntegrationTestKitSupport {

  @Autowired
  private WebClient webClient;

  private final Duration timeout = Duration.of(5, SECONDS);

  @Test
  public void test() {

    String cartId = "card-abc";
    Mod.Address address = new Mod.Address("street", "city");
    Mod.Facility facility = new Mod.Facility("TCL", address, Collections.emptySet());

    ResponseEntity<String> created =
            webClient.post()
                    .uri("/facility/" + cartId + "/create")
                    .body(Mono.just(facility), Mod.Facility.class)
                    .retrieve()
                    .toEntity(String.class)
                    .block(timeout);

    Assertions.assertEquals(HttpStatus.OK, created.getStatusCode());
  }
}