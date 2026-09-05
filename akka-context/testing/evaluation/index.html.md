<!-- <nav> -->
- [Akka](../../index.html)
- [Testing](../index.html)
- [Evaluation](index.html)

<!-- </nav> -->

# Evaluation

Feature set: Evaluations Contact [our support](https://akka.io/contact) for access.
*This functionality evolves quickly, the behavior and APIs might change between releases without further notice.* Evaluation is how a project measures a service against a stated expectation.
A suite runs the service through a fixed set of eval cases and turns each reply into a verdict.
The rendered report lists the failing cases one by one.
The CI job fails the build when the pass rate drops or a case that used to pass now fails.

## <a href="about:blank#_the_parts_of_an_experiment"></a> The parts of an experiment

`EvalCase`
A graded turn, what a correct answer does, an optional specification node or metric, and an
optional eval setup that seeds the conversation first.
Eval cases are Java records. evalkit ships no reader for an on-disk format.

`Evaluator`
One method that turns a recorded run into an outcome.
Three families ship: deterministic (comparison against a stated expectation), heuristic
(computation from the recorded interaction against a threshold), and agentic (a
language-model judge configured with a shipped system prompt).
See [Evaluators](evaluators.html).

`EvalCaseResults`
One eval case together with every attempt made against it.
Repeating an experiment produces several attempts and one `EvalCaseResults`.
The report reads results case by case.
An eval case that passed twice and failed once is one varied verdict.

`Experiment`
The JUnit class that assembles the eval cases, the target and the evaluators into a named
unit and runs them.
Gated on `-Deval=true` so `mvn verify` compiles the class without running it.

`Run`
One execution of an experiment against one target build.

`Report`
The output of a run.
See [Reports](reports.html) for every panel and how the numbers compose.

## <a href="about:blank#_how_evaluation_relates_to_other_components"></a> How evaluation relates to other components

- [Red teaming](../red-teaming/index.html) runs the same target through adversarial prompts and reports each break as an incident.
Red-team evaluators use the same three-family taxonomy the evaluation evaluators use.
- An [Agent](../../sdk/agents.html) is the service being evaluated.
A test adapter, `SystemUnderTest`, puts the Agent in front of the runner.

## <a href="about:blank#_see_also"></a> See also

- [Getting started with evaluation](getting-started.html). From an empty project through the first experiment.
- [Evaluators](evaluators.html). The three families, the built-ins, and how to add your own.
- [Reports](reports.html). Every panel of the rendered report.
- [Red teaming](../red-teaming/index.html). Adversarial suite that reuses the same target adapter.
- [Evaluator reference](../../reference/evaluations/evaluators.html)

<!-- <footer> -->
<!-- <nav> -->
[Integration](../integration.html) [Getting started](getting-started.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->