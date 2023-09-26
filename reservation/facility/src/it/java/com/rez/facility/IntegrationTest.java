package com.rez.facility;

import com.rez.facility.dto.Address;
import com.rez.facility.dto.Facility;
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

import static com.rez.facility.domain.ReservationState.State.FULFILLED;
import static com.rez.facility.domain.ReservationState.State.UNAVAILABLE;
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
  public void shouldReserve() {
    var resourceId1 = "c1";
    var resourceId2 = "c2";
    util.createResource(resourceId1);
    util.createResource(resourceId2);
    List<String> resourceIds = List.of(resourceId1, resourceId2);
    LocalDateTime dateTime = LocalDateTime.now().plusHours(2);
    dateTime = dateTime.minusMinutes(dateTime.getMinute());
    int timeSlot = dateTime.getHour();
    String dateTimeString = dateTime.toString();
    System.out.println("dateTimeString to test = " + dateTimeString);

    String reservationId1 = util.issueNewReservationRequest(resourceIds, dateTimeString);
    System.out.println("reservationId1 = " + reservationId1);
    System.out.println("reservationId1 = " + reservationId1);
    util.assertBookedAtResource(reservationId1, resourceIds, 0, timeSlot);
    util.assertReservationState(reservationId1, FULFILLED);

    String reservationId2 = util.issueNewReservationRequest(resourceIds, dateTimeString);
    System.out.println("reservationId2 = " + reservationId2);
    util.assertBookedAtResource(reservationId2, resourceIds, 1, timeSlot);
    util.assertReservationState(reservationId2, FULFILLED);

    String reservationId3 = util.issueNewReservationRequest(resourceIds, dateTimeString);
    System.out.println("reservationId3 = " + reservationId3);
    util.assertBookedAtResource(reservationId3, resourceIds, -1, timeSlot);
    util.assertReservationState(reservationId3, UNAVAILABLE);
  }
}