# whats-up

An Android home-screen widget for calendar events and birthdays, modelled
on the Chronos month widget. See
`Claude - whats-up - Widget Specification.md` (v1.0) for the requirements.

## Build

A full JDK is needed (the system `openjdk-21` package has no `javac`).
Android Studio's bundled JBR works:

```bash
export JAVA_HOME=/opt/android-studio/jbr
./gradlew :app:testDebugUnitTest :app:assembleDebug
~/Android/Sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

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
- Tuning: words are broken mid-word when a title wraps to two lines.
- Tuning: the line-height estimate still leaves some vertical space unused.
- Glance screenshot tests at 170 × 40 dp, 4 × 2 and 5 × 4 cells.
