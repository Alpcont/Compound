package com.alp.compound

import java.time.LocalDate

// Phase 2: weekly plan with shift-forward rules.
//
// Each week (Mon–Sun) owes the items of the weekly template in order. Walking the week day by day:
// - A day with a logged workout (at least one set ticked) or cardio marked done uses up that item.
// - Otherwise the day gets the next owed item if it's a training day in the template, or if more
//   items are owed than training days are left (so missed work fills rest days first).
// - A past day that got an item but has nothing logged is missed; the item moves on to the next day.
// - Whatever is still owed on Sunday is dropped. Nothing carries into the next week.

enum class DayKind { WORKOUT, CARDIO, REST, NONE }
enum class DayStatus { DONE, PARTIAL, MISSED, REST, TODAY, UPCOMING, NONE }

class DayInfo(
    val date: LocalDate,
    val kind: DayKind,
    val programId: String?,
    val label: String,
    val status: DayStatus,
    val session: Session?,
    val shifted: Boolean
)

class WeekStats(val monday: LocalDate, val done: Int, val partial: Int, val missed: Int)

const val DONE_PCT = 70

fun mondayOf(date: LocalDate): LocalDate = date.minusDays(date.dayOfWeek.value - 1L)

fun defaultWeekPlan(programs: List<Program>): MutableList<String> {
    val ids = programs.map { "p:" + it.id }
    return if (ids.size >= 4) mutableListOf(ids[0], ids[1], "rest", ids[2], ids[3], "cardio", "rest")
    else (0..6).map { ids.getOrNull(it) ?: "rest" }.toMutableList()
}

fun Session.doneCount(): Int = sets.count { it.done }
fun Session.donePct(): Int = if (sets.isEmpty()) 0 else doneCount() * 100 / sets.size

/** The weekly template, with deleted programs treated as rest. */
fun AppData.template(): List<String> = (0..6).map { i ->
    val t = weekPlan.getOrElse(i) { "rest" }
    if (t.startsWith("p:") && programs.none { "p:" + it.id == t }) "rest" else t
}

/** The weekly template for one week, with that week's hand-made day swaps applied in order. */
fun AppData.templateFor(monday: LocalDate): List<String> {
    val t = template().toMutableList()
    for (sw in swapsIn(monday)) { val x = t[sw.a]; t[sw.a] = t[sw.b]; t[sw.b] = x }
    return t
}

fun AppData.tokenName(token: String): String = when {
    token == "cardio" -> "Cardio"
    token.startsWith("p:") -> programs.firstOrNull { "p:" + it.id == token }?.name ?: "Rest"
    else -> "Rest"
}

fun AppData.week(monday: LocalDate, today: LocalDate): List<DayInfo> {
    // Swaps change this week's template first; every rule below then works as before.
    val tpl = templateFor(monday)
    val owed = tpl.filter { it != "rest" }.toMutableList()
    val start = LocalDate.parse(planStart)
    val out = mutableListOf<DayInfo>()
    for (i in 0..6) {
        val date = monday.plusDays(i.toLong())
        val ds = date.toString()
        if (date.isBefore(start)) {
            if (tpl[i] != "rest") owed.remove(tpl[i])
            out.add(DayInfo(date, DayKind.NONE, null, "", DayStatus.NONE, null, false))
            continue
        }
        val cardioHere = ds in cardioDone
        val session = sessions.lastOrNull { it.date == ds && it.doneCount() > 0 }
        if (session != null) {
            owed.remove("p:" + session.programId)
            if (cardioHere) owed.remove("cardio")
            val st = if (session.donePct() >= DONE_PCT) DayStatus.DONE else DayStatus.PARTIAL
            out.add(DayInfo(date, DayKind.WORKOUT, session.programId, session.programName, st, session, false))
            continue
        }
        if (cardioHere) {
            owed.remove("cardio")
            out.add(DayInfo(date, DayKind.CARDIO, null, "Cardio", DayStatus.DONE, null, false))
            continue
        }
        val trainingLeft = (i..6).count { j ->
            tpl[j] != "rest" && monday.plusDays(j.toLong()).toString() !in restDays
        }
        val assigned: String? = when {
            ds in restDays || owed.isEmpty() -> null
            owed.size > trainingLeft -> owed[0]
            tpl[i] != "rest" -> owed[0]
            else -> null
        }
        if (assigned == null) {
            out.add(DayInfo(date, DayKind.REST, null, "Rest", DayStatus.REST, null, false))
            continue
        }
        val kind = if (assigned == "cardio") DayKind.CARDIO else DayKind.WORKOUT
        val pid = if (kind == DayKind.WORKOUT) assigned.removePrefix("p:") else null
        val status = when {
            date.isBefore(today) -> DayStatus.MISSED
            date == today -> DayStatus.TODAY
            else -> DayStatus.UPCOMING
        }
        // A missed item stays owed and moves to the next day; others are used up here.
        if (status != DayStatus.MISSED) owed.removeAt(0)
        out.add(DayInfo(date, kind, pid, tokenName(assigned), status, null, assigned != tpl[i]))
    }
    return out
}

fun AppData.dayInfo(date: LocalDate, today: LocalDate): DayInfo =
    week(mondayOf(date), today).first { it.date == date }

fun AppData.weekStats(monday: LocalDate, today: LocalDate): WeekStats {
    val w = week(monday, today).filter { it.kind == DayKind.WORKOUT }
    return WeekStats(monday, w.count { it.status == DayStatus.DONE },
        w.count { it.status == DayStatus.PARTIAL }, w.count { it.status == DayStatus.MISSED })
}

/** Stats for every week from the plan start up to this week. */
fun AppData.allWeeks(today: LocalDate): List<WeekStats> {
    val list = mutableListOf<WeekStats>()
    var m = mondayOf(LocalDate.parse(planStart))
    while (!m.isAfter(today)) {
        list.add(weekStats(m, today))
        m = m.plusWeeks(1)
    }
    return list
}

/** Done ÷ (done + partial + missed), as a whole percent, or null if nothing was due yet. */
fun consistency(weeks: List<WeekStats>): Int? {
    val due = weeks.sumOf { it.done + it.partial + it.missed }
    return if (due == 0) null else weeks.sumOf { it.done } * 100 / due
}

/** Longest run of workout days in a row (missed days excluded), and whether it reaches today or later. */
fun longestRun(week: List<DayInfo>, today: LocalDate): Pair<Int, Boolean> {
    var best = 0
    var bestCurrent = false
    var run = 0
    var current = false
    for (d in week) {
        if (d.kind == DayKind.WORKOUT && d.status != DayStatus.MISSED) {
            run++
            if (!d.date.isBefore(today)) current = true
            if (run > best || (run == best && current)) { best = run; bestCurrent = current }
        } else {
            run = 0
            current = false
        }
    }
    return best to bestCurrent
}
