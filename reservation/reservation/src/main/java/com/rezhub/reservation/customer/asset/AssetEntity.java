package com.rezhub.reservation.customer.asset;

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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Id("assetId")
@TypeId("asset")
@RequestMapping("/asset/{assetId}")
public class AssetEntity extends EventSourcedEntity<AssetState, AssetEvent> {
  private static final Logger log = LoggerFactory.getLogger(AssetEntity.class);
  private final String entityId;

  public AssetEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public AssetState emptyState() {
    return new AssetState(entityId, "noname");
  }

  @PostMapping("/createFacilityAsset")
  public Effect<String> createFacilityAsset(@RequestBody CreateFacilityAsset resCommand) {
    String id = commandContext().entityId();
    String assetName = resCommand.asset().assetName();
    return effects()
      .emitEvent(new AssetEvent.FacilityAssetCreated(id, assetName, resCommand.facilityId()))
      .thenReply(newState -> id);
  }

  @SuppressWarnings("unused")
  @EventHandler
  public AssetState facilityAssetCreated(AssetEvent.FacilityAssetCreated event) {
    return new AssetState(event.assetId(), event.assetName());
  }

  @PostMapping("/create")
  public Effect<String> create(@RequestBody Asset command) {
    if(command.assetName().equals(Asset.FORBIDDEN_NAME)) {
      return effects().error("Invalid name: name '" + command.assetName() + "' cannot be used.", StatusCode.ErrorCode.BAD_REQUEST);
    }
    if(!currentState().name().equals(Asset.FORBIDDEN_NAME)) {
      return effects().error("Entity with id " + commandContext().entityId() + " is already created", StatusCode.ErrorCode.BAD_REQUEST);
    }
    String id = commandContext().entityId();
    return effects()
      .emitEvent(new AssetEvent.AssetCreated(id, command.assetName()))
      .thenReply(newState -> id);
  }

  @SuppressWarnings("unused")
  @EventHandler
  public AssetState created(AssetEvent.AssetCreated assetCreated) {
    return new AssetState(assetCreated.assetId(), assetCreated.assetName());
  }

  @Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
  @GetMapping()
  public Effect<AssetState> getAsset() {
    return effects().reply(currentState());
  }

  public record CreateFacilityAsset(String facilityId, Asset asset) {}
}