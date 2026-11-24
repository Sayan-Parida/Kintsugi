# Incremental implementation and acceptance gates

**First milestone.** Reproduce one measured host with one guarded and one unguarded *long-lived* JVM using the unchanged upstream SQL shield. Prove the difference through two harmless, fixed H2 queries in each actual process. Capture the evidence, process identities and controls before investing in discovery automation or a new agent. No application or fixture has been implemented as part of this research. [Feasibility and discriminator](FEASIBILITY.md) · [Pinned upstream study](UPSTREAM-ANALYSIS.md)

## M0 — Qualify the development target and a single shield

**Deliverable.** A repeatable experiment on the pinned Shirudo Linux x86-64 KVM/swtpm guest, driven from the separate operator. Set up a maintained HotSpot 21 build, Maven 3.9.x toolchain and two cooperative long-lived H2 fixtures with the same application, driver, JVM build and options except intended startup-agent propagation. Stage the signed load and matching signed unload directive, the expected raw JAR and signed payload digests, runtime version ID and the independent verifier references. Use the operator key already enrolled for this disposable target. Follow the [Windows/Linux VM plan](FEASIBILITY.md#windows-development-and-linux-validation).

**Experiment.** Start JVM A with an approved baseline configuration before the shield is loaded. Establish an unguarded benign control and scalar-reference result. Keep A alive. The target administrator now runs the signed Shirudo load. Start JVM B under a **fresh** systemd user manager that receives the generated agent options. Use a predeclared service owner and the same test harness, adjusted only for the launch route. Challenge both children over their bound channels. Recheck both process identities and the host evidence on either side of the probe window. Use dynamic agent loading disabled in both targets. Keep the target private signing-key-free.

| Acceptance item | Pass condition |
|---|---|
| Environment and provenance | Record source SHA, kernel artifact identifier/digest, runtime version ID, target JDK vendor/build, shield source and package version, test fixture digest, H2/Byte Buddy pins and all digest types. `shirudo init --check-only` checks the compatible target; status conditions and independent verification meet the documented **lab** profile. Record any boot difference from production. |
| Host evidence | Approved signer enrollment, full expected boot inputs, one intended post-boot Java load and the independently predicted chain. Both bracketing challenge responses pass the pinned verifier profile with fresh nonces and the same expected register/TPM epoch. The local list shows precisely the expected Java name and payload for the shield. |
| JVM behavior | A and B overlap in time, their stable identities differ, and both return the control scalar. In three successive fresh challenges each, A returns the exact baseline scalar for the discriminator and B returns the shield-specific exception. Both remain healthy. No observation is credited to a separate helper JVM. |
| Reproducibility | Repeat the whole sequence from a fresh boot and clean workload state; store redacted transcripts, raw evidence digests and actual process lifetimes. An independent contributor can derive the same expected outcomes without target-reported values becoming verifier reference data. |
| Failure decision | If either JVM has unstable identity, the artifact fails admission, the guard never discriminates or verification fails, record the relevant layer and reason. Do not proceed to a positive runtime verdict. Reproduce the upstream shield/driver issue or narrow to inventory-only until qualified. |

Reuse `t3014`, `t3114` and the Java portion of `t9105` as source-of-truth controls for packaging, seal and fresh startup. Their test prerequisites differ; the full backend/tree stack is optional for Kintsugi's direct-artifact MVP. A skipped upstream test is recorded as skipped. [Build tests](https://github.com/shirudo-dev/shirudo/blob/39923c519f601c687a6c95aff40a3733172d5673/testing/functional/upatch/t3014-java-build.sh) · [Target tests](https://github.com/shirudo-dev/shirudo/blob/39923c519f601c687a6c95aff40a3733172d5673/testing/functional/upatch/t3114-java-agent.sh) · [Delivery test](https://github.com/shirudo-dev/shirudo/blob/39923c519f601c687a6c95aff40a3733172d5673/testing/end2end/t9105-delivery.sh)

## M1 — Read-only process inventory and intent

**Deliverable.** Java core and Linux observer classify visible processes against the workload declaration. Implement bounded procfs discovery, identity rechecks, owner and namespace scope, stable missing-slot reporting and a versioned evidence model. Add a Spring Boot non-web runner after the reducer and adapters work independently.

**Gate.** One test host with at least three simultaneous JVMs (two required and one exempt), a non-Java process and a declared but absent workload. Every fixture gets its own lifetime, scope and role. A denied read produces an explicit unknown/partial entry, and an expected-but-hidden JVM leaves its slot unresolved. A supervised exit during a scan or a synthetic PID-reuse fixture invalidates the earlier observation. An exec/image mismatch cannot inherit a previous result. A missing attach provider or `-XX:+DisableAttachMechanism` leaves passive discovery functional. Report the actual scan duration and missed/unknown counts under the declared workload; never silently drop unknowns from the denominator.

## M2 — Upstream verifier integration and per-process report

**Deliverable.** Bounded adapters for `upatch list --json`, `status --json`, `attest predict` and `attest verify --json`; a reducer that joins verified host evidence to process observations while preserving their authority and interval. Keep an operator-side manifest of evidence digests and exact policies. Target admin supplies the protected runtime outputs through the existing operational channel.

**Gate.** Replay stored *test* artifacts for deterministic parsing/reduction tests, then collect live fresh evidence for the integration test. On the M0 pair, the report lists A as a coverage gap and B as behavior corroborated. An unreadable third JVM is unknown. Wrong AK, reused/wrong nonce, unexpected digest/chain, failed control, host change during collection, wrong fixture channel, unknown JSON schema and an upstream CLI error each prevent positive attribution. A missing expected-state policy is rejected even if an upstream minimal verification would otherwise say OK. CLI timeouts and partly written reports end the run as incomplete. Re-running the assessment makes zero shield changes.

## M3 — Restart-based remediation and retirement

**Deliverable.** Operator-guided lifecycle and verification, still with one shield. Capture pre/post host evidence and per-process rows; service owner controls the restart and availability. Exercise stale manager, explicit service configuration and retained placeholder cases with synthetic workloads.

| Transition | Observable acceptance result |
|---|---|
| Signed load while old JVM lives | No reclassification from host load alone. After an authorized restart with correct options, record the replacement lifetime, health control and observed guard effect. |
| Signed unload while a guarded JVM lives | Verify the independently predicted unload chain and archive/list transition. Show that the old healthy JVM can still exhibit the guard with the host retired. Preserve an “unknown” if the old process cannot be tested. |
| Restart after retirement | Old lifetime ends or is explicitly listed as still draining. Replacement starts and passes both baseline controls. Include a stale-manager launch still naming the placeholder; it starts and returns baseline. A matching argument never becomes positive behavioral evidence. |
| Conflicting or interrupted deployment | A foreign-signed artifact or mismatched unload digest is rejected by upstream in its disposable test suite. A simulated failed CLI report or changed host state keeps coverage unresolved until fresh evidence arrives. No observer bypass or automatic rollback. |

**MVP exit.** Pass M0–M3 on the VM with ordinary target observer privilege, dynamic loading disabled, one unchanged shield, at least two simultaneous intended JVMs and a signed retirement. Archive a run showing both forms of divergence: measured-present with a missing effect, then measured-retired with a surviving effect. Publish the limits of the lab profile, the support matrix and the counts of unobservable instances. Do not call a restart an in-place rollback.

## M4 — Optional, gated research into live change

Proceed only if a concrete service owner needs the capability, the shield has an explicit reviewed lifecycle, and upstream maintainers have had the opportunity to review how its authority relates to Shirudo. First evaluate changing a fixed mode of an already startup-loaded shield. A general attachment facility introduces broader authority. The upstream SQL example is startup-only; the Base64 example has a live entry point but lacks a rollback contract. Neither qualifies automatically.

**Admission gate.** Document how an operator-authorized request binds the exact reviewed artifact, process lifetime, service owner, compatible JVM, permitted transition and expiry; how a target's actual file/namespace view and concurrent host changes are handled; and what evidence remains independently verifiable. Denial must leave privilege and JVM policy intact. If this binding is unavailable, retain startup/restart.

**Rollback gate.** For a dedicated compatible future shield only, test preloaded/late-loaded classes, two loaders with the same class name, another transformer, active frames, repeated and concurrent transitions, a failed application of the change and a stopped controller. Require a surviving unrelated transform, quiesced old frames where specified, independent health checks and before/after behavior in the **same** process. List any helper classes, module changes and state that persist. No automatic retry after ambiguous side effects. Failures select service-owner restart. No native Shirudo measurement claim is added by a JVM transition.

## Testing and upstream contribution

| Tier | Infrastructure | Evidence it can produce |
|---|---|---|
| Portable unit and contract | JUnit Jupiter (version managed by the selected Spring Boot BOM) on Windows/Linux; fixed JSON samples from the pinned runtime with fixture-only identities. Golden cases for incomplete fields, precedence, expiry and conflicting observations. | Correct parser and deterministic decisions, never TPM or seal validation. |
| Linux JVM integration | Maven Failsafe, explicit Toolchains, forked processes and bounded timeouts. Exercise permissions, identity races, stdout truncation, agent order and clean restarts. Retain JDK build and class-loader scope. | Runtime effects in named, cooperative JVMs, independently of host attestation. |
| Protected target | Isolated Linux KVM job or dedicated runner with QEMU, swtpm, patched pinned kernel and initrd; operator outside target. Run upstream Java functional controls and the two-JVM experiment. | Lab-qualified admission, measurement, restart and end-to-end correlation. An ordinary container or mocked TPM verifier does not satisfy this gate. |
| Production qualification, outside MVP | Actual approved firmware, measured boot, enrolled hardware TPM and service-owner compatibility tests. | Deployment-specific boot and security claims only after separate qualification. |

A focused upstream contribution would extend the existing fresh-JVM test with a long-lived process across signed install **and** retirement, or document in R24 how the example's standalone attach path differs from host enrollment. Submit a small, reproducible test or documentation change for maintainer discussion; keep Kintsugi's JVM report schema independent until upstream requests a contract. Inspect current upstream tests, issues and PRs again before proposing work. [Contribution guide](https://github.com/shirudo-dev/shirudo/blob/39923c519f601c687a6c95aff40a3733172d5673/CONTRIBUTING.md) · [Existing rollout](https://github.com/shirudo-dev/shirudo/blob/39923c519f601c687a6c95aff40a3733172d5673/testing/end2end/t9113-shield-rollout.sh)
