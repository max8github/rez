package com.rezhub.reservation.view;

import com.rezhub.reservation.customer.facility.FacilityEntity;
import com.rezhub.reservation.customer.facility.FacilityEvent;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;

import java.util.List;
import java.util.Optional;

@Component(id = "view_facilities_by_bot_token")
public class FacilityByBotTokenView extends View {

    public record Entry(String facilityId, String botToken, String timezone) {}
    public record Entries(List<Entry> entries) {}

    @Consume.FromEventSourcedEntity(FacilityEntity.class)
    public static class FacilitiesByBotTokenUpdater extends TableUpdater<Entry> {

        public Effect<Entry> onEvent(FacilityEvent event) {
            return switch (event) {
                case FacilityEvent.Created e ->
                    e.facility().botToken() != null
                        ? effects().updateRow(new Entry(e.entityId(), e.facility().botToken(), e.facility().timezone()))
                        : effects().ignore();
                case FacilityEvent.Renamed e -> effects().ignore();
                case FacilityEvent.AddressChanged e -> effects().ignore();
                case FacilityEvent.BotTokenUpdated e ->
                    e.botToken() == null || e.botToken().isBlank()
                        ? effects().deleteRow()
                        : effects().updateRow(new Entry(e.facilityId(), e.botToken(), e.timezone()));
                case FacilityEvent.ResourceCreateAndRegisterRequested e -> effects().ignore();
                case FacilityEvent.ResourceRegistered e -> effects().ignore();
                case FacilityEvent.ResourceUnregistered e -> effects().ignore();
                case FacilityEvent.AvalabilityRequested e -> effects().ignore();
            };
        }
    }

    @Query("SELECT * FROM facilities_by_bot_token WHERE botToken = :bot_token LIMIT 1")
    public QueryEffect<Optional<Entry>> getByBotToken(String bot_token) {
        return queryResult();
    }

    @Query("SELECT * AS entries FROM facilities_by_bot_token WHERE botToken = :bot_token")
    public QueryEffect<Entries> getAllByBotToken(String bot_token) {
        return queryResult();
    }
}
