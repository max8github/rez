<!-- <nav> -->
- [Akka](../../index.html)
- [Inference](../index.html)
- [Serving models](index.html)
- [Allocating hardware to models](allocating-hardware.html)

<!-- </nav> -->

# Allocating hardware to models

Feature set: Inference Contact [our support](https://akka.io/contact) for access.
*This functionality evolves quickly, the behavior and APIs might change between releases without further notice.* An accelerator claims a slice of the device inventory described in [Reviewing available hardware](reviewing-hardware.html). A model deployment is placed on an accelerator, never on a device, so an accelerator has to exist before a model can run.

An accelerator states which device it claims and whether one model gets the card to itself.

## <a href="about:blank#_reviewing_the_accelerators_you_have"></a> Reviewing the accelerators you have

```shell
akka models accelerators list
```

```shell
NAME       DEVICE   TENANCY     CARDS/REPLICA   MEMORY/MODEL   IN USE   CAN PLACE   READY
standard   l4       Dedicated   1               23034Mi        0        1           True
```
`CAN PLACE` is the column that answers whether another model will fit. This accelerator holds the region’s one card and has not been deployed to yet, so it has a slot free. `READY` only says the accelerator itself is healthy.

To read the same information for one accelerator as a single line:

```shell
akka models accelerators get standard
```

|  | Creating an accelerator does not check that the hardware behind it is free. An accelerator created against a fully allocated card reports `READY True` and `CAN PLACE 0`: it looks healthy and can never place a model. Check `CAN PLACE` before deploying against a new accelerator. |

## <a href="about:blank#_choosing_a_tenancy"></a> Choosing a tenancy

Tenancy decides whether a model gets whole cards or shares one.

| Tenancy | Behavior |
| --- | --- |
| `dedicated` | Each model gets whole devices. This is the default. |
| `shared` | Several models split the memory of one device. |
Dedicated tenancy is the choice for a model that has to hold its latency under load, because nothing else competes for the card’s memory. Set `devicesPerReplica` above 1 to give one replica several cards. That requires a node holding that many cards, and the deployment’s parallelism factors have to multiply to exactly that number.

Shared tenancy fits several small models onto one card. Every model on a shared accelerator has to state `engine.memoryPercent`, because a model that does not declare its share is sized against the whole card and runs out of memory once the weights finish downloading. A shared accelerator splits a single device, so it cannot set `devicesPerReplica` above 1.

## <a href="about:blank#_creating_an_accelerator"></a> Creating an accelerator

Declare the accelerator in the model descriptor when you want it version controlled alongside the models that use it:

```yaml
resource: Accelerator
resourceVersion: v1
metadata:
  name: l4-dedicated
spec:
  device: l4
  tenancy: dedicated
```
Applying the descriptor creates it. See [Accelerator](../../reference/descriptors/model-descriptor.html#accelerator) for every field.

To create one directly instead:

```shell
akka models accelerators create fast --device l4
```
To split one card between two models:

```shell
akka models accelerators create small --device l4 --tenancy Shared --max-models 2
```
An accelerator is immutable once it exists. Re-applying a descriptor leaves an existing accelerator alone rather than reshaping it, so changing a tenancy means deleting the accelerator and creating it again.

## <a href="about:blank#_releasing_capacity"></a> Releasing capacity

```shell
akka models accelerators delete fast
```
Deleting an accelerator releases the capacity it held. Delete the deployments placed on it first, otherwise they have nowhere to run.



|  | The features described in this section are an add-on to Akka Automated Operations. They are not included in the base product. |

## <a href="about:blank#_see_also"></a> See also

- [Deploying models](deploying-models.html)
- [Accelerator descriptor reference](../../reference/descriptors/model-descriptor.html#accelerator)
- <a href="../../reference/inference-cli/models.html#accelerators">`akka models accelerators` command reference</a>

<!-- <footer> -->
<!-- <nav> -->
[Reviewing available hardware](reviewing-hardware.html) [Deploying models](deploying-models.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->