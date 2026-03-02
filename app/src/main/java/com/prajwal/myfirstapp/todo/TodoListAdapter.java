package com.prajwal.myfirstapp.todo;


import com.prajwal.myfirstapp.tasks.CompletionRingView;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * RecyclerView adapter that renders a horizontal strip of TodoList cards.
 * The last item is always an "add new list" (+) card.
 */
public class TodoListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_LIST_CARD = 0;
    private static final int TYPE_ADD_CARD  = 1;

    // ─── Listener Interface ──────────────────────────────────────

    public interface OnListActionListener {
        void onListClick(TodoList list);
        void onAddListClick();
    }

    // ─── Fields ──────────────────────────────────────────────────

    private final Context             context;
    private final List<TodoList>      lists;
    private final TodoRepository      repo;
    private final OnListActionListener listener;

    // ─── Constructor ─────────────────────────────────────────────

    public TodoListAdapter(Context ctx, List<TodoList> lists,
                           TodoRepository repo, OnListActionListener listener) {
        this.context  = ctx;
        this.lists    = lists;
        this.repo     = repo;
        this.listener = listener;
    }

    // ─── ViewHolders ─────────────────────────────────────────────

    /** Holds views for a regular todo-list card. */
    static class ListCardHolder extends RecyclerView.ViewHolder {
        final LinearLayout  cardView;
        final TextView      tvListIcon;
        final TextView      tvListName;
        final TextView      tvTaskCount;
        final CompletionRingView completionRingView;
        final TextView      tvOverdueBadge;

        ListCardHolder(LinearLayout card, TextView icon, TextView name,
                       TextView count, CompletionRingView ring, TextView overdue) {
            super(card);
            this.cardView          = card;
            this.tvListIcon        = icon;
            this.tvListName        = name;
            this.tvTaskCount       = count;
            this.completionRingView = ring;
            this.tvOverdueBadge    = overdue;
        }
    }

    /** Holds the view for the "+" add-card. */
    static class AddCardHolder extends RecyclerView.ViewHolder {
        final LinearLayout cardView;
        final TextView     tvPlus;

        AddCardHolder(LinearLayout card, TextView plus) {
            super(card);
            this.cardView = card;
            this.tvPlus   = plus;
        }
    }

    // ─── Adapter Overrides ───────────────────────────────────────

    @Override
    public int getItemViewType(int position) {
        return (position < lists.size()) ? TYPE_LIST_CARD : TYPE_ADD_CARD;
    }

    @Override
    public int getItemCount() {
        return lists.size() + 1; // +1 for the add card
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_ADD_CARD) {
            return createAddCardHolder();
        }
        return createListCardHolder();
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (getItemViewType(position) == TYPE_ADD_CARD) {
            bindAddCard((AddCardHolder) holder);
        } else {
            bindListCard((ListCardHolder) holder, lists.get(position));
        }
    }

    // ─── ViewHolder Factories ────────────────────────────────────

    private ListCardHolder createListCardHolder() {
        float density = context.getResources().getDisplayMetrics().density;
        int   cardW   = (int) (140 * density);
        int   cardH   = (int) (160 * density);
        int   dp4     = (int) (4  * density);
        int   dp8     = (int) (8  * density);
        int   dp16    = (int) (16 * density);
        int   dp48    = (int) (48 * density);
        int   dp56    = (int) (56 * density);

        // Root card: vertical LinearLayout with rounded background
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        RecyclerView.LayoutParams cardParams =
                new RecyclerView.LayoutParams(cardW, cardH);
        cardParams.setMarginEnd(dp8);
        card.setLayoutParams(cardParams);
        card.setPadding(dp8, dp8, dp8, dp8);
        card.setClickable(true);
        card.setFocusable(true);

        // List icon (large emoji)
        TextView tvIcon = new TextView(context);
        tvIcon.setTextSize(28);
        tvIcon.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams iconParams =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp48);
        tvIcon.setLayoutParams(iconParams);

        // List name
        TextView tvName = new TextView(context);
        tvName.setTextSize(13);
        tvName.setTextColor(0xFFFFFFFF);
        tvName.setTypeface(null, Typeface.BOLD);
        tvName.setGravity(Gravity.CENTER);
        tvName.setMaxLines(2);
        tvName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nameParams =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        nameParams.topMargin = dp4;
        tvName.setLayoutParams(nameParams);

        // Task count
        TextView tvCount = new TextView(context);
        tvCount.setTextSize(11);
        tvCount.setTextColor(0xCCFFFFFF);
        tvCount.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams countParams =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        tvCount.setLayoutParams(countParams);

        // Completion ring
        CompletionRingView ring = new CompletionRingView(context);
        LinearLayout.LayoutParams ringParams =
                new LinearLayout.LayoutParams(dp56, dp56);
        ringParams.gravity    = Gravity.CENTER_HORIZONTAL;
        ringParams.topMargin  = dp4;
        ring.setLayoutParams(ringParams);

        // Overdue badge
        TextView tvOverdue = new TextView(context);
        tvOverdue.setTextSize(10);
        tvOverdue.setTextColor(0xFFFFFFFF);
        tvOverdue.setGravity(Gravity.CENTER);
        tvOverdue.setVisibility(View.GONE);
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setColor(0xFFEF4444);
        badgeBg.setCornerRadius(20 * density);
        tvOverdue.setBackground(badgeBg);
        tvOverdue.setPadding(dp8, dp4 / 2, dp8, dp4 / 2);
        LinearLayout.LayoutParams overdueParams =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        overdueParams.gravity   = Gravity.CENTER_HORIZONTAL;
        overdueParams.topMargin = dp4;
        tvOverdue.setLayoutParams(overdueParams);

        card.addView(tvIcon);
        card.addView(tvName);
        card.addView(tvCount);
        card.addView(ring);
        card.addView(tvOverdue);

        return new ListCardHolder(card, tvIcon, tvName, tvCount, ring, tvOverdue);
    }

    private AddCardHolder createAddCardHolder() {
        float density = context.getResources().getDisplayMetrics().density;
        int   cardW   = (int) (140 * density);
        int   cardH   = (int) (160 * density);
        int   dp8     = (int) (8   * density);

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        RecyclerView.LayoutParams cardParams =
                new RecyclerView.LayoutParams(cardW, cardH);
        cardParams.setMarginEnd(dp8);
        card.setLayoutParams(cardParams);

        // Gradient border background (amber-tinted)
        GradientDrawable borderBg = new GradientDrawable();
        borderBg.setShape(GradientDrawable.RECTANGLE);
        borderBg.setCornerRadius(18 * density);
        borderBg.setColor(0x0DFFFFFF);
        borderBg.setStroke((int) (1.5f * density), 0x33F59E0B);
        card.setBackground(borderBg);
        card.setClickable(true);
        card.setFocusable(true);

        TextView tvPlus = new TextView(context);
        tvPlus.setText("+");
        tvPlus.setTextSize(36);
        tvPlus.setTextColor(0x99FFFFFF);
        tvPlus.setGravity(Gravity.CENTER);
        card.addView(tvPlus);

        TextView tvLabel = new TextView(context);
        tvLabel.setText("New List");
        tvLabel.setTextSize(12);
        tvLabel.setTextColor(0x99FFFFFF);
        tvLabel.setGravity(Gravity.CENTER);
        card.addView(tvLabel);

        return new AddCardHolder(card, tvPlus);
    }

    // ─── Bind Methods ────────────────────────────────────────────

    private void bindListCard(ListCardHolder h, TodoList list) {
        float density = context.getResources().getDisplayMetrics().density;

        // Card background: gradient using colorHex
        int baseColor = parseColorSafe(list.colorHex, 0xFF6B7280);
        int darkColor = darkenColor(baseColor, 0.6f);
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{baseColor, darkColor});
        bg.setCornerRadius(18 * density);
        h.cardView.setBackground(bg);

        // Icon and name
        h.tvListIcon.setText(list.iconIdentifier != null ? list.iconIdentifier : "📋");
        h.tvListName.setText(list.title != null ? list.title : "");

        // Task counts from repo
        int total     = repo.getItemsByListId(list.id).size();
        int completed = repo.getCompletedItems(list.id).size();
        h.tvTaskCount.setText(completed + "/" + total + " tasks");

        // Completion ring
        float rate = repo.getCompletionRate(list.id);
        h.completionRingView.setProgress(rate);

        // Overdue badge
        int overdueCount = repo.getOverdueCount(list.id);
        if (overdueCount > 0) {
            h.tvOverdueBadge.setText(overdueCount + " overdue");
            h.tvOverdueBadge.setVisibility(View.VISIBLE);
        } else {
            h.tvOverdueBadge.setVisibility(View.GONE);
        }

        // Click
        h.cardView.setOnClickListener(v -> {
            if (listener != null) listener.onListClick(list);
        });
    }

    private void bindAddCard(AddCardHolder h) {
        h.cardView.setOnClickListener(v -> {
            if (listener != null) listener.onAddListClick();
        });
    }

    // ─── Color Helpers ───────────────────────────────────────────

    private int parseColorSafe(String hex, int fallback) {
        if (hex == null || hex.isEmpty()) return fallback;
        try {
            return Color.parseColor(hex);
        } catch (Exception e) {
            return fallback;
        }
    }

    /** Returns a darkened version of the given color by the given factor (0.0 = black, 1.0 = original). */
    private int darkenColor(int color, float factor) {
        int r = (int) (Color.red(color)   * factor);
        int g = (int) (Color.green(color) * factor);
        int b = (int) (Color.blue(color)  * factor);
        return Color.rgb(
                Math.max(0, Math.min(255, r)),
                Math.max(0, Math.min(255, g)),
                Math.max(0, Math.min(255, b)));
    }
}
