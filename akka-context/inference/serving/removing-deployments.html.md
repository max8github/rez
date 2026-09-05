<!-- <nav> -->
- [Akka](../../index.html)
- [Inference](../index.html)
- [Serving models](index.html)
- [Removing deployments](removing-deployments.html)

<!-- </nav> -->

# Removing deployments

Feature set: Inference Contact [our support](https://akka.io/contact) for access.
*This functionality evolves quickly, the behavior and APIs might change between releases without further notice.* Tearing down what a descriptor created releases the hardware it held. Deletion runs in the reverse of apply order, so no hostname is left pointing at a model that has already gone.

## <a href="about:blank#_saving_what_is_running_first"></a> Saving what is running first

Export writes everything running back out as a descriptor, routes included:

```shell
akka models export > snapshot.yaml
```
This is the way to capture a deployment that was built with direct commands rather than from a file. The result is ready to version control, with one exception: strip any token out of it first, for the reason given in [Supplying a token for gated weights](deploying-models.html#_supplying_a_token_for_gated_weights).

Export masks secret values with `NOT EXPORTED`. Applying an exported file unedited fails validation rather than overwriting a live secret with the mask. Replace the value, or delete the secret document and create the secret ahead of time.

## <a href="about:blank#_removing_what_a_descriptor_names"></a> Removing what a descriptor names

```shell
akka models delete -f models.yaml
```
Routes are removed first, then deployments, then the resources they depended on.

Cached weights survive by default, so the next apply does not download them again. To remove the cache as well:

```shell
akka models delete -f models.yaml --include-cache
```

## <a href="about:blank#_removing_a_single_deployment"></a> Removing a single deployment

```shell
akka models delete docs
```
This deletes one deployment by name, without a descriptor. The accelerator it ran on stays, and its capacity becomes available to another model. See [Releasing capacity](allocating-hardware.html#_releasing_capacity) to remove the accelerator too.

|  | API keys survive teardown. Deleting a route does not revoke the keys issued against it, and rebuilding the route under the same name makes every key ever issued to it work again. Remove keys explicitly with `akka models routes keys remove`, described in [Issuing API keys](issuing-api-keys.html). |


|  | The features described in this section are an add-on to Akka Automated Operations. They are not included in the base product. |

## <a href="about:blank#_see_also"></a> See also

- [Issuing API keys](issuing-api-keys.html)
- [Model descriptor](../../reference/descriptors/model-descriptor.html)
- <a href="../../reference/inference-cli/models.html#deployments">`akka models delete` command reference</a>

<!-- <footer> -->
<!-- <nav> -->
[Calling a deployed model](calling-a-model.html) [Routing requests to models](../routing/index.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->