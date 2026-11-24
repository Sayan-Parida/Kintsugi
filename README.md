# Kintsugi

Kintsugi is a Java 21 project for assessing Shirudo Java shield coverage in individual JVMs. The initial Linux command inventories visible processes using the `java` launcher and reports coverage as unknown pending evidence. Run it as the workload observer. It reads procfs without attaching to JVMs.

```sh
sh ./mvnw verify
java -jar orchestrator/target/orchestrator-0.1.0-SNAPSHOT.jar inventory
```

On Windows, use `mvnw.cmd verify` for the build. Run inventory on Linux.

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
