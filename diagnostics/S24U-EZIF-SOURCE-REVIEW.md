# S24 Ultra EZIF source review, 2026-10-21

## Result

Temporary root remains unachieved. This review found a diagnostic output-reader bug,
a misleading interpretation of the app probe's context, and an unresolved native
build/manifest inconsistency. None establishes a working kernel-payload repair.

Reviewed snapshots:

- App: `b146d195e5bd46a0ee5ffa222e6b3b5f9b680c0e` on
  `Csiklaoliver/Root-My-Galaxy:experimental-s24u-oneui9`.
- Payload: `28be8785ef01af3fd3432e04f9795b43c120e397` on
  `Csiklaoliver/Root-My-Galaxy-Payloads:experimental-s24u-oneui9`.
- Upstream proposal: [PR #422](https://github.com/BuSung-dev/Root-My-Galaxy-Payloads/pull/422),
  still open and unmerged when checked; unchanged head commit and no discussion
  comments or attached hardware-validation result. The upstream default-branch
  `support/targets-v3.json` did not contain the EZIF profile.

## Findings and changes

### 1. The startup probe reports the app's identity

`InstallViewModel.refresh()` invokes `NativeProbe.run()` in the Android app process.
That probe reads its own UID and `/proc/self/attr/current`. It runs independently of
the selected Shizuku transport. Therefore an `untrusted_app_34` line in that probe
alone cannot establish the identity of the later native-payload process.

The app now labels this output as an app-process probe and logs the selected
execution mode when an installation starts. This does not change the install path
or establish that the previous attempt used Shizuku.

The independent **Settings > Shizuku access check** action launches a metadata-only
script through the same Shizuku remote-process API, without a payload environment.
It records the shell's identity, context, firmware/kernel, event ID, reported control
permissions, and raw trace-file access metadata. It does not invoke the installer.

This check can validate remote command execution. It cannot prove that an
`LD_PRELOAD` constructor, a tracefs write, or the native root chain will succeed.

### 2. Short-command capture could deadlock

`ShizukuController.capture()` previously read stdout to EOF before reading stderr
and had no deadline. A child that filled stderr while keeping stdout open would
block both sides indefinitely. This helper is not the output reader used by the
failed `executeExploit()` run, so this is not a diagnosis of that failure.

The helper now drains both streams concurrently, closes unused stdin, limits kept
output while continuing to drain it, enforces a ten-second deadline through
Shizuku's `waitForTimeout` RPC, and cleans up the process and streams. Its existing empty result for nonzero command exits is
preserved. Regression tests cover full stderr/stdout pipes, output limits, stdin
EOF, exit codes, and timeout cleanup.

The official [Shizuku API source](https://github.com/RikkaApps/Shizuku-API/blob/master/api/src/main/java/rikka/shizuku/Shizuku.java)
also documents that process streams may need separate reader threads. The app's
direct AIDL `newProcess` call depends on an older process interface; migration to a
UserService is a separate maintenance task, not an established root fix.

### 3. Fresh-session manifest and native compile switches differ

The EZIF manifest sets `requiresFreshP0Session: true`. The app parses it and already
passes a single-attempt limit and avoids its cached offset for this profile.

The payload's porting record describes a `stable` build. Its Makefile enables
`APP_S928_STABLE_RACE=1` for that recipe, while the inspected target header and
recipe do not enable `APP_REQUIRE_FRESH_P0_SESSION`. Native freshness-specific
branches in `src/slide_app.c`, `src/pipe.c`, and `src/preload.c` are guarded by that
separate switch. The JSON property cannot activate C code removed at compile time.

This is a confirmed configuration mismatch in the inspected sources, not proof of
the shipped binary's exact compiler invocation or of the failure's cause. Changing
that switch affects native memory-handling behavior and requires a separate native
review, reproducible build, and hardware validation. This change does not alter
native sources, payload bytes, hashes, timings, offsets, or compile switches.

### 4. The native tracefs discovery routine changes global tracing state

`slide_tracefs_resolve_base()` writes `tracing_on`, enables an event, opens `trace`
with `O_TRUNC`, and consumes per-CPU `trace_pipe_raw` data. It does not preserve
and restore the previous control values on all paths. Its early compound-condition
failure can return after an earlier control was already changed.

The new access check therefore only reads event-ID metadata and performs permission
tests on controls and raw trace files. It never opens or reads the trace buffers,
never writes a tracefs control, and never calls that discovery routine. A reported
writable path is a prerequisite observation, not verification of an actual write.

## What the current evidence supports

| Observation | Supported conclusion |
| --- | --- |
| APK compiled and installed | Android packaging works |
| Payload downloaded and hash checked | Exact pinned payload bytes arrived |
| `sched_blocked_reason/id` returned `106` through aShell/Shizuku | That shell can read matching event metadata |
| `ok=1` on physical-write status | Trigger process reported success; intended memory change remains unverified |
| `pipe gate hits=0 changed=0` | The verification step found no matching changed page |
| KernelSU loading not reached | No temporary root has been demonstrated |

## Validation and remaining work

- Check shell grammar and use synthetic tracefs fixtures to verify that the script
  reads only context/event metadata and leaves the fixture controls and buffers
  unchanged.
- Run JVM regression tests and assemble the debug APK in GitHub Actions. This runs
  no Android instrumentation or kernel exploit.
- On the actual device, use **Shizuku access check**, copy its output, and compare
  the shell UID/context and reported permissions. This remains outstanding until
  the owner supplies that output.
- A native repair and a successful full-chain hardware test remain outstanding.
  No additional root attempt is part of this review.

Both main branches and the payload experimental branch are preserved. Changes are
limited to diagnostics and ordinary Android process handling on the app's existing
experimental branch.
