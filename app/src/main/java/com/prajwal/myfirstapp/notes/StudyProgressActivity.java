package com.prajwal.myfirstapp.notes;


import com.prajwal.myfirstapp.R;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *  STUDY PROGRESS ACTIVITY — Dashboard showing flashcard mastery,
 *  quiz performance, topic strengths, achievements, and streak.
 * ═══════════════════════════════════════════════════════════════════════════════════════
 */
public class StudyProgressActivity extends AppCompatActivity {

    private StudyProgressTracker progressTracker;
    private FlashcardManager flashcardManager;
    private StudySessionManager sessionManager;
    private QuizResultsManager quizResultsManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_study_progress);
        getWindow().setStatusBarColor(0xFF0A0E21);
        getWindow().setNavigationBarColor(0xFF0A0E21);

        flashcardManager = new FlashcardManager(this);
        sessionManager = new StudySessionManager(this);
        quizResultsManager = new QuizResultsManager(this);
        progressTracker = new StudyProgressTracker(this);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        loadData();
    }

    private void loadData() {
        StudyProgressTracker.ProgressSnapshot snapshot = progressTracker.getSnapshot(flashcardManager, sessionManager, quizResultsManager);

        // Overview
        ((TextView) findViewById(R.id.tvMasteredCards)).setText(String.valueOf(snapshot.masteredCards));
        ((TextView) findViewById(R.id.tvDueToday)).setText(String.valueOf(snapshot.dueCards));
        ((TextView) findViewById(R.id.tvRetention)).setText(snapshot.overallRetention + "%");

        // Streak
        ((TextView) findViewById(R.id.tvStreakDays)).setText(snapshot.dailyStreak + " days");

        // Build weekly dots
        LinearLayout weekDots = findViewById(R.id.layoutWeekDots);
        weekDots.removeAllViews();
        List<StudyProgressTracker.DayProgress> weekData = snapshot.weeklyProgress;
        String[] dayLabels = {"M", "T", "W", "T", "F", "S", "S"};
        for (int i = 0; i < weekData.size() && i < 7; i++) {
            StudyProgressTracker.DayProgress dp = weekData.get(i);
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(android.view.Gravity.CENTER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            col.setLayoutParams(lp);

            View dot = new View(this);
            int size = dpToPx(20);
            LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(size, size);
            dotLp.bottomMargin = dpToPx(4);
            dot.setLayoutParams(dotLp);
            boolean studied = dp.focusMinutes > 0 || dp.cardsReviewed > 0 || dp.quizScore > 0;
            dot.setBackgroundColor(studied ? 0xFF10B981 : 0xFF1E293B);
            col.addView(dot);

            TextView label = new TextView(this);
            label.setText(i < dayLabels.length ? dayLabels[i] : "");
            label.setTextColor(0xFF64748B);
            label.setTextSize(10);
            label.setGravity(android.view.Gravity.CENTER);
            col.addView(label);

            weekDots.addView(col);
        }

        // Quiz performance
        ((TextView) findViewById(R.id.tvQuizzesTaken)).setText(String.valueOf(snapshot.totalQuizzes));
        ((TextView) findViewById(R.id.tvAvgScore)).setText(snapshot.avgQuizScore + "%");
        // bestQuizScore not directly available, show avg instead
        ((TextView) findViewById(R.id.tvBestScore)).setText(snapshot.avgQuizScore > 0 ? snapshot.avgQuizScore + "%" : "--");

        // Topic strength
        RecyclerView rvTopics = findViewById(R.id.rvTopicStrength);
        rvTopics.setLayoutManager(new LinearLayoutManager(this));
        List<StudyProgressTracker.TopicStrength> topics = progressTracker.getTopicStrengths(flashcardManager);
        rvTopics.setAdapter(new TopicStrengthAdapter(topics));

        // Achievements
        RecyclerView rvAchievements = findViewById(R.id.rvAchievements);
        rvAchievements.setLayoutManager(new LinearLayoutManager(this));
        List<StudyProgressTracker.Achievement> achievements = snapshot.achievements;
        rvAchievements.setAdapter(new AchievementAdapter(achievements));
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  TOPIC STRENGTH ADAPTER
    // ═══════════════════════════════════════════════════════════════════════════════

    private static class TopicStrengthAdapter extends RecyclerView.Adapter<TopicStrengthAdapter.VH> {
        private final List<StudyProgressTracker.TopicStrength> items;

        TopicStrengthAdapter(List<StudyProgressTracker.TopicStrength> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_topic_strength, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            StudyProgressTracker.TopicStrength ts = items.get(position);
            holder.tvName.setText(ts.topic);
            holder.tvLabel.setText(ts.strengthLevel);
            holder.progress.setProgress(ts.avgRetention);

            int color;
            switch (ts.strengthLevel) {
                case "Strong": color = 0xFF10B981; break;
                case "Moderate": color = 0xFFF59E0B; break;
                default: color = 0xFFEF4444; break;
            }
            holder.tvLabel.setTextColor(color);
        }

        @Override
        public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvLabel;
            ProgressBar progress;
            VH(View v) {
                super(v);
                tvName = v.findViewById(R.id.tvTopicName);
                tvLabel = v.findViewById(R.id.tvStrengthLabel);
                progress = v.findViewById(R.id.progressStrength);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  ACHIEVEMENT ADAPTER
    // ═══════════════════════════════════════════════════════════════════════════════

    private static class AchievementAdapter extends RecyclerView.Adapter<AchievementAdapter.VH> {
        private final List<StudyProgressTracker.Achievement> items;

        AchievementAdapter(List<StudyProgressTracker.Achievement> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_achievement, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            StudyProgressTracker.Achievement ach = items.get(position);
            holder.tvIcon.setText(ach.icon);
            holder.tvTitle.setText(ach.title);
            holder.tvDesc.setText(ach.description);
            holder.tvUnlocked.setVisibility(ach.unlocked ? View.VISIBLE : View.GONE);

            // Dim locked achievements
            holder.itemView.setAlpha(ach.unlocked ? 1f : 0.4f);
        }

        @Override
        public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvIcon, tvTitle, tvDesc, tvUnlocked;
            VH(View v) {
                super(v);
                tvIcon = v.findViewById(R.id.tvAchievementIcon);
                tvTitle = v.findViewById(R.id.tvAchievementTitle);
                tvDesc = v.findViewById(R.id.tvAchievementDesc);
                tvUnlocked = v.findViewById(R.id.tvUnlocked);
            }
        }
    }
}
