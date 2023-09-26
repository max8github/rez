package com.rez.facility;

import com.rez.facility.domain.ReservationState;
import com.rez.facility.dto.Reservation;
import com.rez.facility.dto.Resource;
import com.rez.facility.entities.ReservationEntity;
import com.rez.facility.entities.ResourceEntity;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static java.time.temporal.ChronoUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Component
public class ReservationEntityIntegrationTest {
  @Autowired
  private WebClient webClient;

  ReservationEntityIntegrationTest(WebClient webClient) {
    this.webClient = webClient;
  }

  static final Duration timeout = Duration.of(10, SECONDS);

  /**
   * Given the reservation id, the list of resources, and one index identifying one resource from the list,
   * it should assert that:
   * <ol>
   *     <li>the reservation id is saved inside the resource at the given index in the list</li>
   *     <li>all other resources within the given list do not have the reservation id saved in them</li>
   * </ol>
   * If the index is out of bounds, it is interpreted as saying that the reservation id should not be saved in any of the
   * resources in the list.
   *
   * @param reservationId reservation id
   * @param resIds list of resource ids, pointing to resource entities
   * @param index the index of the resource id in the list of the resource that should contain the reservation id
   * @param slotIndex the index of the time slot within the available slots for reserving
   */
  void assertBookedAtResource(String reservationId, List<String> resIds, int index, int slotIndex) {
    await()
      .atMost(10, TimeUnit.of(SECONDS))
      .untilAsserted(() -> {

        Predicate<String> p;

        if(index >= 0 && index < resIds.size()) {
          var resource = getResource(resIds.get(index));
          assertThat(resource.timeWindow()[slotIndex]).as("resource %s should be booked with %s",
                  resource.name(), reservationId).isEqualTo(reservationId);
          System.out.println("resource booked = " + resource);
          p = id -> !id.equals(resIds.get(index));
        } else {
          p = id -> true;
        }

        resIds.stream().filter(p).forEach(id -> {
          var res = getResource(id);
          assertThat(res.timeWindow()[12]).as("resource %s should NOT be booked with %s",
                  res.name(), reservationId).isNotEqualTo(reservationId);
        });
      });
  }

  void assertReservationState(String reservationId, ReservationState.State status) {
    await().atMost(10, TimeUnit.of(SECONDS))
      .untilAsserted(() ->  assertThat(getWorkflowState(reservationId).state()).isEqualTo(status));
  }

  @NotNull
  String issueNewReservationRequest(List<String> resourceIds, String dateTimeString) {
    var reservationId = randomId();
    String facilityId = "fac1";
    LocalDateTime dateTime = LocalDateTime.parse(dateTimeString);
    Reservation reservation = new Reservation(List.of("max@example.com"), dateTime);
    var command = new ReservationEntity.InitiateReservation(reservationId, facilityId, reservation, resourceIds);
    ResponseEntity<Void> response = webClient.post().uri("/reservation/" + reservationId + "/init")
            .body(Mono.just(command), ReservationEntity.InitiateReservation.class)
            .retrieve()
            .toBodilessEntity()
            .block(timeout);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    return reservationId;
  }


  String randomId() {
    return UUID.randomUUID().toString().substring(0, 8);
  }

  void createResource(String resourceId) {
    String facilityId = "fac1";
    Resource resource = new Resource(resourceId, resourceId, 24);
    var command = new ResourceEntity.CreateResourceCommand(facilityId, resource);

    ResponseEntity<Void> response = webClient.post().uri("/resource/" + resourceId + "/create")
      .body(Mono.just(command), ReservationEntity.InitiateReservation.class)
      .retrieve()
      .toBodilessEntity()
      .block(timeout);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  com.rez.facility.domain.Resource getResource(String resourceId) {
    return webClient.get().uri("/resource/" + resourceId)
      .retrieve()
      .bodyToMono(com.rez.facility.domain.Resource.class)
      .block(timeout);
  }

  ReservationState getWorkflowState(String reservationId) {
    return webClient.get().uri("/reservation/" + reservationId)
      .retrieve()
      .bodyToMono(ReservationState.class)
      .block(timeout);
  }
}