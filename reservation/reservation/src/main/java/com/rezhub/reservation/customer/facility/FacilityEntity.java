package com.rezhub.reservation.customer.facility;

import com.rezhub.reservation.customer.facility.dto.Address;
import com.rezhub.reservation.customer.facility.dto.Facility;
import com.rezhub.reservation.customer.asset.dto.Asset;
import kalix.javasdk.StatusCode;
import kalix.javasdk.annotations.Acl;
import kalix.javasdk.annotations.EventHandler;
import kalix.javasdk.annotations.Id;
import kalix.javasdk.annotations.TypeId;
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import kalix.javasdk.eventsourcedentity.EventSourcedEntityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

@Id("facilityId")
@TypeId("facility")
@RequestMapping("/facility/{facilityId}")
public class FacilityEntity extends EventSourcedEntity<FacilityState, FacilityEvent> {
  private static final Logger log = LoggerFactory.getLogger(FacilityEntity.class);
  private final String entityId;

  public FacilityEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public FacilityState emptyState() {
    return FacilityState.create(entityId).withName(Asset.FORBIDDEN_NAME).withAddressState(new AddressState("nostreet", "nocity"));
  }

  @Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
  @PostMapping("/create")
  public Effect<String> create(@RequestBody Facility facility) {
    if(facility.name().equals(Asset.FORBIDDEN_NAME)) {
      return effects().error("Invalid name: name '" + facility.name() + "' cannot be used.", StatusCode.ErrorCode.BAD_REQUEST);
    }
    if(!currentState().name().equals(Asset.FORBIDDEN_NAME)) {
      return effects().error("Entity with id " + commandContext().entityId() + " is already created", StatusCode.ErrorCode.BAD_REQUEST);
    }
    log.info("created facility {}", facility.name());
    return effects()
      .emitEvent(new FacilityEvent.Created(entityId, facility))
      .thenReply(newState -> entityId);
  }

  @SuppressWarnings("unused")
  @EventHandler
  public FacilityState created(FacilityEvent.Created created) {
    var dto = created.facility();
    return FacilityState.create(created.entityId())
      .withName(dto.name())
      .withAddressState(new AddressState(dto.address().street(), dto.address().city()))
      .withAssetIds(dto.assetIds());
  }

  @PutMapping("/rename/{newName}")
  public Effect<String> rename(@PathVariable String newName) {
    return effects()
      .emitEvent(new FacilityEvent.Renamed(newName))
      .thenReply(newState -> "OK");
  }

  @SuppressWarnings("unused")
  @EventHandler
  public FacilityState renamed(FacilityEvent.Renamed renamed) {
    return currentState().withName(renamed.newName());
  }

  @Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
  @PutMapping("/changeAddress")
  public Effect<String> changeAddress(@RequestBody AddressState addressState) {
    return effects()
      .emitEvent(new FacilityEvent.AddressChanged(addressState))
      .thenReply(newState -> "OK");
  }

  @SuppressWarnings("unused")
  @EventHandler
  public FacilityState addressChanged(FacilityEvent.AddressChanged addressChanged) {
    AddressState addressState = addressChanged.addressState();
    return currentState().withAddressState(new AddressState(addressState.street(), addressState.city()));
  }

  @Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
  @PostMapping("/asset/requestAssetCreateAndRegister")
  public Effect<String> requestAssetCreateAndRegister(@RequestBody Asset asset) {
    String assetId = asset.assetId();
    return effects()
      .emitEvent(new FacilityEvent.AssetCreateAndRegisterRequested(currentState().facilityId(), asset, assetId))
      .thenReply(newState -> assetId);
  }

  @SuppressWarnings("unused")
  @EventHandler
  public FacilityState assetCreateAndRegisterRequested(FacilityEvent.AssetCreateAndRegisterRequested event) {
    return currentState();
  }

  @PutMapping("/registerAsset/{assetId}")
  public Effect<String> registerAsset(@PathVariable String assetId) {
    log.info("registering asset with id {}", assetId);
    return effects()
      .emitEvent(new FacilityEvent.AssetRegistered(assetId))
      .thenReply(newState -> assetId);
  }

  @SuppressWarnings("unused")
  @EventHandler
  public FacilityState assetRegistered(FacilityEvent.AssetRegistered event) {
    return currentState().registerAsset(event.assetId());
  }

  @DeleteMapping("/asset/{assetId}")
  public Effect<String> removeAssetId(@PathVariable String assetId) {
    if (!currentState().assetIds().contains(assetId)) {
      return effects().error("Cannot remove asset " + assetId + " because it is not in the facility.");
    }
    return effects()
      .emitEvent(new FacilityEvent.AssetIdRemoved(assetId))
      .thenReply(newState -> "OK");
  }

  @SuppressWarnings("unused")
  @EventHandler
  public FacilityState assetIdRemoved(FacilityEvent.AssetIdRemoved event) {
    return currentState().unregisterAsset(event.assetId());
  }

  @Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
  @GetMapping()
  public Effect<Facility> getFacility() {
    FacilityState state = currentState();
    Address address = new Address(state.addressState().street(), state.addressState().city());
    return effects().reply(new Facility(state.name(), address, state.assetIds()));
  }
}