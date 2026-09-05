<!-- <nav> -->
- [Akka](../../index.html)
- [Testing](../index.html)
- [Evaluation](index.html)
- [Evaluators](evaluators.html)

<!-- </nav> -->

# Evaluators

Feature set: Evaluations Contact [our support](https://akka.io/contact) for access.
*This functionality evolves quickly, the behavior and APIs might change between releases without further notice.* An evaluator decides the outcome of one recorded attempt.
Every evaluator implements the same one-method interface.
Reports read the verdict alongside the evaluator’s name so a reviewer knows what decided each case.

## <a href="about:blank#_overview"></a> Overview

Every evaluator implements `akka.eval.contract.evaluation.Evaluator` and returns an `EvaluationResult`.
Each result carries an `EvaluationOutcome`:

- `VERDICT` (a pass or fail with an optional score),
- `UNDECIDED` (the evaluator looked and declined to call it),
- `INCONCLUSIVE` (the evaluator had nothing to read), or
- `FAILED` (the evaluator itself broke).
An evaluator whose evidence runs out returns `EvaluationResult.inconclusive(…​)`, and the report keeps that case out of the pass rate.

Three families answer three different kinds of question:

- **Deterministic** reads recorded values against stated expectations.
- **Heuristic** computes a measurement from the recorded interaction and reads it against a threshold.
- **Agentic** hands the exchange to a language model and reads back a score with the model’s reason.
An eval case is settled by exactly one evaluator, chosen from what the case declares. `EvaluatorRouter.byExpectation` sends a case naming a specification node to comparison,
one naming required phrases to `ContainsAll`, one naming a metric to that metric, and
everything else to the judge.

Choosing the cheapest evaluator that can answer is therefore something the case author
does, by declaring what the case is really about.
A case that names a decision costs nothing to settle.
One that names nothing pays a model.

## <a href="about:blank#_a_deterministic_evaluator"></a> A deterministic evaluator

`ToolPermission` checks that the agent called only tools on an allow list.
The evaluator produces one finding per tool the agent called, then aggregates into a share the report reads.

ToolPermissionSample.java
```java
public double score(List<String> toolsCalled) {
  var evaluator = ToolPermission.allowing("search_kb", "get_order"); // (1)

  List<Finding> findings = evaluator.judge(toolsCalled); // (2)

  return evaluator.aggregate(findings); // (3)
}
```

| **1** | Build the evaluator with the tools the eval case allows. An empty list on both sides throws at construction.
An evaluator that authorises everything reports a check that examined nothing. |
| **2** | Read the recorded tool calls into per-call findings.
Each finding names one tool and states whether it was authorised. |
| **3** | Aggregate the findings into a share in `[0, 1]`. When plugged into an experiment runner, `ToolPermission.evaluate(EvaluationContext)` composes these steps and returns an `EvaluationResult`. |
Strict mode collapses any non-perfect share to zero, so a single unauthorised call fails the attempt whatever the rest did:

ToolPermissionSample.java
```java
public double scoreStrict(List<String> toolsCalled) {
  var evaluator = ToolPermission.allowing("search_kb", "get_order").strict(); // (1)

  return evaluator.aggregate(evaluator.judge(toolsCalled)); // (2)
}
```

| **1** | A single unauthorised call is a policy breach in its own right.
Strict mode reports the attempt that way: one failed finding collapses the whole attempt to zero, where the default aggregation reports a share. |
| **2** | Same aggregation, but the outcome now scores 0 when any finding failed. |
A deny list can be added alongside the allow list.
A tool named in both is denied.
The two lists are written by different people at different times, and the safe reading of a conflict is the restrictive one:

ToolPermissionSample.java
```java
public double scoreWithDenyList(List<String> toolsCalled) {
  var evaluator = ToolPermission.allowing("search_kb", "get_order").butNot("delete_order"); // (1)

  return evaluator.aggregate(evaluator.judge(toolsCalled));
}
```

| **1** | `butNot(…​)` adds a deny list that outranks the allow list on conflict. |

## <a href="about:blank#_a_heuristic_evaluator"></a> A heuristic evaluator

`LatencyBudget` reads `Interaction.latency()` against a duration the customer states.
The scoring is proportional past the pass line, so a report can distinguish an attempt at the edge of the budget from one well under it.

LatencyBudgetSample.java
```java
public EvaluationResult score(EvalContext evalContext) {
  var evaluator = LatencyBudget.within(Duration.ofSeconds(2)); // (1)

  return evaluator.evaluate(evalContext.asContext()); // (2)
}
```

| **1** | `within(Duration)` is the only builder.
The budget is what the version records.
Changing it changes what a recorded score under the old value meant. |
| **2** | `evaluate(EvaluationContext)` reads the recorded latency and returns a scored `EvaluationResult`, or an inconclusive one when the target reported no latency at all. `EvalContext.asContext()` produces the context an evaluator reads. |
The `EvaluationResult` carries the value and the pass flag.
A wrapper that reads either into a report row looks like this:

LatencyBudgetSample.java
```java
public String describe(EvaluationResult result) {
  return result
    .score()
    .map(value ->
      "score " +
      value + // (1)
      (result.didPass() ? " (within budget)" : " (over budget)")) // (2)
    .orElse("no latency to measure"); // (3)
}
```

| **1** | `EvaluationResult.score()` carries the value when there is one. |
| **2** | `didPass()` is the boolean the report groups by. |
| **3** | A result with no score is inconclusive.
Reading zero as "instant" would put a made-up figure in the report. |

|  | The score halves at the budget rather than stepping.
An attempt one millisecond over the line reads as 0.499. One twice as slow reads as 0.
The gradient is what the tuning conversations depend on. |

## <a href="about:blank#_an_agentic_evaluator"></a> An agentic evaluator

`TurnFaithfulness` asks whether a reply is supported by the passages the target retrieved.
The judgment is a language reading (paraphrase, negation, partial support), so this metric ships as agentic: the customer supplies the model, evalkit supplies the system prompt.

TurnFaithfulnessSample.java
```java
public EvaluationResult score(EvalContext evalContext, ComponentClient componentClient) {
  String prompt = new TurnFaithfulness(null).systemPrompt(); // (1)

  AlignmentMetric.Assessor assessor = question ->
    componentClient // (2)
      .forAgent()
      .inSession(UUID.randomUUID().toString()) // (3)
      .method(AlignmentJudge::assess)
      .invoke(
        new AlignmentJudge.Request(
          prompt,
          question.task() + "\n\n---\n\n" + question.against()
        )
      );

  var evaluator = new TurnFaithfulness(assessor); // (4)

  return evaluator.evaluate(evalContext.asContext()); // (5)
}
```

| **1** | The `Assessor` is a lambda that hands the question to the customer’s Agent.
The `Question` carries the task and what to read it against. |
| **2** | `systemPrompt()` on any built-in agentic metric returns the wording the model needs to be told.
Constructing `new TurnFaithfulness(null)` only to read the prompt is idiomatic here.
The prompt is static. |
| **3** | `.responseConformsTo(Assessment.class)` makes the SDK check the reply against those fields.
A model that returns malformed JSON is retried before the assessor sees it. |
| **4** | Construct the evaluator with the assessor. |
| **5** | The evaluator’s `evaluate(EvaluationContext)` handles the rest.
Check that there is anything to ask about, call the assessor, validate the returned score is a share in `[0, 1]`, produce an `EvaluationResult`. `EvalContext.asContext()` turns the customer’s evidence into the shared context every evaluator reads. |
Tests use a stub assessor in place of the Agent so the evaluator runs without a provider:

TurnFaithfulnessSample.java
```java
public EvaluationResult scoreWithStub(EvalContext evalContext) {
  AlignmentMetric.Assessor stub = question ->
    new AlignmentMetric.Assessment(0.9, "the reply cited the passage verbatim"); // (1)

  var evaluator = new TurnFaithfulness(stub); // (2)

  return evaluator.evaluate(evalContext.asContext());
}
```

| **1** | A test returns an `Assessment` directly.
The framework treats the two callers the same. |
| **2** | Same evaluator type.
The assessor is what differs between test and production. |

## <a href="about:blank#_writing_your_own_evaluator"></a> Writing your own evaluator

Custom evaluators land under `src/test/java/<your-package>/eval/evaluator/{deterministic,heuristic,agentic}/`.
The example below is a deterministic evaluator for an eval case that requires the reply to name the refund window.

RefundWindowEvaluator.java
```java
public final class RefundWindowEvaluator implements Evaluator {

  private static final String ID = "refund_window_named"; // (1)
  private static final String VERSION = "1";

  @Override
  public String id() {
    return ID;
  }

  @Override
  public String version() {
    return VERSION;
  }

  @Override
  public EvaluatorKind kind() {
    return EvaluatorKind.ASSERTION;
  }

  @Override
  public EvaluationResult evaluate(EvaluationContext context) {
    String reply = context.interaction().responseText();

    if (reply == null || reply.isBlank()) { // (2)
      return EvaluationResult.inconclusive(
        ID,
        VERSION,
        EvaluatorKind.ASSERTION,
        "no reply to read"
      );
    }

    boolean stated = reply.matches("(?i).*\\b30[- ]day\\b.*"); // (3)
    return stated
      ? EvaluationResult.passed(
        ID,
        VERSION,
        EvaluatorKind.ASSERTION,
        "30-day refund window stated"
      )
      : EvaluationResult.failed(
        ID,
        VERSION,
        EvaluatorKind.ASSERTION,
        "30-day refund window not stated"
      ); // (4)
  }
}
```

| **1** | The `id` and `version` name the evaluator and its version.
The version is what a recorded score is filed under.
It changes whenever a setting change would silently redefine what "the same score" means. |
| **2** | Return `EvaluationResult.inconclusive(…​)` before reaching for a verdict.
An evaluator that read an empty reply as failed would report a defect the target never had a chance to make. |
| **3** | A deterministic evaluator reads the reply against a stated expectation.
The regex is the expectation. |
| **4** | `EvaluationResult.passed(…​)` and `EvaluationResult.failed(…​)` are what a deterministic evaluator returns.
The explanation lands in the report row. |

## <a href="about:blank#_the_built_in_evaluators_in_one_table"></a> The built-in evaluators, in one table

For the reference tables listing every built-in evaluator and what each reads, see [Evaluator reference](../../reference/evaluations/evaluators.html).

- 3 deterministic: `ToolPermission`, `ToolCorrectness`, `ArgumentCorrectness`.
- 12 heuristic across groups:

  - budget (`LatencyBudget`, `TokenBudget`, `ModelCallBudget`),
  - format (`RegexMatch`, `LengthInRange`, `JsonValidity`),
  - text-overlap (`ExactMatch`, `TokenF1`),
  - retrieval-quality (`ContextRecall`, `ContextPrecision`, `ReciprocalRank`, `NormalizedDiscountedCumulativeGain`).
- 7 agentic: `TaskCompletion`, `PlanQuality`, `PlanAdherence`, `StepEfficiency`, `TurnFaithfulness`, `TurnRelevancy`, `CitationFaithfulness`.

## <a href="about:blank#_testing"></a> Testing

Every evaluator tests the same way: build an `EvalContext` you control, call `.asContext()`, and read the `EvaluationResult` back.
Deterministic and heuristic evaluators score off recorded values and computed measurements, so they run under `mvn verify` with the JVM alone.
Agentic evaluators use a stub `Assessor` returning `Assessment` directly.

Unit tests use the same TestKit setup, described in [Unit testing](../unit.html).

## <a href="about:blank#_see_also"></a> See also

- [Getting started with evaluation](getting-started.html). A worked example from empty project to first experiment.
- [Eval cases and rules](eval-cases-and-rules.html). What an eval case declares, and which evaluator settles it.
- [Experiments and runs](experiments.html). How a set of eval cases reaches a target.
- [Reports](reports.html). How verdicts read in the report.
- [Adversarial evaluators](../red-teaming/evaluators.html). Adversarial evaluators, same three-family model.
- [Evaluator reference](../../reference/evaluations/evaluators.html)

<!-- <footer> -->
<!-- <nav> -->
[Eval cases and rules](eval-cases-and-rules.html) [Experiments and runs](experiments.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->