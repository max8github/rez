package com.rezhub.reservation.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.annotations.http.Put;
import akka.javasdk.client.ComponentClient;
import com.rezhub.reservation.customer.dto.Address;
import com.rezhub.reservation.customer.user.User;
import com.rezhub.reservation.customer.user.UserEntity;

/**
 * HTTP endpoint for User operations.
 */
@HttpEndpoint("/user")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class UserEndpoint {

    private final ComponentClient componentClient;

    public UserEndpoint(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    @Post("/{userId}")
    public String createUser(String userId, User user) {
        return componentClient
            .forKeyValueEntity(userId)
            .method(UserEntity::create)
            .invoke(user);
    }

    @Get("/{userId}")
    public User getUser(String userId) {
        return componentClient
            .forKeyValueEntity(userId)
            .method(UserEntity::getUser)
            .invoke();
    }

    @Put("/{userId}/name")
    public String changeName(String userId, NameChangeRequest request) {
        return componentClient
            .forKeyValueEntity(userId)
            .method(UserEntity::changeName)
            .invoke(request.name());
    }

    @Put("/{userId}/address")
    public String changeAddress(String userId, Address address) {
        return componentClient
            .forKeyValueEntity(userId)
            .method(UserEntity::changeAddress)
            .invoke(address);
    }

    public record NameChangeRequest(String name) {}
}
