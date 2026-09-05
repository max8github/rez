<!-- <nav> -->
- [Akka](../../index.html)
- [Testing](../index.html)
- [Red teaming](index.html)
- [Rules and hazards](rules-and-hazards.html)

<!-- </nav> -->

# Rules and hazards

Feature set: Evaluations Contact [our support](https://akka.io/contact) for access.
*This functionality evolves quickly, the behavior and APIs might change between releases without further notice.* A rule under red teaming is the same object as a rule under evaluation.
An evaluation experiment runs the rule under normal input to confirm it holds; a red-team experiment runs the rule under adversarial input to see whether it breaks.
The report groups the outcomes by the hazard the rule covers, so a security reviewer or a CI dashboard can jump straight to the hazards that broke through.

## <a href="about:blank#_declaring_a_rule_with_hazards"></a> Declaring a rule with hazards

```java
Rule.of("do_not_leak_card_numbers")
    .describedAs("The reply never repeats a customer's card number")
    .coveringHazards(Set.of(Hazard.PRIVACY))
    .coveringOwaspRisks(Set.of(OwaspLlmRisk.LLM02_SENSITIVE_INFO_DISCLOSURE));
```
Every rule declares the hazards it covers.
Reports group results by hazard, so a rule with no hazards is present in the failure counts but absent from the hazard rollup.

## <a href="about:blank#_the_ailuminate_taxonomy"></a> The AILuminate taxonomy

Twelve hazard categories.
Full definitions in [Hazard reference](../../reference/evaluations/hazards.html).

Wire keys:

- `ailuminate:violent_crimes`
- `ailuminate:non_violent_crimes`
- `ailuminate:sex_related_crimes`
- `ailuminate:child_sexual_exploitation`
- `ailuminate:suicide_and_self_harm`
- `ailuminate:indiscriminate_weapons_cbrne`
- `ailuminate:defamation`
- `ailuminate:hate`
- `ailuminate:privacy`
- `ailuminate:intellectual_property`
- `ailuminate:specialized_advice`
- `ailuminate:elections`

## <a href="about:blank#_the_owasp_llm_top_10"></a> The OWASP LLM Top 10

Ten AppSec risks paired with hazards for reports that speak the security vocabulary.
Full definitions in [Hazard reference](../../reference/evaluations/hazards.html).

Wire keys use the `owasp:` prefix (`owasp:llm01_prompt_injection` through `owasp:llm10_unbounded_consumption`).

## <a href="about:blank#_crosswalk"></a> Crosswalk

`HazardTaxonomy.owaspRisksFor(Hazard)` returns the OWASP risks that clearly overlap a given hazard.
The crosswalk is conservative: a hazard maps to the risks it obviously touches, not every risk it could reach.
Reports layer taxonomies; they do not merge them.

## <a href="about:blank#_best_practices"></a> Best practices

- Tag every rule with at least one hazard. A tagged rule is legible to auditors.
- Prefer AILuminate as the primary tag; add OWASP only when the AppSec framing matters for the audience.
- Use the wire keys verbatim in dataset files. Reports and views join on them.

<!-- <footer> -->
<!-- <nav> -->
[Getting started](getting-started.html) [Adversarial evaluators](evaluators.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->