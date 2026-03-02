package com.prajwal.myfirstapp.notes;


import com.prajwal.myfirstapp.timecapsule.TimeCapsuleActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

/**
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *  STUDY DASHBOARD ACTIVITY — Central hub for all study features.
 *  Provides entry points to flashcards, quiz, study sessions, progress, and concept map.
 * ═══════════════════════════════════════════════════════════════════════════════════════
 */
public class StudyDashboardActivity extends AppCompatActivity {

    // ═══════════════════════════════════════════════════════════════════════════════
    //  FIELDS
    // ═══════════════════════════════════════════════════════════════════════════════

    private FlashcardManager flashcardManager;
    private StudySessionManager sessionManager;
    private StudyProgressTracker progressTracker;

    // ═══════════════════════════════════════════════════════════════════════════════
    //  LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(0xFF0A0E21);
        getWindow().setNavigationBarColor(0xFF0A0E21);

        flashcardManager = new FlashcardManager(this);
        sessionManager = new StudySessionManager(this);
        progressTracker = new StudyProgressTracker(this);

        buildUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh stats when returning from child activities
        updateQuickStats();
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  UI BUILDING
    // ═══════════════════════════════════════════════════════════════════════════════

    private TextView tvDueCards, tvStreak, tvRetention;

    private void buildUI() {
        // Build layout programmatically — dark theme, card-based dashboard
        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        scrollView.setBackgroundColor(0xFF0A0E21);
        scrollView.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(48), dp(16), dp(24));

        // ── Top Bar ──
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView btnBack = new TextView(this);
        btnBack.setText("←");
        btnBack.setTextSize(24);
        btnBack.setTextColor(0xFFF1F5F9);
        btnBack.setPadding(dp(8), dp(8), dp(16), dp(8));
        btnBack.setOnClickListener(v -> finish());
        topBar.addView(btnBack);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("📚 Study Mode");
        tvTitle.setTextColor(0xFFF1F5F9);
        tvTitle.setTextSize(22);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        topBar.addView(tvTitle);

        root.addView(topBar);
        root.addView(createSpacer(16));

        // ── Quick Stats Row ──
        LinearLayout statsRow = new LinearLayout(this);
        statsRow.setOrientation(LinearLayout.HORIZONTAL);

        tvDueCards = createStatBadge("Due Cards", "0");
        tvStreak = createStatBadge("Streak", "0 days");
        tvRetention = createStatBadge("Retention", "0%");

        LinearLayout.LayoutParams statLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        statLp.setMargins(dp(4), 0, dp(4), 0);

        tvDueCards.setLayoutParams(statLp);
        tvStreak.setLayoutParams(statLp);
        tvRetention.setLayoutParams(statLp);

        statsRow.addView(tvDueCards);
        statsRow.addView(tvStreak);
        statsRow.addView(tvRetention);
        root.addView(statsRow);
        root.addView(createSpacer(24));

        // ── Feature Cards ──
        root.addView(createFeatureCard("📇 Flashcards",
                "Review your flashcard decks with spaced repetition",
                "Review Due Cards", 0xFF3B82F6,
                v -> startActivity(new Intent(this, FlashcardListActivity.class))));
        root.addView(createSpacer(12));

        root.addView(createFeatureCard("🍅 Study Session",
                "Pomodoro timer with ambient sounds for focused study",
                "Start Session", 0xFFEF4444,
                v -> startActivity(new Intent(this, StudySessionActivity.class))));
        root.addView(createSpacer(12));

        root.addView(createFeatureCard("🧩 Quiz Mode",
                "Test yourself with auto-generated quizzes from your notes",
                "Take Quiz", 0xFF8B5CF6,
                v -> {
                    Intent intent = new Intent(this, QuizActivity.class);
                    intent.putExtra("source", "all");
                    startActivity(intent);
                }));
        root.addView(createSpacer(12));

        root.addView(createFeatureCard("🗺️ Concept Map",
                "Visualize connections between your notes",
                "View Map", 0xFF10B981,
                v -> startActivity(new Intent(this, ConceptMapActivity.class))));
        root.addView(createSpacer(12));

        root.addView(createFeatureCard("📊 Progress",
                "Track your study streak, achievements, and mastery",
                "View Progress", 0xFFF59E0B,
                v -> startActivity(new Intent(this, StudyProgressActivity.class))));
        root.addView(createSpacer(12));

        root.addView(createFeatureCard("⏰ Time Capsules",
                "Lock notes and rediscover them in the future",
                "Manage Capsules", 0xFF06B6D4,
                v -> startActivity(new Intent(this, TimeCapsuleActivity.class))));

        scrollView.addView(root);
        setContentView(scrollView);

        updateQuickStats();
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  STATS
    // ═══════════════════════════════════════════════════════════════════════════════

    private void updateQuickStats() {
        int dueCount = flashcardManager.getDueCards().size();
        tvDueCards.setText("Due Cards\n" + dueCount);

        StudyProgressTracker.ProgressSnapshot snap = progressTracker.getSnapshot(flashcardManager, sessionManager, new QuizResultsManager(this));
        tvStreak.setText("Streak\n" + snap.dailyStreak + " days");
        tvRetention.setText("Retention\n" + snap.overallRetention + "%");
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  UI HELPERS
    // ═══════════════════════════════════════════════════════════════════════════════

    private MaterialCardView createFeatureCard(String title, String desc, String btnText,
                                                int accentColor, View.OnClickListener onClick) {
        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(0xFF0F172A);
        card.setRadius(dp(16));
        card.setCardElevation(0);
        card.setStrokeWidth(dp(1));
        card.setStrokeColor(0xFF1E293B);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(20), dp(20), dp(20));

        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextColor(0xFFF1F5F9);
        tvTitle.setTextSize(18);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        content.addView(tvTitle);

        content.addView(createSpacer(4));

        TextView tvDesc = new TextView(this);
        tvDesc.setText(desc);
        tvDesc.setTextColor(0xFF94A3B8);
        tvDesc.setTextSize(14);
        content.addView(tvDesc);

        content.addView(createSpacer(12));

        MaterialButton btn = new MaterialButton(this);
        btn.setText(btnText);
        btn.setTextColor(0xFF0A0E21);
        btn.setBackgroundColor(accentColor);
        btn.setCornerRadius(dp(12));
        btn.setOnClickListener(onClick);
        content.addView(btn);

        card.addView(content);
        return card;
    }

    private TextView createStatBadge(String label, String value) {
        TextView tv = new TextView(this);
        tv.setText(label + "\n" + value);
        tv.setTextColor(0xFFF1F5F9);
        tv.setTextSize(14);
        tv.setGravity(android.view.Gravity.CENTER);
        tv.setBackgroundColor(0xFF0F172A);
        tv.setPadding(dp(12), dp(16), dp(12), dp(16));
        return tv;
    }

    private View createSpacer(int heightDp) {
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(heightDp)));
        return spacer;
    }

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}
