# Read-only Shirudo evidence adapter

Use `ShirudoCli` with the absolute authenticated runtime path for target collection
and a bounded timeout. `collect(nonce)` runs status, upatch list and attest in
sequence. These are separate observations with independent timestamps. Attestation
is returned as the original stdout string for operator-controlled storage.

Run `verify(evidencePath, nonce, policy)` on a separately trusted operator host.
The adapter invokes the official verifier with a pinned AK, expected chain,
binding PCR 9/11 and config root/PCR. Boot PCR expectations come from the
operator's approved deployment profile. This initial policy supports a config PCR.
Config NvPCR deployments require a later policy extension.

The caller owns enrollment, nonce generation, freshness/expiry and evidence-file
integrity. Use unique verifier-issued nonces and protected immutable local files
throughout verification. Collection and verification require Linux and the native
Shirudo executable with its memory-lock and permission prerequisites.

A successful native report becomes VERIFIED only for the supplied policy.
Local reports and unverified attestations stay UNKNOWN. Native verification
failures and a locally reported tampered status are FAILED with distinct reasons.
Command errors, malformed schemas, timeouts and unsupported environments are
UNKNOWN. Each result retains command arguments, timestamp, stdout and stderr.
Store these records privately. They establish no JVM-level behavior.

Unit fixtures are synthetic parser/process tests. They provide no real TPM
attestation. Protected-guest collection and separate operator verification,
including a real signature failure, remain pending.
