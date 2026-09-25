# Claude - whats-up - Calendar & Birthday Widget Specification

**Private project document**

Date: 24 September 2026
Version: v1.6
Author(s): Henning Gründl

> **AI generation notice.** This document was produced with Claude AI
> assistance. Validate its content before relying on it.

---

## Executive Summary

**Purpose.** "whats-up" is an Android home-screen widget for phones. It
shows calendar entries and birthdays, and is modelled on *Chronos Calendar+
Widget* (`com.candl.chronos`). It is built in Kotlin with Jetpack Glance, and
Compose is used for the configuration screen and the in-app day/agenda
view. It targets Android 12 (API 31) and later, in German and English.

**First milestone (M1).** M1 copies the Chronos *month* widget as it exists
today: a rolling grid that starts at the beginning of the current week,
with birthdays. It then removes the observed rendering defects:

- clipped titles,
- no event times,
- content cut off at the widget edge,
- inconsistent cell backgrounds,
- duplicate holidays,
- undimmed past days.

It must scale down to the same minimum size as the Chronos month widget
(**170 × 40 dp**). This was the user's actual complaint: another Chronos
widget could not be made that small.

**Later milestones.** These add the remaining layout blocks (classic month,
agenda, week timeline, day timeline, birthday strip, next-up card) and
combinations of them. Customisation stays at "presets + per-block
settings". There is no free-form editor.

**Decisions.** All questionnaire answers are final. The six conflicts
found in v0.2 are resolved as proposed (Section 9). In particular:

- The code stays private until M1, and an open-source licence is chosen
  before the first public release.
- "+N" and aggregated birthday chips open the in-app day view.

---

## 1. Scope

### 1.1 In scope

- Phone home-screen widgets (AppWidget) with per-instance configuration.
- A configuration activity with a live preview at the real widget size.
- An in-app **day/agenda view**, opened from "+N" and from aggregated
  birthday chips (D-2).
- Reading events from the Android Calendar Provider.
- Reading birthdays from the Contacts provider and from the Google
  "Birthdays" calendar, de-duplicated.

### 1.2 Out of scope

- Tablets, foldables (beyond what works automatically), and lock-screen or
  hub widgets.
- Creating or editing events. The user's calendar app does this.
- ICS/webcal subscriptions, tasks, and any network access.
- Anniversaries and other contact dates.
- A privacy mode, headers, in-widget navigation, and weather.

> ⚠️ Data in this section requires human validation.

---

## 2. Reference: Chronos on the Target Device

These observations come from the connected device on 24 September 2026,
using `adb dumpsys` and screenshots. Personal event titles are deliberately
left out.

| Item | Value |
|---|---|
| Device | Google Pixel 9 Pro (`caiman`), 960 × 2142 px, Android 16, Pixel Launcher |
| Chronos | 4.7.260901, minSdk 32, targetSdk 36 |
| Exported widget provider | `com.candl.chronos.widget.MonthWidgetProvider` only (one instance placed) |
| Month widget sizes | default min 240 × 240 dp; **min resize 170 × 40 dp**; resizable horizontally and vertically |
| Rendering | Native RemoteViews (no bitmap); hourly update period plus event-driven updates |
| Reconfigure | Not offered from the launcher long-press menu |

The size values were decoded from `dumpsys appwidget`, where they are
stored as `TypedValue` complex values. For example, `43521` = 0xAA01 =
170 dp.

### 2.1 Observed month-widget layout (the baseline for M1)

- **Grid:** 7 columns × N rows. The range is rolling and starts on the
  first day of the current week.
- **Day header:** the weekday initial sits top-left (first row only) and
  the day number top-right.
- **Today:** a filled circle behind the day number. whats-up uses a cell
  border instead (Q-35).
- **All-day events:** a filled chip in the calendar colour.
- **Timed events:** a coloured dot plus the title, with no time.
- **Birthdays:** a pink chip with a gift icon and the name. Several
  birthdays on one day are aggregated into "N birthdays".

### 2.2 Weaknesses and how they are handled

| ID | Weakness | M1? | Resolution |
|---|---|---|---|
| W1 | Titles hard-clipped mid-word | ✅ | FR-L5: single line, clipped at the chip edge like Chronos but without losing width to padding, then "+N" |
| W2 | Unused vertical space while titles are cut | ✅ | Compact padding; multi-day bars share one lane across the week (FR-E2) |
| W3 | Timed events show no time | ✅ | FR-E3: start time in an outlined chip |
| W4 | Right column clipped by the widget edge | ✅ | FR-T5 |
| W5 | Next-month days have no cell background | kept | Kept on purpose in v1.5: it marks the next month (FR-T2) |
| W6 | "2 birthdays" hides the names | ❌ kept | The user chose aggregation (Q-11); names are shown in the in-app day view (D-3) |
| W7 | Duplicate holidays | ✅ | FR-D5 |
| W8 | Past days look like future days | ✅ | FR-E5 |
| W9 | Weekends not distinguished | M1 option | FR-T4, configurable |
| W10 | Not reconfigurable from the launcher | ✅ | FR-C2 |
| W11 | Only fixed presets | M2+ | Section 4 |
| W12 | *(user)* Some Chronos widgets can't be shrunk to the month widget's size | ✅ | FR-L7: every whats-up layout supports 170 × 40 dp |

> ⚠️ Data in this section requires human validation.

---

## 3. Goals

- **G1 — Legible.** One line per entry, using the full chip width; titles
  that don't fit are clipped at the chip edge (no "…").
- **G2 — Space-efficient.** Compact density and no header by default.
- **G3 — Small.** Every layout works down to 170 × 40 dp.
- **G4 — Birthdays built in.** They have an icon, a name, and an age.
- **G5 — Cheap to run and private.** Updates are event-driven only, and
  there is no INTERNET permission.

---

## 4. Layout Model

### 4.1 Blocks

| Block | Milestone | Description |
|---|---|---|
| `DayGrid` (rolling) | **M1** | N weeks × 7 days, starting at the first day of the current week |
| `MonthGrid` | M2 | 1st to last day of the month, with leading and trailing days |
| `Agenda` | M2 | Chronological list grouped by day, with sticky day headers |
| `BirthdayStrip` | M2 | Upcoming birthdays in the next N days |
| `NextUp` | M2 | The next 1–3 events, large, with a countdown |
| `WeekColumns` | M3 | 7 columns with a time axis |
| `DayTimeline` | M3 | A single day with an hour axis |
| Combination | M3 | Two blocks stacked, e.g. `DayGrid` above `Agenda` |

**Customisation (Q-29):** users pick from **presets** and adjust
**per-block settings**. There is no free arrangement of blocks beyond the
combinations the presets offer.

**Header (Q-30):** none. Every pixel goes to content.

**Navigation (Q-40):** none. The widget always shows the current range.

### 4.2 Responsive behaviour

- **FR-L1** The widget uses Glance `SizeMode.Exact`. It is re-rendered
  for the actual size after each resize, so the fitting engine measures
  against the real cell size. (`SizeMode.Responsive` was rejected because
  it would compose the full grid once per size bucket and could exceed the
  RemoteViews size limit.)
- **FR-L2** The layout is *adaptive* (Q-28): blocks switch or hide at size
  thresholds. For example, a combined grid + agenda widget drops the
  agenda when it is lower than about 3 rows.
- **FR-L3** Grid weeks = floor(available height / minimum row height),
  clamped to 1–6. The user can override this with a fixed value from 1 to
  6 (Q-18).
- **FR-L7** **Minimum size 170 × 40 dp** for every layout (W12). The
  widget info uses `minResizeWidth=170dp` and `minResizeHeight=40dp`.
  At the smallest height, `DayGrid` shows **1 week** containing:
  - the day number,
  - up to one entry line, or only coloured bars if even that doesn't fit,
  - "+N".

### 4.3 Content fitting

- **FR-L4** Before building the Glance tree, the layout engine measures
  the text for the actual cell size in dp, using `StaticLayout`.
- **FR-L5** Overflow strategy per cell (Q-22, revised in v1.2 and v1.4):
  1. Every entry is **one line**; there is no wrapping.
  2. A title that doesn't fit is **clipped** at the chip edge, at the exact
     pixel, with no "…". The text view is laid out wider than the chip so
     it never wraps or ellipsises, and the chip's bounds cut it. (Glance's
     TextViews add "…" whenever a line limit is set.)
  3. When entries don't fit vertically, replace the rest with "**+N**".
- **FR-L5a** Glance allows at most 10 children per `Row`/`Column`. Each
  cell therefore shows at most **8 chips**, plus its header and "+N".
- **FR-L6** Narrow-cell fallback (Q-23): reduce the inner padding down to
  a minimum. Icons are not dropped automatically (FR-B6).

  The weekday initial is **never** dropped, and titles are never
  abbreviated.

> ⚠️ Data in this section requires human validation.

---

## 5. Functional Requirements

### 5.1 Data (D)

- **FR-D1** Events come from `CalendarContract.Instances` for the selected
  calendars, so recurrences are expanded. Device calendars only (Q-08).
- **FR-D2** Calendar selection uses a global default, which each widget
  can override (Q-15).
- **FR-D4** Declined events (`SELF_ATTENDEE_STATUS = DECLINED`) and
  cancelled events (`STATUS = CANCELED`) are **hidden** (Q-14).
- **FR-D5** De-duplication (Q-13): on the same day, all-day entries that
  come from calendars classified as *holiday calendars* are merged if
  their normalised titles match, or if both calendars mark the date as a
  public holiday. The entry from the calendar that is first in the user's
  calendar order is kept.
  - Classification: the calendar's owner matches
    `*#holiday@group.v.calendar.google.com`, or the user ticks it under
    "Holiday calendars" on the main screen.
  - Titles in different languages (for example "Day of German Unity" and
    "Tag der Deutschen Einheit") cannot be matched by their titles. For
    these, the rule is **one entry per day per holiday calendar group**,
    with the user choosing the preferred holiday calendar (D-4). If nothing is chosen, the holiday calendar with the lowest ID is preferred.
- **FR-D6** Order within a day (Q-27): birthdays, then all-day events,
  then timed events by start time.

### 5.2 Birthdays (B)

- **FR-B1** Sources (Q-09):
  - `ContactsContract.CommonDataKinds.Event` with `TYPE_BIRTHDAY`,
  - the Google "Birthdays" calendar (`addressbook#contacts@group.v.calendar.google.com`).

  These are **de-duplicated per person**. The match is by contact lookup
  key where it is available, otherwise by normalised display name plus
  date. Anniversaries and other contact dates are ignored (Q-12).
- **FR-B2** Display (Q-10a): a monochrome cake icon (Material Icons
  "cake", tinted like the chip text), the name, and the age "(40)".
  The age is shown only if the birth year is known; year-less dates such
  as `--MM-DD` get no age.
- **FR-B3** 29 February is shown on **28 February** in non-leap years
  (Q-10b).
- **FR-B4** Several birthdays on one day are **always aggregated** into
  one chip: "N birthdays" with the cake icon (Q-11). A single birthday shows the name and
  age. The names are visible in the in-app day view and in TalkBack (D-3).
- **FR-B5** `READ_CONTACTS` is requested only when the contacts source is
  enabled. Without it, only the birthday calendar is used.
- **FR-B6** The cake icon is a per-widget setting: always shown or never
  shown. It sits beside the text and narrows it; it is never dropped to
  make room.

### 5.3 Event rendering (E)

- **FR-E1** All-day events are drawn as **filled** chips.
- **FR-E2** Multi-day all-day events are drawn as a **continuous bar**
  spanning the days (Q-26):
  - Each week gets bar lanes above the day entries; bars that don't
    overlap share a lane, and each lane takes one entry line from every day
    of that week. Bars start and end inside the cells and run to the edge
    where the event continues into the previous or next week (square
    corners there).
  - If there are more overlapping bars than entry lines, the rest fall back
    to one chip per day.
  - Glance only has equal weights, so a week is two layers: the day
    backgrounds (which take day taps) and above them headers, lanes and
    entries. Bars and the gaps between them get exact widths.
- **FR-E3** Timed events are drawn as **outlined** chips in the event
  colour (Q-24).
  - The start time in front of the title is a per-widget option ("Event
    times", on by default). The day view shows start–end (Q-25).
  - The time format follows the system 12/24-hour setting.
- **FR-E4** Colour is the event colour if one is set, otherwise the
  calendar colour (Q-33).
- **FR-E5** Past days are **dimmed** (Q-19a). Today's events that have
  already ended are **dimmed** (Q-19b).
- **FR-E6** Users can style each calendar's chips with two settings,
  each chosen from a dropdown:
  - **Fill**, for filled chips (all-day events, birthdays): solid, stripes,
    dots, grid or zigzag. Patterns alternate between the calendar colour and
    transparent (about half each), so the cell shows through. Text on a
    patterned chip uses the cell's text colour.
  - **Outline**, for outlined chips (timed events): solid, dashed or dotted.
    Birthday calendars have no timed events, so they only offer a fill.
  - Contact birthdays belong to no calendar, so "Birthdays from contacts"
    has its own fill setting.
  - Both settings are global (main screen), like the default calendars.
  - An aggregated birthday chip uses the fill of its first birthday.
  - Glance's tint keeps a drawable's own alpha, so dimmed chips (FR-E5) use
    pre-dimmed drawable variants rather than a translucent tint.

### 5.4 Theming (T)

- **FR-T1** Material You dynamic colours are the default. With them off,
  the user picks an accent colour (today border, today's date) from nine
  presets; surfaces use the standard Material palette (Q-31a). The widget
  follows the system light and dark mode (Q-31b).
- **FR-T2** Background (Q-32), as the user chooses:
  - **per-cell backgrounds**; days of the months after today's have none,
    as in the Chronos month widget (v1.5, replaces the W5 fix), or
  - **one background** with an opacity slider from 0 to 100 %.
- **FR-T3** One **global text scale** (Q-34).
- **FR-T4** Today is marked with a thin (1 dp) **cell border** in the
  accent colour (Q-35); the day letter and number are inset so the border
  doesn't touch them. Weekend styling is configurable, off by default (Q-36).
  ISO week numbers are configurable, off by default (Q-21). The first day
  of the week follows the locale (Q-20).
- **FR-T5** The widget honours `system_app_widget_background_radius` and
  its inner padding. No content may overlap the rounded outline (fixes
  W4).
- **FR-T6** Density is configurable: compact (default) or comfortable
  (Q-37).

### 5.5 Interaction (I)

- **FR-I1** Every tap on the widget (a day, an entry, a bar or "+N") opens
  the **day popup** for that day (v1.6; replaces Q-38/Q-39, which sent taps
  to the calendar app).
- **FR-I2** The day popup is a card over the dimmed home screen, like the
  Chronos month widget's popup:
  - it lists every entry of the day, with birthday names and ages (D-3),
    times as start–end (Q-25) and date ranges for multi-day events;
  - **swiping left or right** moves to the next or previous day; each day
    is its own card, sized to its content, so swiping never resizes a
    shared container;
  - tapping an entry opens it in the calendar app (`ACTION_VIEW` on
    `Events.CONTENT_URI/<id>` with `EXTRA_EVENT_BEGIN_TIME`);
  - tapping outside the card or pressing Back closes it;
  - it uses the calendar selection of the widget it was opened from.
- **FR-I3** The calendar app is user-selectable per widget, from the apps
  that open calendar dates; the default is the system handler (Q-41). If
  the chosen app can't open a date or event link, it is launched instead.

### 5.6 Configuration (C)

- **FR-C1** The configuration activity has a **live preview at the real
  widget size** (Q-46). The same Glance composable is rendered in the
  activity.
- **FR-C2** `android:widgetFeatures="reconfigurable|configuration_optional"`.
  Placing a widget applies the default preset immediately, and long-press
  → "Widget settings" reopens the configuration.
- **FR-C3** Configuration is stored per `appWidgetId` in DataStore and
  included in **Android Auto Backup** (Q-47). The restore flow maps old IDs
  to new ones via `ACTION_APPWIDGET_RESTORED`.

### 5.7 Updating (U)

- **FR-U1** Updates are event-driven only (Q-42):
  - a WorkManager job with content-URI triggers on the Calendar and
    Contacts providers (2 s update delay, 5 s maximum delay), which re-arms
    itself after each run. A plain `ContentObserver` would need a running
    process;
  - `ACTION_TIME_SET`, `ACTION_TIMEZONE_CHANGED`, `ACTION_LOCALE_CHANGED`,
    `BOOT_COMPLETED`, and `MY_PACKAGE_REPLACED`. `ACTION_DATE_CHANGED` is
    not delivered to manifest receivers, so the midnight alarm covers it;
  - a windowed alarm (`setWindow`, 60 s window) at local midnight. It needs
    no exact-alarm permission.
- **FR-U2** Dimming of ended events (FR-E5) is refreshed by an inexact
  alarm at the next event end time. The widget never polls.

> ⚠️ Data in this section requires human validation.

---

## 6. Non-Functional Requirements

| ID | Requirement |
|---|---|
| NFR-1 | A widget update takes under 150 ms CPU for 6 × 7 days with 200 instances on the Pixel 9 Pro |
| NFR-2 | **No INTERNET permission** (Q-08 + Q-43, D-5) |
| NFR-3 | Permissions: `READ_CALENDAR` (required), `READ_CONTACTS` (optional, for birthdays) |
| NFR-4 | **Full TalkBack support** (Q-45). Every day cell reads e.g. "Thursday 24 September, today, 3 events: …". Every chip has its own description, and "+N" reads "N more events" |
| NFR-5 | German and English (Q-07). Weekday initials, date and time formats, and plurals ("1 birthday" / "2 Geburtstage") are localised |
| NFR-6 | minSdk 31, targetSdk 36 (Q-05) |
| NFR-7 | With permissions denied or no calendars, the widget shows a tappable "Grant access" state and never crashes |
| NFR-8 | Test target: Pixel 9 Pro with Pixel Launcher (Q-49). Other launchers are best effort |

---

## 7. Technical Approach

| Topic | Decision |
|---|---|
| Language | Kotlin (Q-06) |
| Widget UI | Jetpack Glance (`GlanceAppWidget`, `SizeMode.Responsive`) |
| App UI | Jetpack Compose (Material 3) for the configuration screen and the day/agenda view |
| Persistence | DataStore with a kotlinx-serialization JSON serializer (one `AppConfig` file: global settings plus a map keyed by `appWidgetId`) |
| Background | `GlanceAppWidget.update` triggered from receivers and observers; `AlarmManager` for midnight and event-end refreshes. WorkManager only where Glance needs it |
| Build | Gradle 9.7.1 (Kotlin DSL), AGP 9.4.1 (built-in Kotlin), Kotlin 2.4.20, Glance 1.2.0; single `app` module. The layout engine is a pure-Kotlin package (`app.whatsup.logic`) so it can be unit-tested. compileSdk 36 pins Compose BOM 2026.06.01 (Compose 1.11) and core-ktx 1.18; newer versions require compileSdk 37 |
| Tests | JVM unit tests for the fitting engine, recurrence/range calculation, and birthday de-duplication and age; Glance screenshot tests at 170 × 40, 4 × 2 and 5 × 4 cells |

**RemoteViews and Glance constraints that affect the design:**

- **Rounded outline:** the outlined chips for timed events need a
  drawable background, because there is no stroke modifier in Glance.
  They are implemented with a 9-patch or shape drawable per colour tint
  via `ColorFilter`, which must be verified in a spike (**Spike S1**).
- **Multi-day bars:** these are built as per-cell segments with matching
  vertical slots. There are no true spanning views (**Spike S2**).
- **Nesting limits:** the 6 × 7 grid with chips must stay within the
  RemoteViews view-count and nesting limits. The maximum grid is measured
  in **Spike S3**.

---

## 8. Milestones

1. **M1 — Chronos month widget, done right.**
   - Content: the rolling `DayGrid` with birthdays, the fitting engine,
     de-duplication, dimming, and today's cell border.
   - Configuration: calendar selection, colours, text scale, density,
     background, weekend styling, and week numbers.
   - Sizing: 170 × 40 dp minimum.
   - The configuration screen has a live preview.
   - German and English, and full TalkBack support.
2. **M2 — More blocks.** `MonthGrid`, `Agenda`, `BirthdayStrip`, and
   `NextUp`, plus the in-app day/agenda view.
3. **M3 — Timelines and combinations.** `WeekColumns`, `DayTimeline`,
   and combined presets.
4. **M4 — Release.** Publish on GitHub and F-Droid under Apache-2.0 (D-1).

### Acceptance criteria for M1 (on the Pixel 9 Pro, next to Chronos at the same size)

- **No overflow.** No content crosses the widget outline; titles wrap at
  word boundaries before they are clipped.
- **Times shown.** Timed events show their start time in an outlined chip.
- **No duplicate holidays.** Holidays such as 3 October appear once.
- **Cells and dimming.** Current-month days have a cell background, next-month
  days none, and past days are dimmed.
- **Resizes like Chronos.** The widget resizes down to 170 × 40 dp and
  remains readable (one week).
- **Timely updates.** The widget updates within 5 s of a calendar change
  and within 1 min of midnight.

---

## 9. Resolved Decisions

These conflicts were found in the questionnaire answers (v0.2) and were
resolved as proposed on 24 September 2026.

| ID | Conflict | Decision |
|---|---|---|
| **D-1** | Q-02 (open source on GitHub/F-Droid) vs Q-50 (private / no licence) | Develop privately until M1. Licence: **Apache-2.0**, copyright Henning Gründl (chosen 25 September 2026) |
| **D-2** | Q-03 (in-app day view opened from the widget) vs Q-38/Q-39 (taps open the calendar app) | "+N" and aggregated birthday chips open the in-app day view. Revised in v1.6: every tap opens the day popup (FR-I1) |
| **D-3** | Q-11 (always aggregate birthdays) vs fixing W6 | Aggregate 2 or more birthdays; a single birthday shows name and age. Names appear in the in-app day view and in TalkBack |
| **D-4** | Q-13: holidays in two languages can't be matched by title | Preferred holiday calendar; other holiday calendars are hidden on days where the preferred one has an entry |
| **D-5** | Q-43 (network only for ICS) vs Q-08 (no ICS) | No INTERNET permission |
| **D-6** | Q-01: the "calendar widget" couldn't be sized like the month widget | Every whats-up layout reaches 170 × 40 dp (W12) |

> ⚠️ Data in this section requires human validation.

---

## References

Sources consulted during generation of this document. All data and
figures must be validated against primary sources before external use.

1. External: Chronos Calendar+ Widget, Google Play listing — https://play.google.com/store/apps/details?id=com.candl.chronos
2. Device inspection: `adb shell dumpsys package com.candl.chronos`, `adb shell dumpsys appwidget`, and home-screen screenshots, Pixel 9 Pro, 24 September 2026 — not publicly accessible
3. Questionnaire answers: "Claude - whats-up - Questionnaire Answers v1.md", exported 24 September 2026 — provided by Henning Gründl
4. External: Android Developers — App widgets overview — https://developer.android.com/develop/ui/views/appwidgets/overview
5. External: Android Developers — Jetpack Glance — https://developer.android.com/develop/ui/compose/glance
6. External: Android Developers — CalendarContract — https://developer.android.com/reference/android/provider/CalendarContract
7. External: Android Developers — ContactsContract.CommonDataKinds.Event — https://developer.android.com/reference/android/provider/ContactsContract.CommonDataKinds.Event
8. External: F-Droid Inclusion Policy — https://f-droid.org/docs/Inclusion_Policy/

## Version History

| Version | Date | Author(s) | Changes |
|---------|------|-----------|---------|
| v0.1 | 24 September 2026 | Henning Gründl | Initial draft — generated with Claude AI |
| v0.2 | 24 September 2026 | Henning Gründl | Questionnaire answers included; M1 scoped to the Chronos month widget; 170 × 40 dp minimum size; clarifications C-1…C-6 added — generated with Claude AI |
| v1.0 | 24 September 2026 | Henning Gründl | Clarifications resolved as proposed (D-1…D-6); technical approach aligned with the project skeleton (SizeMode.Exact, WorkManager content triggers, JSON DataStore, 8-chip cell limit) — generated with Claude AI |
| v1.1 | 24 September 2026 | Henning Gründl | Word-boundary wrapping (FR-L5), birthday icon rule (FR-B6), per-calendar fill patterns and outline styles (FR-E6) — generated with Claude AI |
| v1.2 | 24 September 2026 | Henning Gründl | Clip instead of ellipsis (FR-L5); monochrome cake icon with an always/never setting (FR-B2, FR-B6) — generated with Claude AI |
| v1.3 | 25 September 2026 | Henning Gründl | Licence decided: Apache-2.0 (D-1) — generated with Claude AI |
| v1.4 | 25 September 2026 | Henning Gründl | Single-line entries clipped at the pixel edge (FR-L5); multi-day bars (FR-E2); event-time option (FR-E3); accent colour (FR-T1); holiday calendars marked by hand (FR-D5); calendar app picker (FR-I3); day view uses the widget's calendars (FR-I4) — generated with Claude AI |
| v1.5 | 25 September 2026 | Henning Gründl | Next-month days without cell background, thinner today border with inset header (FR-T2, FR-T4) — generated with Claude AI |
| v1.6 | 25 September 2026 | Henning Gründl | Every widget tap opens a swipeable day popup (FR-I1, FR-I2, D-2) — generated with Claude AI |

<sub>Generated with Claude AI — validate before use.</sub>
