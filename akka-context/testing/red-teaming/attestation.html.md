<!-- <nav> -->
- [Akka](../../index.html)
- [Testing](../index.html)
- [Red teaming](index.html)
- [Signed evidence](attestation.html)

<!-- </nav> -->

# Signed evidence

Feature set: Evaluations Contact [our support](https://akka.io/contact) for access.
*This functionality evolves quickly, the behavior and APIs might change between releases without further notice.* Every red-team run produces a signed evidence bundle under `target/redkit/attestations/<run>.dsse.json`.
The bundle is the audit trail.
A signed DSSE envelope carries the experiment spec, the outcome of every attempt, the provenance of every corpus item, and the attestation the compliance review reads as proof the run happened.
Present it to an auditor as-is. The signature covers the exact bytes.

## <a href="about:blank#_what_it_contains"></a> What it contains

The bundle is a DSSE envelope over an in-toto statement.
The statement records:

- The experiment id and name.
- The dataset id and version.
- The target build identifier and model name.
- The list of attack styles applied.
- The evaluator id and version for every rule.
- The rubric version for every agentic evaluator.
- The final report content hash.

## <a href="about:blank#_verification"></a> Verification

Verify the bundle with any DSSE-aware tooling.
The signing key is the one your build attaches to release artifacts.

## <a href="about:blank#_where_it_comes_from"></a> Where it comes from

The bundle is produced by the `governance-contract` module, the same module that signs deployment artifacts.
Redkit does not roll its own signing.

<!-- <footer> -->
<!-- <nav> -->
[Reports](reports.html) [Operating](../../operations/index.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->