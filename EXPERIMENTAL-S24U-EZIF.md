# Experimental Galaxy S24 Ultra One UI 9 build (NOT hardware validated)

This branch is a private-to-your-fork adaptation of the open [payload PR #422](https://github.com/BuSung-dev/Root-My-Galaxy-Payloads/pull/422). It is **not** an official Root My Galaxy release.

## Intended device (only)

| Field | Required |
|---|---|
| Model | `SM-S928B` |
| Android / One UI | 17 / 9.0 |
| Build | `CP2A.260605.016.S928BXXU6EZIF` |
| Kernel | `6.1.162-android14-11-34343818-abS928BXXU6EZIF` |

The app checks the model, Android release, build suffix, and complete kernel string before displaying/loading the one allowed profile. The branch is locked to the experimental catalog on `Csiklaoliver/Root-My-Galaxy-Payloads:experimental-s24u-oneui9`, and the downloaded root binaries have explicit SHA-256 checks based on PR #422.

## Build the debug APK

1. Open [Actions](https://github.com/Csiklaoliver/Root-My-Galaxy/actions) and **enable workflows** if GitHub displays a fork-workflow warning.
2. Select **CI Build** in the workflow list, then **Run workflow**.
3. Select branch `experimental-s24u-oneui9` and run it.
4. Wait until the run completes successfully. If it fails, inspect the failing job logs; do not treat a failed build as a usable APK.
5. Under that run's **Artifacts**, download `app-debug` (ZIP). Extract `app-debug.apk`.

The debug app ID has an `.experimental` suffix, so it can be installed alongside another signed Root My Galaxy build. The launcher shows **Root My Galaxy (Experimental)**.

## Important before trying it

- **This is UNTESTED on physical EZIF firmware.** The PR author reports static checks but has not reported successful hardware execution.
- An APK that builds successfully can still crash, hang, lose data, or fail to gain root when running the kernel exploit.
- Back up important phone data. This build is not an official supported root release; do not rely on it on a primary phone.
- A **KernelSU v3.3.0 (32601) manager** is specified by the payload author. Earlier versions are not an advertised substitute.
- The app supports an optional Shizuku execution path, which requires an already-running Shizuku service and its permission grant.
- Check the developer's PR #422 for a real-device validation report before treating this as usable.

## Where code lives

- [App experiment (draft PR)](https://github.com/Csiklaoliver/Root-My-Galaxy/pull/1)
- [Payload experiment (draft PR)](https://github.com/Csiklaoliver/Root-My-Galaxy-Payloads/pull/1)
- [Upstream unmerged profile](https://github.com/BuSung-dev/Root-My-Galaxy-Payloads/pull/422)

Do not merge either experimental draft into the default `main` branches until full validation is available.
