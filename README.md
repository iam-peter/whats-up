# whats-up

An Android home-screen widget that shows your calendar events and
birthdays at a glance. It is modelled on the Chronos month widget but
fixes its rendering problems and makes the layout more configurable.

## Features

- **Rolling day grid:** starts at the beginning of the current week; the
  number of weeks follows the widget height (1–6), down to 170 × 40 dp.
- **Text that fits:** titles wrap to two lines at word boundaries, are
  clipped at the chip edge when space runs out, and extra entries collapse
  into "+N".
- **Birthdays:** from Contacts and the Google Birthdays calendar,
  de-duplicated per person, with age and an optional cake icon. Several on
  one day are grouped into "N birthdays".
- **Timed events** show their start time; past days and events that have
  already ended are dimmed.
- **Per-calendar styles:** a fill pattern (stripes, dots, grid, zigzag)
  and an outline style (solid, dashed, dotted) for each calendar.
- **Holiday de-duplication** across holiday calendars in different
  languages.
- **Configurable per widget** with a live preview: weeks, text size,
  density, background, weekend tint, week numbers, colours.
- **Private and light:** no internet permission; it updates only when the
  calendar changes, at midnight or when an event ends.
- German and English, with TalkBack descriptions.

Requires Android 12 or later. The requirements are in
`Claude - whats-up - Widget Specification.md`.

## Install

Download `whats-up-nightly.apk` from **Releases → Nightly** and open it on
the phone. After installing, open the app once to grant calendar (and
optionally contacts) access, then add the widget from the home screen.

## Build

A full JDK is needed (the system `openjdk-21` package has no `javac`).
Android Studio's bundled JBR works:

```bash
export JAVA_HOME=/opt/android-studio/jbr
./gradlew :app:testDebugUnitTest :app:assembleDebug
~/Android/Sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Nightly builds

`.github/workflows/nightly.yml` runs daily at 02:00 UTC and builds only if
`main` has changed since the last nightly. **Actions → Nightly build → Run
workflow** builds on demand. The APK is published as the `nightly`
pre-release (**Releases → Nightly → whats-up-nightly.apk**) and as a
workflow artifact.

Local and CI builds are signed with the same committed debug key
(`app/whatsup-debug.keystore`), so they install over each other. CI builds
get `versionCode` 1000 + run number; a local build installed over one needs
`adb install -r -d`.

## Layout

| Package | Content |
|---|---|
| `app.whatsup.logic` | Pure Kotlin: grid range, cell fitting, birthday rules, de-duplication, grid model builder (unit-tested) |
| `app.whatsup.data` | Calendar Provider and Contacts queries, `EntryLoader` |
| `app.whatsup.widget` | Glance widget, receiver, text measuring, palette, intents |
| `app.whatsup.update` | Content-change worker, midnight/event-end alarms, refresh receiver |
| `app.whatsup.config` | Settings model and DataStore |
| `app.whatsup.ui` | Main screen (permissions, global settings), widget config with live preview, day view |

## Open M1 items

- Spike S2: multi-day events as spanning bars (currently repeated per day).
- Calendar app picker (Q-41).
- Tuning: the line-height estimate still leaves some vertical space unused.
- Glance screenshot tests at 170 × 40 dp, 4 × 2 and 5 × 4 cells.
