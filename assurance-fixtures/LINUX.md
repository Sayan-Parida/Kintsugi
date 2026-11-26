# SQL shield fixture qualification

Run in the protected Shirudo Linux guest described in [the feasibility study](../docs/FEASIBILITY.md). Use the reviewed upstream checkout and the operator key already enrolled in that guest. Record its commit, kernel metadata, JDK vendor/build, runtime version ID, shield payload digest and fixture JAR SHA-256. The harness exercises one concrete H2 Statement path. A result covers the responding process and that invocation.

Build from the Kintsugi root with Java 21.

```sh
sh ./mvnw verify
sha256sum assurance-fixtures/target/assurance-fixtures-0.1.0-alpha.jar
```

Transfer that JAR to a private workload directory in the guest. The shaded JAR includes H2 2.3.232. All queries are scalar SELECTs against an isolated in-memory connection. The second query returns the literal string OR 2=2, which the upstream heuristic is expected to reject even though it is harmless data. The before/after controls must both pass.

On the operator, build the unchanged upstream example and sign its JAR using the existing enrolled operator key. These are the commands established in the [source study](../docs/UPSTREAM-ANALYSIS.md). Substitute absolute paths for the angle-bracket values.

```sh
<upstream-checkout>/upatches/java-sql-inj/build.sh 0.1.0 --keys-dir <operator-keys>
shirudo build <upstream-checkout>/upatches/java-sql-inj/java-sql-inj.jar --name java-sql-inj --keys-dir <operator-keys> -o java-sql-inj.upatch
shirudo upatch info --json java-sql-inj.upatch
shirudo build --unload java-sql-inj --payload-sha256 <load-payload-sha256> --java --keys-dir <operator-keys> -o java-sql-inj.unload.upatch
```

Archive the load artifact, digest and matching directive. Transfer the signed artifacts to the target. Keep the private key on the operator. This guide uses the direct signed-artifact lifecycle to keep the experiment small.

In a guest administrator shell, set the authenticated runtime path and record initial state.

```sh
S=/run/shirudo/runtime/usr/bin/shirudo
"$S" version --version-id
"$S" status --json
"$S" upatch list --json
```

Before deployment, start A as the ordinary workload user in terminal A. Use an absolute Java 21 executable and fixture path. Clear inherited launcher options for repeatability.

```sh
env -u JAVA_TOOL_OPTIONS -u JDK_JAVA_OPTIONS -u _JAVA_OPTIONS /path/to/jdk21/bin/java -XX:-EnableDynamicAgentLoading -jar /path/to/assurance-fixtures-0.1.0-alpha.jar A 2>A.stderr | tee A.tsv
```

The process prints a ready record and waits on stdin. Type CHECK followed by a fresh canonical lowercase UUID, for example generate a request in a separate shell with `printf 'CHECK %s\n' "$(cat /proc/sys/kernel/random/uuid)"` and paste it into terminal A. Repeat three times. Expect control=scalar, discriminator=scalar, after_control=scalar, health=ok and behavior=baseline_observed. Keep A alive across deployment.

The administrator now loads the signed artifact with the independently expected current chain and exact runtime version ID.

```sh
"$S" upatch load --expect-chain=<chain-before-load> --expect-version-id=<runtime-version-id> --report=load.json /path/to/java-sql-inj.upatch
"$S" status --json
"$S" upatch list --json
```

In terminal B as the same ordinary workload owner, launch B with the documented explicit startup-agent path. This isolates agent behavior from systemd manager environment propagation.

```sh
env -u JAVA_TOOL_OPTIONS -u JDK_JAVA_OPTIONS -u _JAVA_OPTIONS /path/to/jdk21/bin/java -XX:-EnableDynamicAgentLoading -javaagent:/run/shirudo-java/java-sql-inj.jar -jar /path/to/assurance-fixtures-0.1.0-alpha.jar B 2>B.stderr | tee B.tsv
```

Challenge A and B three times each using fresh UUIDs. Compare label, instance UUID, PID, start instant, boot ID, PID namespace and kernel start ticks across all records. Verify PIDs and start ticks against administrator-observed procfs. A must retain baseline behavior. B must report discriminator=guard_exception, healthy controls and behavior=guard_observed. The expected SecurityException message is checked by the harness. Other errors yield inconclusive. The upstream agent writes alert diagnostics to stderr and one exact premain
startup banner to stdout. The Java fixture-channel reader permits that banner
once only when independent process inspection confirms the expected SQL agent;
the ready/check records still require every identity and challenge binding.
Preserve stderr separately from protocol transcripts.

For the roadmap's environment-propagation case, repeat B under a fresh systemd user manager receiving the generated options, using the existing upstream guarded-JVM test as a reference. Record that launch route separately. The explicit-agent run above qualifies the shield's behavior only for that startup configuration.

Bracket the checks with administrator-collected attestations using different fresh verifier nonces.

```sh
"$S" attest --nonce=<fresh-verifier-nonce> --output=before.json
# Perform the repeated checks in A and B.
"$S" attest --nonce=<different-fresh-verifier-nonce> --output=after.json
```

On the trusted operator, predict the expected chain from all independently known enrolled certificates, boot inventory and ordered operations. Verify both documents with the enrolled AK pin and approved boot, binding PCR and config policy. The following illustrates the native commands and must be expanded for the particular lab profile.

```sh
shirudo attest predict --cert=<enrolled-cert.der> --event=java-load:java-sql-inj=<load-payload-sha256> --json
shirudo attest verify --evidence=before.json --nonce=<fresh-verifier-nonce> --ak-name=<pinned-ak-name> --expect-chain=<predicted-register> --json
shirudo attest verify --evidence=after.json --nonce=<different-fresh-verifier-nonce> --ak-name=<pinned-ak-name> --expect-chain=<predicted-register> --json
```

Include every boot-restored artifact, certificate and tree root applicable to the deployment in prediction. Apply the binding PCR, boot PCR and approved config checks documented in [the threat model](../docs/THREAT-MODEL.md). Treat failed verification, changed state, missing records or unhealthy controls as unresolved qualification. Matching probe results alone cannot establish signed shield provenance.

Optionally test retirement while B stays alive.

```sh
"$S" upatch unload --expect-chain=<chain-before-unload> --expect-version-id=<runtime-version-id> --report=unload.json /path/to/java-sql-inj.unload.upatch
```

Predict and verify the resulting java-unload event, then challenge B again. Record any surviving guard effect. Start a replacement instance after retirement and confirm baseline controls. Type QUIT in each instance's terminal when finished. EOF also ends a fixture. Each accepted nonce may be used once per process, with a limit of 10,000 checks.

Qualification requires actual protected-guest execution, stable independent process identities, fresh verified host evidence, healthy controls and repeatable expected results. Retain the transcripts and evidence with the run's provenance. Portable tests establish only the fixture's baseline behavior and process protocol.
