<!-- <nav> -->
- [Akka](../../index.html)
- [Testing](../index.html)
- [Evaluation](index.html)
- [Eval cases and rules](eval-cases-and-rules.html)

<!-- </nav> -->

# Eval cases and rules

Feature set: Evaluations Contact [our support](https://akka.io/contact) for access.
*This functionality evolves quickly, the behavior and APIs might change between releases without further notice.* An eval case is one test case the target reads and an evaluator settles. `EvalCaseResults` is one eval case together with every attempt against it.

Running an eval case three times produces three attempts and one `EvalCaseResults`.
The report reads results case by case.
A case that passes twice and fails once is one varied verdict rather than a mixture of passes and failures.

## <a href="about:blank#_the_evalcase_record"></a> The EvalCase record

`EvalCase` is a Java record. Build the list in the experiment class.
evalkit ships no reader for an on-disk format, so where a project keeps its cases is its own choice.

```java
new EvalCase(
    "refund-timing",                           // (1)
    Optional.of("GenUC-16a"),                  // (2)
    Optional.empty(),                          // (3)
    List.of("30 days"),                        // (4)
    new EvalSetup.None(),                      // (5)
    "when do I get my refund?",                // (6)
    "the reply states a 30-day refund window"); // (7)
```

| **1** | The stable identifier the report references. |
| **2** | The specification node the answer should come from, when the target tracks one.
Naming it lets the case be settled by comparison instead of by a model, avoiding a paid model call for a case with one right answer. |
| **3** | A `MetricRef` when this case should be settled by a named metric. |
| **4** | Wording the reply must contain.
Most cases require none, and a shorter constructor omits this. |
| **5** | What has to happen before the graded turn.
See [Eval setups](about:blank#_eval_setups). |
| **6** | The turn that gets scored. |
| **7** | What a correct answer does, in a sentence.
This is what a judge reads the reply against. |
Which evaluator settles an eval case follows from what it declares. `EvaluatorRouter.byExpectation` sends a named spec node to comparison, required phrases to `ContainsAll`, a named metric to that metric, and anything else to the judge.

## <a href="about:blank#_eval_setups"></a> Eval setups

`EvalSetup` is a sealed interface with four cases:

- `None`
The graded turn is the first thing said.
- `Replay(List<String> userTurns)`
The listed turns are said first, and the target answers each.
The conversation is real, so a case about the fourth turn is asked at the fourth turn.
- `Fixture(String name, Map<String, String> parameters)`
The target puts itself into a named state.
The target declares what it can build in `SystemUnderTest.fixtures()`.
An experiment setup naming one it cannot is refused before the experiment runs.
- `FailingTool(String tool, String message, EvalSetup then)`
A named tool is made to fail, then the wrapped eval setup runs.
Used for cases about recovery.
A target that cannot break the named tool causes the experiment to be refused.
Answering with a working tool would report the system as recovering from a failure that
never happened.
Only `Replay` proves the system reached the state under its own power.
A fixture is a seeded starting point, and the report counts it as one.

## <a href="about:blank#_the_four_evaluation_outcomes"></a> The four evaluation outcomes

Every attempt produces an `EvaluationResult` carrying one of four `EvaluationOutcome` values.
Which one an evaluator returns depends on what it read.

- `VERDICT`
The evaluator called the attempt.
Constructed with `EvaluationResult.passed(…​)`, `.failed(…​)`, or `.scored(…​)`.
A deterministic evaluator reports what it compared.
A heuristic evaluator carries a score in `[0, 1]` and the threshold it had to clear.
A judge carries a score and its reason.
- `UNDECIDED`
The evaluator read the answer and would not commit.
Carries the score it declined at.
Constructed with `EvaluationResult.undecided(…​)`.
- `INCONCLUSIVE`
The evaluator had nothing to read.
The pass rate leaves the attempt out rather than counting it either way.
Constructed with `EvaluationResult.inconclusive(…​)`.
- `FAILED`
The evaluator itself broke.
Constructed with `EvaluationResult.broke(…​)`.
An attempt that never reached the graded turn (the fixture would not build, or the system said nothing) carries a `Failure.NOT_REACHED` on the run rather than a result, since there is nothing to evaluate.
That is a fact about the harness, not a verdict on the system.
A report that scored absence as zero would accuse the system of something it was never given the chance to do.

## <a href="about:blank#_from_attempts_to_a_verdict"></a> From attempts to a verdict

`EvalCaseResults` gathers an eval case’s attempts and answers `verdict()` with one of five
values: `PASSED`, `FAILED`, `VARIED`, `UNDECIDED`, `NO_RESULT`.

- `VARIED` is the one worth repeating for.
An eval case the system meets eight times in ten still passes five attempts about a third of
the time.
One attempt cannot tell that apart from a case it always meets.
- `UNDECIDED` and `NO_RESULT` are kept apart on purpose.
A judge that would not commit and an attempt that produced no evidence at all are different facts about an experiment.

## <a href="about:blank#_see_also"></a> See also

- [Evaluators](evaluators.html). The evaluators that produce each outcome.
- [Experiments and runs](experiments.html). How eval cases are bundled into an experiment.
- [Reports](reports.html). How verdicts read on the rendered report.

<!-- <footer> -->
<!-- <nav> -->
[Concepts and vocabulary](concepts.html) [Evaluators](evaluators.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->