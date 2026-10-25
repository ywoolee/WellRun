package com.example.wellrun.mypage

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.wellrun.R
import com.example.wellrun.auth.SessionManager
import com.example.wellrun.course.CourseActivity
import com.example.wellrun.main.MainPageActivity
import com.example.wellrun.model.RunningRecord
import com.example.wellrun.model.User
import com.example.wellrun.network.RetrofitClient
import com.example.wellrun.running.RunningActivity
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

class MyPageActivity : AppCompatActivity() {

    private lateinit var tvProfileInitial: TextView
    private lateinit var tvProfileName: TextView
    private lateinit var tvProfileSub: TextView

    // 2x2 요약 통계 뷰 4개
    private lateinit var tvTotalDistance: TextView
    private lateinit var tvTotalCount: TextView
    private lateinit var tvTotalTime: TextView
    private lateinit var tvTotalElevation: TextView

    private lateinit var tvDistanceChartTitle: TextView
    private lateinit var tvHrChartTitle: TextView
    private lateinit var btnToggleWeekly: TextView
    private lateinit var btnToggleMonthly: TextView

    private lateinit var barChartContainer: FrameLayout
    private lateinit var lineChartContainer: FrameLayout

    private var allRecords: List<RunningRecord> = emptyList()
    private var isWeeklyMode: Boolean = true // 기본값: 최근 4주 보기

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.mypage)

        initViews()
        setupNavigation()
        setupChartToggle()
    }

    override fun onResume() {
        super.onResume()
        loadUserDataAndHistory()
    }

    private fun initViews() {
        tvProfileInitial = findViewById(R.id.tv_profile_initial)
        tvProfileName = findViewById(R.id.tv_profile_name)
        tvProfileSub = findViewById(R.id.tv_profile_sub)

        tvTotalDistance = findViewById(R.id.tv_total_distance)
        tvTotalCount = findViewById(R.id.tv_total_count)
        tvTotalTime = findViewById(R.id.tv_total_time)
        tvTotalElevation = findViewById(R.id.tv_total_elevation)

        tvDistanceChartTitle = findViewById(R.id.tv_distance_chart_title)
        tvHrChartTitle = findViewById(R.id.tv_hr_chart_title)
        btnToggleWeekly = findViewById(R.id.btn_toggle_weekly)
        btnToggleMonthly = findViewById(R.id.btn_toggle_monthly)

        barChartContainer = findViewById(R.id.bar_chart_container)
        lineChartContainer = findViewById(R.id.line_chart_container)

        findViewById<View>(R.id.btn_edit_profile).setOnClickListener {
            startActivity(Intent(this, EditProfileActivity::class.java))
        }
    }

    private fun setupNavigation() {
        findViewById<TextView>(R.id.mypage_nav_calendar).setOnClickListener {
            val intent = Intent(this, MainPageActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
        }
        findViewById<TextView>(R.id.mypage_nav_running).setOnClickListener {
            val intent = Intent(this, RunningActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
        }
        findViewById<TextView>(R.id.mypage_nav_course).setOnClickListener {
            val intent = Intent(this, CourseActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
        }
    }

    private fun setupChartToggle() {
        btnToggleWeekly.setOnClickListener {
            if (!isWeeklyMode) {
                isWeeklyMode = true
                tvDistanceChartTitle.text = "최근 4주 러닝거리"
                tvHrChartTitle.text = "최근 4주 평균 심박수"

                btnToggleWeekly.setBackgroundResource(R.drawable.bg_toggle_selected)
                btnToggleWeekly.setTextColor(Color.parseColor("#FF6B35"))
                btnToggleWeekly.setTypeface(null, Typeface.BOLD)

                btnToggleMonthly.setBackgroundResource(R.drawable.bg_btn_settings)
                btnToggleMonthly.setTextColor(Color.parseColor("#888888"))
                btnToggleMonthly.setTypeface(null, Typeface.NORMAL)

                updateCharts()
            }
        }

        btnToggleMonthly.setOnClickListener {
            if (isWeeklyMode) {
                isWeeklyMode = false
                tvDistanceChartTitle.text = "최근 4개월 러닝거리"
                tvHrChartTitle.text = "최근 4개월 평균 심박수"

                btnToggleMonthly.setBackgroundResource(R.drawable.bg_toggle_selected)
                btnToggleMonthly.setTextColor(Color.parseColor("#FF6B35"))
                btnToggleMonthly.setTypeface(null, Typeface.BOLD)

                btnToggleWeekly.setBackgroundResource(R.drawable.bg_btn_settings)
                btnToggleWeekly.setTextColor(Color.parseColor("#888888"))
                btnToggleWeekly.setTypeface(null, Typeface.NORMAL)

                updateCharts()
            }
        }
    }

    private fun loadUserDataAndHistory() {
        val userId = SessionManager.getUserId(this)

        // 1. 유저 프로필 조회
        RetrofitClient.api.getUserProfile(userId).enqueue(object : Callback<User> {
            override fun onResponse(call: Call<User>, response: Response<User>) {
                if (response.isSuccessful && response.body() != null) {
                    val user = response.body()!!
                    val name = user.nickname.ifEmpty { "러너" }
                    tvProfileInitial.text = name.first().toString()
                    tvProfileName.text = "${name} 님"
                    val goalText = if (!user.goal.isNullOrEmpty()) user.goal else "건강한 러닝"
                    tvProfileSub.text = "목표: $goalText"
                } else {
                    tvProfileInitial.text = userId.firstOrNull()?.uppercase() ?: "W"
                    tvProfileName.text = "${userId.substringBefore("@")} 님"
                    tvProfileSub.text = "WellRun 러너"
                }
            }

            override fun onFailure(call: Call<User>, t: Throwable) {
                tvProfileName.text = "${userId.substringBefore("@")} 님"
                tvProfileSub.text = "프로필 로드 실패"
            }
        })

        // 2. DB 러닝 기록 전체 조회 및 통계 계산
        RetrofitClient.api.getRunningHistory(userId).enqueue(object : Callback<List<RunningRecord>> {
            override fun onResponse(
                call: Call<List<RunningRecord>>,
                response: Response<List<RunningRecord>>
            ) {
                if (response.isSuccessful && response.body() != null) {
                    allRecords = response.body()!!
                    updateSummaryMetrics()
                    updateCharts()
                }
            }

            override fun onFailure(call: Call<List<RunningRecord>>, t: Throwable) {
                updateSummaryMetrics()
                updateCharts()
            }
        })
    }

    private fun updateSummaryMetrics() {
        val totalDist = allRecords.sumOf { it.distance }
        val totalCount = allRecords.size
        val totalSeconds = allRecords.sumOf { it.durationSeconds }
        val totalElev = allRecords.sumOf { it.totalElevation }

        tvTotalDistance.text = String.format("%.1fkm", totalDist)
        tvTotalCount.text = "${totalCount}회"

        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        tvTotalTime.text = if (hours > 0) {
            "${hours}시간 ${minutes}분"
        } else if (minutes > 0) {
            "${minutes}분"
        } else if (totalSeconds > 0) {
            "${totalSeconds}초"
        } else {
            "0분"
        }

        tvTotalElevation.text = String.format("+%dm", totalElev.roundToInt())
    }

    private fun extractDate(createdAt: String?): LocalDate {
        if (createdAt.isNullOrBlank() || createdAt.length < 10) return LocalDate.now()
        return try {
            LocalDate.parse(createdAt.substring(0, 10))
        } catch (e: Exception) {
            LocalDate.now()
        }
    }

    private fun updateCharts() {
        val today = LocalDate.now()
        val barLabels = mutableListOf<String>()
        val barValues = mutableListOf<Float>()
        val hrLabels = mutableListOf<String>()
        val hrValues = mutableListOf<Float>()

        if (isWeeklyMode) {
            val currentMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val weekStarts = (3 downTo 0).map { currentMonday.minusWeeks(it.toLong()) }

            for ((idx, weekStart) in weekStarts.withIndex()) {
                val weekEnd = weekStart.plusDays(6)

                // ✨ 날짜 대신 '3주전, 2주전, 지난주, 이번주'로 통일!
                val label = when (idx) {
                    3 -> "이번주"
                    2 -> "지난주"
                    1 -> "2주전"
                    else -> "3주전"
                }

                val recordsInWeek = allRecords.filter {
                    val d = extractDate(it.createdAt)
                    !d.isBefore(weekStart) && !d.isAfter(weekEnd)
                }

                val sumDist = recordsInWeek.sumOf { it.distance }.toFloat()
                barLabels.add(label)
                barValues.add(sumDist)

                val validHr = recordsInWeek.map { it.averageHeartRate }.filter { it > 40 }
                val avgHr = if (validHr.isNotEmpty()) validHr.average().toFloat() else 0f
                hrLabels.add(label)
                hrValues.add(avgHr)
            }

        } else {
            val currentMonth = YearMonth.from(today)
            val months = (3 downTo 0).map { currentMonth.minusMonths(it.toLong()) }

            for (ym in months) {
                val label = "${ym.monthValue}월"
                val recordsInMonth = allRecords.filter {
                    val d = extractDate(it.createdAt)
                    YearMonth.from(d) == ym
                }

                val sumDist = recordsInMonth.sumOf { it.distance }.toFloat()
                barLabels.add(label)
                barValues.add(sumDist)

                val validHr = recordsInMonth.map { it.averageHeartRate }.filter { it > 40 }
                val avgHr = if (validHr.isNotEmpty()) validHr.average().toFloat() else 0f
                hrLabels.add(label)
                hrValues.add(avgHr)
            }
        }

        barChartContainer.removeAllViews()
        barChartContainer.addView(CustomBarChartView(this, barLabels, barValues))

        lineChartContainer.removeAllViews()
        lineChartContainer.addView(CustomLineChartView(this, hrLabels, hrValues))
    }

    // 🎨 커스텀 막대 그래프 View
    private class CustomBarChartView(
        context: Context,
        private val labels: List<String>,
        private val values: List<Float>
    ) : View(context) {

        private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF6B35")
            style = Paint.Style.FILL
        }
        private val emptyBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#262626")
            style = Paint.Style.FILL
        }
        private val valueTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFFFFF")
            textSize = 30f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        private val labelTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#888888")
            textSize = 28f
            textAlign = Paint.Align.CENTER
        }
        private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#222222")
            strokeWidth = 2f
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (labels.isEmpty()) return

            val w = width.toFloat()
            val h = height.toFloat()
            val topPadding = 55f
            val bottomPadding = 55f
            val chartHeight = h - topPadding - bottomPadding

            canvas.drawLine(20f, h - bottomPadding, w - 20f, h - bottomPadding, gridPaint)

            val maxVal = (values.maxOrNull() ?: 0f).coerceAtLeast(5f)
            val colWidth = w / labels.size
            val barWidth = colWidth * 0.38f

            for (i in labels.indices) {
                val cx = colWidth * i + colWidth / 2f
                val v = values[i]
                val ratio = (v / maxVal).coerceIn(0f, 1f)
                val barH = if (v > 0f) (chartHeight * ratio).coerceAtLeast(12f) else 8f

                val left = cx - barWidth / 2f
                val right = cx + barWidth / 2f
                val bottom = h - bottomPadding
                val top = bottom - barH

                val rect = RectF(left, top, right, bottom)
                canvas.drawRoundRect(rect, 12f, 12f, if (v > 0f) barPaint else emptyBarPaint)

                val valStr = String.format("%.1fkm", v)
                canvas.drawText(valStr, cx, top - 14f, valueTextPaint)
                canvas.drawText(labels[i], cx, h - 15f, labelTextPaint)
            }
        }
    }

    // 🎨 커스텀 꺾은선 그래프 View
    private class CustomLineChartView(
        context: Context,
        private val labels: List<String>,
        private val values: List<Float>
    ) : View(context) {

        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF6B35")
            strokeWidth = 6f
            style = Paint.Style.STROKE
        }
        private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF6B35")
            style = Paint.Style.FILL
        }
        private val innerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#111111")
            style = Paint.Style.FILL
        }
        private val valueTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFFFFF")
            textSize = 28f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        private val labelTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#888888")
            textSize = 28f
            textAlign = Paint.Align.CENTER
        }
        private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#222222")
            strokeWidth = 2f
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (labels.isEmpty()) return

            val w = width.toFloat()
            val h = height.toFloat()
            val topPadding = 50f
            val bottomPadding = 50f
            val chartHeight = h - topPadding - bottomPadding

            canvas.drawLine(20f, h - bottomPadding, w - 20f, h - bottomPadding, gridPaint)

            val nonZeroValues = values.filter { it > 0f }
            val minVal = ((nonZeroValues.minOrNull() ?: 60f) - 15f).coerceAtLeast(40f)
            val maxVal = ((nonZeroValues.maxOrNull() ?: 180f) + 15f).coerceAtLeast(minVal + 30f)

            val colWidth = w / labels.size
            val path = Path()
            var firstPoint = true

            val points = mutableListOf<Triple<Float, Float, Float>>()

            for (i in labels.indices) {
                val cx = colWidth * i + colWidth / 2f
                val v = values[i]
                val cy = if (v > 0f) {
                    val ratio = ((v - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
                    (h - bottomPadding) - (chartHeight * ratio)
                } else {
                    h - bottomPadding
                }

                points.add(Triple(cx, cy, v))

                if (v > 0f) {
                    if (firstPoint) {
                        path.moveTo(cx, cy)
                        firstPoint = false
                    } else {
                        path.lineTo(cx, cy)
                    }
                }

                canvas.drawText(labels[i], cx, h - 12f, labelTextPaint)
            }

            canvas.drawPath(path, linePaint)

            for ((cx, cy, v) in points) {
                if (v > 0f) {
                    canvas.drawCircle(cx, cy, 10f, dotPaint)
                    canvas.drawCircle(cx, cy, 5f, innerDotPaint)
                    canvas.drawText("${v.roundToInt()}bpm", cx, cy - 18f, valueTextPaint)
                } else {
                    canvas.drawText("-", cx, cy - 15f, labelTextPaint)
                }
            }
        }
    }
}