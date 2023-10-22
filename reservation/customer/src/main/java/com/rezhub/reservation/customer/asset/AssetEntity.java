package com.rezhub.reservation.customer.asset;

import com.rezhub.reservation.customer.asset.dto.Asset;
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

@Id("asset_id")
@TypeId("asset")
@RequestMapping("/asset/{asset_id}")
public class AssetEntity extends EventSourcedEntity<AssetState, AssetEvent> {
  private static final Logger log = LoggerFactory.getLogger(AssetEntity.class);
  private final String entityId;

  public AssetEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public AssetState emptyState() {
    return new AssetState("noname", entityId);
  }

  @PostMapping("/create")
  public Effect<String> create(@RequestBody CreateAssetCommand resCommand) {
    String assetName = resCommand.asset().assetName();
    return effects()
      .emitEvent(new AssetEvent.AssetCreated(entityId, assetName, resCommand.facilityId()))
      .thenReply(newState -> "OK - " + assetName);
  }

  @SuppressWarnings("unused")
  @EventHandler
  public AssetState created(AssetEvent.AssetCreated assetCreated) {
    return new AssetState(assetCreated.assetName(), assetCreated.assetName());
  }

  @Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
  @GetMapping()
  public Effect<AssetState> getAsset() {
    return effects().reply(currentState());
  }

  public record CreateAssetCommand(String facilityId, Asset asset) {}

}