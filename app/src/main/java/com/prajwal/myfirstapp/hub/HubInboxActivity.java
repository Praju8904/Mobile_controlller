package com.prajwal.myfirstapp.hub;


import com.prajwal.myfirstapp.R;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * Inbox screen — shows all pending items detected by the file scanner.
 * Allows accepting, overriding placement, or rejecting each item.
 */
public class HubInboxActivity extends AppCompatActivity {

    private HubFileRepository repo;
    private LinearLayout inboxItemsContainer;
    private TextView tvInboxCount;
    private Button btnAcceptHighConfidence;

    private String currentFilter = "ALL";
    private List<InboxItem> allItems = new ArrayList<>();
    private float dp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hub_inbox);

        repo = HubFileRepository.getInstance(this);
        dp = getResources().getDisplayMetrics().density;

        inboxItemsContainer = findViewById(R.id.inboxItemsContainer);
        tvInboxCount = findViewById(R.id.tvInboxCount);
        btnAcceptHighConfidence = findViewById(R.id.btnAcceptHighConfidence);

        findViewById(R.id.btnInboxBack).setOnClickListener(v -> finish());
        btnAcceptHighConfidence.setOnClickListener(v -> acceptHighConfidence());

        buildFilterChips();
        loadItems();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadItems();
    }

    private void buildFilterChips() {
        LinearLayout chipsContainer = findViewById(R.id.inboxFilterChips);
        String[] filters = {"ALL", "IMAGES", "DOCUMENTS", "VIDEOS", "CODE", "HIGH CONFIDENCE", "LOW CONFIDENCE"};
        for (String filter : filters) {
            TextView chip = new TextView(this);
            chip.setText(filter);
            chip.setTextSize(11);
            chip.setPadding((int)(12*dp), (int)(6*dp), (int)(12*dp), (int)(6*dp));
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setCornerRadius(20 * dp);
            boolean selected = filter.equals(currentFilter);
            bg.setColor(Color.parseColor(selected ? "#7C3AED" : "#1E293B"));
            chip.setBackground(bg);
            chip.setTextColor(Color.parseColor(selected ? "#FFFFFF" : "#94A3B8"));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, (int)(8*dp), 0);
            chip.setLayoutParams(lp);
            chip.setClickable(true);
            chip.setFocusable(true);
            chip.setOnClickListener(v -> {
                currentFilter = filter;
                buildFilterChips();
                chipsContainer.removeAllViews();
                buildFilterChips();
                applyFilter();
            });
            chipsContainer.addView(chip);
        }
    }

    private void loadItems() {
        allItems = repo.getPendingInboxItems();
        int count = allItems.size();
        tvInboxCount.setText(count + " file" + (count == 1 ? "" : "s") + " waiting");
        applyFilter();
    }

    private void applyFilter() {
        List<InboxItem> filtered = new ArrayList<>();
        for (InboxItem item : allItems) {
            if (currentFilter.equals("ALL")) { filtered.add(item); continue; }
            if (currentFilter.equals("IMAGES") && item.fileType == HubFile.FileType.IMAGE) { filtered.add(item); continue; }
            if (currentFilter.equals("DOCUMENTS") && item.fileType == HubFile.FileType.DOCUMENT) { filtered.add(item); continue; }
            if (currentFilter.equals("VIDEOS") && item.fileType == HubFile.FileType.VIDEO) { filtered.add(item); continue; }
            if (currentFilter.equals("CODE") && item.fileType == HubFile.FileType.CODE) { filtered.add(item); continue; }
            if (currentFilter.equals("HIGH CONFIDENCE") && item.autoCategorizationConfidence >= 80) { filtered.add(item); continue; }
            if (currentFilter.equals("LOW CONFIDENCE") && item.autoCategorizationConfidence < 50) { filtered.add(item); continue; }
        }
        buildItemCards(filtered);
    }

    private void buildItemCards(List<InboxItem> items) {
        inboxItemsContainer.removeAllViews();

        if (items.isEmpty()) {
            LinearLayout empty = new LinearLayout(this);
            empty.setOrientation(LinearLayout.VERTICAL);
            empty.setGravity(android.view.Gravity.CENTER);
            empty.setPadding(0, (int)(48*dp), 0, (int)(48*dp));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            empty.setLayoutParams(lp);

            TextView emoji = new TextView(this);
            emoji.setText("✅");
            emoji.setTextSize(40);
            emoji.setGravity(android.view.Gravity.CENTER);
            empty.addView(emoji);

            TextView msg = new TextView(this);
            msg.setText("Inbox clear!");
            msg.setTextColor(Color.parseColor("#10B981"));
            msg.setTextSize(18);
            msg.setGravity(android.view.Gravity.CENTER);
            msg.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            empty.addView(msg);

            TextView sub = new TextView(this);
            sub.setText("Everything is beautifully organized");
            sub.setTextColor(Color.parseColor("#64748B"));
            sub.setTextSize(13);
            sub.setGravity(android.view.Gravity.CENTER);
            sub.setPadding(0, (int)(6*dp), 0, 0);
            empty.addView(sub);

            inboxItemsContainer.addView(empty);
            return;
        }

        for (InboxItem item : items) {
            View card = buildInboxItemCard(item);
            inboxItemsContainer.addView(card);
        }
    }

    private View buildInboxItemCard(InboxItem item) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(14 * dp);
        bg.setColor(Color.parseColor("#0F172A"));
        bg.setStroke((int)dp, Color.parseColor("#1E293B"));
        card.setBackground(bg);
        card.setPadding((int)(14*dp), (int)(14*dp), (int)(14*dp), (int)(14*dp));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, (int)(10*dp));
        card.setLayoutParams(lp);

        // Top row: emoji + name + size
        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        topRow.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView emoji = new TextView(this);
        emoji.setText(item.getTypeEmoji());
        emoji.setTextSize(28);
        LinearLayout.LayoutParams emojiLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        emojiLp.setMargins(0, 0, (int)(12*dp), 0);
        emoji.setLayoutParams(emojiLp);
        topRow.addView(emoji);

        LinearLayout nameBlock = new LinearLayout(this);
        nameBlock.setOrientation(LinearLayout.VERTICAL);
        nameBlock.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView name = new TextView(this);
        name.setText(item.fileName != null ? item.fileName : "Unknown file");
        name.setTextColor(Color.parseColor("#F1F5F9"));
        name.setTextSize(13);
        name.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        name.setMaxLines(1);
        name.setEllipsize(TextUtils.TruncateAt.END);
        nameBlock.addView(name);

        LinearLayout metaRow = new LinearLayout(this);
        metaRow.setOrientation(LinearLayout.HORIZONTAL);
        metaRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams metaLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        metaLp.setMargins(0, (int)(2*dp), 0, 0);
        metaRow.setLayoutParams(metaLp);

        // Source badge
        TextView sourceBadge = new TextView(this);
        sourceBadge.setText(item.getSourceEmoji() + " " + (item.source != null ? item.source.name() : "OTHER"));
        sourceBadge.setTextColor(Color.parseColor("#94A3B8"));
        sourceBadge.setTextSize(10);
        LinearLayout.LayoutParams sourceLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sourceLp.setMargins(0, 0, (int)(8*dp), 0);
        sourceBadge.setLayoutParams(sourceLp);
        metaRow.addView(sourceBadge);

        TextView sizeTv = new TextView(this);
        sizeTv.setText(item.getFormattedSize());
        sizeTv.setTextColor(Color.parseColor("#64748B"));
        sizeTv.setTextSize(10);
        metaRow.addView(sizeTv);

        nameBlock.addView(metaRow);
        topRow.addView(nameBlock);
        card.addView(topRow);

        // Confidence chip
        LinearLayout confRow = new LinearLayout(this);
        confRow.setOrientation(LinearLayout.HORIZONTAL);
        confRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams confRowLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        confRowLp.setMargins(0, (int)(8*dp), 0, (int)(8*dp));
        confRow.setLayoutParams(confRowLp);

        // Confidence indicator dot
        View dot = new View(this);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams((int)(8*dp), (int)(8*dp));
        dotLp.setMargins(0, 0, (int)(6*dp), 0);
        dot.setLayoutParams(dotLp);
        int dotColor;
        if (item.autoCategorizationConfidence >= 80) dotColor = Color.parseColor("#10B981");
        else if (item.autoCategorizationConfidence >= 50) dotColor = Color.parseColor("#F59E0B");
        else dotColor = Color.parseColor("#EF4444");
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(dotColor);
        dot.setBackground(dotBg);
        confRow.addView(dot);

        TextView confLabel = new TextView(this);
        confLabel.setText("Suggested: " + (item.fileType != null ? item.fileType.name() : "OTHER")
                + "  (" + item.getConfidenceLabel() + " confidence " + item.autoCategorizationConfidence + "%)");
        confLabel.setTextColor(Color.parseColor("#94A3B8"));
        confLabel.setTextSize(11);
        confRow.addView(confLabel);
        card.addView(confRow);

        // Action buttons
        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button btnAccept = new Button(this);
        btnAccept.setText("✓ Accept");
        btnAccept.setAllCaps(false);
        btnAccept.setTextSize(11);
        btnAccept.setTextColor(Color.WHITE);
        btnAccept.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#059669")));
        LinearLayout.LayoutParams acceptLp = new LinearLayout.LayoutParams(0, (int)(36*dp), 1f);
        acceptLp.setMargins(0, 0, (int)(6*dp), 0);
        btnAccept.setLayoutParams(acceptLp);
        btnAccept.setOnClickListener(v -> {
            repo.updateInboxItemStatus(item.id, InboxItem.Status.ACCEPTED);
            importAndAcceptInboxItem(item);
            loadItems();
            Toast.makeText(this, "Accepted: " + item.fileName, Toast.LENGTH_SHORT).show();
        });

        Button btnSkip = new Button(this);
        btnSkip.setText("⊘ Skip");
        btnSkip.setAllCaps(false);
        btnSkip.setTextSize(11);
        btnSkip.setTextColor(Color.parseColor("#94A3B8"));
        btnSkip.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#1E293B")));
        LinearLayout.LayoutParams skipLp = new LinearLayout.LayoutParams(0, (int)(36*dp), 1f);
        skipLp.setMargins(0, 0, (int)(6*dp), 0);
        btnSkip.setLayoutParams(skipLp);
        btnSkip.setOnClickListener(v -> {
            repo.updateInboxItemStatus(item.id, InboxItem.Status.SNOOZED);
            loadItems();
        });

        Button btnReject = new Button(this);
        btnReject.setText("✕ Reject");
        btnReject.setAllCaps(false);
        btnReject.setTextSize(11);
        btnReject.setTextColor(Color.parseColor("#EF4444"));
        btnReject.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#1E293B")));
        LinearLayout.LayoutParams rejectLp = new LinearLayout.LayoutParams(0, (int)(36*dp), 1f);
        btnReject.setLayoutParams(rejectLp);
        btnReject.setOnClickListener(v -> {
            repo.updateInboxItemStatus(item.id, InboxItem.Status.REJECTED);
            loadItems();
        });

        actionRow.addView(btnAccept);
        actionRow.addView(btnSkip);
        actionRow.addView(btnReject);
        card.addView(actionRow);

        return card;
    }

    private void importAndAcceptInboxItem(InboxItem item) {
        HubFile file = new HubFile();
        file.originalFileName = item.fileName;
        file.displayName = item.fileName;
        file.filePath = item.filePath;
        file.fileSize = item.fileSize;
        file.mimeType = item.mimeType;
        file.fileType = item.fileType;
        file.source = item.source;
        file.folderId = item.suggestedFolderId;
        file.projectId = item.suggestedProjectId;
        String ext = item.fileName != null && item.fileName.contains(".")
                ? item.fileName.substring(item.fileName.lastIndexOf('.') + 1) : "";
        file.fileExtension = ext;
        repo.addFile(file);
    }

    private void acceptHighConfidence() {
        List<InboxItem> pending = repo.getPendingInboxItems();
        int accepted = 0;
        for (InboxItem item : pending) {
            if (item.autoCategorizationConfidence >= 80) {
                repo.updateInboxItemStatus(item.id, InboxItem.Status.ACCEPTED);
                importAndAcceptInboxItem(item);
                accepted++;
            }
        }
        if (accepted > 0) {
            loadItems();
            Toast.makeText(this, "Accepted " + accepted + " high-confidence file" + (accepted == 1 ? "" : "s"), Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "No high-confidence items to accept", Toast.LENGTH_SHORT).show();
        }
    }
}
