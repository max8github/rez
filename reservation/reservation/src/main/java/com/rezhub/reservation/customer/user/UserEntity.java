package com.rezhub.reservation.customer.user;

import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import akka.javasdk.keyvalueentity.KeyValueEntityContext;
import com.rezhub.reservation.customer.dto.Address;

@Component(id = "user")
public class UserEntity extends KeyValueEntity<User> {

    private final String entityId;

    public UserEntity(KeyValueEntityContext context) {
        this.entityId = context.entityId();
    }

    public Effect<String> create(User user) {
        if (currentState() == null)
            return effects()
                .updateState(user)
                .thenReply("OK");
        else
            return effects().error("Facility exists already");
    }

    public ReadOnlyEffect<User> getUser() {
        if (currentState() == null)
            return effects().error(
                "No user found for id '" + entityId + "'"
            );
        else
            return effects().reply(currentState());
    }

    public Effect<String> changeName(String newName) {
        User updatedUser = currentState().withName(newName);
        return effects()
            .updateState(updatedUser)
            .thenReply("OK");
    }

    public Effect<String> changeAddress(Address newAddress) {
        User updatedUser = currentState().withAddress(newAddress);
        return effects().updateState(updatedUser).thenReply("OK");
    }
}
