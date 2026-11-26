package com.alp.compound

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import java.util.UUID

// Version of the backup/data file format. Bump only when the format changes,
// and keep reading older versions.
const val SCHEMA_VERSION = 1

fun newId(): String = UUID.randomUUID().toString()
fun nowMs(): Long = System.currentTimeMillis()
fun fmtKg(v: Double): String = String.format(Locale.US, "%.1f", v)
fun fmtNum(v: Double): String =
    if (v % 1.0 == 0.0) v.toLong().toString()
    else String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')
fun parseNum(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()
fun fmtRest(sec: Int): String = when {
    sec < 60 -> "${sec}s"
    sec % 60 == 0 -> "${sec / 60} min"
    else -> String.format(Locale.US, "%d:%02d", sec / 60, sec % 60)
}

class ImportError(msg: String) : Exception(msg)

class Exercise(
    var id: String,
    var name: String,
    var sets: Int,
    var repMin: Int,
    var repMax: Int,
    var restSec: Int,
    var note: String
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("sets", sets)
        .put("repMin", repMin).put("repMax", repMax)
        .put("restSec", restSec).put("note", note)

    fun target(): String {
        val reps = if (repMin == repMax) "$repMin" else "$repMin–$repMax"
        val parts = mutableListOf("$sets × $reps", "rest ${fmtRest(restSec)}")
        if (note.isNotBlank()) parts.add(note)
        return parts.joinToString(" · ")
    }
}

class Program(
    var id: String,
    var name: String,
    val exercises: MutableList<Exercise>,
    var lastModified: Long
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name)
        .put("exercises", JSONArray().apply { exercises.forEach { put(it.toJson()) } })
        .put("lastModified", lastModified)
}

class SetLog(
    var exerciseId: String,
    var exerciseName: String,
    var setIndex: Int,
    var weight: Double?,
    var reps: Int?,
    var done: Boolean
) {
    fun toJson(): JSONObject {
        val o = JSONObject()
            .put("exerciseId", exerciseId).put("exerciseName", exerciseName)
            .put("setIndex", setIndex).put("done", done)
        weight?.let { o.put("weight", it) }
        reps?.let { o.put("reps", it) }
        return o
    }
}

class Session(
    var id: String,
    var date: String,
    var programId: String,
    var programName: String,
    var finished: Boolean,
    val sets: MutableList<SetLog>,
    var lastModified: Long
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("date", date).put("programId", programId)
        .put("programName", programName).put("finished", finished)
        .put("sets", JSONArray().apply { sets.forEach { put(it.toJson()) } })
        .put("lastModified", lastModified)
}

class WeightEntry(var id: String, var date: String, var kg: Double, var lastModified: Long) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("date", date).put("kg", kg).put("lastModified", lastModified)
}

// Phase 3: cardio sessions (type, minutes, effort RPE 1–10, optional km), waist (one per day),
// day swaps per week (Monday date + two day indexes 0..6) and the weight milestones already celebrated.
class CardioEntry(
    var id: String, var date: String, var type: String, var minutes: Int, var rpe: Int, var km: Double?,
    var lastModified: Long
) {
    fun toJson(): JSONObject {
        val o = JSONObject()
            .put("id", id).put("date", date).put("type", type).put("minutes", minutes)
            .put("rpe", rpe).put("lastModified", lastModified)
        km?.let { o.put("km", it) }
        return o
    }
}

class Swap(val week: String, val a: Int, val b: Int) {
    fun toJson(): JSONObject = JSONObject().put("week", week).put("a", a).put("b", b)
    fun key(): String = "$week:${minOf(a, b)}:${maxOf(a, b)}"
}

class WaistEntry(var id: String, var date: String, var cm: Double, var lastModified: Long) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("date", date).put("cm", cm).put("lastModified", lastModified)
}

class AppData(
    val programs: MutableList<Program>,
    val sessions: MutableList<Session>,
    val weights: MutableList<WeightEntry>,
    val cardioOptions: MutableList<String>,
    // Phase 2: weekly template (Mon..Sun; "p:<programId>", "cardio" or "rest"),
    // days marked cardio done, days made rest by hand, and the day the plan started.
    val weekPlan: MutableList<String> = defaultWeekPlan(programs),
    val cardioDone: MutableList<String> = mutableListOf(),
    val restDays: MutableList<String> = mutableListOf(),
    var planStart: String = LocalDate.now().toString(),
    val cardio: MutableList<CardioEntry> = mutableListOf(),
    val waists: MutableList<WaistEntry> = mutableListOf(),
    val swaps: MutableList<Swap> = mutableListOf(),
    val milestonesShown: MutableList<Int> = mutableListOf()
)

fun AppData.toJson(): JSONObject = JSONObject()
    .put("app", "Compound")
    .put("schemaVersion", SCHEMA_VERSION)
    .put("exportedAt", Instant.now().toString())
    .put("programs", JSONArray().apply { programs.forEach { put(it.toJson()) } })
    .put("sessions", JSONArray().apply { sessions.forEach { put(it.toJson()) } })
    .put("weights", JSONArray().apply { weights.forEach { put(it.toJson()) } })
    .put("cardioOptions", JSONArray().apply { cardioOptions.forEach { put(it) } })
    .put("weekPlan", JSONArray().apply { weekPlan.forEach { put(it) } })
    .put("cardioDone", JSONArray().apply { cardioDone.forEach { put(it) } })
    .put("restDays", JSONArray().apply { restDays.forEach { put(it) } })
    .put("planStart", planStart)
    .put("cardio", JSONArray().apply { cardio.forEach { put(it.toJson()) } })
    .put("waists", JSONArray().apply { waists.forEach { put(it.toJson()) } })
    .put("swaps", JSONArray().apply { swaps.forEach { put(it.toJson()) } })
    .put("milestonesShown", JSONArray().apply { milestonesShown.forEach { put(it) } })

private fun strings(o: JSONObject, key: String): MutableList<String> {
    val a = o.optJSONArray(key) ?: return mutableListOf()
    return (0 until a.length()).map { a.getString(it) }.toMutableList()
}

// ---------- Reading and checking JSON ----------

private fun objs(o: JSONObject, key: String): List<JSONObject> {
    val a = o.optJSONArray(key) ?: throw ImportError("Missing \"$key\" list.")
    return (0 until a.length()).map { a.getJSONObject(it) }
}

// Lists added in later versions: missing in older files means empty.
private fun optObjs(o: JSONObject, key: String): List<JSONObject> =
    if (o.has(key)) objs(o, key) else emptyList()

private fun idOrNew(o: JSONObject): String = o.optString("id", "").ifBlank { newId() }

fun parseExercise(o: JSONObject): Exercise {
    val name = o.optString("name", "").trim()
    if (name.isEmpty()) throw ImportError("An exercise has no name.")
    val sets = o.getInt("sets")
    val min = o.getInt("repMin")
    val max = o.optInt("repMax", min)
    val rest = o.optInt("restSec", 90)
    if (sets !in 1..20) throw ImportError("$name: sets must be 1–20.")
    if (min !in 1..100 || max !in min..100) throw ImportError("$name: rep range is not valid.")
    if (rest !in 0..900) throw ImportError("$name: rest must be 0–900 seconds.")
    return Exercise(idOrNew(o), name, sets, min, max, rest, o.optString("note", ""))
}

fun parseProgram(o: JSONObject): Program {
    val name = o.optString("name", "").trim()
    if (name.isEmpty()) throw ImportError("A program has no name.")
    return Program(idOrNew(o), name, objs(o, "exercises").map { parseExercise(it) }.toMutableList(),
        o.optLong("lastModified", 0L))
}

private fun checkDate(s: String): String {
    try { LocalDate.parse(s) } catch (e: Exception) { throw ImportError("\"$s\" is not a valid date.") }
    return s
}

private fun parseSession(o: JSONObject): Session {
    val sets = objs(o, "sets").map {
        SetLog(
            it.getString("exerciseId"), it.getString("exerciseName"), it.getInt("setIndex"),
            if (it.has("weight") && !it.isNull("weight")) it.getDouble("weight") else null,
            if (it.has("reps") && !it.isNull("reps")) it.getInt("reps") else null,
            it.optBoolean("done", false)
        )
    }.toMutableList()
    return Session(idOrNew(o), checkDate(o.getString("date")), o.optString("programId", ""),
        o.optString("programName", "Workout"), o.optBoolean("finished", false), sets,
        o.optLong("lastModified", 0L))
}

private fun parseWeight(o: JSONObject): WeightEntry {
    val date = checkDate(o.getString("date"))
    val kg = o.getDouble("kg")
    if (kg < 20.0 || kg > 400.0) throw ImportError("Weight $kg kg on $date is out of range.")
    return WeightEntry(idOrNew(o), date, kg, o.optLong("lastModified", 0L))
}

private fun parseCardio(o: JSONObject): CardioEntry {
    val date = checkDate(o.getString("date"))
    val min = o.getInt("minutes")
    if (min !in 1..600) throw ImportError("Cardio on $date: minutes must be 1–600.")
    // Build 5 of 0.3.0 stored "machine" and an easy/moderate/hard "effort"; read those too.
    val oldEffort = when (o.optString("effort", "").lowercase(Locale.ENGLISH)) { "easy" -> 3; "hard" -> 8; else -> 5 }
    val rpe = o.optInt("rpe", oldEffort).coerceIn(1, 10)
    val km = if (o.has("km") && !o.isNull("km")) o.getDouble("km") else null
    if (km != null && (km < 0.0 || km > 300.0)) throw ImportError("Cardio on $date: distance is out of range.")
    return CardioEntry(idOrNew(o), date, o.optString("type", o.optString("machine", "Cardio")), min, rpe, km,
        o.optLong("lastModified", 0L))
}

private fun parseSwap(o: JSONObject): Swap {
    val a = o.getInt("a")
    val b = o.getInt("b")
    if (a !in 0..6 || b !in 0..6) throw ImportError("A day swap has a day outside Monday–Sunday.")
    return Swap(checkDate(o.getString("week")), a, b)
}

private fun parseWaist(o: JSONObject): WaistEntry {
    val date = checkDate(o.getString("date"))
    val cm = o.getDouble("cm")
    if (cm < 40.0 || cm > 250.0) throw ImportError("Waist $cm cm on $date is out of range.")
    return WaistEntry(idOrNew(o), date, cm, o.optLong("lastModified", 0L))
}

/** Reads a full data/backup file. Throws ImportError with a plain message if anything is wrong. */
fun parseData(text: String): AppData {
    // Pasted text may carry extra words around the JSON (e.g. a share title), so read from the first { to the last }.
    val start = text.indexOf('{')
    val end = text.lastIndexOf('}')
    if (start < 0 || end <= start) throw ImportError("No backup data found in this text.")
    val root = try { JSONObject(text.substring(start, end + 1)) } catch (e: Exception) {
        throw ImportError("This isn't valid JSON, so it can't be a Compound backup.")
    }
    if (!root.has("schemaVersion")) throw ImportError("This doesn't look like a Compound backup (no schemaVersion).")
    val v = root.optInt("schemaVersion", -1)
    if (v < 1) throw ImportError("Unknown backup format version.")
    if (v > SCHEMA_VERSION) throw ImportError("This backup was made by a newer version of Compound. Update the app first.")
    try {
        val programs = objs(root, "programs").map { parseProgram(it) }.toMutableList()
        val sessions = objs(root, "sessions").map { parseSession(it) }.sortedBy { it.date }.toMutableList()
        // Files from Phase 1 have no weekly plan yet: build the default one from the programs.
        val plan = strings(root, "weekPlan").let { if (it.size == 7) it else defaultWeekPlan(programs) }
        val today = LocalDate.now().toString()
        val start = root.optString("planStart", "").ifBlank {
            listOfNotNull(sessions.minOfOrNull { it.date }, today).min()
        }
        return AppData(
            programs,
            sessions,
            objs(root, "weights").map { parseWeight(it) }.sortedBy { it.date }.toMutableList(),
            strings(root, "cardioOptions"),
            plan,
            strings(root, "cardioDone").onEach { checkDate(it) },
            strings(root, "restDays").onEach { checkDate(it) },
            checkDate(start),
            optObjs(root, "cardio").map { parseCardio(it) }.sortedBy { it.date }.toMutableList(),
            optObjs(root, "waists").map { parseWaist(it) }.sortedBy { it.date }.toMutableList(),
            optObjs(root, "swaps").map { parseSwap(it) }.toMutableList(),
            root.optJSONArray("milestonesShown")?.let { a -> (0 until a.length()).map { a.getInt(it) } }
                ?.toMutableList() ?: mutableListOf()
        )
    } catch (e: ImportError) {
        throw e
    } catch (e: Exception) {
        throw ImportError("The file has a broken entry: ${e.message}")
    }
}

/** Accepts one program object, a list of programs, or {"programs": [...]} — handy for AI-made programs. */
fun parseProgramsLoose(text: String): List<Program> {
    // Skip any words before the JSON starts (and after it ends).
    val first = listOf(text.indexOf('{'), text.indexOf('[')).filter { it >= 0 }.minOrNull()
        ?: throw ImportError("No program JSON found in this text.")
    val last = maxOf(text.lastIndexOf('}'), text.lastIndexOf(']'))
    if (last <= first) throw ImportError("No program JSON found in this text.")
    val t = text.substring(first, last + 1)
    try {
        val list: List<JSONObject> = when {
            t.startsWith("[") -> JSONArray(t).let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
            else -> JSONObject(t).let { o -> if (o.has("programs")) objs(o, "programs") else listOf(o) }
        }
        if (list.isEmpty()) throw ImportError("No programs found.")
        return list.map { parseProgram(it).also { p -> p.lastModified = nowMs() } }
    } catch (e: ImportError) {
        throw e
    } catch (e: Exception) {
        throw ImportError("Couldn't read this JSON: ${e.message}")
    }
}

// ---------- Merging an imported file ----------

enum class MergeMode { NEWER, DEVICE, FILE }

class MergePlan(val added: Int, val changed: Int, val same: Int, val firstDate: String?, val lastDate: String?)

private fun content(o: JSONObject): String { o.remove("lastModified"); return o.toString() }

private fun <T> mergeList(
    device: MutableList<T>, file: List<T>, key: (T) -> String, json: (T) -> JSONObject,
    modified: (T) -> Long, mode: MergeMode?, stats: IntArray
) {
    for (f in file) {
        val i = device.indexOfFirst { key(it) == key(f) }
        if (i < 0) {
            stats[0]++
            if (mode != null) device.add(f)
        } else if (content(json(device[i])) != content(json(f))) {
            stats[1]++
            val useFile = when (mode) {
                null -> false
                MergeMode.FILE -> true
                MergeMode.DEVICE -> false
                MergeMode.NEWER -> modified(f) > modified(device[i])
            }
            if (useFile) device[i] = f
        } else stats[2]++
    }
}

/** Points the file's exercise ids (in its workouts) at the kept exercises, matched by name. */
private fun relink(data: AppData, from: Program, to: Program) {
    val exMap = mutableMapOf<String, String>()
    for (e in from.exercises) {
        val keep = to.exercises.firstOrNull { it.name.equals(e.name, ignoreCase = true) } ?: continue
        exMap[e.id] = keep.id
    }
    for (s in data.sessions.filter { it.programId == from.id }) {
        s.programId = to.id
        s.sets.forEach { st -> exMap[st.exerciseId]?.let { st.exerciseId = it } }
    }
    data.weekPlan.replaceAll { if (it == "p:" + from.id) "p:" + to.id else it }
}

/**
 * Each device made its own copy of the starting programs, with different ids, so an import used
 * to add a second "Upper A" etc. A file program with no id match but the same name as a device
 * program is treated as that program: its id (and its workouts and plan days) are moved over.
 */
private fun linkSamePrograms(device: AppData, file: AppData) {
    for (fp in file.programs) {
        if (device.programs.any { it.id == fp.id }) continue
        val dp = device.programs.firstOrNull { d ->
            d.name.equals(fp.name, ignoreCase = true) && file.programs.none { it.id == d.id }
        } ?: continue
        relink(file, fp, dp)
        for (e in fp.exercises) {
            dp.exercises.firstOrNull { it.name.equals(e.name, ignoreCase = true) }?.let { e.id = it.id }
        }
        fp.id = dp.id
    }
}

private fun sameExercises(a: Program, b: Program): Boolean =
    a.exercises.size == b.exercises.size && a.exercises.zip(b.exercises).all { (x, y) ->
        x.name.equals(y.name, ignoreCase = true) && x.sets == y.sets && x.repMin == y.repMin &&
            x.repMax == y.repMax && x.restSec == y.restSec && x.note == y.note
    }

/** Removes duplicate programs left by earlier imports (same name and exactly the same exercises). */
fun AppData.dedupePrograms(): Boolean {
    var changed = false
    var i = 0
    while (i < programs.size) {
        val keep = programs[i]
        val dups = programs.drop(i + 1).filter { it.name.equals(keep.name, ignoreCase = true) && sameExercises(it, keep) }
        for (dup in dups) {
            relink(this, dup, keep)
            programs.remove(dup)
            changed = true
        }
        i++
    }
    return changed
}

/** mode == null only counts; otherwise changes `device` in place. */
fun merge(device: AppData, file: AppData, mode: MergeMode?): MergePlan {
    val s = IntArray(3)
    linkSamePrograms(device, file)
    mergeList(device.programs, file.programs, { it.id }, { it.toJson() }, { it.lastModified }, mode, s)
    mergeList(device.sessions, file.sessions, { it.id }, { it.toJson() }, { it.lastModified }, mode, s)
    // One weight per day, so weights are matched by date.
    mergeList(device.weights, file.weights, { it.date }, { JSONObject().put("kg", it.kg) }, { it.lastModified }, mode, s)
    mergeList(device.cardio, file.cardio, { it.id }, { it.toJson() }, { it.lastModified }, mode, s)
    mergeList(device.waists, file.waists, { it.date }, { JSONObject().put("cm", it.cm) }, { it.lastModified }, mode, s)
    if (mode != null) {
        file.cardioOptions.filter { it !in device.cardioOptions }.forEach { device.cardioOptions.add(it) }
        file.cardioDone.filter { it !in device.cardioDone }.forEach { device.cardioDone.add(it) }
        file.restDays.filter { it !in device.restDays }.forEach { device.restDays.add(it) }
        if (file.planStart < device.planStart) device.planStart = file.planStart
        if (mode == MergeMode.FILE) { device.weekPlan.clear(); device.weekPlan.addAll(file.weekPlan) }
        file.swaps.filter { f -> device.swaps.none { it.key() == f.key() } }.forEach { device.swaps.add(it) }
        file.milestonesShown.filter { it !in device.milestonesShown }.forEach { device.milestonesShown.add(it) }
        device.sessions.sortBy { it.date }
        device.weights.sortBy { it.date }
        device.cardio.sortBy { it.date }
        device.waists.sortBy { it.date }
    }
    val dates = file.weights.map { it.date } + file.sessions.map { it.date } + file.cardio.map { it.date }
    return MergePlan(s[0], s[1], s[2], dates.minOrNull(), dates.maxOrNull())
}

// ---------- Weight helpers ----------

fun AppData.weightOn(date: String): WeightEntry? = weights.firstOrNull { it.date == date }

fun AppData.setWeight(date: String, kg: Double) {
    val w = weightOn(date)
    if (w != null) { w.kg = kg; w.lastModified = nowMs() }
    else weights.add(WeightEntry(newId(), date, kg, nowMs()))
    weights.sortBy { it.date }
}

/** Simple 7-day moving average: mean of the entries logged from date-6 to date. */
fun AppData.avg7(date: LocalDate): Double? {
    val from = date.minusDays(6)
    val vals = weights.filter {
        val d = LocalDate.parse(it.date)
        !d.isBefore(from) && !d.isAfter(date)
    }.map { it.kg }
    return if (vals.isEmpty()) null else vals.average()
}

/** Change of the 7-day average over the last 7 days, as % of body weight (negative = losing). */
fun AppData.weeklyRatePct(): Double? {
    val last = weights.maxOfOrNull { it.date }?.let { LocalDate.parse(it) } ?: return null
    val now = avg7(last) ?: return null
    val before = avg7(last.minusDays(7)) ?: return null
    return (now - before) / before * 100
}

/** Average loss per week over the last 2 weeks, as % of body weight (positive = losing). */
fun AppData.lossPerWeekPct2w(): Double? {
    val last = weights.maxOfOrNull { it.date }?.let { LocalDate.parse(it) } ?: return null
    val now = avg7(last) ?: return null
    val before = avg7(last.minusDays(14)) ?: return null
    return (before - now) / before * 100 / 2
}

const val SPEED_LIMIT_PCT = 1.0

fun AppData.startWeight(): Double? = weights.minByOrNull { it.date }?.kg

fun AppData.currentAvg(): Double? = weights.maxOfOrNull { it.date }?.let { avg7(LocalDate.parse(it)) }

/** Whole kg values below the starting weight that the 7-day average has reached (99, 98, …). */
fun AppData.crossedMilestones(): List<Int> {
    val start = startWeight() ?: return emptyList()
    val lowest = weights.mapNotNull { avg7(LocalDate.parse(it.date)) }.minOrNull() ?: return emptyList()
    val hi = Math.ceil(start).toInt() - 1
    val lo = Math.ceil(lowest - 1e-9).toInt()
    return if (lo > hi) emptyList() else (hi downTo lo).toList()
}

// ---------- Waist and cardio helpers ----------

fun AppData.waistOn(date: String): WaistEntry? = waists.firstOrNull { it.date == date }

fun AppData.setWaist(date: String, cm: Double) {
    val w = waistOn(date)
    if (w != null) { w.cm = cm; w.lastModified = nowMs() }
    else waists.add(WaistEntry(newId(), date, cm, nowMs()))
    waists.sortBy { it.date }
}

fun AppData.cardioOn(date: String): List<CardioEntry> = cardio.filter { it.date == date }

fun AppData.logCardio(date: String, type: String, minutes: Int, rpe: Int, km: Double?) {
    cardio.add(CardioEntry(newId(), date, type, minutes, rpe, km, nowMs()))
    cardio.sortBy { it.date }
    if (date !in cardioDone) cardioDone.add(date)
}

fun AppData.clearCardio(date: String) {
    cardioDone.remove(date)
    cardio.removeAll { it.date == date }
}

fun CardioEntry.text(): String =
    "$type · $minutes min · RPE $rpe" + (km?.let { " · ${fmtNum(it)} km" } ?: "")

/** Days marked cardio done before the cardio log existed: shown as entries without minutes. */
fun AppData.cardioWithoutDetails(): List<String> = cardioDone.filter { d -> cardio.none { it.date == d } }.sorted()

// ---------- Day swaps ----------

fun AppData.swapsIn(monday: LocalDate): List<Swap> = swaps.filter { it.week == monday.toString() }

fun AppData.isSwapped(date: LocalDate): Boolean {
    val i = date.dayOfWeek.value - 1
    return swapsIn(mondayOf(date)).any { it.a == i || it.b == i }
}

fun AppData.addSwap(a: LocalDate, b: LocalDate) {
    swaps.add(Swap(mondayOf(a).toString(), a.dayOfWeek.value - 1, b.dayOfWeek.value - 1))
}

fun AppData.undoSwap(date: LocalDate) {
    val i = date.dayOfWeek.value - 1
    val w = mondayOf(date).toString()
    swaps.removeAll { it.week == w && (it.a == i || it.b == i) }
}

// ---------- Strength progress and personal bests ----------

/** Epley formula: estimated one-rep max from a set. */
fun est1rm(kg: Double, reps: Int): Double = kg * (1 + reps / 30.0)

/** A ticked set with weight and reps — the only kind that counts for progress and PBs. */
fun SetLog.counts(): Boolean = done && (weight ?: 0.0) > 0 && (reps ?: 0) > 0

fun SetLog.e1rm(): Double = est1rm(weight ?: 0.0, reps ?: 0)

/** Exercises that have at least one counted set, A–Z. */
fun AppData.loggedExercises(): List<String> =
    sessions.flatMap { s -> s.sets.filter { it.counts() }.map { it.exerciseName } }
        .distinctBy { it.lowercase(Locale.ENGLISH) }.sortedBy { it.lowercase(Locale.ENGLISH) }

private fun Session.setsOf(name: String): List<SetLog> =
    sets.filter { it.counts() && it.exerciseName.equals(name, ignoreCase = true) }

/** Best estimated 1RM per session for one exercise, oldest first. */
fun AppData.oneRmPerSession(name: String): List<Pair<LocalDate, Double>> =
    sessions.mapNotNull { s -> s.setsOf(name).maxOfOrNull { it.e1rm() }?.let { LocalDate.parse(s.date) to it } }
        .sortedBy { it.first }

/** Volume (kg × reps of ticked sets) per session for one exercise, oldest first. */
fun AppData.volumePerSession(name: String): List<Pair<LocalDate, Double>> =
    sessions.mapNotNull { s ->
        val l = s.setsOf(name)
        if (l.isEmpty()) null else LocalDate.parse(s.date) to l.sumOf { it.weight!! * it.reps!! }
    }.sortedBy { it.first }

/** For each point: the mean of the points in the 7 days up to it. */
fun rolling7(points: List<Pair<LocalDate, Double>>): List<Pair<LocalDate, Double>> =
    points.map { (d, _) ->
        d to points.filter { !it.first.isAfter(d) && it.first.isAfter(d.minusDays(7)) }.map { it.second }.average()
    }

/** Cardio minutes per day (days with logged minutes only). */
fun AppData.cardioMinutesPerDay(): List<Pair<LocalDate, Double>> =
    cardio.groupBy { it.date }.map { (d, l) -> LocalDate.parse(d) to l.sumOf { it.minutes }.toDouble() }
        .sortedBy { it.first }

/** 7-day average of cardio minutes per day (total of the last 7 days ÷ 7), for every day in the range. */
fun cardioAvg7(perDay: List<Pair<LocalDate, Double>>): List<Pair<LocalDate, Double>> {
    if (perDay.isEmpty()) return emptyList()
    val out = mutableListOf<Pair<LocalDate, Double>>()
    var d = perDay.first().first
    val end = maxOf(perDay.last().first, LocalDate.now())
    while (!d.isAfter(end)) {
        val day = d
        out.add(day to perDay.filter { !it.first.isAfter(day) && it.first.isAfter(day.minusDays(7)) }.sumOf { it.second } / 7.0)
        d = d.plusDays(1)
    }
    return out
}

class Record(val e1rm: Double, val heaviest: Double)

/** Best e1RM and heaviest weight for an exercise from all other sessions up to this one's date. */
fun AppData.recordBefore(name: String, s: Session): Record? {
    val l = sessions.filter { it !== s && it.date <= s.date }.flatMap { it.setsOf(name) }
    if (l.isEmpty()) return null
    return Record(l.maxOf { it.e1rm() }, l.maxOf { it.weight!! })
}

fun SetLog.isPb(rec: Record?): Boolean =
    rec != null && counts() && (e1rm() > rec.e1rm + 1e-9 || weight!! > rec.heaviest + 1e-9)

/** New PBs in a session, as plain lines: "Leg Press: 1RM 120.0 → 126.7 kg". */
fun AppData.newPbs(s: Session): List<String> {
    val out = mutableListOf<String>()
    for (name in s.sets.map { it.exerciseName }.distinctBy { it.lowercase(Locale.ENGLISH) }) {
        val rec = recordBefore(name, s) ?: continue
        val l = s.setsOf(name)
        if (l.isEmpty()) continue
        val best = l.maxOf { it.e1rm() }
        val heavy = l.maxOf { it.weight!! }
        val parts = mutableListOf<String>()
        if (best > rec.e1rm + 1e-9) parts.add("est. 1RM ${fmtKg(rec.e1rm)} → ${fmtKg(best)} kg")
        if (heavy > rec.heaviest + 1e-9) parts.add("heaviest ${fmtNum(rec.heaviest)} → ${fmtNum(heavy)} kg")
        if (parts.isNotEmpty()) out.add("$name: ${parts.joinToString(", ")}")
    }
    return out
}

// ---------- CSV export (one file per table) ----------

private fun csv(v: Any?): String {
    val s = v?.toString() ?: ""
    return if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
}

private fun table(header: String, rows: List<List<Any?>>): String {
    val sb = StringBuilder(header).append('\n')
    rows.forEach { r -> sb.append(r.joinToString(",") { csv(it) }).append('\n') }
    return sb.toString()
}

/** File name → content. Photos are never part of any export. */
fun AppData.csvFiles(): List<Pair<String, String>> = listOf(
    "weights.csv" to table("date,kg,avg7_kg", weights.sortedBy { it.date }.map {
        listOf(it.date, fmtKg(it.kg), avg7(LocalDate.parse(it.date))?.let { a -> fmtKg(a) })
    }),
    "sets.csv" to table("date,program,exercise,set,kg,reps,done", sessions.sortedBy { it.date }.flatMap { s ->
        s.sets.map { listOf(s.date, s.programName, it.exerciseName, it.setIndex + 1, it.weight?.let { w -> fmtNum(w) }, it.reps, if (it.done) "yes" else "no") }
    }),
    "cardio.csv" to table("date,type,minutes,rpe,distance_km",
        (cardio.map { listOf(it.date, it.type, it.minutes, it.rpe, it.km?.let { k -> fmtNum(k) }) } +
            cardioWithoutDetails().map { listOf(it, "Cardio", null, null, null) }).sortedBy { it[0] as String }),
    "waist.csv" to table("date,cm", waists.sortedBy { it.date }.map { listOf(it.date, fmtKg(it.cm)) })
)

// ---------- Workout helpers ----------

fun AppData.sessionOn(date: String): Session? = sessions.lastOrNull { it.date == date }

/** Programs rotate in list order: the one after the last finished workout. */
fun AppData.nextProgram(): Program? {
    if (programs.isEmpty()) return null
    val last = sessions.filter { it.finished }
        .maxWithOrNull(compareBy<Session>({ it.date }, { it.lastModified })) ?: return programs[0]
    val i = programs.indexOfFirst { it.id == last.programId }
    return if (i < 0) programs[0] else programs[(i + 1) % programs.size]
}

/** The sets logged the last time an exercise with this name was done (any program). */
fun AppData.lastSetsFor(name: String): List<SetLog> {
    val ordered = sessions.sortedWith(compareByDescending<Session> { it.date }.thenByDescending { it.lastModified })
    for (s in ordered) {
        val l = s.sets.filter { it.exerciseName.equals(name, ignoreCase = true) && (it.weight != null || it.reps != null) }
        if (l.isNotEmpty()) return l
    }
    return emptyList()
}

fun AppData.startSession(p: Program, date: String): Session {
    val sets = mutableListOf<SetLog>()
    for (e in p.exercises) {
        val prev = lastSetsFor(e.name)
        for (i in 0 until e.sets) {
            val pr = prev.firstOrNull { it.setIndex == i } ?: prev.lastOrNull()
            sets.add(SetLog(e.id, e.name, i, pr?.weight, pr?.reps, false))
        }
    }
    val s = Session(newId(), date, p.id, p.name, false, sets, nowMs())
    sessions.add(s)
    sessions.sortBy { it.date }
    return s
}

// ---------- Starting data: Alp's program (Nov 2026) ----------

private fun ex(name: String, sets: Int, min: Int, max: Int, rest: Int, note: String = "") =
    Exercise(newId(), name, sets, min, max, rest, note)

private fun prog(name: String, vararg e: Exercise) = Program(newId(), name, e.toMutableList(), nowMs())

fun defaultData(): AppData = AppData(
    mutableListOf(
        prog("Upper A",
            ex("Chest Press (C1)", 3, 8, 12, 150),
            ex("Lat Pulldown (B1)", 3, 8, 12, 150),
            ex("DB Incline Press", 2, 8, 12, 150),
            ex("Machine Row (B2)", 3, 10, 12, 90),
            ex("Lateral Raise (D2)", 2, 12, 15, 60),
            ex("Triceps Extension (F1)", 2, 10, 15, 60),
            ex("Biceps Curl (E1)", 2, 10, 15, 60)),
        prog("Lower A",
            ex("Leg Press (A1)", 3, 8, 12, 150),
            ex("Romanian Deadlift", 3, 8, 10, 150, "fixed barbell"),
            ex("Bulgarian Split Squat", 2, 8, 10, 90, "per leg"),
            ex("Leg Curl (A2)", 3, 10, 15, 75),
            ex("Leg Extension (A3)", 2, 12, 15, 75),
            ex("Calf Raise", 3, 12, 15, 60, "on the leg press"),
            ex("Hanging Knee Raise", 3, 10, 15, 60, "captain's chair")),
        prog("Upper B",
            ex("Lat Pulldown (B1)", 3, 8, 12, 150),
            ex("Chest Press (C1)", 3, 8, 12, 150),
            ex("Machine Row (B2)", 3, 10, 12, 90),
            ex("Shoulder Press (D1)", 2, 8, 12, 90),
            ex("Lateral Raise (D2)", 2, 12, 15, 60),
            ex("Rear Delt Fly (B2)", 2, 12, 15, 60),
            ex("Biceps Curl (E1)", 2, 10, 15, 60),
            ex("Triceps Extension (F1)", 2, 10, 15, 60)),
        prog("Lower B",
            ex("DB Goblet Squat", 3, 8, 12, 150),
            ex("Leg Curl (A2)", 3, 10, 12, 75),
            ex("Glute Machine (A6)", 2, 10, 15, 75),
            ex("Bulgarian Split Squat", 2, 8, 10, 90, "per leg"),
            ex("Back Extension (G3)", 2, 12, 15, 75),
            ex("Hip Abduction (A5)", 2, 12, 15, 60),
            ex("Calf Raise", 3, 12, 15, 60),
            ex("Ab Crunch (G1)", 2, 12, 15, 60))
    ),
    mutableListOf(),
    mutableListOf(),
    mutableListOf("Treadmill", "Bike", "Elliptical")
)

// ---------- Storage: one JSON file in the app's private folder ----------

class Store(context: Context) {
    private val dir: File = context.filesDir
    private val file = File(dir, "data.json")
    private val backups = File(dir, "backups")
    var data: AppData

    init {
        data = if (file.exists()) {
            try {
                parseData(file.readText())
            } catch (e: Exception) {
                // Never delete unreadable data: keep it aside and start fresh.
                file.renameTo(File(dir, "data-unreadable-${nowMs()}.json"))
                defaultData()
            }
        } else defaultData()
        if (!file.exists() || data.dedupePrograms()) save()
    }

    fun json(): String = data.toJson().toString()

    fun save() {
        val tmp = File(dir, "data.json.tmp")
        tmp.writeText(json())
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }

    /** Saved before every import; the newest 5 are kept. */
    fun safetyBackup() {
        backups.mkdirs()
        File(backups, "pre-import-${nowMs()}.json").writeText(json())
        backups.listFiles()?.sortedByDescending { it.name }?.drop(5)?.forEach { it.delete() }
    }

    fun latestBackup(): File? = backups.listFiles()?.maxByOrNull { it.name }

    fun restore(f: File) {
        data = parseData(f.readText())
        save()
        f.delete()
    }
}
