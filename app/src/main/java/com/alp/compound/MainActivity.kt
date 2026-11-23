package com.alp.compound

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
private const val REQ_EXPORT = 1
private const val REQ_IMPORT = 2

class MainActivity : Activity() {
    private lateinit var store: Store
    private lateinit var content: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var tabBar: LinearLayout

    private var tab = 0
    private var editingProgramId: String? = null
    private var chartRange = 1

    private val tabNames = listOf("Today", "Weight", "Program", "Data")
    private val ranges = listOf("1M" to 30, "3M" to 90, "6M" to 180, "All" to 0)
    private val today: String get() = LocalDate.now().toString()
    private val d: AppData get() = store.data

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        store = Store(this)
        content = findViewById(R.id.content)
        scroll = findViewById(R.id.scroll)
        tabBar = findViewById(R.id.tabs)
        tab = savedInstanceState?.getInt("tab") ?: 0
        // Tablet / landscape: keep the content a readable width, centered.
        if (resources.configuration.screenWidthDp > 760) {
            content.layoutParams = FrameLayout.LayoutParams(dp(720), WRAP, Gravity.CENTER_HORIZONTAL)
        }
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("tab", tab)
    }

    private fun render() {
        buildTabs()
        content.removeAllViews()
        when (tab) {
            0 -> renderToday()
            1 -> renderWeight()
            2 -> renderProgram()
            else -> renderData()
        }
    }

    private fun go(t: Int) {
        tab = t
        editingProgramId = null
        hideKeyboard()
        render()
        scroll.scrollTo(0, 0)
    }

    private fun buildTabs() {
        tabBar.removeAllViews()
        tabNames.forEachIndexed { i, name ->
            val t = TextView(this)
            t.text = name
            t.gravity = Gravity.CENTER
            t.textSize = 14f
            t.setPadding(0, dp(14), 0, dp(14))
            t.setTextColor(c(if (i == tab) R.color.brand else R.color.text_secondary))
            if (i == tab) t.setTypeface(t.typeface, Typeface.BOLD)
            t.setOnClickListener { go(i) }
            tabBar.addView(t, LinearLayout.LayoutParams(0, WRAP, 1f))
        }
    }

    // ---------------- Today ----------------

    private fun renderToday() {
        label(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)), 14f, secondary = true)
        val s = d.sessionOn(today)
        if (s == null) renderStart() else renderSession(s)

        if (d.weightOn(today) == null) {
            val cd = card()
            label("Weight not logged today", 15f, bold = true, parent = cd)
            cd.addView(button("Log today's weight", primary = false) { go(1) }, full())
        }
    }

    private fun renderStart() {
        label("Today", 26f, bold = true)
        val next = d.nextProgram()
        if (next == null) {
            label("No programs yet. Add one in the Program tab.", 15f, parent = card())
            return
        }
        var chosen: Program = next
        val cd = card()
        label("Next up", 13f, secondary = true, parent = cd)
        val title = label(next.name, 22f, bold = true, parent = cd)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        cd.addView(list)
        fun showList(p: Program) {
            list.removeAllViews()
            p.exercises.forEach { label("• ${it.name} — ${it.target()}", 14f, parent = list) }
        }
        showList(next)

        label("Or pick another workout:", 13f, secondary = true, parent = cd).setPadding(0, dp(12), 0, dp(4))
        val chips = LinearLayout(this)
        val hs = HorizontalScrollView(this)
        hs.isHorizontalScrollBarEnabled = false
        hs.addView(chips)
        cd.addView(hs)
        val chipViews = mutableListOf<TextView>()
        fun styleChips() = chipViews.forEachIndexed { i, v -> styleChip(v, d.programs[i].id == chosen.id) }
        d.programs.forEach { p ->
            val v = chip(p.name) {
                chosen = p
                title.text = p.name
                showList(p)
                styleChips()
            }
            chipViews.add(v)
            chips.addView(v)
        }
        styleChips()

        cd.addView(button("Start workout") {
            d.startSession(chosen, today)
            store.save()
            render()
        }, full(12))
    }

    private fun renderSession(s: Session) {
        label(s.programName, 26f, bold = true)
        val progress = label("", 14f, secondary = true)
        fun updateProgress() {
            val done = s.sets.count { it.done }
            val pct = if (s.sets.isEmpty()) 0 else done * 100 / s.sets.size
            progress.text = "$done / ${s.sets.size} sets done ($pct%)" + if (s.finished) " · Finished ✓" else ""
        }
        updateProgress()

        val program = d.programs.firstOrNull { it.id == s.programId }
        for ((exId, sets) in s.sets.groupBy { it.exerciseId }) {
            val ex = program?.exercises?.firstOrNull { it.id == exId }
            val cd = card()
            label(sets[0].exerciseName, 17f, bold = true, parent = cd)
            if (ex != null) label(ex.target(), 13f, secondary = true, parent = cd)
            for (set in sets) {
                val r = row(cd)
                label("Set ${set.setIndex + 1}", 14f, secondary = true, parent = r).minWidth = dp(52)
                val w = numberField(set.weight?.let { fmtNum(it) } ?: "", "kg", decimal = true)
                r.addView(w, LinearLayout.LayoutParams(0, WRAP, 1f))
                label("×", 15f, secondary = true, parent = r).setPadding(dp(8), 0, dp(8), 0)
                val rp = numberField(set.reps?.toString() ?: "", "reps", decimal = false)
                r.addView(rp, LinearLayout.LayoutParams(0, WRAP, 1f))
                val cb = CheckBox(this)
                cb.isChecked = set.done
                r.addView(cb)
                w.afterChange { set.weight = parseNum(it); touch(s) }
                rp.afterChange { set.reps = it.trim().toIntOrNull(); touch(s) }
                cb.setOnCheckedChangeListener { _, on -> set.done = on; touch(s); updateProgress() }
            }
        }

        val actions = card()
        if (!s.finished) {
            actions.addView(button("Finish workout") { confirmFinish(s) }, full())
        } else {
            actions.addView(button("Reopen workout", primary = false) {
                s.finished = false; touch(s); render()
            }, full())
        }
        actions.addView(button("Discard workout", primary = false) {
            AlertDialog.Builder(this)
                .setTitle("Discard this workout?")
                .setMessage("Everything logged in today's ${s.programName} will be deleted.")
                .setPositiveButton("Discard") { _, _ -> d.sessions.remove(s); store.save(); render() }
                .setNegativeButton("Cancel", null)
                .show()
        }, full())
    }

    private fun touch(s: Session) {
        s.lastModified = nowMs()
        store.save()
    }

    private fun confirmFinish(s: Session) {
        hideKeyboard()
        val done = s.sets.filter { it.done }
        val pct = if (s.sets.isEmpty()) 0 else done.size * 100 / s.sets.size
        val volume = done.sumOf { (it.weight ?: 0.0) * (it.reps ?: 0) }
        var msg = "${done.size} of ${s.sets.size} sets done ($pct%)\nTotal volume: ${fmtNum(volume)} kg"
        if (pct < 70) msg += "\n\nThat's under 70% of the plan, so it will count as a partial session."
        AlertDialog.Builder(this)
            .setTitle("Finish ${s.programName}?")
            .setMessage(msg)
            .setPositiveButton("Finish") { _, _ -> s.finished = true; touch(s); render() }
            .setNegativeButton("Keep going", null)
            .show()
    }

    // ---------------- Weight ----------------

    private fun renderWeight() {
        label("Weight", 26f, bold = true)

        val cd = card()
        label("Today's weight (kg)", 13f, secondary = true, parent = cd)
        val r = row(cd)
        val input = numberField(d.weightOn(today)?.let { fmtKg(it.kg) } ?: "", "e.g. 98.4", decimal = true)
        input.textSize = 22f
        input.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        r.addView(input, LinearLayout.LayoutParams(0, WRAP, 1f))
        r.addView(button("Save") {
            val kg = parseNum(input.text.toString())
            if (kg == null || kg < 20 || kg > 400) toast("Enter a weight between 20 and 400 kg")
            else { d.setWeight(today, kg); store.save(); hideKeyboard(); render(); toast("Saved ${fmtKg(kg)} kg") }
        }, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = dp(8) })
        label("Weigh in the morning, after the toilet, before eating or drinking — same clothes, same spot on the floor.",
            12f, secondary = true, parent = cd).setPadding(0, dp(8), 0, 0)

        val ws = d.weights.sortedBy { it.date }
        if (ws.isEmpty()) return
        val lastDate = LocalDate.parse(ws.last().date)
        val firstDate = LocalDate.parse(ws.first().date)
        val avgNow = d.avg7(lastDate) ?: ws.last().kg

        val sc = card()
        label("7-day average", 13f, secondary = true, parent = sc)
        label("${fmtKg(avgNow)} kg", 30f, bold = true, parent = sc)
        fun change(text: String, base: Double?) {
            if (base == null) return
            val diff = avgNow - base
            label("$text: ${signed(diff)} kg (${signed(diff / base * 100)}%)", 14f, parent = sc)
        }
        change("vs 7 days ago", d.avg7(lastDate.minusDays(7)))
        change("vs 30 days ago", d.avg7(lastDate.minusDays(30)))
        if (firstDate != lastDate) change("Since start (${pretty(firstDate)})", ws.first().kg)
        label("${ws.size} days logged", 12f, secondary = true, parent = sc).setPadding(0, dp(6), 0, 0)

        val chartCard = card()
        val chips = LinearLayout(this)
        ranges.forEachIndexed { i, (name, _) ->
            val v = chip(name) { chartRange = i; render() }
            styleChip(v, i == chartRange)
            chips.addView(v)
        }
        chartCard.addView(chips)
        val days = ranges[chartRange].second
        val from = if (days == 0) null else lastDate.minusDays(days - 1L)
        val shown = ws.filter { from == null || !LocalDate.parse(it.date).isBefore(from) }
        val chart = WeightChart(this)
        chart.points = shown.map { LocalDate.parse(it.date) to it.kg }
        chart.averages = shown.map { val dt = LocalDate.parse(it.date); dt to (d.avg7(dt) ?: it.kg) }
        chartCard.addView(chart, LinearLayout.LayoutParams(MATCH, dp(240)).apply { topMargin = dp(8) })
        label("Dots: daily weight · Line: 7-day average", 12f, secondary = true, parent = chartCard)

        val hc = card()
        label("History", 17f, bold = true, parent = hc)
        label("Tap a day to edit or delete it.", 12f, secondary = true, parent = hc)
        hc.addView(button("Add a past day", primary = false) { pickPastDay() }, full())
        for (w in ws.reversed()) {
            val dt = LocalDate.parse(w.date)
            val avg = d.avg7(dt)
            val t = label("${pretty(dt)}   ${fmtKg(w.kg)} kg" + (avg?.let { "   ·   avg ${fmtKg(it)}" } ?: ""), 15f, parent = hc)
            t.setPadding(0, dp(10), 0, dp(10))
            t.setOnClickListener { editWeight(w.date) }
        }
    }

    private fun pickPastDay() {
        val now = LocalDate.now()
        DatePickerDialog(this, { _, y, m, day ->
            val date = LocalDate.of(y, m + 1, day)
            if (date.isAfter(now)) toast("You can't log a future day") else editWeight(date.toString())
        }, now.year, now.monthValue - 1, now.dayOfMonth).show()
    }

    private fun editWeight(date: String) {
        val existing = d.weightOn(date)
        val et = numberField(existing?.let { fmtKg(it.kg) } ?: "", "kg", decimal = true)
        val b = AlertDialog.Builder(this)
            .setTitle(pretty(LocalDate.parse(date)))
            .setView(padded(et))
            .setPositiveButton("Save") { _, _ ->
                val kg = parseNum(et.text.toString())
                if (kg == null || kg < 20 || kg > 400) toast("Enter a weight between 20 and 400 kg")
                else { d.setWeight(date, kg); store.save(); render() }
            }
            .setNegativeButton("Cancel", null)
        if (existing != null) b.setNeutralButton("Delete") { _, _ ->
            d.weights.remove(existing); store.save(); render()
        }
        b.show()
    }

    // ---------------- Program ----------------

    private fun renderProgram() {
        val editing = editingProgramId?.let { id -> d.programs.firstOrNull { it.id == id } }
        if (editing != null) { renderProgramEditor(editing); return }

        label("Program", 26f, bold = true)
        label("Workouts rotate in this order: " + d.programs.joinToString(" → ") { it.name }, 13f, secondary = true)

        d.programs.forEachIndexed { i, p ->
            val cd = card()
            val head = row(cd)
            label(p.name, 18f, bold = true, parent = head).layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
            if (i > 0) head.addView(small("↑") { move(d.programs, i, -1); store.save(); render() })
            if (i < d.programs.size - 1) head.addView(small("↓") { move(d.programs, i, 1); store.save(); render() })
            head.addView(small("Edit") { editingProgramId = p.id; render(); scroll.scrollTo(0, 0) })
            p.exercises.forEach { label("${it.name} — ${it.target()}", 13f, parent = cd).setPadding(0, dp(3), 0, dp(3)) }
        }

        val cc = card()
        label("Cardio", 17f, bold = true, parent = cc)
        label("${d.cardioOptions.joinToString(", ")} · 20–30 min, moderate pace", 14f, parent = cc)
        label("Cardio logging comes in a later update.", 12f, secondary = true, parent = cc)

        val ac = card()
        ac.addView(button("Add program", primary = false) {
            askText("New program", "", "Name, e.g. Full Body") { name ->
                if (name.isNotBlank()) {
                    val p = Program(newId(), name.trim(), mutableListOf(), nowMs())
                    d.programs.add(p); store.save(); editingProgramId = p.id; render()
                }
            }
        }, full())
        ac.addView(button("Import program (paste JSON)", primary = false) {
            askText("Paste program JSON", "", "{\"name\": …, \"exercises\": […]}", multiLine = true) { text ->
                try {
                    val list = parseProgramsLoose(text)
                    d.programs.addAll(list); store.save(); render()
                    toast("Added ${list.size} program(s)")
                } catch (e: ImportError) {
                    alert("Can't import", e.message ?: "Unknown problem")
                }
            }
        }, full())
        ac.addView(button("Copy all programs as JSON", primary = false) {
            val arr = org.json.JSONArray().apply { d.programs.forEach { put(it.toJson()) } }
            val cm = getSystemService(ClipboardManager::class.java)
            cm.setPrimaryClip(ClipData.newPlainText("Compound programs", arr.toString(2)))
            toast("Copied. Paste it to Claude to review or improve.")
        }, full())
    }

    private fun renderProgramEditor(p: Program) {
        label("Editing", 13f, secondary = true)
        val head = row(content)
        label(p.name, 26f, bold = true, parent = head).layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        head.addView(small("Rename") {
            askText("Rename program", p.name, "Name") { name ->
                if (name.isNotBlank()) { p.name = name.trim(); changed(p) }
            }
        })
        label("Tap an exercise to edit it.", 12f, secondary = true)

        p.exercises.forEachIndexed { i, e ->
            val cd = card()
            val r = row(cd)
            label(e.name, 16f, bold = true, parent = r).layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
            if (i > 0) r.addView(small("↑") { move(p.exercises, i, -1); changed(p) })
            if (i < p.exercises.size - 1) r.addView(small("↓") { move(p.exercises, i, 1); changed(p) })
            r.addView(small("✕") {
                AlertDialog.Builder(this).setTitle("Remove ${e.name}?")
                    .setPositiveButton("Remove") { _, _ -> p.exercises.remove(e); changed(p) }
                    .setNegativeButton("Cancel", null).show()
            })
            label(e.target(), 13f, secondary = true, parent = cd)
            cd.setOnClickListener { exerciseDialog(p, e) }
        }

        val ac = card()
        ac.addView(button("Add exercise", primary = false) { exerciseDialog(p, null) }, full())
        ac.addView(button("Done") { editingProgramId = null; render(); scroll.scrollTo(0, 0) }, full())
        ac.addView(button("Delete program", primary = false) {
            AlertDialog.Builder(this).setTitle("Delete ${p.name}?")
                .setMessage("Past workouts stay in your history.")
                .setPositiveButton("Delete") { _, _ ->
                    d.programs.remove(p); editingProgramId = null; store.save(); render()
                }
                .setNegativeButton("Cancel", null).show()
        }, full())
    }

    private fun changed(p: Program) {
        p.lastModified = nowMs()
        store.save()
        render()
    }

    private fun <T> move(list: MutableList<T>, i: Int, by: Int) {
        val j = i + by
        if (j < 0 || j >= list.size) return
        val tmp = list[i]; list[i] = list[j]; list[j] = tmp
    }

    private fun exerciseDialog(p: Program, e: Exercise?) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(8), dp(24), 0) }
        fun field(title: String, value: String, numeric: Boolean): EditText {
            label(title, 12f, secondary = true, parent = box).setPadding(0, dp(8), 0, 0)
            val et = if (numeric) numberField(value, "", decimal = false) else EditText(this).apply {
                setText(value); isSingleLine = true; setTextColor(c(R.color.text_primary))
            }
            et.gravity = Gravity.START or Gravity.CENTER_VERTICAL
            box.addView(et, full(0))
            return et
        }
        val name = field("Name", e?.name ?: "", false)
        val sets = field("Sets", (e?.sets ?: 3).toString(), true)
        val min = field("Min reps", (e?.repMin ?: 8).toString(), true)
        val max = field("Max reps", (e?.repMax ?: 12).toString(), true)
        val rest = field("Rest (seconds)", (e?.restSec ?: 90).toString(), true)
        val note = field("Note (optional, e.g. per leg)", e?.note ?: "", false)
        val sv = ScrollView(this).apply { addView(box) }

        AlertDialog.Builder(this)
            .setTitle(if (e == null) "Add exercise" else "Edit exercise")
            .setView(sv)
            .setPositiveButton("Save") { _, _ ->
                val n = name.text.toString().trim()
                val s = sets.text.toString().toIntOrNull()
                val lo = min.text.toString().toIntOrNull()
                val hi = max.text.toString().toIntOrNull()
                val r = rest.text.toString().toIntOrNull()
                when {
                    n.isEmpty() -> toast("Name is empty, nothing saved")
                    s == null || s !in 1..20 -> toast("Sets must be 1–20, nothing saved")
                    lo == null || hi == null || lo < 1 || hi < lo || hi > 100 -> toast("Rep range isn't valid, nothing saved")
                    r == null || r !in 0..900 -> toast("Rest must be 0–900 seconds, nothing saved")
                    else -> {
                        val nt = note.text.toString().trim()
                        if (e == null) p.exercises.add(Exercise(newId(), n, s, lo, hi, r, nt))
                        else { e.name = n; e.sets = s; e.repMin = lo; e.repMax = hi; e.restSec = r; e.note = nt }
                        changed(p)
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---------------- Data ----------------

    private fun renderData() {
        label("Data", 26f, bold = true)

        val cd = card()
        label("Backup", 17f, bold = true, parent = cd)
        label("On this device: ${d.weights.size} weight entries, ${d.sessions.size} workouts, ${d.programs.size} programs.",
            14f, parent = cd)
        cd.addView(button("Save backup file") {
            val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json")
                .putExtra(Intent.EXTRA_TITLE, "compound-backup-$today.json")
            startActivityForResult(i, REQ_EXPORT)
        }, full())
        cd.addView(button("Share backup (Quick Share, Claude…)", primary = false) {
            val i = Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, "Compound backup $today")
                .putExtra(Intent.EXTRA_TEXT, store.json())
            try {
                startActivity(Intent.createChooser(i, "Share backup"))
            } catch (e: Exception) {
                toast("Too large to share as text. Use Save backup file instead.")
            }
        }, full())

        val ic = card()
        label("Import", 17f, bold = true, parent = ic)
        label("The file is checked first and nothing changes until you confirm. A safety copy of your current data is saved before every import.",
            13f, secondary = true, parent = ic)
        ic.addView(button("Import backup file") {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*")
                .putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/json", "text/plain", "application/octet-stream"))
            startActivityForResult(i, REQ_IMPORT)
        }, full())
        ic.addView(button("Import pasted text", primary = false) {
            askText("Paste backup JSON", "", "{\"app\": \"Compound\", …}", multiLine = true) { startImport(it) }
        }, full())
        store.latestBackup()?.let { f ->
            ic.addView(button("Undo last import", primary = false) {
                AlertDialog.Builder(this).setTitle("Undo last import?")
                    .setMessage("Your data goes back to how it was right before the last import.")
                    .setPositiveButton("Undo") { _, _ ->
                        try { store.restore(f); render(); toast("Restored") }
                        catch (e: Exception) { alert("Couldn't restore", e.message ?: "Unknown problem") }
                    }
                    .setNegativeButton("Cancel", null).show()
            }, full())
        }
        label("Backup format: JSON, schemaVersion $SCHEMA_VERSION.", 12f, secondary = true).setPadding(0, dp(12), 0, 0)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) return
        try {
            when (requestCode) {
                REQ_EXPORT -> {
                    val out = contentResolver.openOutputStream(uri) ?: throw Exception("Can't open the file")
                    out.use { it.write(store.json().toByteArray()) }
                    toast("Backup saved")
                }
                REQ_IMPORT -> {
                    val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: throw Exception("Can't open the file")
                    startImport(text)
                }
            }
        } catch (e: Exception) {
            alert("Something went wrong", e.message ?: "Unknown problem")
        }
    }

    private fun startImport(text: String) {
        val file = try {
            parseData(text)
        } catch (e: ImportError) {
            alert("Can't import", e.message ?: "Unknown problem"); return
        } catch (e: Exception) {
            alert("Can't import", "This file couldn't be read."); return
        }
        val plan = merge(store.data, file, null)
        if (plan.added == 0 && plan.changed == 0) {
            alert("Nothing to import", "Everything in this file is already on this device.")
            return
        }
        var msg = "New entries: ${plan.added}\nDifferent from this device: ${plan.changed}\nAlready the same: ${plan.same}"
        if (plan.firstDate != null) msg += "\nDates in file: ${plan.firstDate} → ${plan.lastDate}"
        AlertDialog.Builder(this)
            .setTitle("Import this backup?")
            .setMessage(msg)
            .setPositiveButton("Import") { _, _ ->
                if (plan.changed == 0) doImport(file, MergeMode.NEWER) else askConflict(file, plan.changed)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun askConflict(file: AppData, count: Int) {
        val options = arrayOf("Keep the newer version (recommended)", "Keep this device's version", "Use the file's version")
        val modes = arrayOf(MergeMode.NEWER, MergeMode.DEVICE, MergeMode.FILE)
        AlertDialog.Builder(this)
            .setTitle("$count entries differ. Which should win?")
            .setItems(options) { _, which -> doImport(file, modes[which]) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun doImport(file: AppData, mode: MergeMode) {
        try {
            store.safetyBackup()
            val plan = merge(store.data, file, mode)
            store.save()
            render()
            toast("Imported (${plan.added} new, ${plan.changed} compared)")
        } catch (e: Exception) {
            alert("Import failed", "${e.message}\n\nUse \"Undo last import\" if anything looks wrong.")
        }
    }

    // ---------------- Small view helpers ----------------

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun c(id: Int): Int = getColor(id)

    private fun label(
        s: String, size: Float = 15f, bold: Boolean = false, secondary: Boolean = false,
        parent: ViewGroup = content
    ): TextView {
        val t = TextView(this)
        t.text = s
        t.textSize = size
        t.setTextColor(c(if (secondary) R.color.text_secondary else R.color.text_primary))
        if (bold) t.setTypeface(t.typeface, Typeface.BOLD)
        parent.addView(t)
        return t
    }

    private fun card(): LinearLayout {
        val l = LinearLayout(this)
        l.orientation = LinearLayout.VERTICAL
        l.setPadding(dp(16), dp(14), dp(16), dp(14))
        l.background = GradientDrawable().apply { setColor(c(R.color.surface)); cornerRadius = dp(16).toFloat() }
        content.addView(l, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(12) })
        return l
    }

    private fun row(parent: ViewGroup): LinearLayout {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        parent.addView(r, LinearLayout.LayoutParams(MATCH, WRAP))
        return r
    }

    private fun full(top: Int = 8) = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(top) }

    private fun button(s: String, primary: Boolean = true, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = s
        b.isAllCaps = false
        b.textSize = 15f
        b.stateListAnimator = null
        b.setTextColor(c(if (primary) R.color.on_brand else R.color.brand))
        b.background = GradientDrawable().apply {
            setColor(if (primary) c(R.color.brand) else 0)
            setStroke(dp(1), c(R.color.brand))
            cornerRadius = dp(12).toFloat()
        }
        b.setPadding(dp(16), dp(10), dp(16), dp(10))
        b.setOnClickListener { onClick() }
        return b
    }

    private fun small(s: String, onClick: () -> Unit): TextView {
        val t = TextView(this)
        t.text = s
        t.textSize = 15f
        t.setTextColor(c(R.color.brand))
        t.setPadding(dp(12), dp(8), dp(4), dp(8))
        t.setOnClickListener { onClick() }
        return t
    }

    private fun chip(s: String, onClick: () -> Unit): TextView {
        val t = TextView(this)
        t.text = s
        t.textSize = 14f
        t.setPadding(dp(14), dp(8), dp(14), dp(8))
        t.layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).apply { marginEnd = dp(8) }
        t.setOnClickListener { onClick() }
        return t
    }

    private fun styleChip(t: TextView, selected: Boolean) {
        t.setTextColor(c(if (selected) R.color.on_brand else R.color.brand))
        t.background = GradientDrawable().apply {
            setColor(if (selected) c(R.color.brand) else 0)
            setStroke(dp(1), c(R.color.brand))
            cornerRadius = dp(20).toFloat()
        }
    }

    private fun numberField(value: String, hint: String, decimal: Boolean): EditText {
        val et = EditText(this)
        et.setText(value)
        et.hint = hint
        et.isSingleLine = true
        et.gravity = Gravity.CENTER
        et.setTextColor(c(R.color.text_primary))
        et.setHintTextColor(c(R.color.text_secondary))
        et.inputType = if (decimal) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        else InputType.TYPE_CLASS_NUMBER
        return et
    }

    private fun EditText.afterChange(f: (String) -> Unit) {
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = f(s?.toString() ?: "")
        })
    }

    private fun padded(v: View): View {
        val f = FrameLayout(this)
        f.setPadding(dp(24), dp(12), dp(24), 0)
        f.addView(v)
        return f
    }

    private fun askText(title: String, initial: String, hint: String, multiLine: Boolean = false, onOk: (String) -> Unit) {
        val et = EditText(this)
        et.setText(initial)
        et.hint = hint
        et.setTextColor(c(R.color.text_primary))
        if (multiLine) {
            et.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            et.minLines = 4
            et.maxLines = 10
            et.gravity = Gravity.TOP or Gravity.START
        } else {
            et.isSingleLine = true
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(padded(et))
            .setPositiveButton("OK") { _, _ -> onOk(et.text.toString()) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun alert(title: String, msg: String) {
        AlertDialog.Builder(this).setTitle(title).setMessage(msg).setPositiveButton("OK", null).show()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun hideKeyboard() {
        val v = currentFocus ?: return
        getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(v.windowToken, 0)
        v.clearFocus()
    }

    private fun pretty(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH))

    private fun signed(v: Double): String = String.format(Locale.US, "%+.1f", v).replace("-", "−")
}
