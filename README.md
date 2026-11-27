# Kintsugi

Read-only, per-JVM coverage assessment for Shirudo Java shields.
**v0.1.0-alpha** is an experimental Java 21 CLI. A complete fresh-guest
qualification passed; production readiness has not been established.

## The R24 coverage problem

Shirudo can authenticate a signed Java shield installed on a host while a JVM
started earlier still runs without that guard. Startup environment changes,
service launch routes and per-process options can also leave JVMs with different
coverage under the same measured host state. This is the upstream **R24**
residual: an installed artifact and its TPM measurement do not prove that every
JVM is executing the intended transformation.

Kintsugi correlates independently verified host evidence with a particular JVM's
OS identity, startup configuration and a fresh, nonce-bound cooperative test.
It reports the evidence dimensions separately. A startup option alone is
insufficient to establish guard behaviour.
[Upstream analysis](docs/UPSTREAM-ANALYSIS.md) ·
[Trust boundaries](docs/THREAT-MODEL.md)

## Validated alpha results

The actual engine at `847f01c` assessed both JVMs in one fresh protected Linux
run using the unchanged upstream `java-sql-inj` shield:

| JVM | Launch | Coverage / exit | Behaviour | Opening / closing attestations |
| --- | --- | --- | --- | --- |
| A | Before shield deployment | COVERAGE_GAP / 1 | BASELINE_OBSERVED | VERIFIED / VERIFIED |
| B | After deployment, explicit sealed agent | BEHAVIOR_CORROBORATED / 0 | GUARD_OBSERVED | VERIFIED / VERIFIED |

Both identities remained stable, healthy controls passed and both reason lists
were empty. The missing-closing test returned `UNKNOWN` / exit 3 after 10.085
seconds. **Full Maven verification passed 62 tests**, with no failures, errors
or skips. The framing fix accepts one exact upstream premain banner only for
the independently inspected SQL startup agent; other protocol checks remain strict.

[Sanitized results and artifact digests](docs/validation/sql-shield-847f01c.json) ·
[Procedure and limitations](LINUX-VALIDATION.md)

## Architecture

| Maven module | Responsibility |
| --- | --- |
| `assurance-core` | Pure Java evidence records, fixture challenge/session binding and coverage reduction; no Spring dependency. |
| `shirudo-cli` | Bounded native Shirudo calls, strict JSON parsing and operator-supplied verification policy. Shirudo performs the cryptography. |
| `linux-jvm` | Read-only procfs inventory, process lifetime/namespace/executable identity and startup-option inspection. |
| `orchestrator` | Spring Boot command-line wiring, profiles, observation windows and reports; no web server or embedded agent. |
| `assurance-fixtures` | Cooperative H2 test JVM and protocol tests. The signed upstream shield is an external input. |

Operator policy and native verification establish host-state evidence. Procfs
and private fixture pipes establish observer-scoped process and behaviour evidence.
The core combines these into a per-process conclusion. The administrator deploys
the shield separately; Kintsugi does not sign shields, enroll trust or remediate JVMs.

[Architecture details](docs/ARCHITECTURE.md) ·
[Coverage semantics](assurance-core/COVERAGE.md) ·
[Native adapter](shirudo-cli/README.md)

## Install on Linux

Requirements: Java **21**, a POSIX shell and GNU/Linux utilities used by the
scripts: tar, sha256sum, sed, head, cp, cmp, mkdir, mktemp, mv, rm, rmdir, ln,
stat, id and chmod. Select Java with `JAVA_HOME` or PATH.

Download these manual release assets together:

- `kintsugi-0.1.0-alpha-linux.tar.gz`: launcher, installer, uninstaller, JAR and notices/licenses.
- `kintsugi-0.1.0-alpha.jar`: standalone executable JAR.
- `RELEASE-NOTES.md`.
- `SHA256SUMS`.

Obtain the checksums from the trusted release publication. They detect byte
changes; they are not signatures or an independent source of publisher identity.

```sh
sha256sum -c SHA256SUMS
tar -xzf kintsugi-0.1.0-alpha-linux.tar.gz
cd kintsugi-0.1.0-alpha
export JAVA_HOME=/absolute/path/to/jdk-21
sh install.sh
export PATH="$HOME/.local/bin:$PATH"
kintsugi --help
kintsugi inventory --json
```

The default prefix is `$HOME/.local`; no root access is required.
For another user-owned absolute prefix:

```sh
sh install.sh --prefix /absolute/user-owned/prefix
/absolute/user-owned/prefix/bin/kintsugi --help
```

The installer refuses existing installations, symlink installation directories
and directories writable by other users. Java and Shirudo are installed separately.
The installer does not deploy the shield, configure a verifier or create profiles.

## Developer and operator usage

Use Java 21 and the Maven Wrapper. The local distribution smoke test also needs
`jq` and GNU `find`. No GitHub Actions run is needed:

```sh
export JAVA_HOME=/absolute/path/to/jdk-21
sh ./mvnw --batch-mode --no-transfer-progress verify
jar=orchestrator/target/orchestrator-0.1.0-alpha.jar
java -jar "$jar" --help
java -jar "$jar" inventory --json
sh distribution/package.sh
sh testing/distribution_smoke.sh
```

Release assets appear in `target/dist`. See [release notes](RELEASE-NOTES.md)
for manual publication. On Windows, `mvnw.cmd verify` builds the Java project;
Linux procfs is required for inventory and process assessment.

Run native verification in the trusted operator domain using an operator-owned
profile with absolute verifier/evidence paths, a fresh nonce, a UTC observation
window and independently enrolled AK/PCR/config/chain expectations:

```sh
kintsugi verify-host --profile=/secure/operator/run.json --json
kintsugi assess --pid="$pid" --shield="$name" --digest="$payload_sha256" --json
```

The passive assessment command can report incomplete evidence. Qualified fixture
assessment additionally needs opening/closing profiles, the authorized workload's
procfs and dedicated inherited pipes. Use the existing
[protected qualification supervisor](LINUX-VALIDATION.md) and
[fixture guide](assurance-fixtures/LINUX.md). `--fixture-pipes` is for that
explicitly authorized cooperative test process, not a probe of an arbitrary JVM.

Profiles carry `policy`, `nonce`, `evidence`, `window` and, for coverage,
an approved shield/boot mapping. The operator owns nonce uniqueness. Opening and
closing evidence must satisfy the same approved policy and TPM epoch; missing,
expired, contradictory or unverifiable evidence cannot yield positive coverage.
Keep profiles immutable during verification and privately retain raw evidence.

Exit codes are command-specific: 0 for a completed/verified result, 1 for failed
verification or a qualified coverage gap/residual effect, 2 for invalid input,
and 3 for unknown/incomplete evidence. Successful inventory is not a coverage verdict.

## Remove

Run the uninstaller for the installation's prefix:

```sh
sh "$HOME/.local/lib/kintsugi/uninstall.sh"
# Custom prefix:
sh /absolute/user-owned/prefix/lib/kintsugi/uninstall.sh --prefix /absolute/user-owned/prefix
```

Removal deletes only named installation files. Operator profiles, evidence and
unrelated files are retained. A changed launcher or unrecognized installation is
refused. Additional files inside the application directory are preserved and
reported; inspect them manually before removing that directory. Remove an existing
installation before installing a replacement.

The standalone asset needs no installation:
`java -jar kintsugi-0.1.0-alpha.jar --help`.

## Security and qualification limits

The validated case covers one fixed H2 Statement path, explicit startup-agent
loading and one bounded interval in cooperative JVMs. The lab used a host-controlled
software TPM and explicitly trusted guest observer, with native verification in
the operator domain. It establishes no hardware-rooted or hostile-guest assurance,
generic JVM protection or production readiness. The direct-boot CI kernel is a
lab kernel; compatible protected validation also needs real PID namespace identity.

Systemd environment propagation, general live attach and retirement/in-place
reversal remain separate, unqualified cases. Kintsugi does not attest Java
bytecode. Do not refresh trusted measurements from target output or relax failed
checks to obtain a positive report.

Required validation tooling is retained under `testing/`. Raw lab records,
keys and environment-specific enrollment policy are excluded from release assets.

Kintsugi is [BSD-2-Clause](LICENSE).
[Third-party notices](THIRD-PARTY-NOTICES.md) · [Roadmap](docs/ROADMAP.md)
