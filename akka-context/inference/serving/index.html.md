<!-- <nav> -->
- [Akka](../../index.html)
- [Inference](../index.html)
- [Serving models](index.html)

<!-- </nav> -->

# Serving models

Feature set: Inference Contact [our support](https://akka.io/contact) for access.
*This functionality evolves quickly, the behavior and APIs might change between releases without further notice.* Serving a model means running it on hardware in your project and exposing it on a hostname that OpenAI-compatible clients can call. Akka runs the model, allocates the hardware it needs, and terminates TLS on a hostname you own.

You describe what you want to run in a model descriptor, a YAML file that declares four kinds of resource. Applying that file is what creates them.

| Resource | What it declares |
| --- | --- |
| `Secret` | Credentials the deployment needs, such as the token that downloads gated weights. |
| `Accelerator` | A claim on a slice of the GPU inventory available in your region. |
| `ModelDeployment` | One model, the accelerator it runs on, and how the engine serves it. |
| `InferenceRoute` | A hostname, and the deployments reachable on it. |
Applying a descriptor writes these in dependency order, so nothing refers to a resource that does not exist yet: secrets, then accelerators, then model deployments, then routes. See [Model descriptor](../../reference/descriptors/model-descriptor.html) for every field of every resource.

Each resource also has commands that create and inspect it directly, without a descriptor. Use the descriptor for anything you intend to keep, because a descriptor can be version controlled and re-applied. Use the commands to look at what is running and to make a one-off change.

## <a href="about:blank#_serving_a_model_for_the_first_time"></a> Serving a model for the first time

Work through these pages in order. The result is a model answering requests on a hostname, with a key that authorizes them.

1. [Reviewing available hardware](reviewing-hardware.html) shows the GPUs your region has and how much of each is free.
2. [Allocating hardware to models](allocating-hardware.html) claims a slice of that inventory.
3. [Deploying models](deploying-models.html) declares the model, applies the descriptor, and waits for the model to become ready.
4. [Exposing models on a hostname](exposing-models.html) puts the model behind a URL.
5. [Issuing API keys](issuing-api-keys.html) authorizes clients to use that URL.
6. [Calling a deployed model](calling-a-model.html) sends the first request.
[Removing deployments](removing-deployments.html) covers exporting what is running and tearing it down.

## <a href="about:blank#_serving_and_routing"></a> Serving and routing

Serving decides what runs. [Routing requests to models](../routing/index.html) decides which of several providers answers a given request, and a model you serve yourself is one destination a route can name. The two are independent: a served model is reachable on its own hostname whether or not any route names it.



|  | The features described in this section are an add-on to Akka Automated Operations. They are not included in the base product. |

## <a href="about:blank#_see_also"></a> See also

- [Model descriptor](../../reference/descriptors/model-descriptor.html)
- [Model commands](../../reference/inference-cli/models.html)
- [AI model provider configuration](../../sdk/model-provider-details.html)

<!-- <footer> -->
<!-- <nav> -->
[Inference](../index.html) [Reviewing available hardware](reviewing-hardware.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->