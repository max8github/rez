<!-- <nav> -->
- [Akka](../index.html)
- [Testing](index.html)

<!-- </nav> -->

# Testing

Akka provides four kinds of testing.

- **Unit testing** verifies that a single component returns the expected effect or state when called in isolation.
- **Integration testing** verifies that multiple components work together against a real in-process runtime, with external dependencies stubbed at the boundary.
- **Evaluation** runs your service against a fixed set of eval cases and measures how often each reply matches its expected answer or passes an evaluator’s grading rubric.
- **Red teaming** sends adversarial prompts through your service and measures how often the service breaks a stated rule under a set of attack styles.
Start with [Unit testing](unit.html) to test one component at a time, [Integration testing](integration.html) to test the wiring, [Evaluation](evaluation/index.html) to check answer quality, and [Red teaming](red-teaming/index.html) to check rule enforcement under attack.

<!-- <footer> -->
<!-- <nav> -->
[Design considerations](../sdk/dev-best-practices.html) [Unit](unit.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->