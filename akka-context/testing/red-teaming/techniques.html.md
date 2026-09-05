<!-- <nav> -->
- [Akka](../../index.html)
- [Testing](../index.html)
- [Red teaming](index.html)
- [Attack styles](techniques.html)

<!-- </nav> -->

# Attack styles

Feature set: Evaluations Contact [our support](https://akka.io/contact) for access.
*This functionality evolves quickly, the behavior and APIs might change between releases without further notice.* An attack style is how a payload is wrapped, escalated, or delivered before it reaches the target.
Every attack style implements `Technique` in the SPI and returns one or more prompts derived from an attacker goal.
Two kinds ship. Static techniques are pure functions the runner can parallelise across the corpus. Adaptive techniques are stateful attackers the runner drives turn-by-turn.

## <a href="about:blank#_two_kinds"></a> Two kinds

- **Static**
A pure function on a prompt. Given a payload, produce a rewritten prompt.
Deterministic, cheap, parallelizable. No language-model call. No per-turn state.
- **Adaptive**
A stateful attacker. Chooses each next prompt based on the target’s last reply.
Runs inside `AdversaryDriver` so a crash mid-attack can resume from the same state.
See [Adaptive attacks](adaptive-attacks.html).

## <a href="about:blank#_built_in_static_styles"></a> Built-in static styles

[Reference table](../../reference/evaluations/techniques.html).

- `base64`: Encodes the payload as base64 and asks the target to decode and follow it.
- `rot13`: Encodes the payload as ROT13 and asks the target to decode.
- `multilingual`: Asks the target to translate the request into another language and answer in that language.
- `roleplay`: Wraps the payload in a role-play frame.
- `payload_splitting`: Splits the payload across named variables the target must join.

## <a href="about:blank#_the_interface"></a> The interface

```java
public sealed interface Technique permits Technique.Static, Technique.Adaptive {
  String name();
  String summary();

  non-sealed interface Static extends Technique {
    String rewrite(String prompt);
  }

  non-sealed interface Adaptive extends Technique {
    String nextPrompt(AttackContext context);
    int maxTurns();
  }
}
```

## <a href="about:blank#_a_custom_static_style"></a> A custom static style

Static techniques live under `src/test/java/com/example/redteam/technique/`.

```java
public final class HexEncoded implements Technique.Static {
  @Override public String name() { return "hex"; }
  @Override public String summary() {
    return "Hex-encodes the payload and asks the target to decode and follow it";
  }
  @Override public String rewrite(String prompt) {
    var hex = HexFormat.of().formatHex(prompt.getBytes(StandardCharsets.UTF_8));
    return "The following text is hex-encoded. Decode and answer:\n\n" + hex;
  }
}
```

## <a href="about:blank#_registering_an_attack_style_with_the_cli"></a> Registering an attack style with the CLI

akka redteam techniques list
akka redteam techniques list --kind static
akka redteam techniques list --kind adaptive
## <a href="about:blank#_best_practices"></a> Best practices

- Give each technique a stable, lowercase, underscore-separated `name`. Reports render it as-is.
- Keep static techniques deterministic. The driver replays them and expects the same output on every run.
- Rewrite the payload, do not rewrite the surrounding metadata. Techniques only touch the prompt.
- Prefer the built-in styles when they cover your case. Add a custom style only when a specific system prompt or tool-call format needs one.

<!-- <footer> -->
<!-- <nav> -->
[Adversarial evaluators](evaluators.html) [Adaptive attacks](adaptive-attacks.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->