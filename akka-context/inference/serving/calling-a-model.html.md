<!-- <nav> -->
- [Akka](../../index.html)
- [Inference](../index.html)
- [Serving models](index.html)
- [Calling a deployed model](calling-a-model.html)

<!-- </nav> -->

# Calling a deployed model

Feature set: Inference Contact [our support](https://akka.io/contact) for access.
*This functionality evolves quickly, the behavior and APIs might change between releases without further notice.* A deployed model answers on an OpenAI-compatible endpoint. Existing SDKs, notebooks, and evaluation harnesses work unmodified once the base URL and the API key are set, and this is also the endpoint to give a benchmark.

Set the endpoint from [Exposing models on a hostname](exposing-models.html) and the key from [Issuing API keys](issuing-api-keys.html):

```shell
export AKKA_MODEL_BASE_URL=https://models.acme.example/v1
export AKKA_MODEL_API_KEY=<the key you issued>
```

## <a href="about:blank#_listing_the_models_a_hostname_serves"></a> Listing the models a hostname serves

```shell
curl $AKKA_MODEL_BASE_URL/models -H "Authorization: Bearer $AKKA_MODEL_API_KEY"
```

## <a href="about:blank#_sending_a_request"></a> Sending a request

Send the served name in the `model` field:

```shell
curl $AKKA_MODEL_BASE_URL/chat/completions \
  -H "Authorization: Bearer $AKKA_MODEL_API_KEY" -H 'content-type: application/json' \
  -d '{"model":"docs","messages":[{"role":"user","content":"Hello"}]}'
```
The `model` field is how one hostname serves many models. Send any name the route accepts and the request reaches that model. Nothing else about the request changes.

An OpenAI client needs the same two values:

```python
client = OpenAI(base_url="https://models.acme.example/v1", api_key=...)
client.chat.completions.create(model="docs", ...)
client.chat.completions.create(model="agent", ...)
```
A `404` from an endpoint that is otherwise ready means the name in `model` is not one the route accepts. `akka models routes get NAME` lists the accepted names.

## <a href="about:blank#_calling_tools"></a> Calling tools

With `toolCalling` enabled on the deployment, the `tools` and `tool_choice` fields behave as they do against OpenAI. See [Enabling tool calling](deploying-models.html#_enabling_tool_calling) for the descriptor fields, and note that a parser that does not match the model returns tool calls as ordinary prose rather than failing.

## <a href="about:blank#_calling_a_model_without_a_hostname"></a> Calling a model without a hostname

The CLI can tunnel directly to a deployment. This bypasses the route, which makes it the quickest way to establish whether a model itself is answering:

```shell
akka models proxy docs &
```

```shell
curl localhost:8080/v1/chat/completions -H 'content-type: application/json' \
  -d '{"model":"docs","messages":[{"role":"user","content":"say ok"}],"max_tokens":5}'
```

```json
{"id":"chatcmpl-b5c4083eb2f6252f","object":"chat.completion","model":"docs",
 "choices":[{"index":0,"message":{"role":"assistant","content":"Got it!"},
 "finish_reason":"length"}],
 "usage":{"prompt_tokens":5,"total_tokens":10,"completion_tokens":5}}
```
The tunnel reaches one model, so `model` in the body is echoed back rather than used to choose. Only a route uses that field to select between models.

## <a href="about:blank#_calling_a_served_model_from_an_akka_service"></a> Calling a served model from an Akka service

An Akka agent reaches this endpoint the same way it reaches any OpenAI-compatible provider, by configuring the base URL and the API key. See [AI model provider configuration](../../sdk/model-provider-details.html).



|  | The features described in this section are an add-on to Akka Automated Operations. They are not included in the base product. |

## <a href="about:blank#_see_also"></a> See also

- [Routing requests to models](../routing/index.html)
- [Agents](../../sdk/agents.html)
- <a href="../../reference/inference-cli/models.html#proxy">`akka models proxy` command reference</a>

<!-- <footer> -->
<!-- <nav> -->
[Issuing API keys](issuing-api-keys.html) [Removing deployments](removing-deployments.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->