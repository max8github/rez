package customer.api;

import kalix.javasdk.valueentity.ValueEntity;
import kalix.javasdk.annotations.EntityKey;
import kalix.javasdk.annotations.EntityType;
import org.springframework.web.bind.annotation.*;
import io.grpc.Status;
import customer.domain.Address;
import customer.domain.Customer;

@EntityType("customer")
@EntityKey("customer_id")
@RequestMapping("/customer/{customer_id}")
public class CustomerEntity extends ValueEntity<Customer> {

    @PostMapping("/create")
    public ValueEntity.Effect<String> create(@RequestBody Customer customer) {
        if (currentState() == null)
            return effects()
                    .updateState(customer)
                    .thenReply("OK");
        else
            return effects().error("Facility exists already");
    }

    @GetMapping()
    public ValueEntity.Effect<Customer> getCustomer() {
        if (currentState() == null)
            return effects().error(
                    "No customer found for id '" + commandContext().entityId() + "'",
                    Status.Code.NOT_FOUND
            );
        else
            return effects().reply(currentState());
    }

    @PostMapping("/changeName/{newName}")
    public Effect<String> changeName(@PathVariable String newName) {
        Customer updatedCustomer = currentState().withName(newName);
        return effects()
                .updateState(updatedCustomer)
                .thenReply("OK");
    }

    @PostMapping("/changeAddress")
    public Effect<String> changeAddress(@RequestBody Address newAddress) {
        Customer updatedCustomer = currentState().withAddress(newAddress);
        return effects().updateState(updatedCustomer).thenReply("OK");
    }

}