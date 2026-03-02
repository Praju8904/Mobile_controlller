package com.prajwal.myfirstapp.vault;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Privacy risk scanner for vault files.
 *
 * <p>Scans all vault files and categorises them into three risk buckets:
 * <ul>
 *   <li>GPS Risk – images whose original filenames suggest they came from a camera
 *       app (prefix "IMG_" / "DSC_" etc.) and may therefore carry GPS EXIF data.</li>
 *   <li>Sensitive Names – documents whose filenames contain privacy-sensitive keywords
 *       (passport, ssn, bank, account, tax, …).</li>
 *   <li>Old Files – any file whose {@code importedAt} timestamp is more than two years ago.</li>
 * </ul>
 * A "privacy score" (percentage of risk-free files) is shown at the bottom.
 * A "Strip GPS" button explains the workflow for removing GPS metadata on export.
 */
public class VaultPrivacyScannerActivity extends Activity {

    // ── Risk detection ────────────────────────────────────────────────────────
    private static final String[] CAMERA_PREFIXES = {
            "IMG_", "DSC_", "DCIM", "PXL_", "PANO_", "VID_", "MOV_",
            "CAM_", "PHOTO_", "BURST_", "SNAP_"
    };

    private static final String[] SENSITIVE_KEYWORDS = {
            "passport", "ssn", "bank", "account", "tax", "medical",
            "insurance", "password", "credit", "contract", "license", "id_card"
    };

    private static final long TWO_YEARS_MS = 2L * 365 * 24 * 60 * 60 * 1000;

    // ── Colours (dark-vault palette) ─────────────────────────────────────────
    private static final int CLR_BG        = 0xFF0A0E21;
    private static final int CLR_CARD      = 0xFF1E293B;
    private static final int CLR_ACCENT    = 0xFF6C63FF;
    private static final int CLR_TEXT_PRI  = 0xFFF1F5F9;
    private static final int CLR_TEXT_SEC  = 0xFF94A3B8;
    private static final int CLR_RISK_HIGH = 0xFFEF4444;
    private static final int CLR_RISK_MED  = 0xFFF59E0B;
    private static final int CLR_RISK_LOW  = 0xFF10B981;
    private static final int CLR_DIVIDER   = 0xFF1E293B;

    // ── State ────────────────────────────────────────────────────────────────
    private MediaVaultRepository repo;
    private LinearLayout contentLayout;
    private TextView tvScore;
    private TextView tvScanStatus;

    // ── Lifecycle ────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE);

        repo = MediaVaultRepository.getInstance(this);
        if (!repo.isUnlocked()) { finish(); return; }

        setContentView(buildRootLayout());
        startScan();
    }

    // ── UI construction ──────────────────────────────────────────────────────

    private LinearLayout buildRootLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(CLR_BG);
        root.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        root.addView(buildTopBar());

        // scanning status label
        tvScanStatus = new TextView(this);
        tvScanStatus.setText("🔍  Scanning vault…");
        tvScanStatus.setTextColor(CLR_TEXT_SEC);
        tvScanStatus.setTextSize(13);
        tvScanStatus.setPadding(dp(16), dp(10), dp(16), dp(4));
        root.addView(tvScanStatus);

        // scrollable results area
        ScrollView scroll = new ScrollView(this);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        scroll.setBackgroundColor(CLR_BG);

        contentLayout = new LinearLayout(this);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setPadding(dp(12), dp(4), dp(12), dp(16));
        scroll.addView(contentLayout);
        root.addView(scroll);

        // privacy score footer
        tvScore = new TextView(this);
        tvScore.setText("Privacy Score: –");
        tvScore.setTextColor(CLR_TEXT_PRI);
        tvScore.setTextSize(15);
        tvScore.setTypeface(null, Typeface.BOLD);
        tvScore.setGravity(Gravity.CENTER);
        tvScore.setPadding(dp(16), dp(12), dp(16), dp(12));
        tvScore.setBackgroundColor(CLR_CARD);
        root.addView(tvScore);

        // strip GPS button
        TextView btnStrip = new TextView(this);
        btnStrip.setText("🛡  Strip GPS on Export");
        btnStrip.setTextColor(CLR_TEXT_PRI);
        btnStrip.setTextSize(14);
        btnStrip.setGravity(Gravity.CENTER);
        btnStrip.setPadding(dp(16), dp(14), dp(16), dp(14));
        btnStrip.setBackgroundColor(CLR_ACCENT);
        btnStrip.setOnClickListener(v -> showStripGpsDialog());
        root.addView(btnStrip);

        return root;
    }

    private LinearLayout buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(CLR_CARD);
        bar.setPadding(dp(12), dp(14), dp(16), dp(14));
        bar.setGravity(Gravity.CENTER_VERTICAL);

        TextView btnBack = new TextView(this);
        btnBack.setText("←");
        btnBack.setTextColor(CLR_ACCENT);
        btnBack.setTextSize(20);
        btnBack.setPadding(0, 0, dp(16), 0);
        btnBack.setOnClickListener(v -> finish());

        TextView title = new TextView(this);
        title.setText("Privacy Scanner");
        title.setTextColor(CLR_TEXT_PRI);
        title.setTextSize(18);
        title.setTypeface(null, Typeface.BOLD);
        title.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        bar.addView(btnBack);
        bar.addView(title);
        return bar;
    }

    // ── Scanning logic ───────────────────────────────────────────────────────

    private void startScan() {
        new Thread(() -> {
            List<VaultFileItem> all = repo.getAllFiles();
            List<VaultFileItem> gpsRisk      = new ArrayList<>();
            List<VaultFileItem> sensitiveNames = new ArrayList<>();
            List<VaultFileItem> oldFiles      = new ArrayList<>();

            long cutoff = System.currentTimeMillis() - TWO_YEARS_MS;

            for (VaultFileItem f : all) {
                if (isGpsRisk(f))       gpsRisk.add(f);
                if (isSensitiveName(f)) sensitiveNames.add(f);
                if (f.importedAt > 0 && f.importedAt < cutoff) oldFiles.add(f);
            }

            final List<VaultFileItem> gps  = gpsRisk;
            final List<VaultFileItem> sens = sensitiveNames;
            final List<VaultFileItem> old  = oldFiles;
            final int total = all.size();

            new Handler(Looper.getMainLooper()).post(() ->
                    renderResults(total, gps, sens, old));
        }).start();
    }

    private boolean isGpsRisk(VaultFileItem f) {
        if (f.fileType != VaultFileItem.FileType.IMAGE) return false;
        if (f.originalFileName == null) return false;
        String upper = f.originalFileName.toUpperCase(Locale.ROOT);
        for (String prefix : CAMERA_PREFIXES) {
            if (upper.startsWith(prefix) || upper.contains("/" + prefix)) return true;
        }
        return false;
    }

    private boolean isSensitiveName(VaultFileItem f) {
        if (f.originalFileName == null) return false;
        String lower = f.originalFileName.toLowerCase(Locale.ROOT);
        for (String kw : SENSITIVE_KEYWORDS) {
            if (lower.contains(kw)) return true;
        }
        return false;
    }

    // ── Result rendering ─────────────────────────────────────────────────────

    private void renderResults(int total,
                               List<VaultFileItem> gps,
                               List<VaultFileItem> sensitive,
                               List<VaultFileItem> old) {
        tvScanStatus.setText("✅  Scan complete — " + total + " file" + (total == 1 ? "" : "s") + " analysed");

        contentLayout.removeAllViews();

        addSection("📍 GPS Risk", CLR_RISK_HIGH,
                "Images from camera apps may contain GPS coordinates in EXIF metadata.",
                gps);
        addSection("🔑 Sensitive Names", CLR_RISK_MED,
                "Files whose names contain privacy-sensitive keywords.",
                sensitive);
        addSection("📅 Old Files (2+ years)", CLR_RISK_LOW,
                "Files that have been in the vault for more than two years.",
                old);

        // calculate score: each risky file counts once even if in multiple buckets
        java.util.Set<String> risky = new java.util.HashSet<>();
        for (VaultFileItem f : gps)       risky.add(f.id);
        for (VaultFileItem f : sensitive) risky.add(f.id);
        for (VaultFileItem f : old)       risky.add(f.id);

        int score = total == 0 ? 100 : (int) ((total - risky.size()) * 100.0 / total);
        int scoreColor = score >= 80 ? CLR_RISK_LOW : (score >= 50 ? CLR_RISK_MED : CLR_RISK_HIGH);
        tvScore.setText("Privacy Score: " + score + "%");
        tvScore.setTextColor(scoreColor);
    }

    private void addSection(String title, int accentColor, String description,
                            List<VaultFileItem> files) {
        // Section header
        TextView header = new TextView(this);
        header.setText(title + "  (" + files.size() + ")");
        header.setTextColor(accentColor);
        header.setTextSize(14);
        header.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        hp.setMargins(0, dp(14), 0, dp(4));
        header.setLayoutParams(hp);
        contentLayout.addView(header);

        // Description
        TextView desc = new TextView(this);
        desc.setText(description);
        desc.setTextColor(CLR_TEXT_SEC);
        desc.setTextSize(12);
        LinearLayout.LayoutParams dp_ = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        dp_.setMargins(0, 0, 0, dp(6));
        desc.setLayoutParams(dp_);
        contentLayout.addView(desc);

        if (files.isEmpty()) {
            TextView none = new TextView(this);
            none.setText("No issues found ✓");
            none.setTextColor(CLR_RISK_LOW);
            none.setTextSize(13);
            none.setPadding(dp(12), dp(10), dp(12), dp(10));
            none.setBackgroundColor(CLR_CARD);
            LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            np.setMargins(0, 0, 0, dp(2));
            none.setLayoutParams(np);
            contentLayout.addView(none);
            return;
        }

        for (VaultFileItem f : files) {
            contentLayout.addView(buildFileRow(f, accentColor));
        }
    }

    private LinearLayout buildFileRow(VaultFileItem f, int iconColor) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackgroundColor(CLR_CARD);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(2));
        row.setLayoutParams(lp);

        // Risk icon indicator
        TextView icon = new TextView(this);
        icon.setText("⚠");
        icon.setTextColor(iconColor);
        icon.setTextSize(16);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        iconLp.setMarginEnd(dp(10));
        icon.setLayoutParams(iconLp);

        // Filename
        TextView name = new TextView(this);
        String displayName = (f.originalFileName != null && !f.originalFileName.isEmpty())
                ? f.originalFileName : f.vaultFileName;
        name.setText(displayName);
        name.setTextColor(CLR_TEXT_PRI);
        name.setTextSize(13);
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        name.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // File type badge
        TextView type = new TextView(this);
        type.setText(f.fileType != null ? f.fileType.name().toLowerCase() : "other");
        type.setTextColor(CLR_TEXT_SEC);
        type.setTextSize(11);

        row.addView(icon);
        row.addView(name);
        row.addView(type);
        return row;
    }

    // ── Dialogs ──────────────────────────────────────────────────────────────

    private void showStripGpsDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Strip GPS Metadata")
                .setMessage("When you export an image from the vault, GPS and other EXIF " +
                        "metadata will be stripped from the exported copy automatically. " +
                        "The original encrypted file inside the vault is never modified.\n\n" +
                        "To apply this to a file, export it via the vault viewer and " +
                        "the clean copy will be saved to your chosen destination.")
                .setPositiveButton("Got it", null)
                .show();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }
}
