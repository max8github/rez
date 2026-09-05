<!-- <nav> -->
- [Akka](../../index.html)
- [Testing](../index.html)
- [Red teaming](index.html)
- [Corpora](corpora.html)

<!-- </nav> -->

# Corpora

Feature set: Evaluations Contact [our support](https://akka.io/contact) for access.
*This functionality evolves quickly, the behavior and APIs might change between releases without further notice.* An adversarial corpus is a set of attacker goals ("get the model to describe how to bypass 2FA", "get the model to write a phishing email") that an experiment runs its techniques against.
Corpora ship as a separate date-versioned Maven artifact so a run recorded in April stays interpretable after the September import:

io.akka : evalkit-redteam-corpora : YYYY-MM.N The code side of `evalkit-redteam` reads whatever corpus artifact the customer pulled.
A corpus refresh is a new artifact version; the code stays the same.

## <a href="about:blank#_pull"></a> Pull

akka redteam corpora pull
akka redteam corpora pull --date 2026-08
akka redteam corpora pull --include garak,pyrit
akka redteam corpora pull --exclude wmdp The command is idempotent.
Pulling the same artifact twice is a no-op.
Pulling a newer artifact preserves baselines under `src/test/resources/redteam/baselines/`.

## <a href="about:blank#_what_ships"></a> What ships

Names change as corpora enter and leave.
Run `akka redteam corpora list` for the authoritative list against the artifact you have pulled.

| Corpus | License | Notes | Bundled |
| --- | --- | --- | --- |
| Garak probes | Apache-2.0 | Static payload catalogs. Largest single source. | Yes |
| PyRIT datasets | MIT | Harmful behavior lists and seed prompts. | Yes |
| HarmBench | MIT | 400 curated harmful behaviors and adversarial suffixes. | Yes |
| AdvBench (Zou et al.) | MIT | 520 harmful-string benchmark. | Yes |
| JailbreakBench | MIT | Jailbreak artifacts and judge prompts. | Yes |
| Do-Not-Answer | Apache-2.0 | 939 prompts across five risk areas. | Yes |
| CyberSecEval | MIT | Insecure-code and cyberattack-helpfulness prompts. | Yes |
| BBQ | CC-BY-4.0 | Bias benchmark. Attribution required. | Yes |
| AILuminate (public split) | CC-BY-4.0 | Aligns hazard tags with LlamaGuard. | Yes |
| WMDP | MIT | Weapons-of-mass-destruction proxy questions. | Gated. Pass `--enable-wmdp` at pull time. |
| ToxicChat | CC-BY-NC-4.0 | Non-commercial license. Referenced only. | No |
| BeaverTails | CC-BY-NC-4.0 | Non-commercial license. Referenced only. | No |
| SafetyBench | CC-BY-NC-SA-4.0 | Non-commercial license. Referenced only. | No |

## <a href="about:blank#_inspecting_a_corpus"></a> Inspecting a corpus

The pulled artifact ships with every corpus laid out under `src/test/resources/redteam/corpora/<corpus-name>/` with three files:

- `manifest.json`: name, description, license, source URL, hazards covered, item count, gated flag.
- `items.jsonl`: one adversarial prompt per line.
- `SOURCE.md`: human-readable summary of what the corpus contains and how it was compiled.
Read the summary and manifest at a glance:

akka redteam corpora describe <corpus-name> Prints the manifest fields, the SOURCE.md contents, and the hazards the corpus covers.

Browse individual prompts:

akka redteam corpora items <corpus-name>
akka redteam corpora items <corpus-name> --hazard ailuminate:privacy
akka redteam corpora items <corpus-name> --limit 20 Each printed item shows the id, the hazards it covers, the rule it targets (when the corpus commits to one), and the prompt itself.

Search across every installed corpus:

akka redteam corpora search "credit card"
akka redteam corpora search "credit card" --hazard ailuminate:privacy Prints matching items across every corpus with their source and id.

## <a href="about:blank#_license_attribution"></a> License attribution

akka redteam corpora licenses Prints the aggregated NOTICE across every installed corpus.
Include it in the compliance review pack.

## <a href="about:blank#_provenance_on_every_eval_case"></a> Provenance on every eval case

Every eval case imported from a corpus carries provenance metadata:

- `sourceCorpus`: the corpus name.
- `sourceId`: the id within that corpus.
- `license`: the SPDX identifier.
- `importedAt`: the import timestamp.
Reports show a failing attack’s source corpus and id in Panel 2.

## <a href="about:blank#_the_gated_wmdp_corpus"></a> The gated WMDP corpus

The Weapons of Mass Destruction Proxy corpus covers hazards some deployments will not want in their test suite.
It ships in the artifact but is skipped by default.

akka redteam corpora pull --enable-wmdp Pass `--enable-wmdp` explicitly to include it.

## <a href="about:blank#_best_practices"></a> Best practices

- Refresh corpora quarterly at minimum. Corpora rot as models and mitigations evolve.
- Commit `akka redteam corpora licenses` output alongside your build’s NOTICE file.
- Do not commit the pulled corpora files themselves to source control. The pulled artifact is deterministic; the version identifier is the record.
- Review WMDP inclusion with security before enabling.

<!-- <footer> -->
<!-- <nav> -->
[Adaptive attacks](adaptive-attacks.html) [Experiments and runs](experiments.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->