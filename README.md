# Compound

A personal Android gym tracker built around one idea: small gains, repeated, add up. It holds my
4-day upper/lower program, lets me tick off each set during a workout (weight and reps are filled in
from last time), logs my body weight once a day with a 7-day moving average chart, and exports or
imports everything as one JSON file for backups, moving data to my tablet, or sending to Claude for analysis.

**Download the newest version:** https://github.com/Alpcont/compound/releases/latest/download/Compound.apk

## Phone setup
- Open the link above on the phone, download `Compound.apk`, and allow "Install unknown apps" for your browser or Files app when asked.
- New versions install over the old one; your data is kept.
- Make a backup now and then from the **Data** tab (Save backup file). Data lives only on the device.
- To see your data on the tablet: Save backup file (or Share → Quick Share) on the phone, then **Import** on the tablet.

## What's in this version (Phases 1–3)
- **Home:** calendar (week strip, or month view) showing each day's plan and status: done (70%+ of sets), partial, missed, rest. Today's workout comes from the weekly plan; missed workouts shift forward, filling rest days first. Tap a day to make it a rest day or **swap it with another day** this week (today or later, nothing logged yet; undo any time). Weekly counter (gym and cardio), "never miss twice" and 4-days-in-a-row warnings, consistency % and a weekly history chart.
- **Workout:** one row per set with kg, reps and a tick box (filled in from last time). A **PB** mark appears on a ticked set that beats your best estimated 1RM or heaviest weight for that exercise; Finish lists the new PBs (old → new).
- **Weight:** daily log, 7-day average, change vs 7 and 30 days ago and since the start, **weekly rate** (% of body weight), chart with 1W / 1M / 3M / 6M / 1Y / All, editable history. A celebration once for each whole kg the 7-day average drops below your starting weight. A calm warning if you lose more than 1% of body weight per week over 2 weeks.
- **Waist and photos:** weekly waist log (cm) with chart and a reminder after 7 days; optional progress photos in a grid, stored only inside the app and never exported.
- **Progress:** pick an exercise to see estimated 1RM per workout (kg × (1 + reps ÷ 30)) with a 7-day average line, and volume per workout. Cardio minutes per day with a 7-day average line.
- **Cardio:** log type (treadmill, bike, elliptical), minutes, effort (RPE 1–10) and optional distance from Home or the calendar.
- **Program:** weekly plan (which workout, cardio or rest on each weekday), edit programs and exercises, reorder them, paste a program as JSON, copy all programs as JSON.
- **Data:** save/share a full JSON backup, import a file or pasted text (checked first, preview, choose which version wins on conflicts, automatic safety copy, undo last import). Programs with the same name on both devices are treated as one, so imports don't duplicate them. **Export CSV for analysis:** weights.csv, sets.csv, cardio.csv and waist.csv into a folder you pick.

## Ideas for later (from the build spec)
- Phase 4: workout notes shown next time, developer notes with "Export for Claude", rest timer, +2.5 kg overload hints, Atomic Habits page, streaks, daily checkboxes, reminders.
- Phase 5: tablet dashboard layout, then Google sync.
- Share the CSV files straight from the share sheet (needs a small file-sharing component).
- Choose a photo's date by hand (now it's the day you add it).
