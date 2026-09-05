<!-- <nav> -->
- [Akka](../../index.html)
- [Testing](../index.html)
- [Red teaming](index.html)
- [Experiments and runs](experiments.html)

<!-- </nav> -->

# Experiments and runs

Feature set: Evaluations Contact [our support](https://akka.io/contact) for access.
*This functionality evolves quickly, the behavior and APIs might change between releases without further notice.* A red-team experiment is a named unit of eval cases, rules, and attack styles that run together.
The runner takes each eval case, applies every attack style to it, submits the resulting prompts to the target, and scores each reply.
Every attempt in the report has one case id, one rule, and one attack style, so a break can be traced back to the exact combination that produced it.

## <a href="about:blank#_declaring_an_experiment"></a> Declaring an experiment

```java
RedTeamCampaign.named("refund-safety")
    .withCases(EvalSource.fromCorpus("harmbench"))
    .withRule("do_not_leak_card_numbers", evaluator(new PIILeakDetector()))
    .withRule("do_not_help_wrongdoing", evaluator(new JailbreakEvaluator()))
    .attacking(List.of(new Base64(), new RolePlay(), new Crescendo())) // (1)
    .forHazards(Set.of(Hazard.PRIVACY, Hazard.NON_VIOLENT_CRIMES)) // (2)
    .repeating(3) // (3)
    .withBudget(Budget.of(200_000, Duration.ofHours(1))); // (4)
```

| **1** | `attacking(List<Technique>)`
The attack styles to apply. Each style attacks every eval case. |
| **2** | `forHazards(Set<Hazard>)`
Restrict to eval cases covering these hazards. |
| **3** | `repeating(int)`
Repeat each attack `N` times to measure variance. |
| **4** | `withBudget(Budget)`
Cap tokens and wall-clock. Overrun ends the attack with `BUDGET_EXHAUSTED`. |

## <a href="about:blank#_running_an_experiment_locally"></a> Running an experiment locally

akka redteam run The command wraps the Maven invocation that runs every experiment under `src/test/java/com/example/redteam/`.
Output streams to stdout.
Reports land under `target/redkit/reports/`.

## <a href="about:blank#_running_an_experiment_remotely"></a> Running an experiment remotely

Large corpora or long adaptive runs can be dispatched to the offline-evals service.
The seam is `RemoteCampaign`, the same seam evaluation uses.
Endpoints under `/api/redteam/experiments` mirror the evaluation endpoints.

akka redteam experiments apply -f my-experiment.yaml
akka redteam experiments get <experiment-id>
akka redteam experiments record <experiment-id>
## <a href="about:blank#_the_rules_technique_matrix"></a> The rules × technique matrix

Panel 3 of the report is a rules × attack-styles matrix showing how many attempts broke through in each cell.
Read the panel to see which attack styles are effective against which rules.

## <a href="about:blank#_best_practices"></a> Best practices

- Set a wall-clock budget on every experiment. Adaptive styles can loop.
- Include at least one adaptive style. Static styles alone miss multi-turn failures.
- Repeat each attack three times. Variance on adaptive styles is expected. The report separates flipping from consistent verdicts.
- Restrict an experiment to a set of hazards when running against a specific concern.

<!-- <footer> -->
<!-- <nav> -->
[Corpora](corpora.html) [Reports](reports.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->