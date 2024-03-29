package com.rezhub.reservation.customer.user;

import io.grpc.Status;
import kalix.javasdk.annotations.Id;
import kalix.javasdk.annotations.TypeId;
import kalix.javasdk.valueentity.ValueEntity;
import com.rezhub.reservation.customer.dto.Address;
import org.springframework.web.bind.annotation.*;

@Id("user_id")
@TypeId("user")
@RequestMapping("/user/{user_id}")
public class UserEntity extends ValueEntity<User> {

  @PostMapping("/create")
  public ValueEntity.Effect<String> create(@RequestBody User user) {
    if (currentState() == null)
      return effects()
        .updateState(user)
        .thenReply("OK");
    else
      return effects().error("Facility exists already");
  }

  @GetMapping()
  public ValueEntity.Effect<User> getUser() {
    if (currentState() == null)
      return effects().error(
        "No user found for id '" + commandContext().entityId() + "'",
        Status.Code.NOT_FOUND
      );
    else
      return effects().reply(currentState());
  }

  @PostMapping("/changeName/{newName}")
  public Effect<String> changeName(@PathVariable String newName) {
    User updatedUser = currentState().withName(newName);
    return effects()
      .updateState(updatedUser)
      .thenReply("OK");
  }

  @PostMapping("/changeAddress")
  public Effect<String> changeAddress(@RequestBody Address newAddress) {
    User updatedUser = currentState().withAddress(newAddress);
    return effects().updateState(updatedUser).thenReply("OK");
  }

}