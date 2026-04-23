package com.rezhub.reservation;

import akka.javasdk.testkit.TestKitSupport;
import com.rezhub.reservation.api.FacilityEndpoint;
import com.rezhub.reservation.customer.dto.Address;
import com.rezhub.reservation.customer.facility.FacilityEntity;
import com.rezhub.reservation.customer.facility.dto.Facility;
import com.rezhub.reservation.resource.ResourceEntity;
import com.rezhub.reservation.resource.ResourceView;
import com.rezhub.reservation.resource.dto.Resource;
import com.rezhub.reservation.view.FacilityByBotTokenView;
import com.rezhub.reservation.view.ResourcesByFacilityView;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for the provisioning sprint changes:
 * #7 calendarId on resources, #12 facility timezone, #bot bot-token routing.
 */
public class ProvisioningIntegrationTest extends TestKitSupport {

    // --- #12: timezone and new fields on FacilityEntity ---

    @Test
    public void getFacility_returnsTimezoneAndBotToken() {
        var facilityId = "f_prov-timezone-" + shortId();
        var facility = new Facility("Timezone Club", new Address("Sport St", "Berlin"),
            "Europe/London", "bot:timezone-test", Set.of("admin1"));

        componentClient.forEventSourcedEntity(facilityId)
            .method(FacilityEntity::create)
            .invoke(facility);

        var state = componentClient.forEventSourcedEntity(facilityId)
            .method(FacilityEntity::getFacility)
            .invoke();

        assertThat(state.timezone()).isEqualTo("Europe/London");
        assertThat(state.botToken()).isEqualTo("bot:timezone-test");
        assertThat(state.adminUserIds()).containsExactly("admin1");
        assertThat(state.name()).isEqualTo("Timezone Club");
    }

    @Test
    public void getFacility_withNullFields_doesNotThrow() {
        var facilityId = "f_prov-nullfields-" + shortId();
        var facility = new Facility("Bare Club", new Address("Any St", "Town"), null, null, null);

        componentClient.forEventSourcedEntity(facilityId)
            .method(FacilityEntity::create)
            .invoke(facility);

        var state = componentClient.forEventSourcedEntity(facilityId)
            .method(FacilityEntity::getFacility)
            .invoke();

        assertThat(state.timezone()).isNull();
        assertThat(state.botToken()).isNull();
        assertThat(state.adminUserIds()).isNull();
    }

    // --- #bot: FacilityByBotTokenView ---

    @Test
    public void facilityByBotTokenView_returnsEntryAfterCreate() throws Exception {
        var facilityId = "f_prov-bottoken-" + shortId();
        var botToken = "bot:test-" + shortId();
        var facility = new Facility("Bot Club", new Address("Bot St", "Berlin"),
            "Europe/Berlin", botToken, null);

        componentClient.forEventSourcedEntity(facilityId)
            .method(FacilityEntity::create)
            .invoke(facility);

        Optional<FacilityByBotTokenView.Entry> entry = eventually(() ->
            componentClient.forView()
                .method(FacilityByBotTokenView::getByBotToken)
                .invoke(botToken),
            opt -> opt.isPresent());

        assertThat(entry).isPresent();
        assertThat(entry.get().facilityId()).isEqualTo(facilityId);
        assertThat(entry.get().timezone()).isEqualTo("Europe/Berlin");

        List<FacilityByBotTokenView.Entry> entries = componentClient.forView()
            .method(FacilityByBotTokenView::getAllByBotToken)
            .invoke(botToken)
            .entries();

        assertThat(entries).singleElement().satisfies(found -> {
            assertThat(found.facilityId()).isEqualTo(facilityId);
            assertThat(found.timezone()).isEqualTo("Europe/Berlin");
        });
    }

    @Test
    public void facilityByBotTokenView_unknownToken_returnsEmpty() {
        Optional<FacilityByBotTokenView.Entry> entry = componentClient.forView()
            .method(FacilityByBotTokenView::getByBotToken)
            .invoke("bot:nonexistent-" + shortId());

        assertThat(entry).isEmpty();

        List<FacilityByBotTokenView.Entry> entries = componentClient.forView()
            .method(FacilityByBotTokenView::getAllByBotToken)
            .invoke("bot:nonexistent-" + shortId())
            .entries();

        assertThat(entries).isEmpty();
    }

    @Test
    public void facilityByBotTokenView_noBotToken_createsNoViewRow() throws Exception {
        var facilityId = "f_prov-nobottoken-" + shortId();
        componentClient.forEventSourcedEntity(facilityId)
            .method(FacilityEntity::create)
            .invoke(new Facility("No-Bot Club", new Address("Quiet St", "Berlin"), "Europe/Berlin", null, null));

        Thread.sleep(200);

        Optional<FacilityByBotTokenView.Entry> entry = componentClient.forView()
            .method(FacilityByBotTokenView::getByBotToken)
            .invoke("bot:null-should-not-exist");

        assertThat(entry).isEmpty();
    }

    @Test
    public void clearBotToken_removesViewRow() throws Exception {
        var facilityId = "f_prov-clear-bottoken-" + shortId();
        var botToken = "bot:clear-" + shortId();
        componentClient.forEventSourcedEntity(facilityId)
            .method(FacilityEntity::create)
            .invoke(new Facility("Token Club", new Address("Bot St", "Berlin"), "Europe/Berlin", botToken, null));

        eventually(() ->
                componentClient.forView()
                    .method(FacilityByBotTokenView::getByBotToken)
                    .invoke(botToken),
            Optional::isPresent);

        FacilityEndpoint endpoint = new FacilityEndpoint(componentClient);
        assertThat(endpoint.clearBotToken(facilityId)).isEqualTo("OK");

        Optional<FacilityByBotTokenView.Entry> entry = eventually(() ->
                componentClient.forView()
                    .method(FacilityByBotTokenView::getByBotToken)
                    .invoke(botToken),
            Optional::isEmpty);

        assertThat(entry).isEmpty();
    }

    @Test
    public void createFacility_rejectsDuplicateBotToken() throws Exception {
        String botToken = "bot:duplicate-" + shortId();
        FacilityEndpoint endpoint = new FacilityEndpoint(componentClient);

        String firstId = endpoint.createFacility(new Facility(
            "First Club", new Address("One St", "Berlin"), "Europe/Berlin", botToken, null));

        assertThat(firstId).isNotBlank();

        List<FacilityByBotTokenView.Entry> entries = eventually(() ->
                componentClient.forView()
                    .method(FacilityByBotTokenView::getAllByBotToken)
                    .invoke(botToken)
                    .entries(),
            found -> !found.isEmpty());

        assertThat(entries).singleElement().satisfies(found ->
            assertThat(found.facilityId()).isEqualTo(firstId));

        assertThatThrownBy(() -> endpoint.createFacility(new Facility(
            "Second Club", new Address("Two St", "Rome"), "Europe/Rome", botToken, null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Bot token is already assigned");
    }

    // --- Two-step resource provisioning via ResourceEndpoint ---

    @Test
    public void resourcesByFacilityView_returnsResourceAfterExternalRefSet() throws Exception {
        var facilityId = "f_prov-res-" + shortId();
        var resourceId = "court-" + shortId();
        var calendarId = "cal-" + shortId() + "@group.calendar.google.com";

        componentClient.forEventSourcedEntity(facilityId)
            .method(FacilityEntity::create)
            .invoke(new Facility("Court Club", new Address("Court Rd", "Berlin"), "Europe/Berlin", null, null));

        componentClient.forEventSourcedEntity(resourceId)
            .method(ResourceEntity::create)
            .invoke(new Resource(resourceId, "Court 1", calendarId));

        componentClient.forEventSourcedEntity(resourceId)
            .method(ResourceEntity::setExternalRef)
            .invoke(new ResourceEntity.SetExternalRef(resourceId, facilityId));

        List<ResourcesByFacilityView.Row> rows = eventually(() ->
            componentClient.forView()
                .method(ResourcesByFacilityView::getByFacilityId)
                .invoke(facilityId)
                .rows(),
            found -> !found.isEmpty());

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.resourceId()).isEqualTo(resourceId);
            assertThat(row.facilityId()).isEqualTo(facilityId);
        });
    }

    @Test
    public void resourceView_getResourceById_unknownId_returnsEmpty() {
        Optional<com.rezhub.reservation.resource.ResourceV> result = componentClient.forView()
            .method(ResourceView::getResourceById)
            .invoke("nonexistent-" + shortId());

        assertThat(result).isEmpty();
    }

    // --- helpers ---

    private <T> T eventually(CheckedSupplier<T> query, java.util.function.Predicate<T> until) throws Exception {
        T last = null;
        for (int i = 0; i < 40; i++) {
            last = query.get();
            if (until.test(last)) return last;
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met after 2s. Last value: " + last);
    }

    @FunctionalInterface
    interface CheckedSupplier<T> {
        T get() throws Exception;
    }

    private static String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
