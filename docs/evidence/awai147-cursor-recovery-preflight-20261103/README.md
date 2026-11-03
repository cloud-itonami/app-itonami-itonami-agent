# Recovery preflight extraction

The completed-replacement checks were extracted into `cc-recovery-ready?` so a
controller can reject invalid local state before attempting GitHub readback.
`cc-recover` calls the same predicate again immediately before its sync and
rename operations. The predicate is observation, not a reservation; the host
must continue to exclude concurrent writers and filesystem aliases.

On a local Darwin ARM64 Mac, the native diagnostic fixture used the actual
preflight and recovery, actual checkpoint size/range functions and a limited
CID-decoder stub. A valid replacement was recovered; pending, old or different
cursor, malformed, missing and oversized intents remained unknown. This is
not production provider, GitHub, power-loss or concurrent-writer validation.

SHA-256: source `4393dcffcf722698b5a2cb1427c7631bdc31bdd200102748e9adba7b28e5c830`;
fixture `5edb95adea8e2d1119e879c404e62d64180c2914ff6d879a2f5ed47654272bd4`;
signed fixture `73f3c3111b83cfad1cb2f21455c12bda092e33d74afeaf409f9f150599cf6dd9`.
