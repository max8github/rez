<!-- <nav> -->
- [Akka](../../index.html)
- [Inference](../index.html)
- [Serving models](index.html)
- [Issuing API keys](issuing-api-keys.html)

<!-- </nav> -->

# Issuing API keys

Feature set: Inference Contact [our support](https://akka.io/contact) for access.
*This functionality evolves quickly, the behavior and APIs might change between releases without further notice.* An inference route refuses every request until a key authorizes it. Keys are issued per client, so any one of them can be revoked without disturbing the others.

## <a href="about:blank#_issuing_a_key"></a> Issuing a key

Name the client the key is for:

```shell
akka models routes keys add models production
```

```shell
API key for "production" on route "models":

  sk-EXAMPLE-KEY-DO-NOT-USE-xxxxxxxxxxxxxxxxxxxx

This is the only time it is shown. Store it somewhere safe.
```
Copy the key now. Keys cannot be read back.

## <a href="about:blank#_listing_the_clients_that_hold_a_key"></a> Listing the clients that hold a key

```shell
akka models routes keys list models
```
This shows which clients hold a key, never the keys themselves.

## <a href="about:blank#_rotating_a_key"></a> Rotating a key

Every key in the list works until it is removed, so a rotation has no cutover. Add the new key, move the clients across, and then remove the old one:

```shell
akka models routes keys add models production-2
```

```shell
akka models routes keys remove models production
```

|  | Deleting a route does not revoke the keys issued against it. Rebuild the route under the same name and every key ever issued to it works again. A key you meant to retire is only gone once `akka models routes keys remove` has removed it. |

## <a href="about:blank#_diagnosing_a_rejected_request"></a> Diagnosing a rejected request

| Message | Cause |
| --- | --- |
| `api key authentication failure: no API Key found` | The request carries no `Authorization` header, or the header is missing the `Bearer` prefix. |
| `api key authentication failure: invalid credentials` | The key is not one this route accepts. `akka models routes keys list` shows which clients hold one. Issue a fresh key if the value was lost. |


|  | The features described in this section are an add-on to Akka Automated Operations. They are not included in the base product. |

## <a href="about:blank#_see_also"></a> See also

- [Calling a deployed model](calling-a-model.html)
- [Exposing models on a hostname](exposing-models.html)
- <a href="../../reference/inference-cli/models.html#keys">`akka models routes keys` command reference</a>

<!-- <footer> -->
<!-- <nav> -->
[Exposing models on a hostname](exposing-models.html) [Calling a deployed model](calling-a-model.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->