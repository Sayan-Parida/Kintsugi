# Kintsugi

Kintsugi is a Java 21 project for assessing Shirudo Java shield coverage in individual JVMs. Its Linux CLI inventories visible Java launchers, invokes Shirudo's native attestation verifier, and reports per-JVM evidence.

```sh
sh ./mvnw verify
jar=orchestrator/target/orchestrator-0.1.0-SNAPSHOT.jar
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
invocation and its supplied evidence. The CLI currently accepts one host receipt. Even a qualified
observation retains `UNKNOWN` with `closing_host_attestation_missing` until the host
interval can be verified. Raw verifier output, process arguments and environment are omitted.

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
