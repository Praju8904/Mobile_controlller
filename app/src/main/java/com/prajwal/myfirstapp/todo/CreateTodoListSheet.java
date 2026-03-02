package com.prajwal.myfirstapp.todo;


import com.prajwal.myfirstapp.R;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

/**
 * BottomSheetDialogFragment for creating a new {@link TodoList}.
 *
 * <p>Displays a live-preview card, a name field, a 12-color picker,
 * and a 30-icon picker. Saves via {@link TodoRepository} and fires
 * {@link OnListCreatedListener#onListCreated(TodoList)}.
 */
public class CreateTodoListSheet extends BottomSheetDialogFragment {

    // ─── Constants ───────────────────────────────────────────────

    public static final String TAG = "CreateTodoListSheet";

    // ─── Interface ───────────────────────────────────────────────

    public interface OnListCreatedListener {
        void onListCreated(TodoList list);
    }

    // ─── Fields ──────────────────────────────────────────────────

    private OnListCreatedListener createdListener;
    private TodoRepository repo;

    private String selectedColor = TodoList.GRADIENT_COLORS[0];
    private String selectedIcon  = TodoList.ICON_OPTIONS[0];

    // Views
    private EditText etListName;
    private TextView tvPreviewName;
    private TextView tvPreviewIcon;
    private CardView previewCard;

    // Color / icon selection indicators
    private View lastSelectedColorDot = null;
    private View lastSelectedIconBtn  = null;

    // ─── Factory ─────────────────────────────────────────────────

    public static CreateTodoListSheet newInstance() {
        return new CreateTodoListSheet();
    }

    public void setOnListCreatedListener(OnListCreatedListener l) {
        this.createdListener = l;
    }

    // ─── Lifecycle ───────────────────────────────────────────────

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NORMAL, R.style.DarkBottomSheetDialog);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return buildUi();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repo = new TodoRepository(requireContext());
        etListName.requestFocus();
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() instanceof BottomSheetDialog) {
            BottomSheetDialog d = (BottomSheetDialog) getDialog();
            View sheet = d.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) {
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(sheet);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
                sheet.setBackgroundColor(Color.TRANSPARENT);
            }
        }
    }

    // ─── UI Builder ──────────────────────────────────────────────

    private View buildUi() {
        ScrollView scroll = new ScrollView(requireContext());
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(24));
        root.setBackgroundColor(Color.parseColor("#1E293B"));

        // Handle bar
        View handle = new View(requireContext());
        GradientDrawable handleBg = new GradientDrawable();
        handleBg.setShape(GradientDrawable.RECTANGLE);
        handleBg.setCornerRadius(dp(2));
        handleBg.setColor(Color.parseColor("#475569"));
        handle.setBackground(handleBg);
        LinearLayout.LayoutParams handleLp = new LinearLayout.LayoutParams(dp(40), dp(4));
        handleLp.gravity = Gravity.CENTER_HORIZONTAL;
        handleLp.bottomMargin = dp(16);
        handle.setLayoutParams(handleLp);
        root.addView(handle);

        // Sheet title
        TextView tvTitle = new TextView(requireContext());
        tvTitle.setText("New To-Do List");
        tvTitle.setTextColor(Color.parseColor("#F1F5F9"));
        tvTitle.setTextSize(20);
        tvTitle.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        titleLp.bottomMargin = dp(20);
        tvTitle.setLayoutParams(titleLp);
        root.addView(tvTitle);

        // Preview card (centred, 140×160 dp)
        FrameLayout previewWrapper = new FrameLayout(requireContext());
        LinearLayout.LayoutParams wrapperLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        wrapperLp.bottomMargin = dp(20);
        previewWrapper.setLayoutParams(wrapperLp);

        previewCard = new CardView(requireContext());
        previewCard.setRadius(dp(16));
        previewCard.setCardElevation(dp(6));
        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(dp(140), dp(160));
        cardLp.gravity = Gravity.CENTER_HORIZONTAL;
        previewCard.setLayoutParams(cardLp);

        LinearLayout cardContent = new LinearLayout(requireContext());
        cardContent.setOrientation(LinearLayout.VERTICAL);
        cardContent.setGravity(Gravity.CENTER);
        cardContent.setPadding(dp(12), dp(16), dp(12), dp(16));
        FrameLayout.LayoutParams contentLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
        cardContent.setLayoutParams(contentLp);

        tvPreviewIcon = new TextView(requireContext());
        tvPreviewIcon.setText(selectedIcon);
        tvPreviewIcon.setTextSize(36);
        tvPreviewIcon.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        iconLp.bottomMargin = dp(10);
        tvPreviewIcon.setLayoutParams(iconLp);
        cardContent.addView(tvPreviewIcon);

        tvPreviewName = new TextView(requireContext());
        tvPreviewName.setText("List name");
        tvPreviewName.setTextColor(Color.WHITE);
        tvPreviewName.setTextSize(14);
        tvPreviewName.setTypeface(null, Typeface.BOLD);
        tvPreviewName.setGravity(Gravity.CENTER);
        tvPreviewName.setMaxLines(2);
        cardContent.addView(tvPreviewName);

        previewCard.addView(cardContent);
        previewWrapper.addView(previewCard);
        root.addView(previewWrapper);

        // Apply initial preview colour
        updatePreview();

        // List name input
        root.addView(makeSectionLabel("List Name"));
        etListName = new EditText(requireContext());
        etListName.setHint("e.g. Shopping, Work, Fitness…");
        etListName.setHintTextColor(Color.parseColor("#64748B"));
        etListName.setTextColor(Color.parseColor("#F1F5F9"));
        etListName.setTextSize(16);
        etListName.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        etListName.setBackground(makeDarkFieldBg());
        etListName.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        nameLp.bottomMargin = dp(20);
        etListName.setLayoutParams(nameLp);
        etListName.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { updatePreview(); }
        });
        root.addView(etListName);

        // Color picker
        root.addView(makeSectionLabel("Color"));
        root.addView(buildColorPicker());

        // Icon picker
        root.addView(makeSectionLabel("Icon"));
        root.addView(buildIconPicker());

        // Save button
        TextView btnSave = new TextView(requireContext());
        btnSave.setText("Create List");
        btnSave.setTextColor(Color.WHITE);
        btnSave.setTextSize(16);
        btnSave.setTypeface(null, Typeface.BOLD);
        btnSave.setGravity(Gravity.CENTER);
        GradientDrawable saveBg = new GradientDrawable();
        saveBg.setShape(GradientDrawable.RECTANGLE);
        saveBg.setCornerRadius(dp(12));
        saveBg.setColor(Color.parseColor("#3B82F6"));
        btnSave.setBackground(saveBg);
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        saveLp.topMargin = dp(8);
        btnSave.setLayoutParams(saveLp);
        btnSave.setPadding(0, 0, 0, 0);
        btnSave.setOnClickListener(v -> saveList());
        root.addView(btnSave);

        scroll.addView(root);
        return scroll;
    }

    // ─── Color Picker ────────────────────────────────────────────

    private View buildColorPicker() {
        HorizontalScrollView hsv = new HorizontalScrollView(requireContext());
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout.LayoutParams hsvLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        hsvLp.bottomMargin = dp(20);
        hsv.setLayoutParams(hsvLp);

        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(4), 0, dp(4));

        for (String colorHex : TodoList.GRADIENT_COLORS) {
            FrameLayout dotWrapper = new FrameLayout(requireContext());
            LinearLayout.LayoutParams wLp = new LinearLayout.LayoutParams(dp(44), dp(44));
            wLp.setMarginEnd(dp(8));
            dotWrapper.setLayoutParams(wLp);

            View dot = new View(requireContext());
            GradientDrawable dotBg = new GradientDrawable();
            dotBg.setShape(GradientDrawable.OVAL);
            dotBg.setColor(Color.parseColor(colorHex));
            dot.setBackground(dotBg);
            FrameLayout.LayoutParams dotLp = new FrameLayout.LayoutParams(dp(36), dp(36));
            dotLp.gravity = Gravity.CENTER;
            dot.setLayoutParams(dotLp);

            dotWrapper.addView(dot);

            // Selection ring (initially hidden)
            View ring = new View(requireContext());
            GradientDrawable ringBg = new GradientDrawable();
            ringBg.setShape(GradientDrawable.OVAL);
            ringBg.setColor(Color.TRANSPARENT);
            ringBg.setStroke(dp(2), Color.WHITE);
            ring.setBackground(ringBg);
            FrameLayout.LayoutParams ringLp = new FrameLayout.LayoutParams(dp(44), dp(44));
            ringLp.gravity = Gravity.CENTER;
            ring.setLayoutParams(ringLp);
            ring.setVisibility(View.INVISIBLE);
            dotWrapper.addView(ring);

            // Mark first color as selected initially
            if (colorHex.equals(selectedColor)) {
                ring.setVisibility(View.VISIBLE);
                lastSelectedColorDot = ring;
            }

            dotWrapper.setOnClickListener(v -> {
                selectedColor = colorHex;
                if (lastSelectedColorDot != null) lastSelectedColorDot.setVisibility(View.INVISIBLE);
                ring.setVisibility(View.VISIBLE);
                lastSelectedColorDot = ring;
                updatePreview();
            });

            row.addView(dotWrapper);
        }

        hsv.addView(row);
        return hsv;
    }

    // ─── Icon Picker ─────────────────────────────────────────────

    private View buildIconPicker() {
        HorizontalScrollView hsv = new HorizontalScrollView(requireContext());
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout.LayoutParams hsvLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        hsvLp.bottomMargin = dp(20);
        hsv.setLayoutParams(hsvLp);

        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(4), 0, dp(4));

        for (String emoji : TodoList.ICON_OPTIONS) {
            TextView btn = new TextView(requireContext());
            btn.setText(emoji);
            btn.setTextSize(24);
            btn.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(dp(44), dp(44));
            btnLp.setMarginEnd(dp(4));
            btn.setLayoutParams(btnLp);

            // Initial selection highlight
            if (emoji.equals(selectedIcon)) {
                btn.setBackground(makeSelectedIconBg());
                lastSelectedIconBtn = btn;
            } else {
                btn.setBackground(null);
            }

            btn.setOnClickListener(v -> {
                selectedIcon = emoji;
                if (lastSelectedIconBtn != null) lastSelectedIconBtn.setBackground(null);
                btn.setBackground(makeSelectedIconBg());
                lastSelectedIconBtn = btn;
                updatePreview();
            });

            row.addView(btn);
        }

        hsv.addView(row);
        return hsv;
    }

    // ─── Preview ─────────────────────────────────────────────────

    private void updatePreview() {
        if (previewCard == null) return;

        // Gradient background on the card using selectedColor
        GradientDrawable cardBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.parseColor(selectedColor),
                          darkenColor(Color.parseColor(selectedColor), 0.6f)});
        cardBg.setCornerRadius(dp(16));
        previewCard.setBackground(cardBg);
        // CardView background overrides card colour; apply to inner content area instead
        previewCard.setCardBackgroundColor(Color.TRANSPARENT);

        if (tvPreviewIcon != null) tvPreviewIcon.setText(selectedIcon);

        if (tvPreviewName != null) {
            String name = (etListName != null) ? etListName.getText().toString().trim() : "";
            tvPreviewName.setText(name.isEmpty() ? "List name" : name);
        }
    }

    // ─── Save ────────────────────────────────────────────────────

    private void saveList() {
        String name = etListName.getText().toString().trim();
        if (name.isEmpty()) {
            etListName.setError("Name is required");
            etListName.requestFocus();
            return;
        }

        TodoList list = new TodoList(name, selectedColor, selectedIcon);
        repo.addList(list);

        if (createdListener != null) createdListener.onListCreated(list);
        dismiss();
    }

    // ─── Helpers ─────────────────────────────────────────────────

    private TextView makeSectionLabel(String text) {
        TextView tv = new TextView(requireContext());
        tv.setText(text);
        tv.setTextColor(Color.parseColor("#94A3B8"));
        tv.setTextSize(11);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setAllCaps(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        tv.setLayoutParams(lp);
        return tv;
    }

    private GradientDrawable makeDarkFieldBg() {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(8));
        bg.setColor(Color.parseColor("#0F172A"));
        bg.setStroke(dp(1), Color.parseColor("#334155"));
        return bg;
    }

    private GradientDrawable makeSelectedIconBg() {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(8));
        bg.setColor(Color.parseColor("#1A3B82F6"));
        bg.setStroke(dp(1), Color.parseColor("#3B82F6"));
        return bg;
    }

    /** Scales RGB channels of {@code color} by {@code factor} (0=black, 1=original). */
    private int darkenColor(int color, float factor) {
        float r = Color.red(color)   * factor;
        float g = Color.green(color) * factor;
        float b = Color.blue(color)  * factor;
        return Color.rgb((int) r, (int) g, (int) b);
    }

    private int dp(int value) {
        return (int) (value * requireContext().getResources().getDisplayMetrics().density);
    }
}
