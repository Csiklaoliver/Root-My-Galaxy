# S24 Ultra EZIF tracing preflight (read-only)

**Purpose:** determine whether the ADB shell on an exact `SM-S928B / S928BXXU6EZIF` installation can see the tracing event assumed by the experimental payload. This check **does not install root**, run the kernel exploit, write to tracefs, or unlock the bootloader.

## Why check this?

The first full failure log (2026-10-21) ran in `u:r:untrusted_app_34`. The initial probe reported `tracefs_control=denied errno=13`, then the native payload wrote `slide tracefs pre-oracle unavailable; using physical scan` and ultimately failed with `p0 pipe gate hits=0 changed=0` and `p0 physical pipe reclaim miss`.

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

**Static code check:** the APK's Shizuku path runs a shell command with an LD_PRELOAD native constructor in a Shizuku-launched process. No Shizuku-transport coding defect was identified in this review. No memory-offset, kernel-race, or payload binary changes were made.
