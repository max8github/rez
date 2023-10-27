package com.rezhub.reservation;

import com.rezhub.reservation.customer.facility.dto.Facility;
import com.rezhub.reservation.dto.Reservation;
import com.rezhub.reservation.reservation.ReservationEntity;
import com.rezhub.reservation.reservation.ReservationState;
import com.rezhub.reservation.resource.ResourceEntity;
import com.rezhub.reservation.resource.ResourceState;
import com.rezhub.reservation.resource.dto.Resource;
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
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static java.time.temporal.ChronoUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Component
public class WebClientUtil {
  @Autowired
  private WebClient webClient;

  WebClientUtil(WebClient webClient) {
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
   * @param resourceIds   list of resource ids, pointing to resource entities
   * @param index         the index of the resource id in the list of the resource that should contain the reservation id
   * @param dateTime      the time slot within the available slots for reserving
   */
  void assertBookedAtResource(String reservationId, List<String> resourceIds, int index, LocalDateTime dateTime) {
    await()
      .atMost(10, TimeUnit.of(SECONDS))
      .untilAsserted(() -> {
        Objects.checkIndex(index, resourceIds.size());

        ResourceState resource = getResource(resourceIds.get(index));
        assertThat(!resource.isReservableAt(dateTime)).as("resource %s cannot be booked with %s at resource %s",
          resource.name(), dateTime, resourceIds.get(index)).isTrue();
        assertThat(resource.get(dateTime)).as("reservation id").isEqualTo(reservationId);
        System.out.println("resource booked = " + resource);

        Predicate<String> predicate = id -> !id.equals(resourceIds.get(index));
        resourceIds.stream().filter(predicate).forEach(resourceId -> {
          ResourceState resourceState = getResource(resourceId);
          assertThat(resourceState.get(dateTime)).as("resource %s should NOT be booked with %s",
            resourceState.name(), reservationId).isNotEqualTo(reservationId);
        });
      });
  }

  void assertNotBooked(String reservationId, List<String> resourceIds, LocalDateTime dateTime) {
    await()
      .atMost(10, TimeUnit.of(SECONDS))
      .untilAsserted(() -> resourceIds.forEach(resourceId -> {
        ResourceState resourceState = getResource(resourceId);
        assertThat(resourceState.get(dateTime)).as("resource %s should NOT be booked with %s",
          resourceState.name(), reservationId).isNotEqualTo(reservationId);
      }));
  }

  void assertBooked(String reservationId, List<String> resourceIds, LocalDateTime dateTime) {
    await()
      .atMost(10, TimeUnit.of(SECONDS))
      .untilAsserted(() -> {
        boolean b = resourceIds.stream().anyMatch(resourceId -> getResource(resourceId).get(dateTime).equals(reservationId));
        assertThat(b).isTrue();
      });
  }

  void assertReservationState(String reservationId, ReservationState.State status) {
    await().atMost(10, TimeUnit.of(SECONDS))
      .untilAsserted(() -> assertThat(getReservationState(reservationId).state()).isEqualTo(status));
  }

  @NotNull
  String issueNewReservationRequest(String poolId, LocalDateTime dateTime) {
    var reservationId = UUID.randomUUID().toString().replaceAll("-", "");
    Reservation reservation = new Reservation(List.of("max@example.com"), dateTime);
    Set<String> resources = Set.of(poolId);
    ReservationEntity.Init reservationRequest = new ReservationEntity.Init(reservation, resources);
    ResponseEntity<String> response = webClient.post().uri("selection/" + reservationId)
      .body(Mono.just(reservationRequest), ReservationEntity.Init.class)
      .retrieve()
      .toEntity(String.class)
      .block(timeout);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    System.out.println("Reservation '" + reservationId + "' initiated");
    return reservationId;
  }

  void createAndRegisterResource(String facilityId, String resourceId) {
    Resource resourceDto = new Resource(resourceId, resourceId);
    var command = new ResourceEntity.CreateResourceCommand(facilityId, resourceDto);

    ResponseEntity<Void> response = webClient.post().uri("/facility/" + facilityId + "/resource/" + resourceId)
      .body(Mono.just(command), ReservationEntity.Init.class)
      .retrieve()
      .toBodilessEntity()
      .block(timeout);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    ResourceState resource = getResource(resourceId);
    System.out.println("resource = " + resource);
  }

  ResourceState getResource(String resourceId) {
    return webClient.get().uri("/resource/" + resourceId)
      .retrieve()
      .bodyToMono(ResourceState.class)
      .block(timeout);
  }

  Facility getFacility(String id) {
    return webClient.get().uri("/facility/" + id)
      .retrieve()
      .bodyToMono(Facility.class)
      .block(timeout);
  }

  ReservationState getReservationState(String reservationId) {
    return webClient.get().uri("/reservation/" + reservationId)
      .retrieve()
      .bodyToMono(ReservationState.class)
      .block(timeout);
  }
}