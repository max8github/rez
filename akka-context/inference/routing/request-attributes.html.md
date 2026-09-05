<!-- <nav> -->
- [Akka](../../index.html)
- [Inference](../index.html)
- [Routing requests to models](index.html)
- [Routing on request attributes](request-attributes.html)

<!-- </nav> -->

# Routing on request attributes

Feature set: Inference Contact [our support](https://akka.io/contact) for access.
*This functionality evolves quickly, the behavior and APIs might change between releases without further notice.* Attribute routing chooses a destination by matching declared rules against named attributes of a request. Each rule states a condition, and the condition names an attribute and applies a predicate to it.

No model is consulted, unless a rule defers its decision to a classifier. This is the first of the two stages described in [Routing requests to models](index.html), and a request it claims never reaches the second.

## <a href="about:blank#_writing_a_rule"></a> Writing a rule

A condition names an attribute and states one predicate against it, written flat in a single object:

```json
{ "attribute": "text.prompt", "contains": "task:commit" }
```
Conditions combine with `all`, `any`, and `not`. A rule states the boolean structure rather than leaving it to be inferred from field order, so what a rule means does not depend on how it was written down:

```json
{
  "id": "commit-message",
  "priority": 100,
  "match": {
    "all": [
      { "attribute": "protocol", "equals": "openai.chat" },
      { "attribute": "text.prompt", "contains": "task:commit" }
    ]
  },
  "outcome": { "toDestination": "finetune" }
}
```
Six predicates are available, and the set is closed. There is no regular expression predicate, because predicates run against prompt text a caller can influence and a regular expression failure would be a way to deny service to the gateway.

String comparisons are case sensitive. A rule testing `subject` against `GPT-4` does not match a request carrying `gpt-4`. Header names are lower-cased when the attribute is populated, so `header.x-tenant` matches and `header.X-Tenant` never does.

See [Predicates](../../reference/descriptors/routing-ruleset.html#predicates) for all six, and [Attributes](../../reference/descriptors/routing-ruleset.html#attributes) for everything a rule can match on.

## <a href="about:blank#_grouping_rules_into_a_ruleset"></a> Grouping rules into a ruleset

A ruleset is a named group of rules and the unit that is enforced. Individual rules are not applied to traffic on their own.

A ruleset declares the closed set of destinations its rules may emit, and the destination for traffic no rule claims:

```json
{
  "defaultDestination": "frontier",
  "outputHeader": "x-optimize-route",
  "reasonHeader": "x-optimize-reason",
  "destinations": [
    { "label": "finetune", "description": "the fine-tuned model" },
    { "label": "frontier", "description": "the frontier model" }
  ],
  "rules": [
    {
      "id": "commit-message",
      "priority": 100,
      "match": {
        "any": [
          { "attribute": "text.prompt", "contains": "task:commit" }
        ]
      },
      "outcome": { "toDestination": "finetune" }
    }
  ]
}
```
Every outcome, including the default, has to name a declared destination. See [Routing ruleset](../../reference/descriptors/routing-ruleset.html) for the full document.

## <a href="about:blank#_applying_a_ruleset"></a> Applying a ruleset

```shell
akka rulesets apply commit-routing -f ruleset.json
```
To write only while the ruleset is still at the revision you read, so the write fails if another author changed it in the meantime:

```shell
akka rulesets apply commit-routing -f ruleset.json --if-revision 3
```
To read what is currently applied:

```shell
akka rulesets get commit-routing
```
To list every ruleset with its revision and priority:

```shell
akka rulesets list
```

## <a href="about:blank#_testing_a_ruleset_before_it_sees_traffic"></a> Testing a ruleset before it sees traffic

To see what a ruleset decides for a sample request, with no traffic involved:

```shell
akka rulesets test commit-routing -f request.json
```
Run this after every change. A rule that matches nothing is not an error, and the ruleset applies successfully either way.

## <a href="about:blank#_changing_one_rule"></a> Changing one rule

To add or replace a single rule without sending the rest of the ruleset:

```shell
akka rulesets rules apply commit-routing commit-message -f rule.json
```
To remove one:

```shell
akka rulesets rules delete commit-routing commit-message
```

## <a href="about:blank#_setting_the_order_rules_are_evaluated_in"></a> Setting the order rules are evaluated in

Rules are evaluated by `priority`, lower first, with `id` breaking ties. The ordering is total, so the order you read is the order that is used.

Rulesets are evaluated in priority order too. That priority is set through the CLI rather than in the document, so reordering rulesets does not read as a change of policy:

```shell
akka rulesets priority commit-routing --set 10
```

```shell
akka rulesets priority commit-routing
```

## <a href="about:blank#_reading_what_changed"></a> Reading what changed

To list every change made to a ruleset, with its revision and who made it:

```shell
akka rulesets history commit-routing
```
Retiring a ruleset does not remove that record:

```shell
akka rulesets delete commit-routing
```

## <a href="about:blank#_deferring_a_decision_to_a_classifier"></a> Deferring a decision to a classifier

A `byClassifier` outcome hands the decision to a model. This is the one case where attribute routing consults an LLM, and it is the way to handle a condition that cannot be written as a predicate.

Register the classifier first. Registration is immutable, so a retrained model is registered under a new id:

```shell
akka routing-classifiers apply commit-router-v4 -f classifier.json
```
Then write a rule that defers to it:

```json
{
  "id": "tenant-commit-routing",
  "priority": 100,
  "match": {
    "all": [
      { "attribute": "protocol", "equals": "openai.chat" },
      { "attribute": "header.x-tenant", "exists": true }
    ]
  },
  "outcome": {
    "byClassifier": {
      "classifier": "commit-router-v4",
      "input": "text.lastUserMessage",
      "destinations": { "slm": "finetune", "big": "frontier" },
      "onFailure": "default",
      "affinity": "conversation"
    }
  }
}
```
The two sides of `destinations` come from different places. `slm` and `big` are answers the classifier itself returns, and they belong to its own vocabulary. `finetune` and `frontier` name backends registered with the gateway proxy, and each has to be declared in the ruleset’s `destinations` list.

`affinity: conversation` lets the first turn of a conversation decide and later turns reuse that decision. `none`, which is also what an omitted field means, decides on every turn.

Retiring a classifier is unconditional. Nothing checks whether a rule still names it, so change any rule that selects it first:

```shell
akka routing-classifiers delete commit-router-v4
```


|  | The features described in this section are an add-on to Akka Automated Operations. They are not included in the base product. |

## <a href="about:blank#_see_also"></a> See also

- [Routing on request meaning](request-meaning.html)
- [Routing ruleset reference](../../reference/descriptors/routing-ruleset.html)
- <a href="../../reference/inference-cli/routing.html#rulesets">`akka rulesets` command reference</a>

<!-- <footer> -->
<!-- <nav> -->
[Routing requests to models](index.html) [Routing on request meaning](request-meaning.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->