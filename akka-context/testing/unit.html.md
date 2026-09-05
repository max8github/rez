<!-- <nav> -->
- [Akka](../index.html)
- [Testing](index.html)
- [Unit](unit.html)

<!-- </nav> -->

# Unit testing

Unit testing in Akka exercises one component at a time.
Every component ships with a matching TestKit.
The pattern is the same for every component: create the TestKit, invoke the component, assert on the emitted effect or resulting state.

## <a href="about:blank#_the_shortest_possible_unit_test"></a> The shortest possible unit test

```java
class CounterTest {

  @Test
  void incrementsFromZero() {
    var testKit = EventSourcedTestKit.of(Counter::new);

    var result = testKit.method(Counter::increment).invoke();

    assertThat(result.getReply()).isEqualTo(1);
    assertThat(testKit.getState()).isEqualTo(1);
  }
}
```

## <a href="about:blank#_testkit_by_component"></a> TestKit by component

The table lists every component and the TestKit facility to use.
Each entry links to the deterministic-testing section on that component’s own page.

| Component | What the TestKit gives you | Documentation |
| --- | --- | --- |
| Agent | `TestModelProvider` for deterministic model replies; `whenMessage` and `whenToolResult` shaping. | [Testing the agent](../sdk/agents/testing.html) |
| Autonomous agent | `TestKitSupport` with autonomous-agent bootstrap and controllable task lifecycle. | [Testing](../sdk/autonomous-agents/testing.html) |
| Event Sourced Entity | `EventSourcedTestKit`. Apply commands, assert on emitted events and next state. | [sdk:event-sourced-entities.adoc#_testing_the_entity](../sdk/event-sourced-entities.html#_testing_the_entity) |
| Key Value Entity | `KeyValueEntityTestKit`. Apply commands, assert on state transitions. | [sdk:key-value-entities.adoc#_testing_the_entity](../sdk/key-value-entities.html#_testing_the_entity) |
| Workflow | `WorkflowTestKit`. Drive step by step, assert on deterministic step results. | [Implementing Workflows](../sdk/workflows.html) |
| Timer | A virtual clock the test advances; assert on scheduled firings. | [Timers](../sdk/timed-actions.html) |
| Consumer | Feed events through the consumer; assert on side-effects. | [Consuming and producing](../sdk/consuming-producing.html) |
| HTTP endpoint | Deployable in-process runtime; call over HTTP with the injected client. | [Designing HTTP Endpoints](../sdk/http-endpoints.html) |
| gRPC endpoint | Deployable in-process runtime; call over gRPC with the injected client. | [Designing gRPC Endpoints](../sdk/grpc-endpoints.html) |
| MCP endpoint | Deployable in-process runtime; call over MCP with the injected client. | [Designing MCP Endpoints](../sdk/mcp-endpoints.html) |
| View | Feed source events, query the view, assert on rows. | [Implementing Views](../sdk/views.html) |

## <a href="about:blank#_best_practices"></a> Best practices

- Extend `TestKitSupport` once per test class.
- Register a `TestModelProvider` for every agent under test.
- Assert on effects and state, not on log output.
- Reset the TestKit between tests when the component holds durable state.
- Keep unit tests under 100 ms. Move anything longer to integration.
- Use a separate `TestModelProvider` instance per agent when multiple agents share a test.

## <a href="about:blank#_related"></a> Related

- [Integration testing](integration.html)
- [Evaluation](evaluation/index.html)
- [Red teaming](red-teaming/index.html)

<!-- <footer> -->
<!-- <nav> -->
[Testing](index.html) [Integration](integration.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->