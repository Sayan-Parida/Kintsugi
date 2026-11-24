# Kintsugi

Version 0.1.0-alpha is an experimental CLI distribution. Protected Linux end-to-end
validation remains pending. Production readiness has not been established.

## Install and remove

Use Linux with Java 21 and a POSIX shell. The bundle scripts use standard Linux
utilities including tar, sha256sum, sed, head, cp, cmp, mkdir, mktemp, mv and rm.
Shirudo host verification additionally requires the trusted native verifier and
its existing permissions and enrollment inputs. The distribution includes the
application dependencies in its executable JAR. Java and Shirudo are installed separately.

The GitHub Actions **Alpha distribution** workflow builds versioned artifacts
when run manually or for tag `v0.1.0-alpha`. Download its artifact archive from
the successful workflow run and extract it. The workflow uploads build artifacts
and SHA-256 checksums. A checksum detects changed bytes and should be obtained
from the trusted workflow run along with the artifacts.

```sh
sha256sum -c SHA256SUMS
tar -xzf kintsugi-0.1.0-alpha-linux.tar.gz
cd kintsugi-0.1.0-alpha
sh install.sh
export PATH="$HOME/.local/bin:$PATH"
kintsugi --help
kintsugi inventory --json
```

Installation defaults to `$HOME/.local/bin/kintsugi` and
`$HOME/.local/lib/kintsugi` and requires no root access. Select Java using
`JAVA_HOME` or PATH. Both launcher and installer check for Java 21.
An existing installation is preserved and must be removed before replacement.
For a custom absolute prefix use `sh install.sh --prefix /absolute/prefix`
and add that prefix's bin directory to PATH.

```sh
kintsugi verify-host --profile=/secure/operator/run.json
kintsugi assess --pid="$pid" --shield="$name" --digest="$payload_sha256" --json
sh "$HOME/.local/lib/kintsugi/uninstall.sh"
# Custom prefix: sh /absolute/prefix/lib/kintsugi/uninstall.sh --prefix /absolute/prefix
```

Removal deletes only the named installed files and preserves operator profiles
and evidence. Profiles are supplied explicitly with `--profile` and
`--closing-profile` as documented below. Store them in an operator-controlled
directory. The installer does not create profiles or enroll trust.

The standalone JAR also runs with `java -jar kintsugi-0.1.0-alpha.jar --help`.
For a local distribution build run Maven verification, then
`sh distribution/package.sh` on Linux. Artifacts appear in `target/dist`.

Kintsugi is a Java 21 project for assessing Shirudo Java shield coverage in individual JVMs. Its Linux CLI inventories visible Java launchers, invokes Shirudo's native attestation verifier, and reports per-JVM evidence.

```sh
sh ./mvnw verify
jar=orchestrator/target/orchestrator-0.1.0-alpha.jar
java -jar "$jar" --help
java -jar "$jar" inventory --json
java -jar "$jar" verify-host --profile=/secure/operator/run.json
java -jar "$jar" assess --pid="$pid" --shield="$name" --digest="$payload_sha256" --json
```

On Windows, use `mvnw.cmd verify` for the build. Run inventory on Linux.

Use `verify-host` in the trusted operator environment. Supply a protected JSON profile with
absolute `verifier` and `evidence` paths, the fresh verifier-issued hex `nonce` (16–64 bytes),
and `window` with UTC `issued` and `expires` instants at most 60 seconds apart. The
`policy` object requires `akName`, `expectedChain`, `bindPcr9`, `bindPcr11`, `configRoot`,
numeric `configPcr` and `bootPcrs` (a PCR-number-to-digest object). Use independently
enrolled values, a trusted verifier binary, and immutable evidence. The operator owns
nonce uniqueness. An expired profile requires a fresh challenge.

For `assess`, pass the PID from inventory and the intended shield name and signed payload
digest. Without a profile the command still reads JVM startup evidence. A trusted operator
with access to the workload's procfs can add `--profile=FILE` with an approved `mapping`
object containing `bootId`, `state` (`VERIFIED_PRESENT` or `VERIFIED_RETIRED`), and
`shield` with `name` and `payloadDigest`. Keep independent verification on the trusted
side of the deployment boundary.

For a supervised H2 test JVM, `--fixture-pipes` explicitly authorizes one fixed read-only
check. The supervisor must pass the fixture's fresh stdout pipe as CLI stdin and its stdin
pipe as CLI FD 3. Keep those pipes private. The session binds the reply to the native
verification, payload and JVM lifetime. Reports show host, startup and behavioural
observations separately. `probeAssessment` is the coverage engine's conclusion for that
invocation and its supplied evidence. Add `--closing-profile=/secure/operator/closing.json`
to verify the host interval. After the assessment, stderr emits `assessment_finished`
with its UTC time. The operator must issue a different challenge after this point,
collect fresh evidence and publish the closing profile within ten seconds. Use the same
profile format and independent enrollment. Both profiles must remain within their
freshness windows through closing verification. Keep the files immutable during each
verification and restrict their ownership to the trusted operator.

The command requires matching pinned AK, measurement policy, boot identity and shield
mapping, plus the same TPM reset/restart counts and firmware and a nondecreasing safe
TPM clock extracted from the natively verified quote. A shield change or retirement,
invalid native verdict, missing closing evidence or unavailable epoch keeps coverage
`UNKNOWN`. Startup and behavioural evidence retain their own reasons. Exit 1 also
indicates a qualified coverage gap or residual effect. Raw verifier output, process
arguments and environment are omitted.

Omit `--json` for terminal output. Exit codes are 0 for a completed inventory or verified
host, 1 for native verification failure, 2 for invalid input and 3 for unknown or incomplete
evidence. Inventory is limited to the observer's visible launchers.

The standalone H2 shield fixture and protected-guest execution steps are in
[the Linux harness guide](assurance-fixtures/LINUX.md).

The harness supports independently identifiable, long-lived JVMs, repeated
nonce-bound checks with fixed read-only queries, and process health records.
The assessment core has no Spring dependencies. Spring Boot runs the command
line orchestration. The [Shirudo adapter](shirudo-cli/README.md) collects host reports
and invokes the native verifier. The [coverage engine](assurance-core/COVERAGE.md)
correlates approved host evidence, passive JVM startup clues and qualified
behavioral observations against the intended shield and process lifetime.

The Java 21 Maven build includes portable tests for evidence assessment, process
identity, native CLI failure handling, two concurrent fixture
JVMs and repeated baseline checks. The packaged fixture also passed a baseline
smoke test. Windows directory protection required verification from a temporary
copy whose source hashes matched the project.

Protected Shirudo Linux guest validation remains pending. Qualification requires
the unchanged signed upstream shield, stable process identities, healthy controls,
repeatable guarded and baseline outcomes, and independently verified fresh host
attestations. The current results establish portable harness behavior only.

Kintsugi's original code is licensed under [BSD-2-Clause](LICENSE).
The Maven Wrapper retains its Apache-2.0 license and notices, listed in
[THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).

[Architecture](docs/ARCHITECTURE.md) · [Roadmap](docs/ROADMAP.md)
