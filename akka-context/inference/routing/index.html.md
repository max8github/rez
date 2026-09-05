<!-- <nav> -->
- [Akka](../../index.html)
- [Inference](../index.html)
- [Routing requests to models](index.html)

<!-- </nav> -->

# Routing requests to models

Feature set: Inference Contact [our support](https://akka.io/contact) for access.
*This functionality evolves quickly, the behavior and APIs might change between releases without further notice.* Routing decides which model provider serves a request. Fixed URLs and configurable settings are enough for samples and demos. Production needs performance guarantees, and a choice of provider made from declared rules or from the meaning of the request.

Akka’s AI Gateway Router is an add-on service that makes those decisions. It is not a proxy. A proxy consults it, and it answers with a destination.

## <a href="about:blank#_how_a_request_reaches_a_decision"></a> How a request reaches a decision

![Traffic flows from agents through a proxy](../_images/routing-pipeline.svg)


Agents send their model requests to a proxy, such as [Agentgateway](https://agentgateway.dev/). The proxy calls the gateway router over gRPC. The router runs the exchange through a pipeline of two stages and returns a destination, and the proxy forwards the request to the model provider named in that decision.

An agent does not address the router and does not name a model. Putting the proxy in the request path is what applies routing to every request, so the set of providers behind it can change without a change to agent code.

The router decides about agentic traffic, such as LLM and MCP calls. It is not a general purpose proxy.

## <a href="about:blank#_the_two_stages"></a> The two stages

[Routing on request attributes](request-attributes.html) runs first. It matches declared rules against named attributes of the request, such as the protocol, the model asked for, or a header.

[Routing on request meaning](request-meaning.html) runs second, and only for requests the first stage did not claim. It matches the request against use cases discovered from live traffic, using what the request means rather than a declared rule.

Choose the first stage when the condition can be written down: a tenant header, a protocol, a model name. Choose the second when it cannot, because the thing that decides is what the user is asking for.

## <a href="about:blank#_destinations"></a> Destinations

A destination is a backend registered with the gateway proxy. Providers behind one gateway carry different characteristics: a general-purpose model offers broad capability at a higher cost per token, and a smaller model returns faster answers to simpler requests. A model you run yourself, described in [Serving models](../serving/index.html), is one destination a route can name.



|  | The features described in this section are an add-on to Akka Automated Operations. They are not included in the base product. |

## <a href="about:blank#_see_also"></a> See also

- [Serving models](../serving/index.html)
- [Routing commands](../../reference/inference-cli/routing.html)
- [AI model provider configuration](../../sdk/model-provider-details.html)

<!-- <footer> -->
<!-- <nav> -->
[Removing deployments](../serving/removing-deployments.html) [Routing on request attributes](request-attributes.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->