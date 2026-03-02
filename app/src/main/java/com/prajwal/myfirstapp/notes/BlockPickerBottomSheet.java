package com.prajwal.myfirstapp.notes;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.List;

/**
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *  BlockPickerBottomSheet — Searchable grid of all 21 block types.
 *  Opens from "+" buttons in the editor to let users insert any block type.
 * ═══════════════════════════════════════════════════════════════════════════════════════
 */
public class BlockPickerBottomSheet extends BottomSheetDialogFragment {

    public interface BlockPickerListener {
        void onBlockTypeSelected(String blockType);
    }

    private BlockPickerListener listener;
    private int insertAfterPosition;

    public static BlockPickerBottomSheet newInstance(int insertAfterPosition) {
        BlockPickerBottomSheet sheet = new BlockPickerBottomSheet();
        Bundle args = new Bundle();
        args.putInt("insertAfterPosition", insertAfterPosition);
        sheet.setArguments(args);
        return sheet;
    }

    public void setListener(BlockPickerListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            insertAfterPosition = getArguments().getInt("insertAfterPosition", -1);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Context ctx = requireContext();
        float density = ctx.getResources().getDisplayMetrics().density;

        // Root container
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF0F172A);
        bg.setCornerRadii(new float[]{dp(20, density), dp(20, density), dp(20, density), dp(20, density), 0, 0, 0, 0});
        root.setBackground(bg);
        root.setPadding(dp(16, density), dp(12, density), dp(16, density), dp(24, density));
        root.setLayoutParams(new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ─── Handle bar ───
        View handle = new View(ctx);
        GradientDrawable hbg = new GradientDrawable();
        hbg.setColor(0xFF334155);
        hbg.setCornerRadius(dp(3, density));
        handle.setBackground(hbg);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(dp(40, density), dp(4, density));
        hlp.gravity = Gravity.CENTER_HORIZONTAL;
        hlp.bottomMargin = dp(16, density);
        handle.setLayoutParams(hlp);
        root.addView(handle);

        // ─── Title ───
        TextView title = new TextView(ctx);
        title.setText("Add Block");
        title.setTextColor(0xFFF1F5F9);
        title.setTextSize(18);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        title.setPadding(dp(4, density), 0, 0, dp(12, density));
        root.addView(title);

        // ─── Search bar ───
        EditText searchInput = new EditText(ctx);
        searchInput.setHint("🔍  Search blocks...");
        searchInput.setTextColor(0xFFE2E8F0);
        searchInput.setHintTextColor(0xFF64748B);
        searchInput.setTextSize(14);
        GradientDrawable sbg = new GradientDrawable();
        sbg.setColor(0xFF1E293B);
        sbg.setCornerRadius(dp(12, density));
        searchInput.setBackground(sbg);
        searchInput.setPadding(dp(16, density), dp(10, density), dp(16, density), dp(10, density));
        searchInput.setSingleLine(true);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.bottomMargin = dp(16, density);
        searchInput.setLayoutParams(slp);
        root.addView(searchInput);

        // ─── Sections ───
        ScrollView scroll = new ScrollView(ctx);
        scroll.setScrollBarSize(0);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        scroll.setLayoutParams(scrollLp);

        LinearLayout sectionsContainer = new LinearLayout(ctx);
        sectionsContainer.setOrientation(LinearLayout.VERTICAL);
        sectionsContainer.setTag("sectionsContainer");
        scroll.addView(sectionsContainer);
        root.addView(scroll);

        // Build block type sections
        buildSections(ctx, sectionsContainer, density, "");

        // Search filtering
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                sectionsContainer.removeAllViews();
                buildSections(ctx, sectionsContainer, density, s.toString().trim().toLowerCase());
            }
        });

        return root;
    }

    private void buildSections(Context ctx, LinearLayout container, float density, String filter) {
        // Define sections
        String[][] sections = {
            {"Basic", "0", "1", "2", "3"},           // text, h1, h2, h3
            {"Lists", "4", "5", "6", "7"},            // checklist, bullet, numbered, toggle
            {"Rich Content", "8", "9", "10", "11"},   // quote, callout, code, divider
            {"Media", "12", "18", "19", "17"},        // image, drawing, video, audio
            {"Embeds", "13", "14", "15", "16", "20"}, // table, math, file, link_preview, location
        };

        for (String[] section : sections) {
            String sectionName = section[0];
            List<Integer> matchingIndices = new ArrayList<>();

            for (int i = 1; i < section.length; i++) {
                int idx = Integer.parseInt(section[i]);
                if (idx < ContentBlock.ALL_TYPES.length) {
                    if (filter.isEmpty()
                        || ContentBlock.TYPE_NAMES[idx].toLowerCase().contains(filter)
                        || ContentBlock.TYPE_DESCRIPTIONS[idx].toLowerCase().contains(filter)
                        || ContentBlock.ALL_TYPES[idx].toLowerCase().contains(filter)) {
                        matchingIndices.add(idx);
                    }
                }
            }

            if (matchingIndices.isEmpty()) continue;

            // Section label
            TextView sectionLabel = new TextView(ctx);
            sectionLabel.setText(sectionName.toUpperCase());
            sectionLabel.setTextColor(0xFF64748B);
            sectionLabel.setTextSize(11);
            sectionLabel.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            sectionLabel.setLetterSpacing(0.1f);
            sectionLabel.setPadding(dp(4, density), dp(4, density), 0, dp(8, density));
            container.addView(sectionLabel);

            // Block items
            for (int idx : matchingIndices) {
                LinearLayout item = new LinearLayout(ctx);
                item.setOrientation(LinearLayout.HORIZONTAL);
                item.setGravity(Gravity.CENTER_VERTICAL);
                item.setPadding(dp(12, density), dp(10, density), dp(12, density), dp(10, density));
                GradientDrawable ibg = new GradientDrawable();
                ibg.setColor(0xFF1E293B);
                ibg.setCornerRadius(dp(10, density));
                item.setBackground(ibg);
                LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                ilp.bottomMargin = dp(6, density);
                item.setLayoutParams(ilp);

                // Icon
                TextView icon = new TextView(ctx);
                icon.setText(ContentBlock.TYPE_ICONS[idx]);
                icon.setTextSize(20);
                icon.setGravity(Gravity.CENTER);
                icon.setMinWidth(dp(36, density));
                icon.setPadding(0, 0, dp(12, density), 0);
                item.addView(icon);

                // Name + description
                LinearLayout info = new LinearLayout(ctx);
                info.setOrientation(LinearLayout.VERTICAL);
                info.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

                TextView name = new TextView(ctx);
                name.setText(ContentBlock.TYPE_NAMES[idx]);
                name.setTextColor(0xFFE2E8F0);
                name.setTextSize(14);
                name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                info.addView(name);

                TextView desc = new TextView(ctx);
                desc.setText(ContentBlock.TYPE_DESCRIPTIONS[idx]);
                desc.setTextColor(0xFF64748B);
                desc.setTextSize(11);
                desc.setMaxLines(1);
                info.addView(desc);

                item.addView(info);

                final String blockType = ContentBlock.ALL_TYPES[idx];
                item.setOnClickListener(v -> {
                    if (listener != null) listener.onBlockTypeSelected(blockType);
                    dismiss();
                });

                container.addView(item);
            }

            // Section spacer
            View spacer = new View(ctx);
            spacer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(8, density)));
            container.addView(spacer);
        }
    }

    public int getInsertAfterPosition() {
        return insertAfterPosition;
    }

    private int dp(int dp, float density) {
        return Math.round(dp * density);
    }
}
