# Version 1.3 verification

Release metadata: `com.example.exercisetracker`, version name `1.3`, code `4`,
minimum API 26, target API 33, compiled with SDK 35/build tools 35.0.0.

Tested on 17 October 2026 using an isolated Android 16 / API 36 x86_64 emulator.
No physical phone was attached and no actual user records were modified.
Generated reports, APKs, fixture data, and screenshots are in ignored `build/`
directories.

## Build and signing

- Java/resource compilation, D8 conversion, APK alignment and signing: passed.
- Android lint: **0 errors, 4 warnings**. Warnings cover target SDK 33, retaining
  the v26 icon directory, and monochrome layers absent in v26 resources. The
  Android 13+ resources do include monochrome layers. Target 33 supports explicit
  notification permission while keeping this release's platform changes limited.
- APK signatures: v2 and v3 verified by `apksigner`.
- Original v1.2 and replacement v1.3 certificates match exactly:
  `4ee722ea894bf72a8739eae8f3bf82ce4628244642ad4b91aa1836e749d1242d`.
- This is the original Android debug signing identity, preserved intentionally;
  no new identity or key was generated, and no key or password is committed.
- APK SHA-256:
  `5879206750149ff68397db641c64712a3c3bcbd5160cb10d3cb29807009d3601`.

## Automated integration checks

| Check | Result |
| --- | --- |
| Install and launch original v1.2; seed its real preference file | Passed |
| Install signed v1.3 over v1.2 without uninstalling | Passed |
| Every existing preference identical after update | Passed |
| All four feeling choices save immediately and highlight | Passed |
| All four exercise choices, including No exercise | Passed |
| Historical records and records older than 30 days preserved | Passed |
| Existing completion calculation and progress count | Passed |
| Today plus 29 preceding dates; correct compact duration labels | Passed |
| All 30 rows exactly one line, full text fits without ellipsis | Passed |
| Cold restart restores feeling, exercise, highlights and changed reminder time | Passed |
| 320dp-wide portrait and 30% larger font | Passed |
| Landscape layout with larger font | Passed |
| Interactive controls have at least 48dp touch height | Passed |
| Enable reminder with native picker and display 18:00 | Passed |
| Change native picker to 19:15, then back to 18:00 | Passed |
| Exact reminder registered when access is granted | Passed |
| Actual reboot re-arms 18:00 reminder before app launch | Passed |
| Denied exact-alarm access uses an inexact idle-compatible alarm | Passed |
| Denied notification permission: no crash; Settings and Disable available | Passed |
| Receiver posts notification with custom icon and app-opening intent | Passed |
| Disable cancels alarm and notification; stale receiver does not notify | Passed |
| Disabled reminder remains disabled after restart | Passed |
| Installed application icon is adaptive | Passed |
| Ten legacy PNGs: dimensions, alpha corners, centered white art | Passed |
| Resource XML and shell syntax checks | Passed |
| AndroidRuntime crash log after checks | Empty |

The update suite passed 354 assertions. Separate suites cover changing time,
process restart, layouts, reboot, denied permissions, delivery and disabling.
The reboot check observed Android restoring the reminder with the inexact
fallback; the exact scheduling path was verified separately before reboot.
Instrumentation flushes its asynchronous test preference writes before ending
its process, because its teardown bypasses the normal Activity stop flush.
Screenshots of the main screen, compact history, and small screen with larger
fonts were visually reviewed locally.

## Remaining physical Android checks

- Install the update over the user's actual installed app and confirm their
  existing records; do not uninstall or clear storage.
- Check the icon in the actual OEM launcher, including circular masks and themed
  icons. Launcher caches can differ from the emulator.
- Exercise the real notification permission/settings flow and notification tap.
- Check real scheduled reminder delivery, device reboot, battery restrictions,
  and Doze on the phone. These timing/OEM behaviors cannot be established by the
  emulator's receiver and alarm-registration checks alone.
- Spot-check Android 8/API 26 on older hardware. Lint validates API compatibility;
  runtime tests here used API 36 only.
