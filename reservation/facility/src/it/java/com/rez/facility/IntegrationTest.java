package com.rez.facility;

import com.rez.facility.pool.dto.Address;
import com.rez.facility.pool.dto.Facility;
import com.rez.facility.resource.ResourceState;
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
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static com.rez.facility.reservation.ReservationState.State.FULFILLED;
import static com.rez.facility.reservation.ReservationState.State.UNAVAILABLE;
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
  @Autowired
  private ReservationEntityIntegrationTest util;

  private final Duration timeout = Duration.of(5, SECONDS);

  @Test
  public void test() {

    String cartId = "card-abc";
    Address address = new Address("street", "city");
    Facility facility = new Facility("TCL", address, Collections.emptySet());

    ResponseEntity<String> created =
            webClient.post()
                    .uri("/facility/" + cartId + "/create")
                    .body(Mono.just(facility), Facility.class)
                    .retrieve()
                    .toEntity(String.class)
                    .block(timeout);

    Assertions.assertEquals(HttpStatus.OK, created.getStatusCode());
  }

  @Test
  public void shouldReserve() throws Exception {
    var resourceId1 = "c1";
    var resourceId2 = "c2";
    util.createResource(resourceId1);
    util.createResource(resourceId2);
    List<String> resourceIds = List.of(resourceId1, resourceId2);
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime dateTime = now.plusHours(2).minusMinutes(now.getMinute()).minusSeconds(now.getSecond()).minusNanos(now.getNano());
    String dateTimeString = dateTime.toString();
    System.out.println("dateTimeString to test = " + dateTimeString);

    String reservationId1 = util.issueNewReservationRequest(resourceIds, dateTime);
    System.out.println("reservationId1 = " + reservationId1);
    Thread.sleep(2000);

    String reservationId2 = util.issueNewReservationRequest(resourceIds, dateTime);
    System.out.println("reservationId2 = " + reservationId2);

    String reservationId3 = util.issueNewReservationRequest(resourceIds, dateTime);
    System.out.println("reservationId3 = " + reservationId3);
    util.assertReservationState(reservationId1, FULFILLED);
    util.assertReservationState(reservationId2, FULFILLED);
    util.assertNotBooked(reservationId3, resourceIds, dateTime);
    util.assertReservationState(reservationId3, UNAVAILABLE);
    ResourceState resourceC1 = util.getResource(resourceIds.get(0));
    ResourceState resourceC2 = util.getResource(resourceIds.get(1));
    System.out.println("resourceC1 = " + resourceC1);
    System.out.println("resourceC2 = " + resourceC2);
    util.assertBookedAtResource(reservationId1, resourceIds, 0, dateTime);
    util.assertBookedAtResource(reservationId2, resourceIds, 1, dateTime);
  }
}