<!-- <nav> -->
- [Akka](../../index.html)
- [Inference](../index.html)
- [Routing requests to models](index.html)
- [Routing on request meaning](request-meaning.html)

<!-- </nav> -->

# Routing on request meaning

Feature set: Inference Contact [our support](https://akka.io/contact) for access.
*This functionality evolves quickly, the behavior and APIs might change between releases without further notice.* Semantic routing selects a destination from what a request means. The meaning of a request is derived when it arrives and matched against a list of known use cases, each mapped to a backend.

Meaning is determined by vector arithmetic, not by a model, so it costs no tokens. This is different from a [classifier](request-attributes.html#_deferring_a_decision_to_a_classifier), which does call a model.

This is the second of the two stages described in [Routing requests to models](index.html). It runs only for requests that attribute routing did not claim.

## <a href="about:blank#_where_routing_on_meaning_belongs"></a> Where routing on meaning belongs

Some language SDKs and agent frameworks offer semantic routing in application code or in configuration. Sending all agentic traffic through an AI gateway is the way to apply one set of routing rules to every request and to prove afterwards that it was applied.

## <a href="about:blank#_architecture"></a> Architecture

![Semantic routing architecture with an evidence log](../_images/semantic-routing.svg)


Agents cannot know they are going through a gateway. As with an HTTP proxy, the infrastructure directs traffic to it and application code never makes that decision.

The gateway passes each request to the semantic router. The router examines the content, returns the backend that should serve it, and the gateway forwards the request to that model provider.

The router streams a verifiable record of each decision to an evidence log. Each record covers one routing decision, so the backend chosen for a past request can be checked afterwards: what was decided, when, from which context, for which input, and against which hashed version of the routing rules. The evidence chain is cryptographically unforgeable, which is what lets a decision withstand governance and compliance scrutiny.

Because the router derives its decision from the request rather than from a model name the caller supplied, the set of providers behind the gateway can change without a change to agent code.

## <a href="about:blank#_getting_from_traffic_to_a_routing_policy"></a> Getting from traffic to a routing policy

Discovery runs continuously against live traffic. Turning what it finds into enforced routing is four steps.

### <a href="about:blank#_1_read_the_working_set"></a> 1. Read the working set

The working set is what has been discovered so far, and it keeps changing as traffic arrives.

```shell
akka use-cases working-set
```
To see whether the working set is stable enough to fix, and what is holding it back:

```shell
akka use-cases status
```

```shell
akka use-cases non-admission
```

### <a href="about:blank#_2_label_what_was_discovered"></a> 2. Label what was discovered

The router does not name the clusters it finds. Read the requests nearest the center of a cluster and decide what to call it:

```shell
akka use-cases exemplars list 7
```

```shell
akka use-cases label 7 orders
```
See [Defining use cases](defining-use-cases.html) for what a use case is and how one is discovered.

### <a href="about:blank#_3_freeze_the_working_set_as_a_manifest"></a> 3. Freeze the working set as a manifest

Once every use case above the relevance threshold has a label, fix the set and give each label a backend:

```shell
akka use-cases freeze --as 2026-08 --backend orders=frontier --backend cart=finetune
```
A manifest is immutable, versioned, and provenance-verifiable. To compare one against the working set as it now stands:

```shell
akka use-cases manifests diff 2026-08
```

### <a href="about:blank#_4_deploy_the_manifest"></a> 4. Deploy the manifest

Only one manifest is live at a time:

```shell
akka use-cases manifests deploy 2026-08
```
See [Routing use cases](routing-use-cases.html) for what a manifest holds and how a use case reaches a backend.

## <a href="about:blank#_shifting_traffic_gradually"></a> Shifting traffic gradually

A routing policy states what serves a use case and in what share. To send a copy of the traffic to a second model without using its answers, or to move a fraction of real traffic onto it:

```shell
akka routing-policies route finetune --usecase orders --shadow 10
```

```shell
akka routing-policies route finetune --usecase orders --canary 5
```
Setting a dial to `0` removes it. To return to the previous published policy:

```shell
akka routing-policies rollback orders
```
To refuse any routing change to a use case until the refusal is lifted:

```shell
akka routing-policies veto orders
```


|  | The features described in this section are an add-on to Akka Automated Operations. They are not included in the base product. |

## <a href="about:blank#_see_also"></a> See also

- [Defining use cases](defining-use-cases.html)
- [Routing use cases](routing-use-cases.html)
- <a href="../../reference/inference-cli/routing.html#use-cases">`akka use-cases` command reference</a>
- [Agentgateway (CNCF)](https://agentgateway.dev/)
- [Envoy AI Gateway (CNCF)](https://aigateway.envoyproxy.io/)

<!-- <footer> -->
<!-- <nav> -->
[Routing on request attributes](request-attributes.html) [Defining use cases](defining-use-cases.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->