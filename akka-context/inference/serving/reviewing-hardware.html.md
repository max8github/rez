<!-- <nav> -->
- [Akka](../../index.html)
- [Inference](../index.html)
- [Serving models](index.html)
- [Reviewing available hardware](reviewing-hardware.html)

<!-- </nav> -->

# Reviewing available hardware

Feature set: Inference Contact [our support](https://akka.io/contact) for access.
*This functionality evolves quickly, the behavior and APIs might change between releases without further notice.* A device is a kind of GPU the platform has in a region. Devices are inventory rather than something you create, so the first question before deploying a model is which devices exist and how many cards of each are unused.

## <a href="about:blank#_listing_the_devices_in_a_region"></a> Listing the devices in a region

```shell
akka models devices list
```
The output names each kind of card, its memory, and how much of it is free:

```shell
NAME   PRODUCT     MEMORY    ARCHITECTURE   FP8   TOTAL   FREE   MAX/NODE
l4     NVIDIA L4   23034Mi   Ada Lovelace   yes   1       1      1
```

| Column | Value |
| --- | --- |
| `NAME` | The device name. This is the value a descriptor gives as `spec.device`. |
| `PRODUCT` | The card the name refers to. |
| `MEMORY` | Memory on one card. A model has to fit inside this, at the context length you configure. |
| `ARCHITECTURE` | The GPU architecture, which determines the numeric formats the card supports. |
| `FP8` | Whether the card supports 8-bit floating point. `fp8` quantization requires it. |
| `TOTAL` | Cards of this kind in the region. |
| `FREE` | Cards not currently claimed by an accelerator. |
| `MAX/NODE` | The most cards of this kind on any single node. A deployment that splits one model across several cards has to fit inside one node, so this is the ceiling on `devicesPerReplica`. |

## <a href="about:blank#_inspecting_one_kind_of_device"></a> Inspecting one kind of device

To see the individual cards behind a device name and what is running on each:

```shell
akka models devices get l4
```
Use this when a card appears free in the list but a deployment will not place on it, because it shows the workload holding each card.

## <a href="about:blank#_from_devices_to_deployable_capacity"></a> From devices to deployable capacity

A deployment is not placed on a device directly. It is placed on an accelerator, which is a claim on a slice of this inventory. See [Allocating hardware to models](allocating-hardware.html).



|  | The features described in this section are an add-on to Akka Automated Operations. They are not included in the base product. |

## <a href="about:blank#_see_also"></a> See also

- [Allocating hardware to models](allocating-hardware.html)
- <a href="../../reference/inference-cli/models.html#devices">`akka models devices` command reference</a>

<!-- <footer> -->
<!-- <nav> -->
[Serving models](index.html) [Allocating hardware to models](allocating-hardware.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->