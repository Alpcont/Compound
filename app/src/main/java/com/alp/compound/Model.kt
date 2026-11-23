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

class AppData(
    val programs: MutableList<Program>,
    val sessions: MutableList<Session>,
    val weights: MutableList<WeightEntry>,
    val cardioOptions: MutableList<String>
)

fun AppData.toJson(): JSONObject = JSONObject()
    .put("app", "Compound")
    .put("schemaVersion", SCHEMA_VERSION)
    .put("exportedAt", Instant.now().toString())
    .put("programs", JSONArray().apply { programs.forEach { put(it.toJson()) } })
    .put("sessions", JSONArray().apply { sessions.forEach { put(it.toJson()) } })
    .put("weights", JSONArray().apply { weights.forEach { put(it.toJson()) } })
    .put("cardioOptions", JSONArray().apply { cardioOptions.forEach { put(it) } })

// ---------- Reading and checking JSON ----------

private fun objs(o: JSONObject, key: String): List<JSONObject> {
    val a = o.optJSONArray(key) ?: throw ImportError("Missing \"$key\" list.")
    return (0 until a.length()).map { a.getJSONObject(it) }
}

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
        val cardio = root.optJSONArray("cardioOptions")
        return AppData(
            objs(root, "programs").map { parseProgram(it) }.toMutableList(),
            objs(root, "sessions").map { parseSession(it) }.sortedBy { it.date }.toMutableList(),
            objs(root, "weights").map { parseWeight(it) }.sortedBy { it.date }.toMutableList(),
            if (cardio == null) mutableListOf() else (0 until cardio.length()).map { cardio.getString(it) }.toMutableList()
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

/** mode == null only counts; otherwise changes `device` in place. */
fun merge(device: AppData, file: AppData, mode: MergeMode?): MergePlan {
    val s = IntArray(3)
    mergeList(device.programs, file.programs, { it.id }, { it.toJson() }, { it.lastModified }, mode, s)
    mergeList(device.sessions, file.sessions, { it.id }, { it.toJson() }, { it.lastModified }, mode, s)
    // One weight per day, so weights are matched by date.
    mergeList(device.weights, file.weights, { it.date }, { JSONObject().put("kg", it.kg) }, { it.lastModified }, mode, s)
    if (mode != null) {
        file.cardioOptions.filter { it !in device.cardioOptions }.forEach { device.cardioOptions.add(it) }
        device.sessions.sortBy { it.date }
        device.weights.sortBy { it.date }
    }
    val dates = file.weights.map { it.date } + file.sessions.map { it.date }
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
        if (!file.exists()) save()
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
