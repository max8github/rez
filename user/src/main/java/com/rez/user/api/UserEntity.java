package com.rez.user.api;

import com.rez.user.domain.Address;
import com.rez.user.domain.User;
import kalix.javasdk.valueentity.ValueEntity;
import kalix.javasdk.annotations.EntityKey;
import kalix.javasdk.annotations.EntityType;
import org.springframework.web.bind.annotation.*;
import io.grpc.Status;

@EntityType("user")
@EntityKey("user_id")
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