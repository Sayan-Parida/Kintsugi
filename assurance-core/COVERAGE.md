# Per-JVM evidence assessment

CoverageAssessment binds an operator intent to a process lifetime and a shield
name plus signed payload digest. It preserves host verification, startup clues
and behavioral observations as separate dimensions. Missing, inaccessible,
conflicting, expired or future evidence produces UNKNOWN with reasons.

The Linux inspector reuses inventory boot/PID/start ticks, adds namespace,
executable inode and UID information, and rechecks identity after bounded procfs
reads. It reads command-line and launcher environment options without attachment.
A startup option is a configuration clue. Current procfs and application-owned
environment data cannot prove the bytes originally loaded by the JVM.

CoverageService accepts a native verifier receipt and the operator-approved
mapping between that receipt's expected chain and the intended shield.
The caller is responsible for enrollment, binding that mapping to the receipt,
and obtaining a fresh behavioral challenge with healthy controls in the same
process. The service rechecks the current process before reduction.
No verifier receipt or startup option alone yields positive coverage.
Host and probe collection windows must be bracketed by the caller's verified
host protocol described in the architecture.

Behavior corroboration covers the tested invocation and interval. Inaccessible
procfs, a changed identity or unresolved agent options remain UNKNOWN.
Protected Linux validation remains pending.

## Authorized fixture sessions

CoverageService.openFixtureSession requires explicit authorization for the exact
test process, a native verifier receipt, the operator-approved chain-to-shield
mapping and a fresh OS identity. The shield payload digest identifies the version;
no shield assertion from fixture output is used. FixtureAssessmentSession is the
protocol validator in the existing core module, whose Host input retains the same
trusted-caller boundary as CoverageAssessment. Direct core construction is not
native verification.

The operator must own the fixture's dedicated stdin/stdout channel (normally the
Process pipes from launching the existing H2 fixture), read its ready record,
open the session, capture the OS identity immediately before sending command(),
then pass the single check record and that identity to assessFixture. This API
does not discover or probe arbitrary applications. A channel selected only by an
untrusted label, PID assertion or copied log is not authorized evidence.

Each session issues one random UUID challenge, binds the complete independently
observed process identity and ready-instance fields, and consumes even an invalid
or missing response. Reuse, duplicate fields, wrong nonce, inconsistent controls,
PID changes, missing identity and expired host/challenge evidence remain UNKNOWN.
The maximum session window is one minute; shorter operator limits are supported.
The service obtains the after-response identity and the coverage engine checks
identity again. Fixture timestamps must fall inside the challenge window.

These are cooperative local observations, not cryptographic proof of JVM state.
The caller still owns verifier nonce freshness, profile enrollment, trusted host
mapping and lifecycle bracketing; an in-process attacker can forge fixture output.
Unit tests use explicitly synthetic protocol inputs and do not establish actual
Shirudo protection. Protected Linux end-to-end validation remains pending.
