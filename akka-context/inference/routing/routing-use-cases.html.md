<!-- <nav> -->
- [Akka](../../index.html)
- [Inference](../index.html)
- [Routing requests to models](index.html)
- [Routing on request meaning](request-meaning.html)
- [Routing use cases](routing-use-cases.html)

<!-- </nav> -->

# Routing use cases

Feature set: Inference Contact [our support](https://akka.io/contact) for access.
*This functionality evolves quickly, the behavior and APIs might change between releases without further notice.* Routing connects a use case to the backend that serves it. After the semantic router matches a request to a use case, as described in [Defining use cases](defining-use-cases.html), the routing rules for that use case decide which model provider receives the request.

## <a href="about:blank#_working_sets_and_manifests"></a> Working sets and manifests

The gateway router observes incoming traffic continuously, produces embeddings, and groups requests into discovered use cases. This set of use cases and their rankings keeps changing as traffic arrives, and it is called the *working set*.

The router reports which use cases are stable and which are still moving. Once every use case above the relevance threshold carries a label, you take a snapshot of the working set and give each use case a backend name.

That snapshot is a *use case manifest*. It is immutable, versioned, and provenance-verifiable. The backend names an operator supplies correspond to the gateway proxy configuration, so the manifest is a route table keyed on the meaning of a request.

Only one manifest enforces routing at a time.

## <a href="about:blank#_inspecting_a_manifest"></a> Inspecting a manifest

To list every manifest and whether it is able to enforce:

```shell
akka use-cases manifests list
```
To read one as it was frozen:

```shell
akka use-cases manifests get 2026-08
```
A manifest is fixed and the working set is not, so the two separate over time. To see how far apart they have moved:

```shell
akka use-cases manifests diff 2026-08
```
A large difference means traffic has changed shape since the manifest was frozen, and that some current traffic is being routed by a table that no longer describes it.

## <a href="about:blank#_replacing_the_live_manifest"></a> Replacing the live manifest

Deploying a manifest makes it the routing policy and displaces the one before it:

```shell
akka use-cases manifests deploy 2026-09
```
To withdraw a manifest from use:

```shell
akka use-cases manifests delete 2026-08
```
[Getting from traffic to a routing policy](request-meaning.html#_getting_from_traffic_to_a_routing_policy) covers the steps that produce a manifest, and [Shifting traffic gradually](request-meaning.html#_shifting_traffic_gradually) covers moving a use case onto a new backend without replacing the manifest.



|  | The features described in this section are an add-on to Akka Automated Operations. They are not included in the base product. |

## <a href="about:blank#_see_also"></a> See also

- [Routing on request meaning](request-meaning.html)
- [Routing on request attributes](request-attributes.html)
- <a href="../../reference/inference-cli/routing.html#working-set-and-manifests">`akka use-cases manifests` command reference</a>

<!-- <footer> -->
<!-- <nav> -->
[Defining use cases](defining-use-cases.html) [Reference](../../reference/index.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->