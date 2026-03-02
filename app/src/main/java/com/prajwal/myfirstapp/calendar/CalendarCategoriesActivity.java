package com.prajwal.myfirstapp.calendar;


import com.prajwal.myfirstapp.R;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

import java.util.*;

/**
 * CalendarCategoriesActivity - Manage event categories.
 *
 * Features:
 *   - List all categories (default + custom) with color preview
 *   - Add new custom category (name, color, icon)
 *   - Edit existing custom categories
 *   - Delete custom categories with reassignment prompt
 *   - Cannot delete/edit default categories (but can change color)
 */
public class CalendarCategoriesActivity extends AppCompatActivity {

    private static final int COLOR_BG_PRIMARY     = 0xFF0A0E21;
    private static final int COLOR_BG_SURFACE     = 0xFF111827;
    private static final int COLOR_BG_ELEVATED    = 0xFF1E293B;
    private static final int COLOR_TEXT_PRIMARY    = 0xFFF1F5F9;
    private static final int COLOR_TEXT_SECONDARY  = 0xFF94A3B8;
    private static final int COLOR_TEXT_MUTED      = 0xFF64748B;
    private static final int COLOR_ACCENT          = 0xFF3B82F6;
    private static final int COLOR_DANGER          = 0xFFEF4444;
    private static final int COLOR_DIVIDER         = 0xFF1E293B;

    private CalendarRepository repository;
    private LinearLayout categoryListContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        repository = new CalendarRepository(this);
        buildUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshCategoryList();
    }

    // =====================================================================
    // UI CONSTRUCTION
    // =====================================================================

    private void buildUI() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(COLOR_BG_PRIMARY);
        scrollView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        scrollView.setFillViewport(true);

        LinearLayout mainContainer = new LinearLayout(this);
        mainContainer.setOrientation(LinearLayout.VERTICAL);
        mainContainer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(0xFF0D1117);
        header.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12));

        TextView btnBack = new TextView(this);
        btnBack.setText("\u2190");
        btnBack.setTextColor(COLOR_TEXT_PRIMARY);
        btnBack.setTextSize(20);
        btnBack.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));
        btnBack.setOnClickListener(v -> finish());
        header.addView(btnBack);

        TextView titleTv = new TextView(this);
        titleTv.setText("\uD83C\uDFF7\uFE0F Categories");
        titleTv.setTextColor(COLOR_TEXT_PRIMARY);
        titleTv.setTextSize(18);
        titleTv.setTypeface(null, Typeface.BOLD);
        titleTv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        titleTv.setPadding(dpToPx(12), 0, 0, 0);
        header.addView(titleTv);

        // Add button
        TextView addBtn = new TextView(this);
        addBtn.setText("+ New");
        addBtn.setTextColor(Color.WHITE);
        addBtn.setTextSize(13);
        addBtn.setTypeface(null, Typeface.BOLD);
        addBtn.setPadding(dpToPx(14), dpToPx(8), dpToPx(14), dpToPx(8));
        GradientDrawable addBg = new GradientDrawable();
        addBg.setCornerRadius(dpToPx(8));
        addBg.setColor(COLOR_ACCENT);
        addBtn.setBackground(addBg);
        addBtn.setOnClickListener(v -> showAddCategoryDialog());
        header.addView(addBtn);

        mainContainer.addView(header);

        // Description
        TextView desc = new TextView(this);
        desc.setText("Organize your events with categories. Default categories can be customized; custom categories can be deleted.");
        desc.setTextColor(COLOR_TEXT_MUTED);
        desc.setTextSize(12);
        desc.setPadding(dpToPx(20), dpToPx(12), dpToPx(20), dpToPx(8));
        mainContainer.addView(desc);

        // Category list
        categoryListContainer = new LinearLayout(this);
        categoryListContainer.setOrientation(LinearLayout.VERTICAL);
        categoryListContainer.setPadding(dpToPx(16), dpToPx(4), dpToPx(16), dpToPx(40));
        mainContainer.addView(categoryListContainer);

        scrollView.addView(mainContainer);
        setContentView(scrollView);

        refreshCategoryList();
    }

    private void refreshCategoryList() {
        categoryListContainer.removeAllViews();
        List<EventCategory> categories = repository.getAllCategories();

        // Default categories section
        addSectionHeader("Default Categories");
        for (EventCategory cat : categories) {
            if (cat.isDefault) {
                categoryListContainer.addView(buildCategoryItem(cat));
                addVertSpacer(8);
            }
        }

        // Custom categories section
        boolean hasCustom = false;
        for (EventCategory cat : categories) {
            if (!cat.isDefault) { hasCustom = true; break; }
        }

        if (hasCustom) {
            addVertSpacer(8);
            addSectionHeader("Custom Categories");
            for (EventCategory cat : categories) {
                if (!cat.isDefault) {
                    categoryListContainer.addView(buildCategoryItem(cat));
                    addVertSpacer(8);
                }
            }
        }
    }

    private LinearLayout buildCategoryItem(EventCategory cat) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dpToPx(14), dpToPx(14), dpToPx(14), dpToPx(14));
        card.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setCornerRadius(dpToPx(12));
        cardBg.setColor(COLOR_BG_SURFACE);
        card.setBackground(cardBg);

        // Color dot
        View colorDot = new View(this);
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dpToPx(14), dpToPx(14));
        dotParams.setMargins(0, 0, dpToPx(12), 0);
        colorDot.setLayoutParams(dotParams);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(cat.getColor());
        colorDot.setBackground(dotBg);
        card.addView(colorDot);

        // Icon + Name
        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView nameTv = new TextView(this);
        nameTv.setText(cat.iconIdentifier + "  " + cat.name);
        nameTv.setTextColor(COLOR_TEXT_PRIMARY);
        nameTv.setTextSize(15);
        nameTv.setTypeface(null, Typeface.BOLD);
        textCol.addView(nameTv);

        // Event count
        int count = repository.getEventsByCategory(cat.id).size();
        TextView countTv = new TextView(this);
        countTv.setText(count + " event" + (count != 1 ? "s" : ""));
        countTv.setTextColor(COLOR_TEXT_MUTED);
        countTv.setTextSize(11);
        textCol.addView(countTv);

        card.addView(textCol);

        // Default badge
        if (cat.isDefault) {
            TextView defaultBadge = new TextView(this);
            defaultBadge.setText("DEFAULT");
            defaultBadge.setTextColor(COLOR_TEXT_MUTED);
            defaultBadge.setTextSize(9);
            defaultBadge.setPadding(dpToPx(6), dpToPx(2), dpToPx(6), dpToPx(2));
            GradientDrawable badgeBg = new GradientDrawable();
            badgeBg.setCornerRadius(dpToPx(4));
            badgeBg.setStroke(dpToPx(1), COLOR_TEXT_MUTED);
            defaultBadge.setBackground(badgeBg);
            card.addView(defaultBadge);
            addHSpacer(card, 8);
        }

        // Menu
        TextView menuBtn = new TextView(this);
        menuBtn.setText("\u22EE");
        menuBtn.setTextColor(COLOR_TEXT_SECONDARY);
        menuBtn.setTextSize(18);
        menuBtn.setPadding(dpToPx(8), 0, dpToPx(4), 0);
        menuBtn.setOnClickListener(v -> showCategoryMenu(cat));
        card.addView(menuBtn);

        return card;
    }

    // =====================================================================
    // DIALOGS
    // =====================================================================

    private void showCategoryMenu(EventCategory cat) {
        List<String> options = new ArrayList<>();
        options.add("\u270F\uFE0F Edit");
        if (!cat.isDefault) {
            options.add("\uD83D\uDDD1\uFE0F Delete");
        }

        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setItems(options.toArray(new String[0]), (dialog, which) -> {
                    if (which == 0) {
                        showEditCategoryDialog(cat);
                    } else if (which == 1) {
                        confirmDeleteCategory(cat);
                    }
                })
                .show();
    }

    private void showAddCategoryDialog() {
        showCategoryFormDialog(null);
    }

    private void showEditCategoryDialog(EventCategory cat) {
        showCategoryFormDialog(cat);
    }

    private void showCategoryFormDialog(EventCategory existing) {
        boolean isEdit = existing != null;

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dpToPx(20), dpToPx(16), dpToPx(20), dpToPx(8));

        // Name
        TextView nameLabel = new TextView(this);
        nameLabel.setText("Category Name");
        nameLabel.setTextColor(COLOR_TEXT_SECONDARY);
        nameLabel.setTextSize(12);
        form.addView(nameLabel);

        EditText etName = new EditText(this);
        etName.setHint("e.g. Meetings");
        etName.setHintTextColor(COLOR_TEXT_MUTED);
        etName.setTextColor(COLOR_TEXT_PRIMARY);
        etName.setTextSize(14);
        etName.setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10));
        GradientDrawable nameBg = new GradientDrawable();
        nameBg.setCornerRadius(dpToPx(8));
        nameBg.setColor(COLOR_BG_ELEVATED);
        etName.setBackground(nameBg);
        if (isEdit) etName.setText(existing.name);
        form.addView(etName);

        addDialogSpacer(form);

        // Icon
        TextView iconLabel = new TextView(this);
        iconLabel.setText("Icon (emoji)");
        iconLabel.setTextColor(COLOR_TEXT_SECONDARY);
        iconLabel.setTextSize(12);
        form.addView(iconLabel);

        EditText etIcon = new EditText(this);
        etIcon.setHint("\uD83D\uDCCC");
        etIcon.setHintTextColor(COLOR_TEXT_MUTED);
        etIcon.setTextColor(COLOR_TEXT_PRIMARY);
        etIcon.setTextSize(18);
        etIcon.setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10));
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setCornerRadius(dpToPx(8));
        iconBg.setColor(COLOR_BG_ELEVATED);
        etIcon.setBackground(iconBg);
        if (isEdit) etIcon.setText(existing.iconIdentifier);
        form.addView(etIcon);

        addDialogSpacer(form);

        // Color picker
        TextView colorLabel = new TextView(this);
        colorLabel.setText("Color");
        colorLabel.setTextColor(COLOR_TEXT_SECONDARY);
        colorLabel.setTextSize(12);
        form.addView(colorLabel);

        final String[] selectedColor = {isEdit ? existing.colorHex : "#3B82F6"};

        LinearLayout colorRow = new LinearLayout(this);
        colorRow.setOrientation(LinearLayout.HORIZONTAL);
        colorRow.setPadding(0, dpToPx(8), 0, dpToPx(4));

        String[] colors = {"#3B82F6", "#6366F1", "#8B5CF6", "#22C55E", "#F97316",
                "#EC4899", "#EF4444", "#14B8A6", "#F59E0B", "#6B7280"};

        for (String hex : colors) {
            View colorBtn = new View(this);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dpToPx(28), dpToPx(28));
            cp.setMargins(dpToPx(3), 0, dpToPx(3), 0);
            colorBtn.setLayoutParams(cp);
            GradientDrawable cBg = new GradientDrawable();
            cBg.setShape(GradientDrawable.OVAL);
            try { cBg.setColor(Color.parseColor(hex)); } catch (Exception e) { cBg.setColor(COLOR_ACCENT); }
            if (hex.equals(selectedColor[0])) {
                cBg.setStroke(dpToPx(3), Color.WHITE);
            }
            colorBtn.setBackground(cBg);
            colorBtn.setOnClickListener(v -> {
                selectedColor[0] = hex;
                // Refresh all color buttons
                for (int i = 0; i < colorRow.getChildCount(); i++) {
                    View child = colorRow.getChildAt(i);
                    GradientDrawable bd = new GradientDrawable();
                    bd.setShape(GradientDrawable.OVAL);
                    try { bd.setColor(Color.parseColor(colors[i])); } catch (Exception e) { bd.setColor(COLOR_ACCENT); }
                    if (colors[i].equals(hex)) bd.setStroke(dpToPx(3), Color.WHITE);
                    child.setBackground(bd);
                }
            });
            colorRow.addView(colorBtn);
        }
        form.addView(colorRow);

        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle(isEdit ? "Edit Category" : "New Category")
                .setView(form)
                .setPositiveButton("Save", (dialog, which) -> {
                    String name = etName.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(this, "Name is required", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String icon = etIcon.getText().toString().trim();
                    if (icon.isEmpty()) icon = "\uD83D\uDCCC";

                    if (isEdit) {
                        existing.name = name;
                        existing.iconIdentifier = icon;
                        existing.colorHex = selectedColor[0];
                        repository.updateCategory(existing);
                    } else {
                        EventCategory newCat = new EventCategory();
                        newCat.name = name;
                        newCat.iconIdentifier = icon;
                        newCat.colorHex = selectedColor[0];
                        newCat.isDefault = false;
                        repository.addCategory(newCat);
                    }
                    refreshCategoryList();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmDeleteCategory(EventCategory cat) {
        List<CalendarEvent> events = repository.getEventsByCategory(cat.id);

        if (events.isEmpty()) {
            new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                    .setTitle("Delete Category")
                    .setMessage("Delete '" + cat.name + "'?")
                    .setPositiveButton("Delete", (d, w) -> {
                        repository.deleteCategory(cat.id);
                        refreshCategoryList();
                        Toast.makeText(this, "Category deleted", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        } else {
            // Need to reassign events
            List<EventCategory> otherCats = repository.getAllCategories();
            List<String> names = new ArrayList<>();
            List<String> ids = new ArrayList<>();
            for (EventCategory c : otherCats) {
                if (!c.id.equals(cat.id)) {
                    names.add(c.iconIdentifier + " " + c.name);
                    ids.add(c.id);
                }
            }

            new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                    .setTitle("Delete '" + cat.name + "'?")
                    .setMessage(events.size() + " event(s) use this category. Reassign them to:")
                    .setItems(names.toArray(new String[0]), (dialog, which) -> {
                        String newCatId = ids.get(which);
                        for (CalendarEvent event : events) {
                            event.categoryId = newCatId;
                            repository.updateEvent(event);
                        }
                        repository.deleteCategory(cat.id);
                        refreshCategoryList();
                        Toast.makeText(this, "Category deleted, events reassigned", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }
    }

    // =====================================================================
    // HELPERS
    // =====================================================================

    private void addSectionHeader(String title) {
        TextView header = new TextView(this);
        header.setText(title);
        header.setTextColor(COLOR_TEXT_MUTED);
        header.setTextSize(12);
        header.setTypeface(null, Typeface.BOLD);
        header.setPadding(dpToPx(4), dpToPx(12), 0, dpToPx(6));
        categoryListContainer.addView(header);
    }

    private void addVertSpacer(int dp) {
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(dp)));
        categoryListContainer.addView(spacer);
    }

    private void addHSpacer(LinearLayout parent, int dp) {
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(dp), dpToPx(1)));
        parent.addView(spacer);
    }

    private void addDialogSpacer(LinearLayout parent) {
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(12)));
        parent.addView(spacer);
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}
