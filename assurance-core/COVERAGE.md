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
