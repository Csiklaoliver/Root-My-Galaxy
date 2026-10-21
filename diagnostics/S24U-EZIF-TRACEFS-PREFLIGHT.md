# S24 Ultra EZIF tracing preflight (read-only)

**Purpose:** determine whether the ADB shell on an exact `SM-S928B / S928BXXU6EZIF` installation can see the tracing event assumed by the experimental payload. This check **does not install root**, run the kernel exploit, write to tracefs, or unlock the bootloader.

## Why check this?

The first failure log (2026-10-21) included an app probe with `u:r:untrusted_app_34` and `tracefs_control=denied errno=13`. That probe always runs in the app process, independently of Shizuku mode, so its context alone does not identify the later payload's execution context. The native payload then wrote `slide tracefs pre-oracle unavailable; using physical scan` and ultimately failed with `p0 pipe gate hits=0 changed=0` and `p0 physical pipe reclaim miss`.

The proposed payload has a separate `slide_tracefs_resolve_base()` path before trying the physical method, while its developer explicitly marks `SLIDE_TRACEFS_EVENT_ID = 106` **not verified on device**.

## Safe command from a PC with ADB connected

Run these *read-only* checks:

```sh
adb shell id
adb shell getprop ro.build.display.id
adb shell cat /sys/kernel/tracing/events/sched/sched_blocked_reason/id
```

If the event file is located elsewhere, check the same path under `/sys/kernel/debug/tracing`:

```sh
adb shell cat /sys/kernel/debug/tracing/events/sched/sched_blocked_reason/id
```

The `cat` commands only read an event ID. They do **not** enable tracing, change memory, or run any exploit. Do **not** use commands that write into `tracing_on`, `set_event`, or event `enable` files.

## How to interpret

- `Permission denied`: the shell context cannot read this path. This preflight does not support using the tracefs path.
- `No such file or directory`: the event isn't exposed at that location.
- A numeric event ID: the shell can at least read the event metadata. Compare it against the current experimental profile's claimed `106`. A match is **not proof** that the actual tracing-based exploit will work.
- The shell may have a different SELinux context than the app; don't assume ordinary app permissions match ADB/Shizuku permissions.

## Scope

This is static investigation and a non-invasive prerequisite check, **not a root fix**. On-device validation of the new kernel payload is still pending. Don't retry or alter the exploit based only on this preflight. Relevant code paths are `src/slide_app.c`, `src/pipe.c`, and the provisional tracefs constants in the EZIF target header in the companion payload repository.

## 2026-10-21 device result: event ID confirmed

The owner ran the read-only event ID command via aShell with Shizuku and obtained `106`. This **confirms the provisional event number** on the device and that Shizuku's shell can read this path. It does **not** confirm write access or exploit viability.

### Additional read-only Shizuku permission preflight

In aShell (through Shizuku) run:

```sh
id
for p in /sys/kernel/tracing/tracing_on /sys/kernel/tracing/events/sched/sched_blocked_reason/enable /sys/kernel/tracing/trace; do
  if test -w "$p"; then echo "$p : appears writable"; else echo "$p : not writable"; fi
done
```

This does **not** write to these tracing controls. It checks reported permissions only, and even a writable result does not establish that the exploit works. If any path reports not writable, do not assume tracefs discovery can proceed. The payload's `slide_tracefs_resolve_base()` also reads `per_cpu/cpu*/trace_pipe_raw`; permission or content requirements can still fail after these checks.

## Independent check inside the experimental APK

In a build containing the 2026-10-21 source-review changes:

1. Start Shizuku.
2. Open the experimental app's **Settings > Shizuku access check**.
3. Tap **Check Shizuku access** and grant the app Shizuku access if requested.
4. Tap **Copy results** to collect the report.

This action does not call the installer and does not require pressing Install.
The script is `app/src/main/assets/shizuku-preflight.sh`. It reads identity,
context, build properties, kernel release, and the event ID. It checks metadata
for the required control and raw trace files without opening the trace buffers.
Even a readable `trace_pipe_raw` is not consumed by this check.

For an ADB check from a local checkout, the same script can be streamed to the
device without installing a new APK:

```sh
adb shell < app/src/main/assets/shizuku-preflight.sh
```

ADB and this app's Shizuku launch are separate transports. The in-app check is
the direct test of the app's remote-command API. For an adb-started Shizuku,
UID `2000` and a shell SELinux context are the expected observations, not root.

**Static code check:** the installer's Shizuku path runs a shell command with an
LD_PRELOAD native constructor in a Shizuku-launched process. A separate
short-command capture helper could deadlock on stderr and now has concurrent
readers and a deadline; that does not explain the previous payload verification
failure. See [the source review](S24U-EZIF-SOURCE-REVIEW.md) for the native
manifest/build mismatch and the limits of the app probe. No native payload was
changed or run by these diagnostic changes.
