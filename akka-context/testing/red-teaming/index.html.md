<!-- <nav> -->
- [Akka](../../index.html)
- [Testing](../index.html)
- [Red teaming](index.html)

<!-- </nav> -->

# Red teaming

Feature set: Evaluations Contact [our support](https://akka.io/contact) for access.
*This functionality evolves quickly, the behavior and APIs might change between releases without further notice.* Red teaming is how a project probes a service for the things it should refuse.
A suite runs the target through prompts written to attack it, drawn from a corpus, wraps or escalates them through attack styles, and asks an evaluator whether the target broke through, held, or the evidence was inconclusive.
The rendered report lists each break as its own incident, grouped by hazard and by attack style, with a signed record the compliance review reads as evidence the run happened.

## <a href="about:blank#_shared_with_evaluation"></a> Shared with evaluation

Red teaming reads the same `SystemUnderTest` adapter an evaluation experiment uses.
A project already running evaluation adds red teaming by adding the `evalkit-redteam` dependency and a new experiment class.
The target adapter carries across unchanged. `Rule`, `EvalCase`, `Evaluator`, `Experiment`, `Run`, `Rubric`, and `Ledger` mean the same thing in both.

## <a href="about:blank#_techniques"></a> Techniques

A technique is how a payload is wrapped, escalated, or delivered before it reaches the target.
Every attack style implements the `Technique` interface and returns one or more prompts derived from an attacker goal.

Two families ship:

- `Static`
A pure function on a prompt.
The Base64 encoder, the role-play wrapper, the payload splitter across turns.
Reproducible across runs.
The same goal produces the same prompt every time.
- `Adaptive`
An attacker with state.
Each next prompt reads the target’s last reply and revises the attack.
Runs inside a durable driver so a mid-attack crash resumes from the same state.
For every technique `evalkit-redteam` ships and the design of the driver, see [Attack styles](techniques.html) and [Adaptive attacks](adaptive-attacks.html).

## <a href="about:blank#_hazard_taxonomies"></a> Hazard taxonomies

Rules and eval cases carry hazard tags from two taxonomies.

**AILuminate v1.1** is the primary. Twelve hazard categories the report groups by. **OWASP LLM Top 10 (2025)** is the secondary. Ten application-security risks a security review already knows.
Wire keys use the taxonomy prefix: `ailuminate:privacy`, `owasp:llm01_prompt_injection`.
For the full lists and how each evaluator maps to them, see [Hazard reference](../../reference/evaluations/hazards.html).

## <a href="about:blank#_how_red_teaming_relates_to_other_components"></a> How red teaming relates to other components

- [Evaluation](../evaluation/index.html) measures the service under normal input.
Red teaming measures it under adversarial input.
Both read the same target adapter.
- [Guardrails](../../sdk/agents/guardrails.html) are the runtime component that refuses at request time.
Red-team evaluators are the test-time component that finds the cases where a guardrail was bypassed.
- An [Agent](../../sdk/agents.html) is the service under test.
The adapter puts the Agent in front of the runner the same way an evaluation experiment does.

## <a href="about:blank#_see_also"></a> See also

- [Getting started with red teaming](getting-started.html). From an empty project through the first red-team experiment.
- [Adversarial evaluators](evaluators.html). The three families, the built-ins, and how to add your own.
- [Reports](reports.html). Every panel of the rendered report and the attestation bundle.
- [Evaluation](../evaluation/index.html). The evaluation suite that reads the same target adapter.

<!-- <footer> -->
<!-- <nav> -->
[Remote experiments](../evaluation/remote-experiments.html) [Getting started](getting-started.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->