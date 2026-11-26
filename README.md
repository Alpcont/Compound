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
- **Home:** calendar (week strip, or month view) showing each day's plan and status: done (70%+ of sets), partial, missed, rest. Today's workout comes from the weekly plan; missed workouts shift forward, filling rest days first. Weekly counter (gym and cardio), "never miss twice" and 4-days-in-a-row warnings, consistency % and a weekly history chart. One row per set with kg, reps and a tick box.
- **Weight:** daily log, 7-day average, change vs 7 and 30 days ago and since the start (kg and %), chart with 1M / 3M / 6M / All, editable history. 1 kg milestones (by 7-day average), a "slow down" warning if the average drops more than 1 kg in a week, and a waist log (cm).
- **Progress:** estimated 1RM per exercise (best ticked set each workout, kg × (1 + reps ÷ 30)), and weekly bars for volume, cardio minutes and gym days (last 12 weeks).
- **Cardio:** log machine (treadmill, bike, elliptical), minutes and effort (easy / moderate / hard) from Home or the calendar.
- **Program:** weekly plan (which workout, cardio or rest on each weekday), edit programs and exercises, reorder them, paste a program as JSON, copy all programs as JSON.
- **Data:** save/share a full JSON backup, import a file or pasted text (checked first, preview, choose which version wins on conflicts, automatic safety copy, undo last import). Programs with the same name on both devices are treated as one program, so imports no longer duplicate them. CSV export for spreadsheets.

## Ideas for later (from the build spec)
- Phase 4: workout notes shown next time, developer notes with "Export for Claude", rest timer, +2.5 kg overload hints, Atomic Habits page, streaks, daily checkboxes, reminders.
- Phase 5: tablet dashboard layout, then Google sync.
