# Protected Linux validation

Execution is pending. This repository contains test automation and portable tests.
There are no recorded protected-guest integration results or production-readiness claims.

## Audit status and trust

The focused audit fixes through e40375a address evidence-file substitution, schema
overflow, identity-read races, receipt ordering, fixture-channel reuse and installer
permissions. Review of those paths found no remaining confirmed critical issue.
This is a scoped review with protected Linux execution still outstanding.

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
