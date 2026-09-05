<!-- <nav> -->
- [Akka](../index.html)
- [Testing](index.html)
- [Integration](integration.html)

<!-- </nav> -->

# Integration testing

Integration testing in Akka exercises multiple components together against a real in-process runtime.
External dependencies are stubbed at the boundary.
A test class extends `TestKitSupport`, opts into the in-process runtime, and drives components through `ComponentClient`.

## <a href="about:blank#_the_shortest_possible_integration_test"></a> The shortest possible integration test

```java
class CheckoutFlowIT extends TestKitSupport {

  @Test
  void placesAnOrder() {
    var order = componentClient
        .forEventSourcedEntity("order-42")
        .method(OrderEntity::place)
        .invoke(new PlaceOrder("book-1", 1));

    assertThat(order.total()).isEqualTo(1900);
  }
}
```

## <a href="about:blank#_what_akka_provides_for_integration_testing"></a> What Akka provides for integration testing

### <a href="about:blank#_in_process_runtime"></a> In-process runtime

`TestKitSupport` boots a real Akka runtime in-process.
Component invocation, event journal, view projection, and workflow durability are all real.
Model calls are stubbed when a `TestModelProvider` is registered.
Broker consumers are stubbed when the test does not set one up.

### <a href="about:blank#_component_client"></a> Component client

`ComponentClient` is the same client the runtime uses in production.
Invocations go through real serialization and dispatch.
Use it to call entities, workflows, views, and endpoints.

### <a href="about:blank#_deterministic_model_responses"></a> Deterministic model responses

Use `TestModelProvider` to remove non-determinism from agents.

```java
var provider = new TestModelProvider();
provider.fixedResponse("hello");

provider.whenMessage(m -> m.contains("weather"))
        .reply("It's sunny.");

provider.whenToolResult(tr -> tr.name().equals("Weather_get"))
        .thenReply("Reported the weather.");
```
The tool name the model sees is prefixed with the tool’s simple class name.

### <a href="about:blank#_deterministic_broker_fixtures"></a> Deterministic broker fixtures

`TestBrokerFixture` provides an in-process broker for consumers and producers.
Publish messages, drive consumers, and assert on downstream state.

### <a href="about:blank#_deterministic_external_services"></a> Deterministic external services

For a service the target calls over HTTP or gRPC, add a small in-process endpoint that stands in for the real service.
Wire it in `TestKitSettings.withRemoteService(…​)`.

### <a href="about:blank#_time_control"></a> Time control

The virtual clock advances on demand.
Advance it with `advance(Duration)` to drive timers, retries, and workflow steps that wait.
Do not sleep in tests.

## <a href="about:blank#_recommended_utilities"></a> Recommended utilities

| Concern | Utility |
| --- | --- |
| Deterministic model responses | `TestModelProvider` |
| A real event journal for entities and workflows | `TestKitSupport` in-process runtime |
| A component invocation with real serialization | `ComponentClient` from `TestKitSupport` |
| An external HTTP dependency | In-process endpoint plus `TestKitSettings.withRemoteService` |
| An external broker | `TestBrokerFixture` |
| Clock control (timers, retries) | Virtual clock via `advance(Duration)` |

## <a href="about:blank#_best_practices"></a> Best practices

- Isolate persistent state between tests. Use a fresh `TestKitSupport` per class, or reset explicitly.
- Register a `TestModelProvider` for every agent under test.
- Stub every external HTTP dependency with an in-process endpoint.
- Advance the virtual clock explicitly. Do not sleep.
- Assert on state via `ComponentClient`, not on log output.
- Prefer one integration test per user-visible outcome, not per code path.
- Run integration tests under a separate Maven profile when they take longer than a second.

## <a href="about:blank#_related"></a> Related

- [Unit testing](unit.html)
- [Evaluation](evaluation/index.html)
- [Red teaming](red-teaming/index.html)

<!-- <footer> -->
<!-- <nav> -->
[Unit](unit.html) [Evaluation](evaluation/index.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->