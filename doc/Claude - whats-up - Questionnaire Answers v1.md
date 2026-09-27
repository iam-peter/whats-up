# whats-up — Questionnaire answers

Exported: 2026-09-24T06:45:37.210Z

Private project document. Generated with Claude AI — validate before use.

## A. Product & scope

**Q-01 Which Chronos limitations bother you most?**
- _(unanswered)_
- Note: actually on month not so much, but rather on the calendar widget. It wasn't able to make it as small as the month widget. We should for now copy what is there in month.

**Q-02 How will the app be distributed?**
- Open source, e.g. GitHub releases / F-Droid

**Q-03 What does the app contain besides the widget?**
- + an in-app day / agenda view opened from the widget

**Q-04 Which form factors / surfaces must be supported?**
- Phone home screen

**Q-05 Minimum Android version?**
- Android 12 / API 31

**Q-06 Technology stack?**
- Kotlin + Jetpack Glance (+ Compose for config)

**Q-07 UI languages?**
- German + English

## B. Data sources & birthdays

**Q-08 Data sources besides the calendars already synced on the device?**
- None — device calendars only (Google, Exchange, CalDAV via DAVx⁵ …)

**Q-09 Where do birthdays come from?**
- Both, de-duplicated per person

**Q-10a What should a birthday entry show?**
- Gift / cake icon
- Name
- Age, e.g. “(40)” — only when the birth year is known

**Q-10b Birthdays on 29 February in non-leap years?**
- Show on 28 February

**Q-11 Several birthdays on the same day?**
- Always aggregate into a count (Chronos behaviour)

**Q-12 Show other contact dates as well?**
- No, birthdays only

**Q-13 Duplicate entries (e.g. same holiday in two holiday calendars, W7)?**
- De-duplicate same-day entries across holiday calendars only

**Q-14 Declined / cancelled events?**
- Hide

**Q-15 Calendar selection scope?**
- Global default + optional per-widget override

## C. Layouts & content fitting

**Q-16 Which layout building blocks are needed (eventually)?**
- Rolling day grid (N weeks from the current week — current Chronos widget)
- Classic month grid (1st to last day of month)
- Agenda list grouped by day
- Week columns with time axis (timeline)
- Single-day timeline
- Birthday strip (upcoming birthdays, next N days)
- “Next up” card (next 1–3 events, big, with countdown)
- Combination in one widget, e.g. grid on top + agenda below

**Q-17 Where does the day grid start?**
- First day of the current week (Chronos behaviour)

**Q-18 Number of weeks in the grid?**
- Derived from widget height, user can override (1–6)

**Q-19a Past days in the grid (W8)?**
- Dimmed

**Q-19b Events of today that are already over?**
- Dim them

**Q-20 First day of the week?**
- Follow locale / system setting

**Q-21 Show ISO week numbers?**
- Configurable (default off)

**Q-22 Overflow strategy when content doesn’t fit a cell (W1, W2)?**
- Wrap titles to 2 lines
- Ellipsis “…” at the end (never clip mid-letter)
- “+N more” indicator for hidden entries

**Q-23 Allowed space-saving fallbacks for narrow cells?**
- Drop the event dot / icon
- Reduce inner padding

**Q-24 How are timed events displayed in the grid?**
- Outlined chip (to separate them from filled all-day chips)

**Q-25 Which time is shown for timed events?**
- Start in grid, start–end in agenda

**Q-26 Multi-day events?**
- Continuous bar spanning the days

**Q-27 Order of entries within a day?**
- Birthdays → all-day → timed (by start)

**Q-28 Behaviour when the widget is resized?**
- Adaptive: blocks switch or hide at size thresholds

**Q-29 How deep should layout customisation go (W11)?**
- Presets + per-block settings

**Q-30 What may appear in the widget header?**
- Nothing — maximise content

## D. Look & theming

**Q-31a Colour source?**
- Both, dynamic as default

**Q-31b Light / dark mode?**
- Follow system

**Q-32 Background style?**
- Both options, user chooses

**Q-33 How are event colours determined?**
- Event colour if set
- Calendar colour as fallback

**Q-34 Text size control?**
- One global scale

**Q-35 Today highlight?**
- Cell border

**Q-36 Weekend styling (W9)?**
- Configurable

**Q-37 Density?**
- Configurable (default compact)

## E. Interaction

**Q-38 Tap on a day (empty area)?**
- Open calendar app at that date

**Q-39 Tap on an event?**
- Open the event in the calendar app

**Q-40 Navigation inside the widget?**
- None — always shows the current range

**Q-41 Which calendar app is opened?**
- User-selectable

## F. Behaviour, non-functional & delivery

**Q-42 Refresh strategy?**
- Event-driven only (calendar change, midnight, time/zone change)

**Q-43 Network access?**
- Only if ICS subscriptions are used

**Q-44 Privacy mode (hide titles, show only coloured bars)?**
- Not needed

**Q-45 Accessibility level?**
- Full TalkBack support (descriptions for every day and event)

**Q-46 Configuration screen?**
- Live preview at the real widget size

**Q-47 Backup of widget configuration?**
- Android Auto Backup

**Q-48 What must the first usable version (M1) contain?**
- Rolling grid + birthdays, fixing W1–W8

**Q-49 Test targets?**
- Pixel 9 Pro / Pixel Launcher

**Q-50 Source code licence?**
- Private / no licence
