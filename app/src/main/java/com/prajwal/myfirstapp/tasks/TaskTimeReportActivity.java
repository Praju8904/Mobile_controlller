package com.prajwal.myfirstapp.tasks;

import android.graphics.Color;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.github.mikephil.charting.animation.Easing;
import com.github.mikephil.charting.charts.HorizontalBarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.prajwal.myfirstapp.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Time tracking report with three charts:
 *   1. Time per category (this week) — HorizontalBarChart
 *   2. Daily focus trend (past 14 days) — LineChart
 *   3. Estimated vs actual duration — HorizontalBarChart
 */
public class TaskTimeReportActivity extends AppCompatActivity {

    private TaskRepository repo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_task_time_report);

        repo = new TaskRepository(this);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        List<Task> tasks = repo.getAllTasks();

        setupCategoryChart(tasks);
        setupDailyTrendChart(tasks);
        setupEstVsActualChart(tasks);
    }

    // ─── Chart 1: Time per Category (This Week) ─────────────────

    private void setupCategoryChart(List<Task> tasks) {
        HorizontalBarChart chart = findViewById(R.id.chartCategory);
        if (chart == null) return;

        long startOfWeek = getStartOfWeekMillis();
        Map<String, Float> categoryMinutes = new LinkedHashMap<>();

        for (Task task : tasks) {
            if (task.timerSessions == null) continue;
            String cat = task.category != null ? task.category : "Uncategorized";
            for (long[] session : task.timerSessions) {
                if (session.length >= 2 && session[0] >= startOfWeek) {
                    float mins = (session[1] - session[0]) / 60000f;
                    categoryMinutes.merge(cat, mins, Float::sum);
                }
            }
        }

        if (categoryMinutes.isEmpty()) {
            chart.setNoDataText("No time tracked this week");
            chart.setNoDataTextColor(Color.parseColor("#94A3B8"));
            return;
        }

        List<BarEntry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        int i = 0;
        for (Map.Entry<String, Float> e : categoryMinutes.entrySet()) {
            entries.add(new BarEntry(i, e.getValue() / 60f)); // hours
            labels.add(e.getKey());
            i++;
        }

        BarDataSet dataSet = new BarDataSet(entries, "Hours");
        dataSet.setColors(getCategoryColors(labels.size()));
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setValueTextSize(11f);
        dataSet.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int hrs = (int) value;
                int mins = (int) ((value - hrs) * 60);
                return hrs > 0 ? hrs + "h " + mins + "m" : mins + "m";
            }
        });

        BarData data = new BarData(dataSet);
        data.setBarWidth(0.5f);
        chart.setData(data);

        styleBarChart(chart, labels);
        chart.animateY(1000, Easing.EaseOutCubic);
    }

    // ─── Chart 2: Daily Focus Trend (14 Days) ───────────────────

    private void setupDailyTrendChart(List<Task> tasks) {
        LineChart chart = findViewById(R.id.chartDailyTrend);
        if (chart == null) return;

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.add(Calendar.DAY_OF_YEAR, -13); // 14 days including today
        long startMs = cal.getTimeInMillis();

        List<Entry> entries = new ArrayList<>();
        List<String> dateLabels = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd", Locale.US);

        for (int day = 0; day < 14; day++) {
            long dayStart = startMs + (long) day * 86400000L;
            long dayEnd = dayStart + 86400000L;
            float totalMins = 0;

            for (Task task : tasks) {
                if (task.timerSessions == null) continue;
                for (long[] session : task.timerSessions) {
                    if (session.length >= 2 && session[0] >= dayStart && session[0] < dayEnd) {
                        totalMins += (session[1] - session[0]) / 60000f;
                    }
                }
            }

            entries.add(new Entry(day, totalMins / 60f)); // hours
            dateLabels.add(sdf.format(new Date(dayStart)));
        }

        LineDataSet dataSet = new LineDataSet(entries, "Focus (hours)");
        dataSet.setColor(Color.parseColor("#10B981"));
        dataSet.setLineWidth(2f);
        dataSet.setCircleColor(Color.parseColor("#10B981"));
        dataSet.setCircleRadius(3f);
        dataSet.setDrawFilled(true);
        dataSet.setFillColor(Color.parseColor("#10B981"));
        dataSet.setFillAlpha(50);
        dataSet.setValueTextColor(Color.TRANSPARENT);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);

        LineData data = new LineData(dataSet);
        chart.setData(data);

        // Styling
        chart.setBackgroundColor(Color.parseColor("#0A0F1E"));
        chart.getDescription().setEnabled(false);
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(false);

        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextColor(Color.parseColor("#94A3B8"));
        xAxis.setTextSize(10f);
        xAxis.setGranularity(1f);
        xAxis.setValueFormatter(new IndexAxisValueFormatter(dateLabels));
        xAxis.setLabelRotationAngle(-45f);
        xAxis.setGridColor(Color.parseColor("#FFFFFF0D"));

        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setTextColor(Color.parseColor("#94A3B8"));
        leftAxis.setTextSize(11f);
        leftAxis.setGridColor(Color.parseColor("#FFFFFF0D"));
        leftAxis.setAxisMinimum(0f);

        chart.getAxisRight().setEnabled(false);

        Legend legend = chart.getLegend();
        legend.setTextColor(Color.WHITE);
        legend.setTextSize(11f);
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);

        chart.animateX(1000, Easing.EaseOutCubic);
    }

    // ─── Chart 3: Estimated vs Actual Duration ──────────────────

    private void setupEstVsActualChart(List<Task> tasks) {
        HorizontalBarChart chart = findViewById(R.id.chartEstVsActual);
        if (chart == null) return;

        // Filter tasks with both estimated and actual
        List<Task> relevant = new ArrayList<>();
        for (Task t : tasks) {
            if (t.estimatedDuration > 0 && t.actualDuration > 0) {
                relevant.add(t);
            }
        }

        if (relevant.isEmpty()) {
            chart.setNoDataText("No tasks with both estimated and actual duration");
            chart.setNoDataTextColor(Color.parseColor("#94A3B8"));
            return;
        }

        // Sort by most overrun first
        relevant.sort((a, b) -> (b.actualDuration - b.estimatedDuration)
                - (a.actualDuration - a.estimatedDuration));

        // Limit to top 10
        if (relevant.size() > 10) relevant = relevant.subList(0, 10);

        List<BarEntry> estEntries = new ArrayList<>();
        List<BarEntry> actEntries = new ArrayList<>();
        List<String> labels = new ArrayList<>();

        for (int i = 0; i < relevant.size(); i++) {
            Task t = relevant.get(i);
            estEntries.add(new BarEntry(i, t.estimatedDuration / 60f)); // hours
            actEntries.add(new BarEntry(i, t.actualDuration / 60f));
            String title = t.title != null && t.title.length() > 20
                    ? t.title.substring(0, 20) + "…" : t.title;
            labels.add(title != null ? title : "Untitled");
        }

        BarDataSet estSet = new BarDataSet(estEntries, "Estimated");
        estSet.setColor(Color.parseColor("#3B82F6"));
        estSet.setValueTextColor(Color.WHITE);
        estSet.setValueTextSize(10f);

        BarDataSet actSet = new BarDataSet(actEntries, "Actual");
        // Color actual bars: green if under, red if over
        int[] actColors = new int[relevant.size()];
        for (int i = 0; i < relevant.size(); i++) {
            Task t = relevant.get(i);
            actColors[i] = t.actualDuration > t.estimatedDuration
                    ? Color.parseColor("#EF4444") : Color.parseColor("#10B981");
        }
        actSet.setColors(actColors);
        actSet.setValueTextColor(Color.WHITE);
        actSet.setValueTextSize(10f);

        float groupSpace = 0.1f;
        float barSpace = 0.05f;
        float barWidth = 0.4f;

        BarData data = new BarData(estSet, actSet);
        data.setBarWidth(barWidth);
        chart.setData(data);
        chart.groupBars(-0.5f, groupSpace, barSpace);

        styleBarChart(chart, labels);
        chart.animateY(1000, Easing.EaseOutCubic);
    }

    // ─── Helpers ─────────────────────────────────────────────────

    private void styleBarChart(HorizontalBarChart chart, List<String> labels) {
        chart.setBackgroundColor(Color.parseColor("#0A0F1E"));
        chart.getDescription().setEnabled(false);
        chart.setFitBars(true);
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(false);

        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextColor(Color.parseColor("#94A3B8"));
        xAxis.setTextSize(11f);
        xAxis.setGranularity(1f);
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        xAxis.setGridColor(Color.parseColor("#FFFFFF0D"));
        xAxis.setDrawGridLines(false);

        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setTextColor(Color.parseColor("#94A3B8"));
        leftAxis.setTextSize(11f);
        leftAxis.setGridColor(Color.parseColor("#FFFFFF0D"));
        leftAxis.setAxisMinimum(0f);

        chart.getAxisRight().setEnabled(false);

        Legend legend = chart.getLegend();
        legend.setTextColor(Color.WHITE);
        legend.setTextSize(11f);
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);
    }

    private long getStartOfWeekMillis() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());
        return cal.getTimeInMillis();
    }

    private int[] getCategoryColors(int count) {
        int[] palette = {
                Color.parseColor("#EF4444"), Color.parseColor("#F59E0B"),
                Color.parseColor("#10B981"), Color.parseColor("#3B82F6"),
                Color.parseColor("#6366F1"), Color.parseColor("#EC4899"),
                Color.parseColor("#06B6D4"), Color.parseColor("#A855F7"),
                Color.parseColor("#84CC16"), Color.parseColor("#F97316")
        };
        int[] colors = new int[count];
        for (int i = 0; i < count; i++) {
            colors[i] = palette[i % palette.length];
        }
        return colors;
    }
}
