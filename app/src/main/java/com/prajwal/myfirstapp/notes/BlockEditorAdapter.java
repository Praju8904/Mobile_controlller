package com.prajwal.myfirstapp.notes;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.Editable;
import android.text.InputType;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *  BlockEditorAdapter — Renders all 21 block types in a RecyclerView.
 *  Each block is an independent, styled, editable unit. Supports drag-reorder,
 *  block menus, inline formatting, and focus management.
 * ═══════════════════════════════════════════════════════════════════════════════════════
 */
public class BlockEditorAdapter extends RecyclerView.Adapter<BlockEditorAdapter.BlockViewHolder> {

    private static final String TAG = "BlockEditorAdapter";
    private final Context context;
    private final List<ContentBlock> blocks;
    private final BlockEditorListener listener;
    private int focusedPosition = -1;
    private final float density;

    // ═══ Listener Interface ═══
    public interface BlockEditorListener {
        void onBlockChanged(int position, ContentBlock block);
        void onBlockFocused(int position, ContentBlock block);
        void onBlockInsertRequested(int afterPosition);
        void onBlockMenuRequested(int position, ContentBlock block, View anchor);
        void onBlockDragStarted(RecyclerView.ViewHolder holder);
        void onEnterPressed(int position, ContentBlock block, int cursorPos);
        void onDeleteAtStart(int position, ContentBlock block);
        void onImageBlockClicked(int position, ContentBlock block);
        void onFileBlockClicked(int position, ContentBlock block);
        void onAudioPlayRequested(int position, ContentBlock block);
        void onVideoBlockClicked(int position, ContentBlock block);
        void onDrawingBlockClicked(int position, ContentBlock block);
        void onLocationBlockClicked(int position, ContentBlock block);
        void onLinkPreviewClicked(int position, ContentBlock block);
        void onToggleExpanded(int position, ContentBlock block, boolean expanded);
        void onChecklistToggled(int position, ContentBlock block, boolean checked);
    }

    public BlockEditorAdapter(Context context, List<ContentBlock> blocks, BlockEditorListener listener) {
        this.context = context;
        this.blocks = blocks;
        this.listener = listener;
        this.density = context.getResources().getDisplayMetrics().density;
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        return blocks.get(position).id.hashCode();
    }

    @Override
    public int getItemViewType(int position) {
        return blocks.get(position).getViewType();
    }

    @Override
    public int getItemCount() {
        return blocks.size();
    }

    // ═══ ViewHolder Creation ═══

    @NonNull
    @Override
    public BlockViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Build the root container: drag handle + content area + menu button + insert line
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new RecyclerView.LayoutParams(
            RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));

        // Block row: drag handle | content | menu
        LinearLayout blockRow = new LinearLayout(context);
        blockRow.setOrientation(LinearLayout.HORIZONTAL);
        blockRow.setGravity(Gravity.TOP);
        blockRow.setTag("blockRow");

        // Drag handle (hidden by default, shown on long press)
        TextView dragHandle = new TextView(context);
        dragHandle.setText("⠿");
        dragHandle.setTextColor(0x60FFFFFF);
        dragHandle.setTextSize(16);
        dragHandle.setGravity(Gravity.CENTER);
        dragHandle.setPadding(dp(2), dp(8), dp(4), dp(8));
        dragHandle.setVisibility(View.INVISIBLE);
        dragHandle.setTag("dragHandle");
        LinearLayout.LayoutParams dhp = new LinearLayout.LayoutParams(dp(20), ViewGroup.LayoutParams.WRAP_CONTENT);
        dragHandle.setLayoutParams(dhp);
        blockRow.addView(dragHandle);

        // Content container (fills remaining space)
        FrameLayout contentContainer = new FrameLayout(context);
        contentContainer.setTag("contentContainer");
        LinearLayout.LayoutParams ccp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        contentContainer.setLayoutParams(ccp);
        blockRow.addView(contentContainer);

        // Three-dot menu button (shown on focus)
        TextView menuBtn = new TextView(context);
        menuBtn.setText("⋮");
        menuBtn.setTextColor(0x60FFFFFF);
        menuBtn.setTextSize(18);
        menuBtn.setGravity(Gravity.CENTER);
        menuBtn.setPadding(dp(4), dp(8), dp(2), dp(8));
        menuBtn.setVisibility(View.INVISIBLE);
        menuBtn.setTag("menuBtn");
        LinearLayout.LayoutParams mbp = new LinearLayout.LayoutParams(dp(20), ViewGroup.LayoutParams.WRAP_CONTENT);
        menuBtn.setLayoutParams(mbp);
        blockRow.addView(menuBtn);

        root.addView(blockRow);

        // Insert line between blocks (thin "+" indicator)
        LinearLayout insertLine = new LinearLayout(context);
        insertLine.setOrientation(LinearLayout.HORIZONTAL);
        insertLine.setGravity(Gravity.CENTER_VERTICAL);
        insertLine.setPadding(dp(24), 0, dp(24), 0);
        insertLine.setTag("insertLine");

        View line1 = new View(context);
        line1.setBackgroundColor(0x20FFFFFF);
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0, dp(1), 1);
        line1.setLayoutParams(lp1);
        insertLine.addView(line1);

        TextView plusBtn = new TextView(context);
        plusBtn.setText("+");
        plusBtn.setTextColor(0x40FFFFFF);
        plusBtn.setTextSize(14);
        plusBtn.setGravity(Gravity.CENTER);
        plusBtn.setPadding(dp(8), dp(2), dp(8), dp(2));
        GradientDrawable plusBg = new GradientDrawable();
        plusBg.setCornerRadius(dp(10));
        plusBg.setStroke(1, 0x20FFFFFF);
        plusBtn.setBackground(plusBg);
        plusBtn.setTag("plusBtn");
        insertLine.addView(plusBtn);

        View line2 = new View(context);
        line2.setBackgroundColor(0x20FFFFFF);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, dp(1), 1);
        line2.setLayoutParams(lp2);
        insertLine.addView(line2);

        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(20));
        insertLine.setLayoutParams(ilp);
        insertLine.setVisibility(View.GONE);
        root.addView(insertLine);

        // Build the actual content view based on viewType
        View contentView = buildContentView(viewType);
        contentContainer.addView(contentView);

        return new BlockViewHolder(root, viewType);
    }

    // ═══ Content View Builders ═══

    private View buildContentView(int viewType) {
        switch (viewType) {
            case ContentBlock.VIEW_HEADING1: return buildTextEdit(28, Typeface.BOLD, 0xFFF1F5F9);
            case ContentBlock.VIEW_HEADING2: return buildTextEdit(22, Typeface.BOLD, 0xFFF1F5F9);
            case ContentBlock.VIEW_HEADING3: return buildTextEdit(18, Typeface.BOLD, 0xFFE2E8F0);
            case ContentBlock.VIEW_CHECKLIST: return buildChecklistView();
            case ContentBlock.VIEW_BULLET: return buildBulletView();
            case ContentBlock.VIEW_NUMBERED: return buildNumberedView();
            case ContentBlock.VIEW_TOGGLE: return buildToggleView();
            case ContentBlock.VIEW_QUOTE: return buildQuoteView();
            case ContentBlock.VIEW_CALLOUT: return buildCalloutView();
            case ContentBlock.VIEW_CODE: return buildCodeView();
            case ContentBlock.VIEW_DIVIDER: return buildDividerView();
            case ContentBlock.VIEW_IMAGE: return buildImageView();
            case ContentBlock.VIEW_TABLE: return buildTableView();
            case ContentBlock.VIEW_MATH: return buildMathView();
            case ContentBlock.VIEW_FILE: return buildFileView();
            case ContentBlock.VIEW_LINK_PREVIEW: return buildLinkPreviewView();
            case ContentBlock.VIEW_AUDIO: return buildAudioView();
            case ContentBlock.VIEW_DRAWING: return buildDrawingView();
            case ContentBlock.VIEW_VIDEO: return buildVideoView();
            case ContentBlock.VIEW_LOCATION: return buildLocationView();
            default: return buildTextEdit(16, Typeface.NORMAL, 0xFFE2E8F0);
        }
    }

    private EditText buildTextEdit(int textSizeSp, int typeface, int textColor) {
        EditText et = new EditText(context);
        et.setTag("blockEditText");
        et.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp);
        et.setTypeface(Typeface.create("sans-serif", typeface));
        et.setTextColor(textColor);
        et.setHintTextColor(0xFF475569);
        et.setHint("Type something...");
        et.setBackgroundColor(Color.TRANSPARENT);
        et.setPadding(dp(4), dp(4), dp(4), dp(4));
        et.setMinHeight(dp(32));
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
            | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        et.setImeOptions(EditorInfo.IME_ACTION_NONE);
        et.setLineSpacing(dp(3), 1f);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        et.setLayoutParams(lp);
        return et;
    }

    // ─── Checklist Block ───
    private LinearLayout buildChecklistView() {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);
        row.setPadding(0, dp(2), 0, dp(2));

        CheckBox cb = new CheckBox(context);
        cb.setTag("checkBox");
        cb.setButtonTintList(android.content.res.ColorStateList.valueOf(0xFF3B82F6));
        LinearLayout.LayoutParams cbp = new LinearLayout.LayoutParams(dp(36), dp(36));
        cb.setLayoutParams(cbp);
        row.addView(cb);

        EditText et = buildTextEdit(15, Typeface.NORMAL, 0xFFE2E8F0);
        et.setHint("To-do");
        LinearLayout.LayoutParams etp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        et.setLayoutParams(etp);
        row.addView(et);

        return row;
    }

    // ─── Bullet List Block ───
    private LinearLayout buildBulletView() {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);

        TextView bullet = new TextView(context);
        bullet.setTag("bulletChar");
        bullet.setText("●");
        bullet.setTextColor(0xFF94A3B8);
        bullet.setTextSize(10);
        bullet.setGravity(Gravity.CENTER);
        bullet.setPadding(dp(4), dp(10), dp(8), 0);
        row.addView(bullet);

        EditText et = buildTextEdit(15, Typeface.NORMAL, 0xFFE2E8F0);
        et.setHint("List item");
        LinearLayout.LayoutParams etp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        et.setLayoutParams(etp);
        row.addView(et);

        return row;
    }

    // ─── Numbered List Block ───
    private LinearLayout buildNumberedView() {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);

        TextView num = new TextView(context);
        num.setTag("numberLabel");
        num.setText("1.");
        num.setTextColor(0xFF94A3B8);
        num.setTextSize(15);
        num.setPadding(dp(4), dp(4), dp(8), 0);
        num.setMinWidth(dp(24));
        row.addView(num);

        EditText et = buildTextEdit(15, Typeface.NORMAL, 0xFFE2E8F0);
        et.setHint("List item");
        LinearLayout.LayoutParams etp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        et.setLayoutParams(etp);
        row.addView(et);

        return row;
    }

    // ─── Toggle Block ───
    private LinearLayout buildToggleView() {
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(4), dp(8), dp(4), dp(8));

        TextView chevron = new TextView(context);
        chevron.setTag("toggleChevron");
        chevron.setText("▶");
        chevron.setTextColor(0xFF94A3B8);
        chevron.setTextSize(12);
        chevron.setPadding(0, 0, dp(8), 0);
        header.addView(chevron);

        EditText et = buildTextEdit(16, Typeface.BOLD, 0xFFE2E8F0);
        et.setHint("Toggle heading");
        LinearLayout.LayoutParams etp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        et.setLayoutParams(etp);
        header.addView(et);

        container.addView(header);

        // Child content area (hidden when collapsed)
        LinearLayout childArea = new LinearLayout(context);
        childArea.setTag("toggleChildArea");
        childArea.setOrientation(LinearLayout.VERTICAL);
        childArea.setPadding(dp(24), 0, 0, dp(4));
        childArea.setVisibility(View.GONE);

        // Placeholder text for empty toggle
        TextView emptyHint = new TextView(context);
        emptyHint.setText("Empty toggle. Click to add content.");
        emptyHint.setTextColor(0xFF475569);
        emptyHint.setTextSize(13);
        emptyHint.setPadding(dp(4), dp(8), dp(4), dp(8));
        childArea.addView(emptyHint);

        container.addView(childArea);
        return container;
    }

    // ─── Quote Block ───
    private LinearLayout buildQuoteView() {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);

        View bar = new View(context);
        bar.setTag("quoteBar");
        bar.setBackgroundColor(0xFF3B82F6);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(4), ViewGroup.LayoutParams.MATCH_PARENT);
        bp.setMargins(dp(4), dp(4), dp(12), dp(4));
        bar.setLayoutParams(bp);
        row.addView(bar);

        EditText et = buildTextEdit(15, Typeface.ITALIC, 0xFFCBD5E1);
        et.setHint("Quote...");
        LinearLayout.LayoutParams etp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        et.setLayoutParams(etp);
        row.addView(et);

        return row;
    }

    // ─── Callout Block ───
    private LinearLayout buildCalloutView() {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.TOP);
        box.setTag("calloutBox");
        box.setPadding(dp(12), dp(12), dp(12), dp(12));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF1E3A5F);
        bg.setCornerRadius(dp(12));
        box.setBackground(bg);

        TextView emoji = new TextView(context);
        emoji.setTag("calloutEmoji");
        emoji.setText("💡");
        emoji.setTextSize(22);
        emoji.setPadding(0, dp(2), dp(12), 0);
        box.addView(emoji);

        EditText et = buildTextEdit(15, Typeface.NORMAL, 0xFFE2E8F0);
        et.setHint("Type something...");
        LinearLayout.LayoutParams etp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        et.setLayoutParams(etp);
        box.addView(et);

        LinearLayout wrapper = new LinearLayout(context);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setPadding(0, dp(4), 0, dp(4));
        wrapper.addView(box);
        return wrapper;
    }

    // ─── Code Block ───
    private LinearLayout buildCodeView() {
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(0, dp(4), 0, dp(4));

        // Header bar with language selector and copy button
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), dp(6), dp(12), dp(6));
        GradientDrawable hbg = new GradientDrawable();
        hbg.setColor(0xFF1A1A2E);
        float[] radii = {dp(10), dp(10), dp(10), dp(10), 0, 0, 0, 0};
        hbg.setCornerRadii(radii);
        header.setBackground(hbg);

        TextView langLabel = new TextView(context);
        langLabel.setTag("codeLangLabel");
        langLabel.setText("plain ▾");
        langLabel.setTextColor(0xFF94A3B8);
        langLabel.setTextSize(11);
        langLabel.setTypeface(Typeface.MONOSPACE);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        langLabel.setLayoutParams(llp);
        header.addView(langLabel);

        // Line count label
        TextView lineCount = new TextView(context);
        lineCount.setTag("codeLineCount");
        lineCount.setTextColor(0xFF475569);
        lineCount.setTextSize(10);
        lineCount.setPadding(0, 0, dp(12), 0);
        header.addView(lineCount);

        TextView copyBtn = new TextView(context);
        copyBtn.setTag("codeCopyBtn");
        copyBtn.setText("📋 Copy");
        copyBtn.setTextColor(0xFF64748B);
        copyBtn.setTextSize(11);
        header.addView(copyBtn);

        container.addView(header);

        // Code content with line numbers
        HorizontalScrollView hScroll = new HorizontalScrollView(context);
        hScroll.setHorizontalScrollBarEnabled(false);
        GradientDrawable codeBg = new GradientDrawable();
        codeBg.setColor(0xFF0D1117);
        float[] cRadii = {0, 0, 0, 0, dp(10), dp(10), dp(10), dp(10)};
        codeBg.setCornerRadii(cRadii);
        hScroll.setBackground(codeBg);
        hScroll.setPadding(dp(12), dp(8), dp(12), dp(8));

        EditText codeEdit = new EditText(context);
        codeEdit.setTag("blockEditText");
        codeEdit.setTypeface(Typeface.MONOSPACE);
        codeEdit.setTextColor(0xFFE2E8F0);
        codeEdit.setTextSize(13);
        codeEdit.setHintTextColor(0xFF475569);
        codeEdit.setHint("// code here");
        codeEdit.setBackgroundColor(Color.TRANSPARENT);
        codeEdit.setPadding(0, 0, dp(16), 0);
        codeEdit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
            | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        codeEdit.setMinHeight(dp(40));
        codeEdit.setLineSpacing(dp(2), 1f);
        hScroll.addView(codeEdit);
        container.addView(hScroll);

        return container;
    }

    // ─── Divider Block ───
    private LinearLayout buildDividerView() {
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(16), dp(12), dp(16), dp(12));
        container.setGravity(Gravity.CENTER);

        View divider = new View(context);
        divider.setTag("dividerLine");
        divider.setBackgroundColor(0xFF334155);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(2));
        divider.setLayoutParams(dlp);
        container.addView(divider);

        return container;
    }

    // ─── Image Block ───
    private LinearLayout buildImageView() {
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(0, dp(8), 0, dp(4));
        container.setGravity(Gravity.CENTER_HORIZONTAL);

        ImageView img = new ImageView(context);
        img.setTag("blockImage");
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        img.setAdjustViewBounds(true);
        img.setMaxHeight(dp(300));
        GradientDrawable imgBg = new GradientDrawable();
        imgBg.setColor(0xFF1E293B);
        imgBg.setCornerRadius(dp(12));
        img.setBackground(imgBg);
        img.setClipToOutline(true);
        FrameLayout.LayoutParams imgLp = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(200));
        img.setLayoutParams(imgLp);

        // Placeholder text for empty image
        TextView placeholder = new TextView(context);
        placeholder.setTag("imagePlaceholder");
        placeholder.setText("🖼️  Tap to add image");
        placeholder.setTextColor(0xFF64748B);
        placeholder.setTextSize(14);
        placeholder.setGravity(Gravity.CENTER);
        placeholder.setPadding(dp(16), dp(40), dp(16), dp(40));
        GradientDrawable phBg = new GradientDrawable();
        phBg.setColor(0xFF1E293B);
        phBg.setCornerRadius(dp(12));
        phBg.setStroke(2, 0xFF334155);
        placeholder.setBackground(phBg);

        FrameLayout imgFrame = new FrameLayout(context);
        imgFrame.setTag("imageFrame");
        imgFrame.addView(img);
        imgFrame.addView(placeholder);
        container.addView(imgFrame);

        // Caption
        EditText caption = new EditText(context);
        caption.setTag("imageCaption");
        caption.setHint("Add a caption...");
        caption.setTextColor(0xFF94A3B8);
        caption.setHintTextColor(0xFF475569);
        caption.setTextSize(12);
        caption.setGravity(Gravity.CENTER);
        caption.setBackgroundColor(Color.TRANSPARENT);
        caption.setPadding(dp(8), dp(4), dp(8), dp(4));
        caption.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        container.addView(caption);

        return container;
    }

    // ─── Table Block ───
    private LinearLayout buildTableView() {
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(0, dp(8), 0, dp(8));

        HorizontalScrollView hScroll = new HorizontalScrollView(context);
        hScroll.setHorizontalScrollBarEnabled(true);

        LinearLayout tableContainer = new LinearLayout(context);
        tableContainer.setTag("tableContainer");
        tableContainer.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable tbg = new GradientDrawable();
        tbg.setColor(0xFF0F172A);
        tbg.setCornerRadius(dp(10));
        tbg.setStroke(1, 0xFF1E293B);
        tableContainer.setBackground(tbg);
        tableContainer.setPadding(dp(2), dp(2), dp(2), dp(2));

        hScroll.addView(tableContainer);
        container.addView(hScroll);

        // Table controls row
        LinearLayout controls = new LinearLayout(context);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(0, dp(6), 0, 0);

        String[] ctrls = {"+ Row", "+ Column", "⚙ Header"};
        String[] ctrlTags = {"addRow", "addCol", "toggleHeader"};
        for (int i = 0; i < ctrls.length; i++) {
            TextView btn = new TextView(context);
            btn.setText(ctrls[i]);
            btn.setTag(ctrlTags[i]);
            btn.setTextColor(0xFF64748B);
            btn.setTextSize(11);
            btn.setPadding(dp(10), dp(4), dp(10), dp(4));
            GradientDrawable cbg = new GradientDrawable();
            cbg.setCornerRadius(dp(8));
            cbg.setStroke(1, 0xFF1E293B);
            btn.setBackground(cbg);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            clp.setMargins(dp(4), 0, dp(4), 0);
            btn.setLayoutParams(clp);
            controls.addView(btn);
        }

        container.addView(controls);
        return container;
    }

    // ─── Math/Equation Block ───
    private LinearLayout buildMathView() {
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(0, dp(4), 0, dp(4));

        // Rendered math preview
        TextView mathPreview = new TextView(context);
        mathPreview.setTag("mathPreview");
        mathPreview.setTextColor(0xFFE2E8F0);
        mathPreview.setTextSize(18);
        mathPreview.setGravity(Gravity.CENTER);
        mathPreview.setPadding(dp(16), dp(16), dp(16), dp(16));
        GradientDrawable mpbg = new GradientDrawable();
        mpbg.setColor(0xFF0F172A);
        mpbg.setCornerRadius(dp(10));
        mathPreview.setBackground(mpbg);
        container.addView(mathPreview);

        // LaTeX input
        EditText latexInput = new EditText(context);
        latexInput.setTag("blockEditText");
        latexInput.setHint("LaTeX: e.g. \\frac{a}{b}");
        latexInput.setTypeface(Typeface.MONOSPACE);
        latexInput.setTextColor(0xFFCBD5E1);
        latexInput.setHintTextColor(0xFF475569);
        latexInput.setTextSize(13);
        latexInput.setBackgroundColor(Color.TRANSPARENT);
        latexInput.setPadding(dp(8), dp(8), dp(8), dp(4));
        latexInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        container.addView(latexInput);

        return container;
    }

    // ─── File Attachment Block ───
    private LinearLayout buildFileView() {
        LinearLayout pill = new LinearLayout(context);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(dp(14), dp(12), dp(14), dp(12));
        pill.setTag("filePill");

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF1E293B);
        bg.setCornerRadius(dp(12));
        bg.setStroke(1, 0xFF334155);
        pill.setBackground(bg);

        TextView icon = new TextView(context);
        icon.setTag("fileIcon");
        icon.setText("📄");
        icon.setTextSize(24);
        icon.setPadding(0, 0, dp(12), 0);
        pill.addView(icon);

        LinearLayout info = new LinearLayout(context);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView name = new TextView(context);
        name.setTag("fileName");
        name.setText("Tap to attach file");
        name.setTextColor(0xFFE2E8F0);
        name.setTextSize(14);
        info.addView(name);

        TextView size = new TextView(context);
        size.setTag("fileSize");
        size.setTextColor(0xFF64748B);
        size.setTextSize(11);
        info.addView(size);

        pill.addView(info);

        TextView openBtn = new TextView(context);
        openBtn.setTag("fileOpenBtn");
        openBtn.setText("Open ▸");
        openBtn.setTextColor(0xFF3B82F6);
        openBtn.setTextSize(12);
        openBtn.setPadding(dp(8), dp(4), dp(8), dp(4));
        pill.addView(openBtn);

        LinearLayout wrapper = new LinearLayout(context);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setPadding(0, dp(4), 0, dp(4));
        wrapper.addView(pill);
        return wrapper;
    }

    // ─── Link Preview Block ───
    private LinearLayout buildLinkPreviewView() {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setTag("linkCard");

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF1E293B);
        bg.setCornerRadius(dp(12));
        bg.setStroke(1, 0xFF334155);
        card.setBackground(bg);

        TextView title = new TextView(context);
        title.setTag("linkTitle");
        title.setTextColor(0xFFE2E8F0);
        title.setTextSize(15);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setMaxLines(2);
        card.addView(title);

        TextView desc = new TextView(context);
        desc.setTag("linkDesc");
        desc.setTextColor(0xFF94A3B8);
        desc.setTextSize(12);
        desc.setMaxLines(2);
        desc.setPadding(0, dp(4), 0, dp(4));
        card.addView(desc);

        TextView domain = new TextView(context);
        domain.setTag("linkDomain");
        domain.setTextColor(0xFF3B82F6);
        domain.setTextSize(11);
        card.addView(domain);

        // URL input (for editing)
        EditText urlInput = new EditText(context);
        urlInput.setTag("blockEditText");
        urlInput.setHint("Paste URL here...");
        urlInput.setTextColor(0xFF94A3B8);
        urlInput.setHintTextColor(0xFF475569);
        urlInput.setTextSize(12);
        urlInput.setBackgroundColor(Color.TRANSPARENT);
        urlInput.setPadding(0, dp(6), 0, 0);
        urlInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        card.addView(urlInput);

        LinearLayout wrapper = new LinearLayout(context);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setPadding(0, dp(4), 0, dp(4));
        wrapper.addView(card);
        return wrapper;
    }

    // ─── Audio Recording Block ───
    private LinearLayout buildAudioView() {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setTag("audioCard");

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF1E293B);
        bg.setCornerRadius(dp(12));
        bg.setStroke(1, 0xFF334155);
        card.setBackground(bg);

        // Play/record button
        TextView playBtn = new TextView(context);
        playBtn.setTag("audioPlayBtn");
        playBtn.setText("🎙️");
        playBtn.setTextSize(28);
        playBtn.setPadding(0, 0, dp(12), 0);
        card.addView(playBtn);

        LinearLayout info = new LinearLayout(context);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        // Waveform placeholder
        View waveform = new View(context);
        waveform.setTag("audioWaveform");
        waveform.setBackgroundColor(0xFF3B82F6);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(4));
        wlp.bottomMargin = dp(4);
        waveform.setLayoutParams(wlp);
        info.addView(waveform);

        TextView duration = new TextView(context);
        duration.setTag("audioDuration");
        duration.setText("Tap to record");
        duration.setTextColor(0xFF94A3B8);
        duration.setTextSize(12);
        info.addView(duration);

        card.addView(info);

        LinearLayout wrapper = new LinearLayout(context);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setPadding(0, dp(4), 0, dp(4));
        wrapper.addView(card);
        return wrapper;
    }

    // ─── Drawing/Sketch Block ───
    private LinearLayout buildDrawingView() {
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(0, dp(8), 0, dp(4));
        container.setGravity(Gravity.CENTER_HORIZONTAL);

        ImageView drawing = new ImageView(context);
        drawing.setTag("drawingImage");
        drawing.setScaleType(ImageView.ScaleType.FIT_CENTER);
        drawing.setAdjustViewBounds(true);
        drawing.setMaxHeight(dp(250));
        GradientDrawable dbg = new GradientDrawable();
        dbg.setColor(0xFF1E293B);
        dbg.setCornerRadius(dp(12));
        drawing.setBackground(dbg);
        drawing.setClipToOutline(true);

        TextView placeholder = new TextView(context);
        placeholder.setTag("drawingPlaceholder");
        placeholder.setText("✏️  Tap to draw");
        placeholder.setTextColor(0xFF64748B);
        placeholder.setTextSize(14);
        placeholder.setGravity(Gravity.CENTER);
        placeholder.setPadding(dp(16), dp(40), dp(16), dp(40));
        GradientDrawable phBg = new GradientDrawable();
        phBg.setColor(0xFF1E293B);
        phBg.setCornerRadius(dp(12));
        phBg.setStroke(2, 0xFF334155);
        placeholder.setBackground(phBg);

        FrameLayout frame = new FrameLayout(context);
        frame.setTag("drawingFrame");
        frame.addView(drawing);
        frame.addView(placeholder);
        container.addView(frame);

        return container;
    }

    // ─── Video Block ───
    private LinearLayout buildVideoView() {
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(0, dp(8), 0, dp(4));
        container.setGravity(Gravity.CENTER_HORIZONTAL);

        FrameLayout videoFrame = new FrameLayout(context);
        videoFrame.setTag("videoFrame");

        ImageView thumb = new ImageView(context);
        thumb.setTag("videoThumbnail");
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumb.setAdjustViewBounds(true);
        GradientDrawable vbg = new GradientDrawable();
        vbg.setColor(0xFF1E293B);
        vbg.setCornerRadius(dp(12));
        thumb.setBackground(vbg);
        thumb.setClipToOutline(true);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(200));
        thumb.setLayoutParams(tlp);
        videoFrame.addView(thumb);

        // Play overlay
        TextView playOverlay = new TextView(context);
        playOverlay.setText("▶");
        playOverlay.setTextColor(0xCCFFFFFF);
        playOverlay.setTextSize(32);
        playOverlay.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        playOverlay.setLayoutParams(plp);
        GradientDrawable pbg = new GradientDrawable();
        pbg.setColor(0x40000000);
        pbg.setCornerRadius(dp(12));
        playOverlay.setBackground(pbg);
        videoFrame.addView(playOverlay);

        TextView placeholder = new TextView(context);
        placeholder.setTag("videoPlaceholder");
        placeholder.setText("🎬  Tap to add video");
        placeholder.setTextColor(0xFF64748B);
        placeholder.setTextSize(14);
        placeholder.setGravity(Gravity.CENTER);
        placeholder.setPadding(dp(16), dp(40), dp(16), dp(40));
        GradientDrawable phBg = new GradientDrawable();
        phBg.setColor(0xFF1E293B);
        phBg.setCornerRadius(dp(12));
        phBg.setStroke(2, 0xFF334155);
        placeholder.setBackground(phBg);
        videoFrame.addView(placeholder);

        container.addView(videoFrame);
        return container;
    }

    // ─── Location Block ───
    private LinearLayout buildLocationView() {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setTag("locationCard");

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF1E293B);
        bg.setCornerRadius(dp(12));
        bg.setStroke(1, 0xFF334155);
        card.setBackground(bg);

        // Map placeholder
        TextView mapPlaceholder = new TextView(context);
        mapPlaceholder.setTag("mapPreview");
        mapPlaceholder.setText("📍");
        mapPlaceholder.setTextSize(32);
        mapPlaceholder.setGravity(Gravity.CENTER);
        mapPlaceholder.setPadding(dp(8), dp(20), dp(8), dp(8));
        card.addView(mapPlaceholder);

        TextView locName = new TextView(context);
        locName.setTag("locationName");
        locName.setText("Tap to add location");
        locName.setTextColor(0xFFE2E8F0);
        locName.setTextSize(14);
        locName.setGravity(Gravity.CENTER);
        card.addView(locName);

        TextView coords = new TextView(context);
        coords.setTag("locationCoords");
        coords.setTextColor(0xFF64748B);
        coords.setTextSize(11);
        coords.setGravity(Gravity.CENTER);
        coords.setPadding(0, dp(2), 0, dp(4));
        card.addView(coords);

        LinearLayout wrapper = new LinearLayout(context);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setPadding(0, dp(4), 0, dp(4));
        wrapper.addView(card);
        return wrapper;
    }

    // ═══ ViewHolder Binding ═══

    @Override
    public void onBindViewHolder(@NonNull BlockViewHolder holder, int position) {
        ContentBlock block = blocks.get(position);
        holder.currentBlock = block;
        boolean isFocused = position == focusedPosition;

        // Show/hide drag handle & menu based on focus
        View dragHandle = holder.itemView.findViewWithTag("dragHandle");
        View menuBtn = holder.itemView.findViewWithTag("menuBtn");
        View insertLine = holder.itemView.findViewWithTag("insertLine");
        if (dragHandle != null) dragHandle.setVisibility(isFocused ? View.VISIBLE : View.INVISIBLE);
        if (menuBtn != null) {
            menuBtn.setVisibility(isFocused ? View.VISIBLE : View.INVISIBLE);
            menuBtn.setOnClickListener(v -> {
                if (listener != null) listener.onBlockMenuRequested(holder.getAdapterPosition(), block, v);
            });
        }
        if (insertLine != null) {
            insertLine.setVisibility(isFocused ? View.VISIBLE : View.GONE);
            View plusBtn = insertLine.findViewWithTag("plusBtn");
            if (plusBtn != null) {
                plusBtn.setOnClickListener(v -> {
                    if (listener != null) listener.onBlockInsertRequested(holder.getAdapterPosition());
                });
            }
        }

        // Apply indent for indentable blocks
        View blockRow = holder.itemView.findViewWithTag("blockRow");
        if (blockRow != null) {
            int indentPx = block.indentLevel * dp(24);
            blockRow.setPadding(indentPx, 0, 0, 0);
        }

        // Setup drag handle
        if (dragHandle != null) {
            dragHandle.setOnLongClickListener(v -> {
                if (listener != null) listener.onBlockDragStarted(holder);
                return true;
            });
        }

        // Bind content based on type
        switch (block.getViewType()) {
            case ContentBlock.VIEW_TEXT:
            case ContentBlock.VIEW_HEADING1:
            case ContentBlock.VIEW_HEADING2:
            case ContentBlock.VIEW_HEADING3:
                bindTextBlock(holder, block, position);
                break;
            case ContentBlock.VIEW_CHECKLIST:
                bindChecklistBlock(holder, block, position);
                break;
            case ContentBlock.VIEW_BULLET:
                bindBulletBlock(holder, block, position);
                break;
            case ContentBlock.VIEW_NUMBERED:
                bindNumberedBlock(holder, block, position);
                break;
            case ContentBlock.VIEW_TOGGLE:
                bindToggleBlock(holder, block, position);
                break;
            case ContentBlock.VIEW_QUOTE:
                bindQuoteBlock(holder, block, position);
                break;
            case ContentBlock.VIEW_CALLOUT:
                bindCalloutBlock(holder, block, position);
                break;
            case ContentBlock.VIEW_CODE:
                bindCodeBlock(holder, block, position);
                break;
            case ContentBlock.VIEW_DIVIDER:
                bindDividerBlock(holder, block);
                break;
            case ContentBlock.VIEW_IMAGE:
                bindImageBlock(holder, block, position);
                break;
            case ContentBlock.VIEW_TABLE:
                bindTableBlock(holder, block, position);
                break;
            case ContentBlock.VIEW_MATH:
                bindMathBlock(holder, block, position);
                break;
            case ContentBlock.VIEW_FILE:
                bindFileBlock(holder, block, position);
                break;
            case ContentBlock.VIEW_LINK_PREVIEW:
                bindLinkPreviewBlock(holder, block, position);
                break;
            case ContentBlock.VIEW_AUDIO:
                bindAudioBlock(holder, block, position);
                break;
            case ContentBlock.VIEW_DRAWING:
                bindDrawingBlock(holder, block, position);
                break;
            case ContentBlock.VIEW_VIDEO:
                bindVideoBlock(holder, block, position);
                break;
            case ContentBlock.VIEW_LOCATION:
                bindLocationBlock(holder, block, position);
                break;
        }

        // Long press entire block row for drag
        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) listener.onBlockDragStarted(holder);
            return true;
        });
    }

    // ═══ Block Binding Methods ═══

    private void bindTextBlock(BlockViewHolder holder, ContentBlock block, int position) {
        EditText et = holder.findEditText();
        if (et == null) return;
        setupEditText(et, block, position, holder);
    }

    private void bindChecklistBlock(BlockViewHolder holder, ContentBlock block, int position) {
        EditText et = holder.findEditText();
        CheckBox cb = (CheckBox) holder.findByTag("checkBox");
        if (et == null) return;
        setupEditText(et, block, position, holder);

        if (cb != null) {
            cb.setOnCheckedChangeListener(null);
            cb.setChecked(block.isChecked());
            cb.setOnCheckedChangeListener((btn, checked) -> {
                block.setChecked(checked);
                // Strike-through effect
                if (checked) {
                    et.setPaintFlags(et.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                    et.setTextColor(0xFF64748B);
                } else {
                    et.setPaintFlags(et.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
                    et.setTextColor(0xFFE2E8F0);
                }
                if (listener != null) listener.onChecklistToggled(holder.getAdapterPosition(), block, checked);
            });
            // Apply visual state
            if (block.isChecked()) {
                et.setPaintFlags(et.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                et.setTextColor(0xFF64748B);
            } else {
                et.setPaintFlags(et.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
                et.setTextColor(0xFFE2E8F0);
            }
        }
    }

    private void bindBulletBlock(BlockViewHolder holder, ContentBlock block, int position) {
        EditText et = holder.findEditText();
        TextView bullet = (TextView) holder.findByTag("bulletChar");
        if (et == null) return;
        setupEditText(et, block, position, holder);
        if (bullet != null) bullet.setText(block.getBulletChar());
    }

    private void bindNumberedBlock(BlockViewHolder holder, ContentBlock block, int position) {
        EditText et = holder.findEditText();
        TextView num = (TextView) holder.findByTag("numberLabel");
        if (et == null) return;
        setupEditText(et, block, position, holder);
        if (num != null) {
            int number = getNumberForBlock(position);
            num.setText(number + ".");
        }
    }

    private void bindToggleBlock(BlockViewHolder holder, ContentBlock block, int position) {
        EditText et = holder.findEditText();
        TextView chevron = (TextView) holder.findByTag("toggleChevron");
        View childArea = holder.findByTag("toggleChildArea");
        if (et == null) return;
        setupEditText(et, block, position, holder);

        boolean collapsed = block.isCollapsed();
        if (chevron != null) {
            chevron.setText(collapsed ? "▶" : "▼");
            chevron.setOnClickListener(v -> {
                block.setCollapsed(!block.isCollapsed());
                chevron.setText(block.isCollapsed() ? "▶" : "▼");
                if (childArea != null) {
                    childArea.setVisibility(block.isCollapsed() ? View.GONE : View.VISIBLE);
                }
                if (listener != null) listener.onToggleExpanded(holder.getAdapterPosition(), block, !block.isCollapsed());
            });
        }
        if (childArea != null) {
            childArea.setVisibility(collapsed ? View.GONE : View.VISIBLE);
        }
    }

    private void bindQuoteBlock(BlockViewHolder holder, ContentBlock block, int position) {
        EditText et = holder.findEditText();
        View bar = holder.findByTag("quoteBar");
        if (et == null) return;
        setupEditText(et, block, position, holder);
        if (bar != null) {
            try { bar.setBackgroundColor(Color.parseColor(block.getQuoteColor())); }
            catch (Exception e) { bar.setBackgroundColor(0xFF3B82F6); }
        }
    }

    private void bindCalloutBlock(BlockViewHolder holder, ContentBlock block, int position) {
        EditText et = holder.findEditText();
        TextView emoji = (TextView) holder.findByTag("calloutEmoji");
        View box = holder.findByTag("calloutBox");
        if (et == null) return;
        setupEditText(et, block, position, holder);

        if (emoji != null) {
            emoji.setText(block.getEmoji());
            emoji.setOnClickListener(v -> {
                // Cycle through common emojis
                String[] emojis = {"💡", "⚠️", "❗", "✅", "📌", "🔥", "💬", "📝", "🎯", "🚀"};
                String current = block.getEmoji();
                int idx = 0;
                for (int i = 0; i < emojis.length; i++) {
                    if (emojis[i].equals(current)) { idx = (i + 1) % emojis.length; break; }
                }
                block.setEmoji(emojis[idx]);
                emoji.setText(emojis[idx]);
                if (listener != null) listener.onBlockChanged(holder.getAdapterPosition(), block);
            });
        }
        if (box != null) {
            try {
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(Color.parseColor(block.getBlockColor()));
                bg.setCornerRadius(dp(12));
                box.setBackground(bg);
            } catch (Exception ignored) {}
        }
    }

    private void bindCodeBlock(BlockViewHolder holder, ContentBlock block, int position) {
        EditText et = holder.findEditText();
        TextView langLabel = (TextView) holder.findByTag("codeLangLabel");
        TextView lineCount = (TextView) holder.findByTag("codeLineCount");
        TextView copyBtn = (TextView) holder.findByTag("codeCopyBtn");

        if (et == null) return;
        setupEditText(et, block, position, holder);

        if (langLabel != null) {
            langLabel.setText(block.getLanguage() + " ▾");
            langLabel.setOnClickListener(v -> showLanguagePicker(block, langLabel, holder.getAdapterPosition()));
        }
        if (lineCount != null) {
            String text = block.getText();
            int lines = text.isEmpty() ? 0 : text.split("\n").length;
            lineCount.setText(lines + " lines");
        }
        if (copyBtn != null) {
            copyBtn.setOnClickListener(v -> {
                android.content.ClipboardManager cm = (android.content.ClipboardManager)
                    context.getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(android.content.ClipData.newPlainText("code", block.getText()));
                copyBtn.setText("✓ Copied");
                copyBtn.postDelayed(() -> copyBtn.setText("📋 Copy"), 1500);
            });
        }
    }

    private void showLanguagePicker(ContentBlock block, TextView label, int position) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(context);
        builder.setTitle("Select Language");
        builder.setItems(ContentBlock.CODE_LANGUAGES, (dialog, which) -> {
            block.setLanguage(ContentBlock.CODE_LANGUAGES[which]);
            label.setText(ContentBlock.CODE_LANGUAGES[which] + " ▾");
            if (listener != null) listener.onBlockChanged(position, block);
        });
        builder.show();
    }

    private void bindDividerBlock(BlockViewHolder holder, ContentBlock block) {
        View divider = holder.findByTag("dividerLine");
        if (divider == null) return;

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF334155);

        String style = block.getDividerStyle();
        if ("dashed".equals(style)) {
            bg.setColor(Color.TRANSPARENT);
            bg.setStroke(dp(2), 0xFF334155, dp(8), dp(4));
        } else if ("dotted".equals(style)) {
            bg.setColor(Color.TRANSPARENT);
            bg.setStroke(dp(2), 0xFF334155, dp(3), dp(3));
        }
        divider.setBackground(bg);

        // Tap to cycle style
        divider.setOnClickListener(v -> {
            if ("solid".equals(style)) block.setDividerStyle("dashed");
            else if ("dashed".equals(style)) block.setDividerStyle("dotted");
            else block.setDividerStyle("solid");
            notifyItemChanged(holder.getAdapterPosition());
        });
    }

    private void bindImageBlock(BlockViewHolder holder, ContentBlock block, int position) {
        ImageView img = (ImageView) holder.findByTag("blockImage");
        TextView placeholder = (TextView) holder.findByTag("imagePlaceholder");
        EditText caption = (EditText) holder.findByTag("imageCaption");
        View frame = holder.findByTag("imageFrame");

        String uri = block.getImageUri();
        if (uri != null && !uri.isEmpty()) {
            try {
                img.setImageURI(Uri.parse(uri));
                img.setVisibility(View.VISIBLE);
                if (placeholder != null) placeholder.setVisibility(View.GONE);
            } catch (Exception e) {
                img.setVisibility(View.GONE);
                if (placeholder != null) placeholder.setVisibility(View.VISIBLE);
            }
        } else {
            img.setVisibility(View.GONE);
            if (placeholder != null) placeholder.setVisibility(View.VISIBLE);
        }

        if (caption != null) {
            caption.removeTextChangedListener(holder.textWatcher);
            caption.setText(block.getCaption());
            TextWatcher tw = new SimpleTextWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    block.setCaption(s.toString());
                    if (listener != null) listener.onBlockChanged(holder.getAdapterPosition(), block);
                }
            };
            caption.addTextChangedListener(tw);
        }

        View clickTarget = (placeholder != null && placeholder.getVisibility() == View.VISIBLE) ? placeholder : frame;
        if (clickTarget != null) {
            clickTarget.setOnClickListener(v -> {
                if (listener != null) listener.onImageBlockClicked(position, block);
            });
        }
    }

    private void bindTableBlock(BlockViewHolder holder, ContentBlock block, int position) {
        LinearLayout tableContainer = (LinearLayout) holder.findByTag("tableContainer");
        if (tableContainer == null) return;
        tableContainer.removeAllViews();

        try {
            org.json.JSONArray rows = block.getTableRows();
            if (rows == null) return;
            boolean hasHeader = block.hasTableHeader();

            for (int r = 0; r < rows.length(); r++) {
                org.json.JSONArray row = rows.getJSONArray(r);
                LinearLayout rowLayout = new LinearLayout(context);
                rowLayout.setOrientation(LinearLayout.HORIZONTAL);

                for (int c = 0; c < row.length(); c++) {
                    EditText cell = new EditText(context);
                    cell.setText(row.optString(c, ""));
                    cell.setTextColor(0xFFE2E8F0);
                    cell.setTextSize(13);
                    cell.setBackgroundColor(Color.TRANSPARENT);
                    cell.setPadding(dp(8), dp(6), dp(8), dp(6));
                    cell.setMinWidth(dp(80));
                    cell.setInputType(InputType.TYPE_CLASS_TEXT);
                    cell.setSingleLine(true);

                    if (r == 0 && hasHeader) {
                        cell.setTypeface(Typeface.DEFAULT_BOLD);
                        cell.setBackgroundColor(0xFF1E293B);
                    }

                    // Cell border
                    GradientDrawable cellBg = new GradientDrawable();
                    cellBg.setStroke(1, 0xFF1E293B);
                    if (r == 0 && hasHeader) cellBg.setColor(0xFF1E293B);
                    else cellBg.setColor(Color.TRANSPARENT);
                    cell.setBackground(cellBg);

                    final int fr = r, fc = c;
                    cell.addTextChangedListener(new SimpleTextWatcher() {
                        @Override
                        public void afterTextChanged(Editable s) {
                            try {
                                org.json.JSONArray trows = block.getTableRows();
                                if (trows != null && fr < trows.length()) {
                                    org.json.JSONArray trow = trows.getJSONArray(fr);
                                    trow.put(fc, s.toString());
                                }
                                if (listener != null) listener.onBlockChanged(holder.getAdapterPosition(), block);
                            } catch (Exception ignored) {}
                        }
                    });

                    LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
                    cell.setLayoutParams(clp);
                    rowLayout.addView(cell);
                }
                tableContainer.addView(rowLayout);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error binding table", e);
        }

        // Table control buttons
        TextView addRow = (TextView) holder.findByTag("addRow");
        TextView addCol = (TextView) holder.findByTag("addCol");
        TextView toggleHeader = (TextView) holder.findByTag("toggleHeader");

        if (addRow != null) {
            addRow.setOnClickListener(v -> {
                try {
                    org.json.JSONArray rows = block.getTableRows();
                    if (rows != null && rows.length() > 0) {
                        int cols = rows.getJSONArray(0).length();
                        org.json.JSONArray newRow = new org.json.JSONArray();
                        for (int i = 0; i < cols; i++) newRow.put("");
                        rows.put(newRow);
                        notifyItemChanged(holder.getAdapterPosition());
                        if (listener != null) listener.onBlockChanged(holder.getAdapterPosition(), block);
                    }
                } catch (Exception ignored) {}
            });
        }

        if (addCol != null) {
            addCol.setOnClickListener(v -> {
                try {
                    org.json.JSONArray rows = block.getTableRows();
                    if (rows != null) {
                        for (int i = 0; i < rows.length(); i++) {
                            rows.getJSONArray(i).put("");
                        }
                        notifyItemChanged(holder.getAdapterPosition());
                        if (listener != null) listener.onBlockChanged(holder.getAdapterPosition(), block);
                    }
                } catch (Exception ignored) {}
            });
        }

        if (toggleHeader != null) {
            toggleHeader.setOnClickListener(v -> {
                try {
                    block.content.put("hasHeader", !block.hasTableHeader());
                    notifyItemChanged(holder.getAdapterPosition());
                    if (listener != null) listener.onBlockChanged(holder.getAdapterPosition(), block);
                } catch (Exception ignored) {}
            });
        }
    }

    private void bindMathBlock(BlockViewHolder holder, ContentBlock block, int position) {
        EditText et = holder.findEditText();
        TextView preview = (TextView) holder.findByTag("mathPreview");
        if (et == null) return;
        setupEditText(et, block, position, holder);

        if (preview != null) {
            String latex = block.getLatex();
            if (latex.isEmpty()) {
                preview.setText("∑ Math equation");
                preview.setTextColor(0xFF475569);
            } else {
                // Simple LaTeX preview (basic rendering — full LaTeX would use WebView+MathJax)
                preview.setText(renderSimpleLatex(latex));
                preview.setTextColor(0xFFE2E8F0);
            }
        }
    }

    private String renderSimpleLatex(String latex) {
        // Basic LaTeX → Unicode approximation for preview
        return latex
            .replace("\\frac", "÷")
            .replace("\\sqrt", "√")
            .replace("\\sum", "∑")
            .replace("\\int", "∫")
            .replace("\\infty", "∞")
            .replace("\\pi", "π")
            .replace("\\alpha", "α")
            .replace("\\beta", "β")
            .replace("\\gamma", "γ")
            .replace("\\delta", "δ")
            .replace("\\theta", "θ")
            .replace("\\lambda", "λ")
            .replace("\\mu", "μ")
            .replace("\\sigma", "σ")
            .replace("\\omega", "ω")
            .replace("\\times", "×")
            .replace("\\div", "÷")
            .replace("\\pm", "±")
            .replace("\\leq", "≤")
            .replace("\\geq", "≥")
            .replace("\\neq", "≠")
            .replace("\\approx", "≈")
            .replace("\\rightarrow", "→")
            .replace("\\leftarrow", "←")
            .replace("\\forall", "∀")
            .replace("\\exists", "∃")
            .replace("\\in", "∈")
            .replace("\\subset", "⊂")
            .replace("\\cup", "∪")
            .replace("\\cap", "∩")
            .replace("{", "").replace("}", "")
            .replace("^", "^").replace("_", "₊");
    }

    private void bindFileBlock(BlockViewHolder holder, ContentBlock block, int position) {
        TextView nameView = (TextView) holder.findByTag("fileName");
        TextView sizeView = (TextView) holder.findByTag("fileSize");
        TextView iconView = (TextView) holder.findByTag("fileIcon");
        View openBtn = holder.findByTag("fileOpenBtn");
        View pill = holder.findByTag("filePill");

        String fileName = block.getFileName();
        if (fileName.isEmpty()) {
            if (nameView != null) nameView.setText("Tap to attach file");
            if (sizeView != null) sizeView.setText("");
        } else {
            if (nameView != null) nameView.setText(fileName);
            if (sizeView != null) sizeView.setText(ContentBlock.formatFileSize(block.getFileSize()));
            if (iconView != null) iconView.setText(getFileIcon(block.getFileType()));
        }

        View clickTarget = fileName.isEmpty() ? pill : openBtn;
        if (clickTarget != null) {
            clickTarget.setOnClickListener(v -> {
                if (listener != null) listener.onFileBlockClicked(position, block);
            });
        }
        if (pill != null && !fileName.isEmpty()) {
            pill.setOnClickListener(v -> {
                if (listener != null) listener.onFileBlockClicked(position, block);
            });
        }
    }

    private String getFileIcon(String fileType) {
        if (fileType == null) return "📄";
        switch (fileType.toLowerCase()) {
            case "pdf": return "📕";
            case "doc": case "docx": return "📘";
            case "xls": case "xlsx": return "📗";
            case "ppt": case "pptx": return "📙";
            case "zip": case "rar": return "📦";
            case "jpg": case "jpeg": case "png": case "gif": return "🖼️";
            case "mp3": case "wav": case "aac": return "🎵";
            case "mp4": case "avi": case "mkv": return "🎬";
            case "txt": return "📝";
            case "csv": return "📊";
            default: return "📄";
        }
    }

    private void bindLinkPreviewBlock(BlockViewHolder holder, ContentBlock block, int position) {
        EditText et = holder.findEditText();
        TextView titleView = (TextView) holder.findByTag("linkTitle");
        TextView descView = (TextView) holder.findByTag("linkDesc");
        TextView domainView = (TextView) holder.findByTag("linkDomain");
        View card = holder.findByTag("linkCard");

        if (et != null) {
            et.removeTextChangedListener(holder.textWatcher);
            et.setText(block.getUrl());
            TextWatcher tw = new SimpleTextWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    block.setUrl(s.toString());
                    // Update domain
                    try {
                        String url = s.toString();
                        if (url.contains("://")) {
                            String domain = url.split("://")[1].split("/")[0];
                            if (domainView != null) domainView.setText("🔗 " + domain);
                            block.content.put("domain", domain);
                        }
                    } catch (Exception ignored) {}
                    if (listener != null) listener.onBlockChanged(holder.getAdapterPosition(), block);
                }
            };
            holder.textWatcher = tw;
            et.addTextChangedListener(tw);
        }

        String title = block.getLinkTitle();
        String desc = block.getLinkDescription();
        String domain = block.getLinkDomain();

        if (titleView != null) titleView.setText(title.isEmpty() ? "Link Preview" : title);
        if (descView != null) {
            descView.setText(desc.isEmpty() ? "Paste a URL below" : desc);
        }
        if (domainView != null) domainView.setText(domain.isEmpty() ? "" : "🔗 " + domain);

        if (card != null) {
            card.setOnClickListener(v -> {
                if (listener != null) listener.onLinkPreviewClicked(position, block);
            });
        }
    }

    private void bindAudioBlock(BlockViewHolder holder, ContentBlock block, int position) {
        TextView playBtn = (TextView) holder.findByTag("audioPlayBtn");
        TextView duration = (TextView) holder.findByTag("audioDuration");
        View card = holder.findByTag("audioCard");

        String path = block.getAudioPath();
        if (path.isEmpty()) {
            if (playBtn != null) playBtn.setText("🎙️");
            if (duration != null) duration.setText("Tap to record");
        } else {
            if (playBtn != null) playBtn.setText("▶");
            if (duration != null) duration.setText(ContentBlock.formatDuration(block.getAudioDuration()));
        }

        if (card != null) {
            card.setOnClickListener(v -> {
                if (listener != null) listener.onAudioPlayRequested(position, block);
            });
        }
    }

    private void bindDrawingBlock(BlockViewHolder holder, ContentBlock block, int position) {
        ImageView img = (ImageView) holder.findByTag("drawingImage");
        TextView placeholder = (TextView) holder.findByTag("drawingPlaceholder");
        View frame = holder.findByTag("drawingFrame");

        String path = block.getDrawingPath();
        if (path != null && !path.isEmpty()) {
            try {
                img.setImageURI(Uri.parse(path));
                img.setVisibility(View.VISIBLE);
                if (placeholder != null) placeholder.setVisibility(View.GONE);
            } catch (Exception e) {
                img.setVisibility(View.GONE);
                if (placeholder != null) placeholder.setVisibility(View.VISIBLE);
            }
        } else {
            img.setVisibility(View.GONE);
            if (placeholder != null) placeholder.setVisibility(View.VISIBLE);
        }

        if (frame != null) {
            frame.setOnClickListener(v -> {
                if (listener != null) listener.onDrawingBlockClicked(position, block);
            });
        }
    }

    private void bindVideoBlock(BlockViewHolder holder, ContentBlock block, int position) {
        ImageView thumb = (ImageView) holder.findByTag("videoThumbnail");
        TextView placeholder = (TextView) holder.findByTag("videoPlaceholder");
        View frame = holder.findByTag("videoFrame");

        String uri = block.getVideoUri();
        if (uri != null && !uri.isEmpty()) {
            if (thumb != null) {
                try {
                    thumb.setImageURI(Uri.parse(uri));
                    thumb.setVisibility(View.VISIBLE);
                } catch (Exception e) {
                    thumb.setVisibility(View.GONE);
                }
            }
            if (placeholder != null) placeholder.setVisibility(View.GONE);
        } else {
            if (thumb != null) thumb.setVisibility(View.GONE);
            if (placeholder != null) placeholder.setVisibility(View.VISIBLE);
        }

        if (frame != null) {
            frame.setOnClickListener(v -> {
                if (listener != null) listener.onVideoBlockClicked(position, block);
            });
        }
    }

    private void bindLocationBlock(BlockViewHolder holder, ContentBlock block, int position) {
        TextView nameView = (TextView) holder.findByTag("locationName");
        TextView coordsView = (TextView) holder.findByTag("locationCoords");
        View card = holder.findByTag("locationCard");

        String name = block.getLocationName();
        double lat = block.getLatitude();
        double lng = block.getLongitude();

        if (name.isEmpty()) {
            if (nameView != null) nameView.setText("Tap to add location");
            if (coordsView != null) coordsView.setText("");
        } else {
            if (nameView != null) nameView.setText(name);
            if (coordsView != null) coordsView.setText(
                String.format(java.util.Locale.US, "%.4f, %.4f", lat, lng));
        }

        if (card != null) {
            card.setOnClickListener(v -> {
                if (listener != null) listener.onLocationBlockClicked(position, block);
            });
        }
    }

    // ═══ Common EditText Setup ═══

    private void setupEditText(EditText et, ContentBlock block, int position, BlockViewHolder holder) {
        // Clear old listeners before modifying text to prevent stale callbacks on recycled views
        et.setOnFocusChangeListener(null);
        et.setOnKeyListener(null);

        // Remove previous watcher
        et.removeTextChangedListener(holder != null ? holder.textWatcher : null);
        String nextText = block.getText() != null ? block.getText() : "";
        String currentText = et.getText() != null ? et.getText().toString() : "";
        if (!nextText.equals(currentText)) {
            int prevSelection = Math.max(0, et.getSelectionStart());
            et.setText(nextText);
            int newSelection = Math.min(prevSelection, nextText.length());
            et.setSelection(newSelection);
        }

        // Create & attach text watcher
        TextWatcher tw = new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                block.setText(s.toString());
                if (listener != null) {
                    int pos = holder != null ? holder.getAdapterPosition() : position;
                    if (pos != RecyclerView.NO_POSITION) listener.onBlockChanged(pos, block);
                }
            }
        };
        if (holder != null) holder.textWatcher = tw;
        et.addTextChangedListener(tw);

        // Focus handling
        et.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                int pos = holder != null ? holder.getAdapterPosition() : position;
                if (pos != RecyclerView.NO_POSITION && pos != focusedPosition) {
                    int old = focusedPosition;
                    focusedPosition = pos;
                    if (old >= 0 && old < getItemCount()) notifyItemChanged(old);
                    notifyItemChanged(pos);
                    if (listener != null) listener.onBlockFocused(pos, block);
                }
            }
        });

        // Enter key → create new block
        et.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN) {
                int cursorPos = et.getSelectionStart();
                if (listener != null) {
                    int pos = holder != null ? holder.getAdapterPosition() : position;
                    listener.onEnterPressed(pos, block, cursorPos);
                }
                return true;
            }
            // Backspace at start → merge with previous
            if (keyCode == KeyEvent.KEYCODE_DEL && event.getAction() == KeyEvent.ACTION_DOWN) {
                if (et.getSelectionStart() == 0 && et.getSelectionEnd() == 0) {
                    if (listener != null) {
                        int pos = holder != null ? holder.getAdapterPosition() : position;
                        if (pos > 0) listener.onDeleteAtStart(pos, block);
                    }
                    return true;
                }
            }
            return false;
        });
    }

    // ═══ Utility Methods ═══

    private int getNumberForBlock(int position) {
        int number = 1;
        for (int i = position - 1; i >= 0; i--) {
            ContentBlock prev = blocks.get(i);
            if (prev.blockType.equals(ContentBlock.TYPE_NUMBERED) && prev.indentLevel == blocks.get(position).indentLevel) {
                number++;
            } else {
                break;
            }
        }
        return number;
    }

    public void setFocusedPosition(int position) {
        int old = focusedPosition;
        focusedPosition = position;
        if (old >= 0 && old < getItemCount()) notifyItemChanged(old);
        if (position >= 0 && position < getItemCount()) notifyItemChanged(position);
    }

    public int getFocusedPosition() { return focusedPosition; }

    public EditText getFocusedEditText(RecyclerView recyclerView) {
        if (focusedPosition < 0 || focusedPosition >= getItemCount()) return null;
        RecyclerView.ViewHolder vh = recyclerView.findViewHolderForAdapterPosition(focusedPosition);
        if (vh instanceof BlockViewHolder) {
            return ((BlockViewHolder) vh).findEditText();
        }
        return null;
    }

    private int dp(int dp) {
        return Math.round(dp * density);
    }

    // ═══ Block Management ═══

    public void insertBlock(int position, ContentBlock block) {
        block.sortOrder = position;
        blocks.add(position, block);
        // Update sort orders
        for (int i = position; i < blocks.size(); i++) blocks.get(i).sortOrder = i;
        notifyItemInserted(position);
    }

    public void removeBlock(int position) {
        if (position >= 0 && position < blocks.size()) {
            blocks.remove(position);
            for (int i = position; i < blocks.size(); i++) blocks.get(i).sortOrder = i;
            notifyItemRemoved(position);
        }
    }

    public void moveBlock(int from, int to) {
        if (from < 0 || from >= blocks.size() || to < 0 || to >= blocks.size()) return;
        ContentBlock block = blocks.remove(from);
        blocks.add(to, block);
        for (int i = 0; i < blocks.size(); i++) blocks.get(i).sortOrder = i;
        notifyItemMoved(from, to);
    }

    public List<ContentBlock> getBlocks() { return blocks; }

    // ═══ ViewHolder ═══

    public static class BlockViewHolder extends RecyclerView.ViewHolder {
        ContentBlock currentBlock;
        TextWatcher textWatcher;
        int viewType;

        public BlockViewHolder(View itemView, int viewType) {
            super(itemView);
            this.viewType = viewType;
        }

        public EditText findEditText() {
            return (EditText) itemView.findViewWithTag("blockEditText");
        }

        public View findByTag(String tag) {
            return itemView.findViewWithTag(tag);
        }
    }

    // ═══ Simple TextWatcher ═══

    private static abstract class SimpleTextWatcher implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
    }
}
