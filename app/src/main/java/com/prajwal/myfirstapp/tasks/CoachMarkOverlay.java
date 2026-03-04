package com.prajwal.myfirstapp.tasks;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Coach mark overlay for the task manager onboarding tour (Feature 14C, gap 14.8).
 * Shows a spotlight cutout over each target view with a tooltip card.
 * Usage: CoachMarkOverlay.showIfNeeded(activity)
 */
public class CoachMarkOverlay extends View {

    private static final String PREF_KEY  = "task_onboarding_done";
    private static final String PREF_FILE = "task_prefs";

    // Step data
    private final RectF spotlightRect = new RectF();

    // Tooltip card views (added programmatically to parent)
    LinearLayout tooltipCard;
    TextView tvTooltipTitle;
    TextView tvTooltipBody;
    Button   btnNext;
    Button   btnSkip;

    // Paint for overlay
    private final Paint overlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cutoutPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);

    public CoachMarkOverlay(@NonNull Context context) { super(context); init(); }
    public CoachMarkOverlay(@NonNull Context context, @Nullable AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        setLayerType(LAYER_TYPE_HARDWARE, null);
        overlayPaint.setColor(0xCC0A0F1E); // #0A0F1E at ~80% alpha
        overlayPaint.setStyle(Paint.Style.FILL);
        cutoutPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        cutoutPaint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        // Draw full overlay
        canvas.drawRect(0, 0, getWidth(), getHeight(), overlayPaint);
        // Cut out spotlight
        if (spotlightRect.width() > 0) {
            canvas.drawRoundRect(spotlightRect, 16f, 16f, cutoutPaint);
        }
    }

    public void setSpotlight(Rect viewBounds) {
        float padding = 16f;
        spotlightRect.set(
            viewBounds.left - padding,
            viewBounds.top - padding,
            viewBounds.right + padding,
            viewBounds.bottom + padding
        );
        invalidate();
    }

    public void setTooltipContent(String title, String body) {
        if (tvTooltipTitle != null) tvTooltipTitle.setText(title);
        if (tvTooltipBody  != null) tvTooltipBody.setText(body);
    }

    // ─── Static entry point ──────────────────────────────────────

    /**
     * Show the onboarding tour if it hasn't been completed yet.
     * Call from TaskManagerActivity.onCreate().
     */
    public static void showIfNeeded(AppCompatActivity activity) {
        SharedPreferences prefs = activity.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
        if (prefs.getBoolean(PREF_KEY, false)) return;
        new OnboardingTour(activity).start();
    }

    public static void markDone(Context context) {
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
               .edit().putBoolean(PREF_KEY, true).apply();
    }

    // ─── Tour orchestrator ───────────────────────────────────────

    public static class OnboardingTour {
        private static final String[][] STEPS = {
            {"⭐ Add tasks instantly",  "Tap ＋ to create a task with smart date and priority detection."},
            {"📋 Today's focus",        "Today shows tasks due now and overdue. Upcoming shows the next 7 days."},
            {"↔ Swipe to act",          "Swipe right to complete a task. Swipe left to push it to tomorrow."},
            {"🔍 Find anything",        "Search by title, notes, or tags. Use filters to precisely narrow your list."}
        };
        private static final String[] NEXT_LABELS  = {"Next →", "Next →", "Next →", "Done ✓"};
        private static final String[] TARGET_IDS   = {"fab_add_task", "filter_chip_today", "task_list", "toolbar_search"};

        private final AppCompatActivity activity;
        private int currentStep = 0;
        private CoachMarkOverlay overlay;

        public OnboardingTour(AppCompatActivity activity) {
            this.activity = activity;
        }

        public void start() {
            ViewGroup root = activity.getWindow().getDecorView().findViewById(android.R.id.content);
            overlay = new CoachMarkOverlay(activity);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            );
            root.post(() -> {
                ((ViewGroup) root).addView(overlay, lp);
                buildTooltipCard(root);
                showStep(0);
            });
        }

        private void buildTooltipCard(ViewGroup root) {
            overlay.tooltipCard = new LinearLayout(activity);
            overlay.tooltipCard.setOrientation(LinearLayout.VERTICAL);
            overlay.tooltipCard.setPadding(40, 32, 40, 32);
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setColor(0xFF1A1F35);
            bg.setCornerRadius(16f);
            bg.setStroke(2, 0x33FFFFFF);
            overlay.tooltipCard.setBackground(bg);

            overlay.tvTooltipTitle = new TextView(activity);
            overlay.tvTooltipTitle.setTextColor(0xFFFFFFFF);
            overlay.tvTooltipTitle.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f);
            overlay.tvTooltipTitle.setTypeface(null, Typeface.BOLD);

            overlay.tvTooltipBody = new TextView(activity);
            overlay.tvTooltipBody.setTextColor(0xFF94A3B8);
            overlay.tvTooltipBody.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f);
            LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            bodyLp.topMargin = 8;
            overlay.tvTooltipBody.setLayoutParams(bodyLp);

            LinearLayout buttonRow = new LinearLayout(activity);
            buttonRow.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams btnRowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            btnRowLp.topMargin = 24;
            buttonRow.setLayoutParams(btnRowLp);

            overlay.btnSkip = new Button(activity);
            overlay.btnSkip.setText("Skip");
            overlay.btnSkip.setTextColor(0xFF94A3B8);
            overlay.btnSkip.setBackground(null);
            overlay.btnSkip.setOnClickListener(v -> dismiss());

            overlay.btnNext = new Button(activity);
            overlay.btnNext.setText("Next →");
            overlay.btnNext.setTextColor(0xFF6366F1);
            overlay.btnNext.setBackground(null);
            LinearLayout.LayoutParams nextLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            overlay.btnNext.setLayoutParams(nextLp);
            overlay.btnNext.setOnClickListener(v -> advance());

            buttonRow.addView(overlay.btnSkip);
            buttonRow.addView(overlay.btnNext);

            overlay.tooltipCard.addView(overlay.tvTooltipTitle);
            overlay.tooltipCard.addView(overlay.tvTooltipBody);
            overlay.tooltipCard.addView(buttonRow);

            FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(
                700, ViewGroup.LayoutParams.WRAP_CONTENT);
            cardLp.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL;
            cardLp.bottomMargin = 160;
            ((ViewGroup) root).addView(overlay.tooltipCard, cardLp);
        }

        private void showStep(int step) {
            currentStep = step;
            String[] s = STEPS[step];
            overlay.tvTooltipTitle.setText(s[0]);
            overlay.tvTooltipBody.setText(s[1]);
            overlay.btnNext.setText(NEXT_LABELS[step]);
            overlay.btnSkip.setVisibility(step < STEPS.length - 1 ? View.VISIBLE : View.GONE);
            View target = findViewByName(TARGET_IDS[step]);
            if (target != null) {
                int[] loc = new int[2];
                target.getLocationOnScreen(loc);
                overlay.setSpotlight(new Rect(loc[0], loc[1],
                    loc[0] + target.getWidth(), loc[1] + target.getHeight()));
            }
        }

        private void advance() {
            if (currentStep < STEPS.length - 1) {
                showStep(currentStep + 1);
            } else {
                dismiss();
            }
        }

        private void dismiss() {
            CoachMarkOverlay.markDone(activity);
            ViewGroup root = activity.getWindow().getDecorView().findViewById(android.R.id.content);
            ObjectAnimator fadeOut = ObjectAnimator.ofFloat(overlay, "alpha", 1f, 0f);
            fadeOut.setDuration(400);
            fadeOut.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    try { ((ViewGroup) root).removeView(overlay); } catch (Exception ignored) {}
                    try { ((ViewGroup) root).removeView(overlay.tooltipCard); } catch (Exception ignored) {}
                }
            });
            fadeOut.start();
        }

        private View findViewByName(String id) {
            try {
                int resId = activity.getResources().getIdentifier(id, "id", activity.getPackageName());
                if (resId != 0) return activity.findViewById(resId);
            } catch (Exception ignored) {}
            return null;
        }
    }
}
