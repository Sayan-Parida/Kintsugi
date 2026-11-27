# Kintsugi v0.1.0-alpha

Experimental Linux / Java 21 CLI for per-JVM Shirudo shield coverage assessment.

- Explains and measures the R24 gap between an installed Java shield and its
  effect in individual JVMs.
- Correlates native operator-domain TPM verification, procfs identity/startup
  observations and nonce-bound cooperative fixture behaviour.
- Complete same-run qualification at engine `847f01c`: A `COVERAGE_GAP` / exit 1;
  B `BEHAVIOR_CORROBORATED` / exit 0. Both opening/closing attestations verified,
  identities stayed stable and reason lists were empty.
- Missing closing evidence failed closed as `UNKNOWN` / exit 3 after 10.085s.
- Full Maven verification: 62 passing tests, no failures, errors or skips.
- Linux installation, standalone/installed CLI and removal passed for default
  and custom prefixes (including spaces); duplicate/changed-launcher/unsafe-prefix
  refusals and preservation of unrelated files also passed.
- Includes the narrowly bounded fix for the unchanged SQL agent's premain stdout
  banner, with strict negative framing regressions.

## Assets and installation

Upload the locally prepared `kintsugi-0.1.0-alpha-linux.tar.gz`,
`kintsugi-0.1.0-alpha.jar`, `RELEASE-NOTES.md` and `SHA256SUMS` together.
Run `sha256sum -c SHA256SUMS`, extract the archive and run `sh install.sh`.
Java 21 must already be installed. Installation is user-local, supports an
absolute custom prefix and refuses an existing installation.
Removal uses the included `uninstall.sh` with the same prefix and preserves
operator profiles, evidence and unrelated files.

## Scope

Validated only in a controlled software-TPM lab with a trusted observer, one fixed
H2 path and explicit startup agent. Hardware-rooted/hostile-guest assurance,
production readiness, generic JVM coverage, systemd propagation, live attach
and retirement/reversal are not established. Java, Shirudo, shields and enrollment
policy are external inputs. No signing keys or raw lab records are distributed.

See `LINUX-VALIDATION.md` and the sanitized qualification results in the source
repository. Checksums detect changed bytes and do not replace trusted publication.

## Manual publication checklist

1. Use the verified release-preparation commit on `main` for tag
   `v0.1.0-alpha`; record the exact commit when publishing.
2. Build locally with Java 21: `sh ./mvnw verify`, then
   `sh distribution/package.sh` and `sh testing/distribution_smoke.sh`.
3. From `target/dist`, verify `sha256sum -c SHA256SUMS` and upload exactly the
   four assets above to a manual GitHub prerelease. Paste these notes.
4. Keep private evidence and operator configuration outside the publication.

No GitHub release or tag is created by the local packaging scripts.
