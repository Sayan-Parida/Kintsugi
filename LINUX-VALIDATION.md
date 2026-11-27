# Protected Linux validation

The complete fresh-cohort procedure passed on 2026-11-27 using Java engine commit
847f01c and the unchanged upstream shield. A and B were assessed in the same
controlled run with genuine opening/closing evidence verified in the operator
domain. This is a scoped software-TPM lab qualification, not production readiness.
[Sanitized machine-readable results](docs/validation/sql-shield-847f01c.json).

## Audit status and trust

The focused audit fixes through e40375a address evidence-file substitution, schema
overflow, identity-read races, receipt ordering, fixture-channel reuse and installer
permissions. Review of those paths found no remaining confirmed critical issue.
This is a scoped review. The fresh-cohort qualification below passed after the
narrow stdout-framing fix; the existing identity, nonce and attestation checks
were retained.

Use a disposable protected Shirudo guest with its approved boot configuration, TPM,
enrolled operator certificate and unchanged reviewed upstream shield. The approved
upstream pin is 39923c519f601c687a6c95aff40a3733172d5673. Use the existing
[shield build and signing instructions](assurance-fixtures/LINUX.md). Keep private
signing keys on the operator. No shield or kernel change is performed by the driver
except the explicitly supplied administrator deployment hook.

The supervisor, profile template and verifier executable must belong to the trusted
lab operator. Native verification must execute in the trusted operator domain.
The included collector is suitable only for a controlled lab where the guest
observer/verifier execution is explicitly trusted. For a guest treated as hostile,
supply a provider using the existing administrative transport to collect evidence
and verify in the operator domain. Current CLI assessment requires access to the
same workload procfs and dedicated pipes, so a hostile-guest remote-verifier
deployment needs separate trust-boundary validation. Do not treat local guest
verification as hostile-host assurance.

## Prerequisites and setup

On the Linux KVM host, check accessible acceleration before booting the already
prepared guest. QEMU/KVM setup, protected boot enrollment and TPM provisioning
follow the existing lab setup in docs/FEASIBILITY.md.

```sh
python3 testing/linux_validation.py --check-kvm-host
```

Inside the protected guest, require Python 3, Bash, systemd-detect-virt, Java 21,
readable procfs, an accessible TPM device and the authenticated Shirudo runtime
at /run/shirudo/runtime/usr/bin/shirudo. A QEMU/KVM guest identifier alone cannot
prove host KVM acceleration. Retain the host check alongside actual VM launch
configuration. Use a fresh snapshot without a prior sealed java-sql-inj JAR.

Build and transfer the Kintsugi application and fixture, preserving their digests.

```sh
sh ./mvnw verify
sha256sum orchestrator/target/orchestrator-0.1.0-alpha.jar
sha256sum assurance-fixtures/target/assurance-fixtures-0.1.0-alpha.jar
```

Create a private operator template using the existing profile contract. Every
placeholder below must be replaced by independently enrolled or predicted values.
The payload digest is the signed upatch payload digest from upstream upatch info,
which can differ from the raw agent JAR digest.

```json
{
  "verifier": "/trusted/operator/bin/shirudo",
  "policy": {
    "akName": "<68 hex>",
    "expectedChain": "<64 hex predicted after java-load>",
    "bindPcr9": "<64 hex>",
    "bindPcr11": "<64 hex>",
    "configRoot": "<64 hex>",
    "configPcr": 14,
    "bootPcrs": {"0": "<64 hex>", "7": "<64 hex>"}
  },
  "mapping": {
    "bootId": "<approved guest boot ID>",
    "state": "VERIFIED_PRESENT",
    "shield": {"name": "java-sql-inj", "payloadDigest": "<64 lowercase hex>"}
  }
}
```

The collector adds actual evidence paths, fresh nonces and UTC windows. All
policy fields and boot PCR selections must reflect the deployment. Do not copy
illustrative values as enrollment. Protect template and hook ownership and use
chmod 600 for the template and chmod 700 for hook executables. The verifier binary
must come from the trusted operator installation.

Create /secure/operator/deploy-shield as an executable POSIX script with the
already approved exact load operation. Substitute real independent values.

```sh
#!/bin/sh
set -eu
exec /run/shirudo/runtime/usr/bin/shirudo upatch load \
  --expect-chain=<chain-before-load> \
  --expect-version-id=<runtime-version-id> \
  --report=/secure/operator/load.json /secure/operator/java-sql-inj.upatch
```

For the explicitly trusted controlled-lab observer, make the included collector
executable. The driver calls it with two arguments, phase and absolute profile
destination. A replacement provider must atomically publish a profile only after
collecting real evidence for a newly issued challenge. Closing collection must
complete within the CLI's ten-second wait. Provider invocations have a thirty-second
supervisor timeout to permit capture of a failed collection-window test.

## Execute

```sh
chmod 700 testing/collect_profile.py /secure/operator/deploy-shield
export KINTSUGI_PROFILE_TEMPLATE=/secure/operator/profile-template.json
python3 testing/linux_validation.py \
  --java=/absolute/jdk21/bin/java \
  --cli=/absolute/orchestrator-0.1.0-alpha.jar \
  --fixture=/absolute/assurance-fixtures-0.1.0-alpha.jar \
  --digest=<approved-lowercase-payload-digest> \
  --provider="$(pwd)/testing/collect_profile.py" \
  --deploy=/secure/operator/deploy-shield
```

Run under an explicitly authorized lab identity with the existing Shirudo and
procfs permissions. Resolve the workload/administrator split using the approved lab
policy. The driver grants no privileges and installs no operating system.

A starts before load and stays alive while the hook deploys the unchanged shield.
B starts afterward with -javaagent:/run/shirudo-java/java-sql-inj.jar. Both use the
existing fixed H2 queries with dynamic loading disabled and cleared launcher-option
environment variables. Each process owns dedicated anonymous pipes. The CLI sends
its own nonce and validates the ready/check records. The supervisor additionally
checks boot ID, PID and mount namespaces, start ticks and executable inode before
and after assessment.

Required real outcomes are A with COVERAGE_GAP, baseline behaviour, absent startup
option and exit 1, and B with BEHAVIOR_CORROBORATED, guard behaviour, expected
startup option and exit 0. Both opening and closing native verifications must pass,
all evidence reasons must be empty, and process identities must stay stable.
No result is promoted when these checks fail.

A separate fresh fixture deliberately receives no closing profile. It must return
UNKNOWN, exit 3 and closing_host_attestation_missing after at least nine measured
seconds. The positive cases test successful publication inside the ten-second
collection wait. Closing native verification has its own bounded timeout, so total
closing latency can approach twenty seconds. Failure to meet timing is a failed
qualification result.

## Evidence and cleanup

The supervisor prints a unique private temporary directory. It retains allowlisted
result JSON with evidence states, reason codes, timing and exit code, plus SHA-256
provenance for both input JARs and the approved payload. Raw fixture diagnostics
and real evidence/profile files remain private in that directory. The driver
does not copy raw subprocess exceptions into public logs. Review even sanitized
JSON before sharing. Record the actual upstream commit, guest boot configuration,
runtime version ID, Java vendor/build and VM launch/KVM evidence with the private
run record. Unsuccessful runs can contain partial artifacts and never count as
successful validation.

All child fixtures are terminated on completion or failure. The loaded signed
shield remains installed. Use the existing matching signed retirement procedure
or restore the disposable snapshot according to lab policy. Preserve results
before restoring. Retirement and systemd environment propagation remain separate
cases in the existing Linux harness guide.

Local checks can validate Python syntax, command help and rejection of a non-Linux
host. They cannot establish protected boot, native signatures or JVM shield efficacy.

## Earlier diagnostic execution (2026-11-27)

The actual Java engine ran against the unchanged upstream java-sql-inj shield,
with genuine fresh opening/closing evidence verified in the WSL operator domain.
The restricted SSH verifier uses fixed operator-owned AK/PCR/config/chain policy,
not refreshed guest measurements. This is an explicitly trusted lab observer and
software TPM, not hostile-host or hardware-rooted assurance.

| Run | Coverage / exit | Opening / closing | Startup / behaviour | Identity |
| --- | --- | --- | --- | --- |
| A, initial two-JVM run | COVERAGE_GAP / 1 | VERIFIED / VERIFIED | OPTION_ABSENT / BASELINE_OBSERVED | Stable |
| B, initial run | UNKNOWN / 3 | VERIFIED / VERIFIED | EXPECTED_OPTION / INCONCLUSIVE | Channel/session rejected |
| B, corrected-engine live regression | BEHAVIOR_CORROBORATED / 0 | VERIFIED / VERIFIED | EXPECTED_OPTION / GUARD_OBSERVED | Stable |
| Missing-closing live regression | UNKNOWN / 3 | VERIFIED / UNKNOWN | OPTION_ABSENT / BASELINE_OBSERVED | Stable |

A's closing latency was 3.942 seconds. Corrected B's was 3.494 seconds; its
reason list was empty. The missing-closing case waited 10.149 seconds and reported
closing_host_attestation_missing (plus closing_profile_invalid_or_missing).

The confirmed failure was the unchanged upstream premain startup banner on stdout,
before the fixture ready record. FixturePipes now permits exactly that one literal
line only for the independently inspected expected SQL agent and matching process.
Unknown/repeated preambles, malformed records and identity/nonce mismatches remain
fail-closed. Banner text is not coverage evidence. The normal five-second deadline,
dedicated-pipe checks, strict ready/check validation and both native attestation
requirements remain unchanged. Six new framing tests and the existing session,
CLI, snapshot and closing-window contracts passed (28 targeted tests).

These results span the initial run and a later B/negative-case regression. They
must not be combined into a claim that the full fresh-cohort procedure passed.
The first run installed the shield. Its signed retirement leaves a sealed JAR
placeholder, so the fresh-guest precondition above cannot be bypassed to repeat
the whole driver. Preserve failed-run records and obtain approval for a fresh
qualification guest/enrollment rather than deleting the sealed file, resetting
trusted measurements or removing that precondition.

Raw private evidence, profiles, native receipts and diagnostics remain in the lab;
no raw evidence, nonces, keys or environment-specific trust configuration is
committed here. Systemd agent propagation and retirement behavior remain separate
qualification cases.

## Complete fresh-cohort qualification (2026-11-27)

The original supervisor completed successfully in a fresh protected guest using
engine commit 847f01c. A started before the guarded signed load and remained alive
while B started afterward with the sealed upstream SQL agent. Both JVMs belonged
to this one controlled run, not combined earlier/regression results.

| Case | Coverage / exit | Opening / closing | Startup / behaviour | Identity | Closing seconds |
| --- | --- | --- | --- | --- | --- |
| A | COVERAGE_GAP / 1 | VERIFIED / VERIFIED | OPTION_ABSENT / BASELINE_OBSERVED | Stable | 2.955 |
| B | BEHAVIOR_CORROBORATED / 0 | VERIFIED / VERIFIED | EXPECTED_OPTION / GUARD_OBSERVED | Stable | 2.908 |
| Missing closing | UNKNOWN / 3 | VERIFIED / UNKNOWN | OPTION_ABSENT / BASELINE_OBSERVED | Stable | 10.085 |

A and B had empty reason lists and healthy controls. The negative case reported
closing_host_attestation_missing and closing_profile_invalid_or_missing. The
native signed-load receipt matched the independently planned post-load chain.
Full Maven verification at 847f01c passed all 62 tests, with no failures, errors
or skips. [Sanitized results and artifact digests](docs/validation/sql-shield-847f01c.json)
contain only allowlisted assessment fields and public artifact provenance.

The compatible kernel adds PID namespace support to the pinned CI recipe.
Existing signed bundle trust material was reused. A fresh AK came from the
trusted host-side pre-boot builder and was explicitly approved; all previously
approved PCR/config references remained fixed. Fresh baseline verification
passed before use. The restricted verifier ran the existing native Shirudo
binary in the WSL operator domain, with independently pinned transport identity.
No failed check was bypassed and no live measurement was promoted into policy.

Both earlier VM states and enrollment records remain preserved. Raw evidence,
profiles, nonces, process identifiers, keys and deployment-specific references
stay in private lab storage. The public result file is sanitized, not a raw
evidence bundle or independently replayable TPM enrollment.

This run qualifies one fixed H2 Statement path, the explicit Java startup-agent
route, stable cooperative process identities and this observation interval.
The host controls swtpm and the guest observer is explicitly trusted. Kernel/UKI
hardware measurement, hostile-guest remote assurance, generic protection,
systemd propagation, live attach and retirement/reversal are not established.
