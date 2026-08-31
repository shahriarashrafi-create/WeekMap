package com.weekmap.app

import android.app.AlertDialog
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private val days = arrayOf(
        "شنبه",
        "یکشنبه",
        "دوشنبه",
        "سه‌شنبه",
        "چهارشنبه",
        "پنج‌شنبه",
        "جمعه"
    )

    private val startHour = 7
    private val endHour = 24

    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var mainContainer: LinearLayout
    private lateinit var statsText: TextView

    private val events = mutableListOf<WeekEvent>()
    private val dates = MutableList(7) { "" }

    data class WeekEvent(
        var id: Long,
        var day: Int,
        var startHour: Int,
        var endHour: Int,
        var title: String,
        var recurring: Boolean,
        var status: String = "planned"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences("weekmap_data", Context.MODE_PRIVATE)

        loadData()
        buildMainScreen()
    }

    private fun buildMainScreen() {

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(248, 250, 252))
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }

        val title = TextView(this).apply {
            text = "WEEK MAP"
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.rgb(15, 23, 42))
        }

        statsText = TextView(this).apply {
            textSize = 12f
            gravity = Gravity.END
            setTextColor(Color.DKGRAY)
        }

        val historyButton = Button(this).apply {
            text = "History"
            setOnClickListener {
                showHistory()
            }
        }

        val newWeekButton = Button(this).apply {
            text = "New Week"
            setOnClickListener {
                confirmNewWeek()
            }
        }

        topBar.addView(
            title,
            LinearLayout.LayoutParams(0, dp(48), 1f)
        )

        topBar.addView(historyButton)
        topBar.addView(newWeekButton)

        root.addView(topBar)

        root.addView(
            statsText,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(28)
            )
        )

        mainContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val verticalScroll = ScrollView(this)

        val horizontalScroll = HorizontalScrollView(this).apply {
            isFillViewport = true
        }

        horizontalScroll.addView(mainContainer)
        verticalScroll.addView(horizontalScroll)

        root.addView(
            verticalScroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(root)

        renderWeek()
    }
        private fun renderWeek() {

        mainContainer.removeAllViews()

        updateStats()

        val landscape =
            resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        val cellWidth = if (landscape) dp(135) else dp(125)
        val hourWidth = dp(65)
        val rowHeight = dp(62)

        val table = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(4), dp(6), dp(12))
        }

        // Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        header.addView(
            createHeaderCell(
                "ساعت",
                hourWidth,
                dp(74),
                false
            )
        )

        for (dayIndex in days.indices) {

            val dayBox = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(5), dp(4), dp(5))
                setBackgroundColor(Color.rgb(241, 245, 249))

                setOnClickListener {
                    editDate(dayIndex)
                }
            }

            val dayName = TextView(this).apply {
                text = days[dayIndex]
                gravity = Gravity.CENTER
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.rgb(15, 23, 42))
            }

            val dateText = TextView(this).apply {
                text = if (dates[dayIndex].isBlank()) {
                    "تاریخ +"
                } else {
                    dates[dayIndex]
                }

                gravity = Gravity.CENTER
                textSize = 11f
                setTextColor(Color.rgb(71, 85, 105))
            }

            dayBox.addView(dayName)
            dayBox.addView(dateText)

            header.addView(
                dayBox,
                LinearLayout.LayoutParams(cellWidth, dp(74))
            )
        }

        table.addView(header)

        for (hour in startHour until endHour) {

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            val hourLabel = TextView(this).apply {
                text = String.format("%02d:00", hour)
                gravity = Gravity.CENTER
                textSize = 12f
                setTextColor(Color.rgb(71, 85, 105))
                setBackgroundColor(Color.rgb(248, 250, 252))
            }

            row.addView(
                hourLabel,
                LinearLayout.LayoutParams(hourWidth, rowHeight)
            )

            for (dayIndex in days.indices) {

                val event = findEvent(dayIndex, hour)

                val cell = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(dp(5), dp(3), dp(5), dp(3))
                }

                if (event == null) {

                    cell.setBackgroundColor(Color.WHITE)

                    val plus = TextView(this).apply {
                        text = "+"
                        textSize = 18f
                        gravity = Gravity.CENTER
                        setTextColor(Color.rgb(148, 163, 184))
                    }

                    cell.addView(plus)

                    cell.setOnClickListener {
                        showEventEditor(
                            existing = null,
                            dayIndex = dayIndex,
                            selectedHour = hour
                        )
                    }

                } else {

                    styleEventCell(cell, event)

                    if (event.startHour == hour) {

                        val eventTitle = TextView(this).apply {
                            text = event.title
                            gravity = Gravity.CENTER
                            textSize = 12f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(
                                when (event.status) {
                                    "done" -> Color.rgb(22, 101, 52)
                                    "missed" -> Color.rgb(153, 27, 27)
                                    else -> Color.rgb(30, 64, 175)
                                }
                            )
                        }

                        val meta = TextView(this).apply {

                            val typeSymbol =
                                if (event.recurring) "↻" else "•"

                            text =
                                "$typeSymbol ${String.format("%02d", event.startHour)}-${String.format("%02d", event.endHour)}"

                            gravity = Gravity.CENTER
                            textSize = 9f
                            setTextColor(Color.DKGRAY)
                        }

                        cell.addView(eventTitle)
                        cell.addView(meta)

                    } else {

                        val continuation = TextView(this).apply {
                            text = "↳"
                            gravity = Gravity.CENTER
                            textSize = 13f
                        }

                        cell.addView(continuation)
                    }

                    cell.setOnClickListener {
                        showEventActions(event)
                    }
                }

                row.addView(
                    cell,
                    LinearLayout.LayoutParams(cellWidth, rowHeight)
                )
            }

            table.addView(row)
        }

        mainContainer.addView(table)
    }

    private fun createHeaderCell(
        text: String,
        width: Int,
        height: Int,
        bold: Boolean
    ): TextView {

        return TextView(this).apply {
            this.text = text
            gravity = Gravity.CENTER
            textSize = 13f

            if (bold) {
                setTypeface(null, Typeface.BOLD)
            }

            setTextColor(Color.rgb(15, 23, 42))
            setBackgroundColor(Color.rgb(241, 245, 249))

            layoutParams = LinearLayout.LayoutParams(width, height)
        }
    }

    private fun findEvent(
        dayIndex: Int,
        hour: Int
    ): WeekEvent? {

        return events.firstOrNull {
            it.day == dayIndex &&
            hour >= it.startHour &&
            hour < it.endHour
        }
    }

    private fun styleEventCell(
        view: View,
        event: WeekEvent
    ) {

        val color = when (event.status) {

            "done" ->
                Color.rgb(220, 252, 231)

            "missed" ->
                Color.rgb(254, 226, 226)

            else ->
                Color.rgb(219, 234, 254)
        }

        view.setBackgroundColor(color)
    }

    private fun editDate(dayIndex: Int) {

        val input = EditText(this).apply {
            hint = "مثلاً ۷ شهریور"
            textDirection = View.TEXT_DIRECTION_RTL
            setText(dates[dayIndex])
            setSelectAllOnFocus(true)
        }

        AlertDialog.Builder(this)
            .setTitle("تاریخ ${days[dayIndex]}")
            .setView(input)
            .setPositiveButton("ذخیره") { _, _ ->

                dates[dayIndex] =
                    input.text.toString().trim()

                saveData()
                renderWeek()
            }
            .setNegativeButton("لغو", null)
            .show()
    }
        private fun showEventEditor(
        existing: WeekEvent?,
        dayIndex: Int,
        selectedHour: Int
    ) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }

        val titleInput = EditText(this).apply {
            hint = "نام برنامه یا جلسه"
            setText(existing?.title ?: "")
            textDirection = View.TEXT_DIRECTION_RTL
        }

        val startInput = EditText(this).apply {
            hint = "ساعت شروع"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText((existing?.startHour ?: selectedHour).toString())
        }

        val endInput = EditText(this).apply {
            hint = "ساعت پایان"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText((existing?.endHour ?: (selectedHour + 1)).toString())
        }

        val recurringCheck = CheckBox(this).apply {
            text = "برنامه ثابت هفتگی"
            isChecked = existing?.recurring ?: false
        }

        box.addView(titleInput)
        box.addView(startInput)
        box.addView(endInput)
        box.addView(recurringCheck)

        AlertDialog.Builder(this)
            .setTitle(
                if (existing == null)
                    "برنامه جدید - ${days[dayIndex]}"
                else
                    "ویرایش برنامه"
            )
            .setView(box)
            .setPositiveButton("ذخیره") { _, _ ->

                val title = titleInput.text.toString().trim()
                val start = startInput.text.toString().toIntOrNull()
                val end = endInput.text.toString().toIntOrNull()

                if (
                    title.isBlank() ||
                    start == null ||
                    end == null ||
                    start < startHour ||
                    end > endHour ||
                    end <= start
                ) {
                    Toast.makeText(
                        this,
                        "اطلاعات ساعت یا عنوان صحیح نیست",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }

                val overlap = events.any {
                    it.id != existing?.id &&
                    it.day == dayIndex &&
                    start < it.endHour &&
                    end > it.startHour
                }

                if (overlap) {
                    Toast.makeText(
                        this,
                        "این بازه زمانی قبلاً پر شده",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }

                if (existing == null) {
                    events.add(
                        WeekEvent(
                            id = System.currentTimeMillis(),
                            day = dayIndex,
                            startHour = start,
                            endHour = end,
                            title = title,
                            recurring = recurringCheck.isChecked
                        )
                    )
                } else {
                    existing.day = dayIndex
                    existing.startHour = start
                    existing.endHour = end
                    existing.title = title
                    existing.recurring = recurringCheck.isChecked
                }

                saveData()
                renderWeek()
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun showEventActions(event: WeekEvent) {

        val options = arrayOf(
            "✅ انجام شد",
            "❌ انجام نشد",
            "✏️ ویرایش",
            "🗑 حذف"
        )

        AlertDialog.Builder(this)
            .setTitle(event.title)
            .setItems(options) { _, which ->

                when (which) {

                    0 -> {
                        event.status = "done"
                        saveData()
                        renderWeek()
                    }

                    1 -> {
                        event.status = "missed"
                        saveData()
                        renderWeek()
                    }

                    2 -> {
                        showEventEditor(
                            existing = event,
                            dayIndex = event.day,
                            selectedHour = event.startHour
                        )
                    }

                    3 -> {
                        confirmDelete(event)
                    }
                }
            }
            .setNegativeButton("بستن", null)
            .show()
    }

    private fun confirmDelete(event: WeekEvent) {

        AlertDialog.Builder(this)
            .setTitle("حذف برنامه")
            .setMessage("«${event.title}» حذف شود؟")
            .setPositiveButton("حذف") { _, _ ->

                events.removeAll {
                    it.id == event.id
                }

                saveData()
                renderWeek()
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun updateStats() {

        val total = events.size
        val done = events.count { it.status == "done" }
        val missed = events.count { it.status == "missed" }
        val planned = events.count { it.status == "planned" }

        statsText.text =
            "کل $total   ✓ $done   ✕ $missed   ⏳ $planned"
    }
        private fun confirmNewWeek() {

        AlertDialog.Builder(this)
            .setTitle("شروع هفته جدید")
            .setMessage(
                "هفته فعلی در History ذخیره می‌شود. " +
                "برنامه‌های ثابت باقی می‌مانند و برنامه‌های یک‌باره حذف می‌شوند."
            )
            .setPositiveButton("شروع هفته جدید") { _, _ ->
                archiveCurrentWeek()
                startNewWeek()
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun archiveCurrentWeek() {

        if (events.isEmpty() && dates.all { it.isBlank() }) {
            return
        }

        val history =
            JSONArray(prefs.getString("history", "[]") ?: "[]")

        val week = JSONObject()

        week.put("savedAt", System.currentTimeMillis())

        val dateArray = JSONArray()
        dates.forEach {
            dateArray.put(it)
        }

        week.put("dates", dateArray)

        val eventArray = JSONArray()

        events.forEach { event ->

            val obj = JSONObject()

            obj.put("id", event.id)
            obj.put("day", event.day)
            obj.put("startHour", event.startHour)
            obj.put("endHour", event.endHour)
            obj.put("title", event.title)
            obj.put("recurring", event.recurring)
            obj.put("status", event.status)

            eventArray.put(obj)
        }

        week.put("events", eventArray)

        history.put(week)

        // حداکثر 52 هفته نگهداری شود
        val trimmed = JSONArray()

        val start =
            if (history.length() > 52)
                history.length() - 52
            else
                0

        for (i in start until history.length()) {
            trimmed.put(history.getJSONObject(i))
        }

        prefs.edit()
            .putString("history", trimmed.toString())
            .apply()
    }

    private fun startNewWeek() {

        val recurringEvents =
            events.filter { it.recurring }
                .map {
                    it.copy(
                        id = System.currentTimeMillis() +
                            (0..100000).random(),
                        status = "planned"
                    )
                }

        events.clear()
        events.addAll(recurringEvents)

        for (i in dates.indices) {
            dates[i] = ""
        }

        saveData()
        renderWeek()

        Toast.makeText(
            this,
            "هفته جدید ساخته شد",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun showHistory() {

        val history =
            JSONArray(prefs.getString("history", "[]") ?: "[]")

        if (history.length() == 0) {

            Toast.makeText(
                this,
                "هنوز هفته‌ای در History ذخیره نشده",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val labels = mutableListOf<String>()

        for (i in history.length() - 1 downTo 0) {

            val week = history.getJSONObject(i)
            val weekDates = week.getJSONArray("dates")
            val weekEvents = week.getJSONArray("events")

            var done = 0
            var missed = 0

            for (j in 0 until weekEvents.length()) {

                when (
                    weekEvents.getJSONObject(j)
                        .optString("status", "planned")
                ) {
                    "done" -> done++
                    "missed" -> missed++
                }
            }

            val firstDate =
                weekDates.optString(0, "").ifBlank { "بدون تاریخ" }

            val lastDate =
                weekDates.optString(6, "").ifBlank { "بدون تاریخ" }

            labels.add(
                "$firstDate تا $lastDate\n" +
                "✓ $done   ✕ $missed   کل ${weekEvents.length()}"
            )
        }

        AlertDialog.Builder(this)
            .setTitle("History هفته‌ها")
            .setItems(labels.toTypedArray()) { _, position ->

                val realIndex =
                    history.length() - 1 - position

                showHistoryWeek(
                    history.getJSONObject(realIndex)
                )
            }
            .setNegativeButton("بستن", null)
            .show()
    }

    private fun showHistoryWeek(week: JSONObject) {

        val weekDates = week.getJSONArray("dates")
        val weekEvents = week.getJSONArray("events")

        val text = StringBuilder()

        for (dayIndex in days.indices) {

            val date =
                weekDates.optString(dayIndex, "")

            text.append(days[dayIndex])

            if (date.isNotBlank()) {
                text.append(" - $date")
            }

            text.append("\n")

            var hasEvent = false

            for (i in 0 until weekEvents.length()) {

                val event =
                    weekEvents.getJSONObject(i)

                if (event.getInt("day") == dayIndex) {

                    hasEvent = true

                    val status = when (
                        event.optString(
                            "status",
                            "planned"
                        )
                    ) {
                        "done" -> "✓"
                        "missed" -> "✕"
                        else -> "○"
                    }

                    text.append(
                        "$status " +
                        "${event.getInt("startHour")}:00-" +
                        "${event.getInt("endHour")}:00  " +
                        "${event.getString("title")}\n"
                    )
                }
            }

            if (!hasEvent) {
                text.append("—\n")
            }

            text.append("\n")
        }

        val scroll = ScrollView(this)

        val content = TextView(this).apply {
            this.text = text.toString()
            textSize = 15f
            setPadding(
                dp(22),
                dp(15),
                dp(22),
                dp(15)
            )
            textDirection = View.TEXT_DIRECTION_RTL
        }

        scroll.addView(content)

        AlertDialog.Builder(this)
            .setTitle("جزئیات هفته")
            .setView(scroll)
            .setPositiveButton("بستن", null)
            .show()
    }
        private fun saveData() {

        val eventArray = JSONArray()

        events.forEach { event ->

            val obj = JSONObject()

            obj.put("id", event.id)
            obj.put("day", event.day)
            obj.put("startHour", event.startHour)
            obj.put("endHour", event.endHour)
            obj.put("title", event.title)
            obj.put("recurring", event.recurring)
            obj.put("status", event.status)

            eventArray.put(obj)
        }

        val dateArray = JSONArray()

        dates.forEach {
            dateArray.put(it)
        }

        prefs.edit()
            .putString("events", eventArray.toString())
            .putString("dates", dateArray.toString())
            .apply()
    }

    private fun loadData() {

        events.clear()

        val savedEvents =
            prefs.getString("events", "[]") ?: "[]"

        val eventArray =
            JSONArray(savedEvents)

        for (i in 0 until eventArray.length()) {

            val obj =
                eventArray.getJSONObject(i)

            events.add(
                WeekEvent(
                    id = obj.optLong(
                        "id",
                        System.currentTimeMillis()
                    ),
                    day = obj.optInt("day", 0),
                    startHour = obj.optInt(
                        "startHour",
                        7
                    ),
                    endHour = obj.optInt(
                        "endHour",
                        8
                    ),
                    title = obj.optString(
                        "title",
                        ""
                    ),
                    recurring = obj.optBoolean(
                        "recurring",
                        false
                    ),
                    status = obj.optString(
                        "status",
                        "planned"
                    )
                )
            )
        }

        val savedDates =
            prefs.getString("dates", "[]") ?: "[]"

        val dateArray =
            JSONArray(savedDates)

        for (i in dates.indices) {

            dates[i] =
                if (i < dateArray.length())
                    dateArray.optString(i, "")
                else
                    ""
        }
    }

    private fun dp(value: Int): Int {

        return (
            value *
            resources.displayMetrics.density
        ).toInt()
    }
}
