<!-- <nav> -->
- [Akka](../index.html)
- [Inference](index.html)

<!-- </nav> -->

# Inference

Feature set: Inference Contact [our support](https://akka.io/contact) for access.
*This functionality evolves quickly, the behavior and APIs might change between releases without further notice.* Inference is the execution of a trained AI model on an input to produce an output. Code performs inference when it sends a prompt to a large language model, requests an embedding, or classifies a piece of text.

Inference can be used by applications to deliver results to customers and it can be used by developers to produce code, agentic or otherwise.

## <a href="about:blank#_how_an_inference_call_differs_from_a_function_call"></a> How an inference call differs from a function call

A function call is deterministic. It receives arguments, runs a fixed sequence of instructions, and returns a value of a declared type. The same arguments produce the same value.

An inference call has the following properties:

- *The result varies*. The same input can produce a different output on the next call.
- *The result is untyped at the language level*. A model returns text, and the calling code parses and validates it. See [Structured responses](../sdk/agents/structured.html).
- *Latency can be measured in hundreds of milliseconds to minutes.* Communicating with models is a streaming operation that can take a long time to finish. These long-running streams often present their own infrastructure and development challenges.
- *Providers bill per token*, so the cost of a call depends on the size of the input, the context, and the output.
These properties determine what the surrounding code has to do. It validates the response before using it. It handles a malformed or failed response as a normal path, described in [Failure handling](../sdk/agents/failures.html). It sets a timeout and a token budget for each call, and it defines the behavior when a model is unavailable and failover rules when exceptions occur.

These properties apply to any use of a model, whether the model ranks search results or scores transactions for fraud.

## <a href="about:blank#_inference_in_agentic_systems"></a> Inference in agentic systems

An agent uses a model to decide what to do next. Selecting a tool, judging whether a task is complete, and producing an answer are inference calls. See [AI agents](../concepts/ai-agents.html) for the concept and [Agents](../sdk/agents.html) for the component.

One request to an agentic system typically produces multiple inference calls. An agent that plans, calls tools, and checks its own output makes several calls in sequence. Systems that run agents in parallel or pass work between them increase the count further. See [Multi-agent orchestration](../sdk/agents/orchestrating.html).

The number of calls per request influences four characteristics of the system:

- *Sequential calls add their latencies*, so response time follows the depth of the call chain.
- *Cost scales with the number of calls*. A request that expands into twenty model calls costs approximately twenty times a single call.
- The probability that at least one call in a request fails increases with the number of calls, so *retry and fallback behavior applies to every call*.
- Agents in the same system share provider rate limits and serving capacity. This catches a lot of people by surprise when defining per-app quotas that can run out of provider quota rapidly.
When other inference calls are used to judge the first inferences, this number can scale out exponentially.

## <a href="about:blank#_in_this_section"></a> In this section

- <a href="serving/index.html">**Serving models**</a>: Running a model on hardware in your project and exposing it on a hostname clients can call.
- <a href="routing/index.html">**Routing requests to models**</a>: Deciding which model provider serves a request.


|  | The features described in this section are an add-on to Akka Automated Operations. They are not included in the base product. |

## <a href="about:blank#_see_also"></a> See also

- [Model descriptor](../reference/descriptors/model-descriptor.html)
- [Inference CLI commands](../reference/inference-cli/index.html)
- [AI model provider configuration](../sdk/model-provider-details.html)
- [AI & models](../sdk/integrations/ai-and-models.html)
- [LLM evaluation](../sdk/agents/llm_eval.html)

<!-- <footer> -->
<!-- <nav> -->
[Production readiness checklist](../operations/production-readiness/byok8s/checklist.html) [Serving models](serving/index.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->