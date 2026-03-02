package com.prajwal.myfirstapp.hub;


import com.prajwal.myfirstapp.R;
import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Smart File Hub — home screen.
 *
 * Sections:
 * 1. Header with dynamic subtitle
 * 2. Storage Overview Card (arc chart + segmented bar)
 * 3. Quick Access grid (8 file types)
 * 4. Recently Accessed strip
 * 5. Inbox card
 * 6. Smart Folders
 * 7. Projects strip
 * 8. Activity feed
 * 9. FAB speed dial
 */
public class SmartFileHubActivity extends AppCompatActivity {

    private static final int REQUEST_IMPORT_FILE = 5001;
    private static final int REQUEST_STORAGE_PERMISSION = 5002;
    private static final int REQUEST_MANAGE_STORAGE = 5003;

    private HubFileRepository repo;

    // Views
    private DrawerLayout drawerLayout;
    private TextView tvHubSubtitle;
    private StorageArcView storageArcView;
    private StorageArcView drawerStorageArc;
    private LinearLayout storageSegmentBar;
    private LinearLayout storageLegend;
    private TextView tvTotalFilesCount;
    private TextView tvTotalFoldersCount;
    private TextView tvDuplicatesCount;
    private TextView tvInboxBadge;
    private TextView tvInboxTitle;
    private TextView tvInboxSubtitle;
    private LinearLayout inboxPreviewStrip;
    private LinearLayout inboxActionRow;
    private LinearLayout smartFoldersContainer;
    private LinearLayout projectsStrip;
    private LinearLayout activityFeedContainer;
    private LinearLayout activityEmptyState;
    private LinearLayout recentFilesStrip;
    private Button fabMain;
    private LinearLayout fabSpeedDial;
    private boolean fabExpanded = false;

    // Drawer views
    private TextView tvDrawerStorageLabel;
    private TextView drawerInboxBadge;

    // Type count views
    private TextView tvCountPdf, tvCountDoc, tvCountImage, tvCountScreenshot;
    private TextView tvCountVideo, tvCountAudio, tvCountCode, tvCountArchive;
    private TextView drawerCountPdf, drawerCountDoc, drawerCountImage;
    private TextView drawerCountVideo, drawerCountAudio, drawerCountCode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_smart_file_hub);

        repo = HubFileRepository.getInstance(this);
        bindViews();
        setupClickListeners();
        loadData();

        // Request storage permissions, then scan
        requestStoragePermissionAndScan();

        // Trigger content indexing on background thread
        HubContentIndexer.getInstance(this).indexAllPending(null);

        // Detect version chains in background
        repo.detectVersionChains(null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshData();
    }

    // ─── Storage Permission Handling ──────────────────────────────────────────

    private void requestStoragePermissionAndScan() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ — need MANAGE_EXTERNAL_STORAGE
            if (Environment.isExternalStorageManager()) {
                triggerScan();
            } else {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivityForResult(intent, REQUEST_MANAGE_STORAGE);
                } catch (Exception e) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivityForResult(intent, REQUEST_MANAGE_STORAGE);
                }
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6-10 — request READ/WRITE_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{
                                Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                        }, REQUEST_STORAGE_PERMISSION);
            } else {
                triggerScan();
            }
        } else {
            triggerScan();
        }
    }

    private void triggerScan() {
        repo.scanForNewFiles(this::refreshData);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                triggerScan();
            } else {
                Toast.makeText(this, "Storage permission needed to scan files", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void bindViews() {
        drawerLayout = findViewById(R.id.hubDrawerLayout);
        tvHubSubtitle = findViewById(R.id.tvHubSubtitle);
        storageArcView = findViewById(R.id.storageArcView);
        drawerStorageArc = findViewById(R.id.drawerStorageArc);
        storageSegmentBar = findViewById(R.id.storageSegmentBar);
        storageLegend = findViewById(R.id.storageLegend);
        tvTotalFilesCount = findViewById(R.id.tvTotalFilesCount);
        tvTotalFoldersCount = findViewById(R.id.tvTotalFoldersCount);
        tvDuplicatesCount = findViewById(R.id.tvDuplicatesCount);
        tvInboxBadge = findViewById(R.id.tvInboxBadge);
        tvInboxTitle = findViewById(R.id.tvInboxTitle);
        tvInboxSubtitle = findViewById(R.id.tvInboxSubtitle);
        inboxPreviewStrip = findViewById(R.id.inboxPreviewStrip);
        inboxActionRow = findViewById(R.id.inboxActionRow);
        smartFoldersContainer = findViewById(R.id.smartFoldersContainer);
        projectsStrip = findViewById(R.id.projectsStrip);
        activityFeedContainer = findViewById(R.id.activityFeedContainer);
        activityEmptyState = findViewById(R.id.activityEmptyState);
        recentFilesStrip = findViewById(R.id.recentFilesStrip);
        fabMain = findViewById(R.id.fabMain);
        fabSpeedDial = findViewById(R.id.fabSpeedDial);
        tvDrawerStorageLabel = findViewById(R.id.tvDrawerStorageLabel);
        drawerInboxBadge = findViewById(R.id.drawerInboxBadge);

        tvCountPdf = findViewById(R.id.tvCountPdf);
        tvCountDoc = findViewById(R.id.tvCountDoc);
        tvCountImage = findViewById(R.id.tvCountImage);
        tvCountScreenshot = findViewById(R.id.tvCountScreenshot);
        tvCountVideo = findViewById(R.id.tvCountVideo);
        tvCountAudio = findViewById(R.id.tvCountAudio);
        tvCountCode = findViewById(R.id.tvCountCode);
        tvCountArchive = findViewById(R.id.tvCountArchive);

        drawerCountPdf = findViewById(R.id.drawerCountPdf);
        drawerCountDoc = findViewById(R.id.drawerCountDoc);
        drawerCountImage = findViewById(R.id.drawerCountImage);
        drawerCountVideo = findViewById(R.id.drawerCountVideo);
        drawerCountAudio = findViewById(R.id.drawerCountAudio);
        drawerCountCode = findViewById(R.id.drawerCountCode);
    }

    private void setupClickListeners() {
        // Back
        findViewById(R.id.btnHubBack).setOnClickListener(v -> finish());

        // Duplicates count — open Duplicate Manager
        tvDuplicatesCount.setOnClickListener(v ->
                startActivity(new Intent(this, HubDuplicateManagerActivity.class)));

        // Drawer toggle
        findViewById(R.id.btnHubDrawer).setOnClickListener(v -> {
            if (drawerLayout.isDrawerOpen(Gravity.START)) {
                drawerLayout.closeDrawer(Gravity.START);
            } else {
                drawerLayout.openDrawer(Gravity.START);
            }
        });

        // Search
        findViewById(R.id.btnHubSearch).setOnClickListener(v ->
                startActivity(new Intent(this, HubSearchActivity.class)));

        // Settings
        findViewById(R.id.btnHubSettings).setOnClickListener(v ->
                startActivity(new Intent(this, HubSettingsActivity.class)));

        // Privacy Analyzer
        safeClick(R.id.btnHubPrivacy, () ->
                startActivity(new Intent(this, HubPrivacyAnalyzerActivity.class)));

        // Inbox card
        findViewById(R.id.inboxCard).setOnClickListener(v -> openInbox());

        // Inbox buttons
        findViewById(R.id.btnReviewAll).setOnClickListener(v -> openInbox());
        findViewById(R.id.btnAutoOrganize).setOnClickListener(v -> autoOrganizeHighConfidence());

        // View all recent
        findViewById(R.id.btnViewAllRecent).setOnClickListener(v -> {
            Intent i = new Intent(this, HubFileBrowserActivity.class);
            i.putExtra(HubFileBrowserActivity.EXTRA_TITLE, "Recent Files");
            i.putExtra(HubFileBrowserActivity.EXTRA_RECENT_ONLY, true);
            startActivity(i);
        });

        // View all projects
        findViewById(R.id.btnViewAllProjects).setOnClickListener(v ->
                startActivity(new Intent(this, HubProjectsActivity.class)));

        // View all activity
        findViewById(R.id.btnViewAllActivity).setOnClickListener(v -> {
            Intent i = new Intent(this, HubFileBrowserActivity.class);
            i.putExtra(HubFileBrowserActivity.EXTRA_TITLE, "Activity Log");
            i.putExtra(HubFileBrowserActivity.EXTRA_RECENT_ONLY, true);
            startActivity(i);
        });

        // Manage smart folders
        findViewById(R.id.btnManageSmartFolders).setOnClickListener(v -> {
            Intent i = new Intent(this, HubFileBrowserActivity.class);
            i.putExtra(HubFileBrowserActivity.EXTRA_TITLE, "Smart Folders");
            startActivity(i);
        });

        // Type cards
        setupTypeCard(R.id.typeCardPdf, HubFile.FileType.PDF);
        setupTypeCard(R.id.typeCardDoc, HubFile.FileType.DOCUMENT);
        setupTypeCard(R.id.typeCardImage, HubFile.FileType.IMAGE);
        setupTypeCard(R.id.typeCardScreenshot, HubFile.FileType.SCREENSHOT);
        setupTypeCard(R.id.typeCardVideo, HubFile.FileType.VIDEO);
        setupTypeCard(R.id.typeCardAudio, HubFile.FileType.AUDIO);
        setupTypeCard(R.id.typeCardCode, HubFile.FileType.CODE);
        setupTypeCard(R.id.typeCardArchive, HubFile.FileType.ARCHIVE);

        // Drawer items
        findViewById(R.id.drawerItemHome).setOnClickListener(v -> drawerLayout.closeDrawer(Gravity.START));
        findViewById(R.id.drawerItemInbox).setOnClickListener(v -> { drawerLayout.closeDrawer(Gravity.START); openInbox(); });
        findViewById(R.id.drawerItemAllFiles).setOnClickListener(v -> { drawerLayout.closeDrawer(Gravity.START); showAllFiles(); });
        findViewById(R.id.drawerItemFavourites).setOnClickListener(v -> { drawerLayout.closeDrawer(Gravity.START); showFavourites(); });
        findViewById(R.id.drawerItemRecent).setOnClickListener(v -> { drawerLayout.closeDrawer(Gravity.START); showRecent(); });

        // ── New advanced navigation items in drawer ──────────────────────────
        safeClick(R.id.drawerItemAnalytics, () -> {
            drawerLayout.closeDrawer(Gravity.START);
            startActivity(new Intent(this, HubAnalyticsActivity.class));
        });
        safeClick(R.id.drawerItemTimeline, () -> {
            drawerLayout.closeDrawer(Gravity.START);
            startActivity(new Intent(this, HubFileTimelineActivity.class));
        });
        safeClick(R.id.drawerItemCollections, () -> {
            drawerLayout.closeDrawer(Gravity.START);
            startActivity(new Intent(this, HubCollectionsActivity.class));
        });
        safeClick(R.id.drawerItemWatchlist, () -> {
            drawerLayout.closeDrawer(Gravity.START);
            startActivity(new Intent(this, HubWatchlistActivity.class));
        });
        safeClick(R.id.drawerItemBatchOps, () -> {
            drawerLayout.closeDrawer(Gravity.START);
            startActivity(new Intent(this, HubBatchOperationsActivity.class));
        });

        // Prompt 2 drawer items
        safeClick(R.id.drawerItemShareProfiles, () -> {
            drawerLayout.closeDrawer(Gravity.START);
            startActivity(new Intent(this, HubShareProfilesActivity.class));
        });
        safeClick(R.id.drawerItemShareHistory, () -> {
            drawerLayout.closeDrawer(Gravity.START);
            startActivity(new Intent(this, HubShareHistoryActivity.class));
        });
        safeClick(R.id.drawerItemExpiryCalendar, () -> {
            drawerLayout.closeDrawer(Gravity.START);
            startActivity(new Intent(this, HubExpiryCalendarActivity.class));
        });
        safeClick(R.id.drawerItemTimeCapsule, () -> {
            drawerLayout.closeDrawer(Gravity.START);
            startActivity(new Intent(this, HubTimeCapsuleActivity.class));
        });

        // Drawer type items
        setupDrawerTypeItem(R.id.drawerTypePdf, HubFile.FileType.PDF);
        setupDrawerTypeItem(R.id.drawerTypeDoc, HubFile.FileType.DOCUMENT);
        setupDrawerTypeItem(R.id.drawerTypeImage, HubFile.FileType.IMAGE);
        setupDrawerTypeItem(R.id.drawerTypeVideo, HubFile.FileType.VIDEO);
        setupDrawerTypeItem(R.id.drawerTypeAudio, HubFile.FileType.AUDIO);
        setupDrawerTypeItem(R.id.drawerTypeCode, HubFile.FileType.CODE);

        // FAB
        fabMain.setOnClickListener(v -> toggleFabSpeedDial());

        // Storage arc → Storage Intelligence
        storageArcView.setOnClickListener(v ->
                startActivity(new Intent(this, HubStorageIntelligenceActivity.class)));

        // Browse Storage buttons
        safeClick(R.id.btnBrowseInternalStorage, () -> browseStorage(false));
        safeClick(R.id.btnBrowseExternalStorage, () -> browseStorage(true));

        // Speed dial items
        safeClick(R.id.fabImportFiles, () -> {
            collapseFab();
            openFilePicker();
        });
        safeClick(R.id.fabScanNow, () -> {
            collapseFab();
            triggerManualScan();
        });
        safeClick(R.id.fabNewFolder, () -> {
            collapseFab();
            showCreateFolderDialog();
        });
        safeClick(R.id.fabNewProject, () -> {
            collapseFab();
            showCreateProjectDialog();
        });
        safeClick(R.id.fabNewSmartFolder, () -> {
            collapseFab();
            startActivity(new Intent(this, HubSmartFolderBuilderActivity.class));
        });
        safeClick(R.id.fabPackageSend, () -> {
            collapseFab();
            startActivity(new Intent(this, HubPackageAndSendActivity.class));
        });
        safeClick(R.id.fabFocusMode, () -> {
            collapseFab();
            startActivity(new Intent(this, HubFocusModeActivity.class));
        });

        // Long-press on search bar → Command Palette
        View searchBar = findViewById(R.id.searchBarContainer);
        if (searchBar != null) {
            searchBar.setOnLongClickListener(v -> {
                startActivity(new Intent(this, HubCommandPaletteActivity.class));
                return true;
            });
        }
    }

    /** Helper: attaches a click listener to any view if it exists. */
    private void safeClick(int viewId, Runnable action) {
        View v = findViewById(viewId);
        if (v != null) v.setOnClickListener(ignored -> action.run());
    }

    private void setupTypeCard(int viewId, HubFile.FileType type) {
        View card = findViewById(viewId);
        if (card != null) {
            card.setOnClickListener(v -> {
                Intent intent = new Intent(this, HubFileBrowserActivity.class);
                intent.putExtra(HubFileBrowserActivity.EXTRA_FILTER_TYPE, type.name());
                intent.putExtra(HubFileBrowserActivity.EXTRA_TITLE, type.name() + " Files");
                startActivity(intent);
            });
        }
    }

    private void setupDrawerTypeItem(int viewId, HubFile.FileType type) {
        View item = findViewById(viewId);
        if (item != null) {
            item.setOnClickListener(v -> {
                drawerLayout.closeDrawer(Gravity.START);
                Intent intent = new Intent(this, HubFileBrowserActivity.class);
                intent.putExtra(HubFileBrowserActivity.EXTRA_FILTER_TYPE, type.name());
                intent.putExtra(HubFileBrowserActivity.EXTRA_TITLE, type.name() + " Files");
                startActivity(intent);
            });
        }
    }

    private void showAllFiles() {
        Intent i = new Intent(this, HubFileBrowserActivity.class);
        i.putExtra(HubFileBrowserActivity.EXTRA_TITLE, "All Files");
        startActivity(i);
    }
    private void showFavourites() {
        Intent i = new Intent(this, HubFileBrowserActivity.class);
        i.putExtra(HubFileBrowserActivity.EXTRA_TITLE, "Favourites");
        i.putExtra(HubFileBrowserActivity.EXTRA_FAVOURITES_ONLY, true);
        startActivity(i);
    }
    private void showRecent() {
        Intent i = new Intent(this, HubFileBrowserActivity.class);
        i.putExtra(HubFileBrowserActivity.EXTRA_TITLE, "Recent Files");
        i.putExtra(HubFileBrowserActivity.EXTRA_RECENT_ONLY, true);
        startActivity(i);
    }
    // Legacy methods kept for any remaining references
    private void showAllFilesToast() { showAllFiles(); }
    private void showFavouritesToast() { showFavourites(); }
    private void showRecentToast() { showRecent(); }

    // ─── Data Loading ─────────────────────────────────────────────────────────

    private void loadData() {
        refreshStorageCard();
        refreshTypeCounts();
        refreshInboxSection();
        refreshSmartFolders();
        refreshProjects();
        refreshActivityFeed();
        refreshRecentFiles();
        refreshPredictiveSection();
        updateSubtitle();
    }

    private void refreshData() {
        runOnUiThread(this::loadData);
    }

    /**
     * Shows the "You might need this" predictive section on the home screen.
     * Only shown when the predictive engine has enough data (2+ weeks of usage);
     * before that shows "Learning your patterns…" placeholder.
     */
    private void refreshPredictiveSection() {
        View predictiveCard = findViewById(R.id.predictiveSection);
        if (predictiveCard == null) return;
        android.widget.LinearLayout container = predictiveCard.findViewWithTag("predictive_container");
        if (container == null) return;
        container.removeAllViews();

        HubPredictiveEngine engine = HubPredictiveEngine.getInstance(this);
        if (!engine.hasEnoughData()) {
            TextView tv = new TextView(this);
            tv.setText("🧠 Learning your patterns… (" + engine.getSessionCount()
                    + "/" + HubPredictiveEngine.MIN_SESSIONS_BEFORE_PREDICTION + " sessions)");
            tv.setTextColor(Color.parseColor("#6B7280"));
            tv.setTextSize(13);
            container.addView(tv);
            return;
        }

        java.util.List<HubPredictiveEngine.Suggestion> suggestions = engine.getSuggestions();
        if (suggestions.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText("No suggestions right now.");
            tv.setTextColor(Color.parseColor("#6B7280"));
            tv.setTextSize(13);
            container.addView(tv);
            return;
        }

        for (HubPredictiveEngine.Suggestion s : suggestions) {
            HubFile f = repo.getFileById(s.fileId);
            if (f == null) continue;
            android.widget.LinearLayout row = new android.widget.LinearLayout(this);
            row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(0, 8, 0, 8);

            TextView tvEmoji = new TextView(this);
            tvEmoji.setText(f.getTypeEmoji());
            tvEmoji.setTextSize(20);
            row.addView(tvEmoji);

            android.widget.LinearLayout info = new android.widget.LinearLayout(this);
            info.setOrientation(android.widget.LinearLayout.VERTICAL);
            android.widget.LinearLayout.LayoutParams infoLp =
                    new android.widget.LinearLayout.LayoutParams(0,
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            infoLp.setMargins(12, 0, 0, 0);
            info.setLayoutParams(infoLp);

            String name = f.displayName != null ? f.displayName : f.originalFileName;
            if (name != null && name.length() > 28) name = name.substring(0, 25) + "…";
            TextView tvName = new TextView(this);
            tvName.setText(name);
            tvName.setTextColor(Color.parseColor("#F1F5F9"));
            tvName.setTextSize(13);
            info.addView(tvName);

            TextView tvReason = new TextView(this);
            tvReason.setText(s.reason);
            tvReason.setTextColor(Color.parseColor("#6B7280"));
            tvReason.setTextSize(11);
            info.addView(tvReason);

            row.addView(info);
            final HubFile finalF = f;
            row.setOnClickListener(v -> {
                Intent i = new Intent(this, HubFileViewerActivity.class);
                i.putExtra("fileId", finalF.id);
                startActivity(i);
            });
            container.addView(row);
        }
    }

    private void refreshStorageCard() {
        try {
            long total = repo.getDeviceTotalBytes();
            long used = repo.getDeviceUsedBytes();
            float pct = total > 0 ? (used * 100f / total) : 0f;

            String usedStr = formatBytes(used);
            String totalStr = formatBytes(total);

            storageArcView.setPercentage(pct, (int) pct + "%", usedStr + " / " + totalStr);
            drawerStorageArc.setPercentage(pct, (int) pct + "%", null);
            tvDrawerStorageLabel.setText(usedStr + " of " + totalStr + " used");

            // Segmented bar
            storageSegmentBar.removeAllViews();
            storageLegend.removeAllViews();
            Map<HubFile.FileType, Long> breakdown = repo.getStorageBreakdown();
            long tracked = repo.getTotalTrackedBytes();

            int[] colors = {0xFF3B82F6, 0xFF8B5CF6, 0xFF10B981, 0xFFF59E0B, 0xFF6B7280};
            String[] labels = {"Images", "Videos", "Docs", "Audio", "Other"};
            HubFile.FileType[] types = {HubFile.FileType.IMAGE, HubFile.FileType.VIDEO,
                    HubFile.FileType.DOCUMENT, HubFile.FileType.AUDIO, HubFile.FileType.OTHER};
            long[] sizes = new long[types.length];
            for (int i = 0; i < types.length; i++) {
                sizes[i] = breakdown.containsKey(types[i]) ? breakdown.get(types[i]) : 0L;
            }

            if (tracked > 0) {
                for (int i = 0; i < types.length; i++) {
                    if (sizes[i] <= 0) continue;
                    float weight = sizes[i] * 100f / tracked;
                    View seg = new View(this);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight);
                    seg.setLayoutParams(lp);
                    seg.setBackgroundColor(colors[i]);
                    storageSegmentBar.addView(seg);
                }
                for (int i = 0; i < labels.length; i++) {
                    if (sizes[i] <= 0) continue;
                    LinearLayout row = new LinearLayout(this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                    LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    rowLp.setMargins(0, 0, 0, 2);
                    row.setLayoutParams(rowLp);

                    View dot = new View(this);
                    LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(8, 8);
                    dotLp.setMargins(0, 0, 6, 0);
                    dot.setLayoutParams(dotLp);
                    dot.setBackgroundColor(colors[i]);
                    row.addView(dot);

                    TextView lbl = new TextView(this);
                    lbl.setText(labels[i] + "  " + formatBytes(sizes[i]));
                    lbl.setTextColor(Color.parseColor("#94A3B8"));
                    lbl.setTextSize(10);
                    lbl.setLayoutParams(new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    row.addView(lbl);
                    storageLegend.addView(row);
                }
            }

            // Stats pills
            tvTotalFilesCount.setText(String.valueOf(repo.getTotalFileCount()));
            tvTotalFoldersCount.setText(String.valueOf(repo.getTotalFolderCount()));
            int dupeCount = repo.getTotalDuplicateCount();
            tvDuplicatesCount.setText(String.valueOf(dupeCount));
            tvDuplicatesCount.setTextColor(Color.parseColor(dupeCount > 0 ? "#EF4444" : "#10B981"));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void refreshTypeCounts() {
        tvCountPdf.setText(String.valueOf(repo.getFilesByType(HubFile.FileType.PDF).size()));
        tvCountDoc.setText(String.valueOf(repo.getFilesByType(HubFile.FileType.DOCUMENT).size()));
        tvCountImage.setText(String.valueOf(repo.getFilesByType(HubFile.FileType.IMAGE).size()));
        tvCountScreenshot.setText(String.valueOf(repo.getFilesByType(HubFile.FileType.SCREENSHOT).size()));
        tvCountVideo.setText(String.valueOf(repo.getFilesByType(HubFile.FileType.VIDEO).size()));
        tvCountAudio.setText(String.valueOf(repo.getFilesByType(HubFile.FileType.AUDIO).size()));
        tvCountCode.setText(String.valueOf(repo.getFilesByType(HubFile.FileType.CODE).size()));
        tvCountArchive.setText(String.valueOf(repo.getFilesByType(HubFile.FileType.ARCHIVE).size()));

        drawerCountPdf.setText(String.valueOf(repo.getFilesByType(HubFile.FileType.PDF).size()));
        drawerCountDoc.setText(String.valueOf(repo.getFilesByType(HubFile.FileType.DOCUMENT).size()));
        drawerCountImage.setText(String.valueOf(repo.getFilesByType(HubFile.FileType.IMAGE).size()));
        drawerCountVideo.setText(String.valueOf(repo.getFilesByType(HubFile.FileType.VIDEO).size()));
        drawerCountAudio.setText(String.valueOf(repo.getFilesByType(HubFile.FileType.AUDIO).size()));
        drawerCountCode.setText(String.valueOf(repo.getFilesByType(HubFile.FileType.CODE).size()));
    }

    private void refreshInboxSection() {
        int pendingCount = repo.getPendingInboxCount();
        List<InboxItem> pendingItems = repo.getPendingInboxItems();
        int dupeCount = repo.getTotalDuplicateCount();

        if (pendingCount > 0) {
            tvInboxBadge.setVisibility(View.VISIBLE);
            tvInboxBadge.setText(String.valueOf(pendingCount));
            drawerInboxBadge.setVisibility(View.VISIBLE);
            drawerInboxBadge.setText(String.valueOf(pendingCount));
            tvInboxTitle.setText(pendingCount + " file" + (pendingCount == 1 ? "" : "s") + " waiting to be organized");
            tvInboxSubtitle.setText("Tap to review and organize");
            inboxActionRow.setVisibility(View.VISIBLE);
            inboxPreviewStrip.setVisibility(View.VISIBLE);
            buildInboxPreview(pendingItems);
        } else if (dupeCount > 0) {
            tvInboxBadge.setVisibility(View.GONE);
            drawerInboxBadge.setVisibility(View.GONE);
            tvInboxTitle.setText(dupeCount + " duplicate" + (dupeCount == 1 ? "" : "s") + " found — free up space");
            tvInboxSubtitle.setText("Tap to review duplicates");
            inboxActionRow.setVisibility(View.GONE);
            inboxPreviewStrip.setVisibility(View.GONE);
        } else {
            tvInboxBadge.setVisibility(View.GONE);
            drawerInboxBadge.setVisibility(View.GONE);
            tvInboxTitle.setText("✅ Inbox clear");
            tvInboxSubtitle.setText("Everything is organized");
            inboxActionRow.setVisibility(View.GONE);
            inboxPreviewStrip.setVisibility(View.GONE);
        }
    }

    private void buildInboxPreview(List<InboxItem> items) {
        inboxPreviewStrip.removeAllViews();
        int limit = Math.min(4, items.size());
        for (int i = 0; i < limit; i++) {
            InboxItem item = items.get(i);
            TextView preview = new TextView(this);
            preview.setText(item.getTypeEmoji());
            preview.setTextSize(24);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setCornerRadius(8 * getResources().getDisplayMetrics().density);
            bg.setColor(Color.parseColor("#1E293B"));
            preview.setBackground(bg);
            preview.setGravity(android.view.Gravity.CENTER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    (int) (52 * getResources().getDisplayMetrics().density),
                    (int) (52 * getResources().getDisplayMetrics().density));
            lp.setMargins(0, 0, (int) (8 * getResources().getDisplayMetrics().density), 0);
            preview.setLayoutParams(lp);
            inboxPreviewStrip.addView(preview);
        }
    }

    private void updateSubtitle() {
        int pendingCount = repo.getPendingInboxCount();
        int dupeCount = repo.getTotalDuplicateCount();
        if (pendingCount > 0) {
            tvHubSubtitle.setText(pendingCount + " new file" + (pendingCount == 1 ? "" : "s") + " in your inbox");
        } else if (dupeCount > 0) {
            tvHubSubtitle.setText(dupeCount + " duplicate" + (dupeCount == 1 ? "" : "s") + " found — free up space");
        } else {
            tvHubSubtitle.setText("Everything is organized ✨");
        }
    }

    private void refreshSmartFolders() {
        smartFoldersContainer.removeAllViews();
        List<HubFolder> smartFolders = repo.getSmartFolders();
        for (HubFolder folder : smartFolders) {
            List<HubFile> folderFiles = repo.getFilesForSmartFolder(folder);
            LinearLayout card = buildSmartFolderCard(folder, folderFiles.size());
            smartFoldersContainer.addView(card);
        }
    }

    private LinearLayout buildSmartFolderCard(HubFolder folder, int fileCount) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(android.view.Gravity.CENTER_VERTICAL);
        card.setBackground(getDrawable(R.drawable.hub_section_card_bg));
        card.setClickable(true);
        card.setFocusable(true);
        card.setPadding(
                (int) (16 * getResources().getDisplayMetrics().density),
                (int) (12 * getResources().getDisplayMetrics().density),
                (int) (16 * getResources().getDisplayMetrics().density),
                (int) (12 * getResources().getDisplayMetrics().density));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, (int) (8 * getResources().getDisplayMetrics().density));
        card.setLayoutParams(lp);

        // Apply ripple effect using typed value to resolve attribute
        android.util.TypedValue rippleValue = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, rippleValue, true);
        card.setForeground(getDrawable(rippleValue.resourceId));

        // Color indicator
        View colorDot = new View(this);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(
                (int) (4 * getResources().getDisplayMetrics().density),
                (int) (40 * getResources().getDisplayMetrics().density));
        dotLp.setMargins(0, 0, (int) (12 * getResources().getDisplayMetrics().density), 0);
        colorDot.setLayoutParams(dotLp);
        try {
            colorDot.setBackgroundColor(Color.parseColor(folder.colorHex != null ? folder.colorHex : "#3B82F6"));
        } catch (Exception e) {
            colorDot.setBackgroundColor(Color.parseColor("#3B82F6"));
        }
        card.addView(colorDot);

        // Icon
        TextView icon = new TextView(this);
        icon.setText(folder.iconIdentifier != null ? folder.iconIdentifier : "📁");
        icon.setTextSize(22);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        iconLp.setMargins(0, 0, (int) (12 * getResources().getDisplayMetrics().density), 0);
        icon.setLayoutParams(iconLp);
        card.addView(icon);

        // Text block
        LinearLayout textBlock = new LinearLayout(this);
        textBlock.setOrientation(LinearLayout.VERTICAL);
        textBlock.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(textBlock);

        TextView name = new TextView(this);
        name.setText(folder.name != null ? folder.name : "Folder");
        name.setTextColor(Color.parseColor("#F1F5F9"));
        name.setTextSize(14);
        name.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        textBlock.addView(name);

        // Rule description
        String ruleDesc = getSmartFolderRuleDesc(folder.smartFolderRules);
        if (!ruleDesc.isEmpty()) {
            TextView rule = new TextView(this);
            rule.setText(ruleDesc);
            rule.setTextColor(Color.parseColor("#64748B"));
            rule.setTextSize(11);
            textBlock.addView(rule);
        }

        // Count badge
        TextView count = new TextView(this);
        count.setText(fileCount + " files");
        count.setTextColor(Color.parseColor("#94A3B8"));
        count.setTextSize(12);
        card.addView(count);

        HubFolder finalFolder = folder;
        card.setOnClickListener(v -> {
            Intent intent = new Intent(this, HubFileBrowserActivity.class);
            intent.putExtra(HubFileBrowserActivity.EXTRA_FOLDER_ID, finalFolder.id);
            intent.putExtra(HubFileBrowserActivity.EXTRA_TITLE, finalFolder.name);
            startActivity(intent);
        });

        return card;
    }

    private String getSmartFolderRuleDesc(String rules) {
        if (rules == null || rules.isEmpty()) return "";
        try {
            org.json.JSONObject json = new org.json.JSONObject(rules);
            if (json.has("source")) return "Source: " + json.getString("source");
            if (json.has("fileType")) return "Type: " + json.getString("fileType");
            if (json.has("minSize")) return "Size > " + formatBytes(json.getLong("minSize"));
            if (json.has("unorganized")) return "Not in any folder or project";
            if (json.has("maxAgeDays")) return "Last " + json.getInt("maxAgeDays") + " days";
        } catch (Exception e) { /* ignore */ }
        return "";
    }

    private void refreshProjects() {
        projectsStrip.removeAllViews();
        List<HubProject> projects = repo.getAllProjects();
        float dp = getResources().getDisplayMetrics().density;

        for (HubProject project : projects) {
            LinearLayout card = buildProjectCard(project, dp);
            projectsStrip.addView(card);
        }

        // "Create new project" card
        LinearLayout addCard = new LinearLayout(this);
        addCard.setOrientation(LinearLayout.VERTICAL);
        addCard.setGravity(android.view.Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(16 * dp);
        bg.setColor(Color.parseColor("#0F172A"));
        bg.setStroke((int) dp, Color.parseColor("#334155"));
        addCard.setBackground(bg);
        addCard.setPadding((int)(16*dp), (int)(16*dp), (int)(16*dp), (int)(16*dp));
        addCard.setClickable(true);
        addCard.setFocusable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int)(130*dp), (int)(110*dp));
        lp.setMargins(0, 0, (int)(10*dp), 0);
        addCard.setLayoutParams(lp);
        { android.util.TypedValue tv = new android.util.TypedValue(); getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true); addCard.setForeground(getDrawable(tv.resourceId)); }

        TextView plus = new TextView(this);
        plus.setText("+");
        plus.setTextColor(Color.parseColor("#64748B"));
        plus.setTextSize(28);
        plus.setGravity(android.view.Gravity.CENTER);
        plus.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addCard.addView(plus);

        TextView label = new TextView(this);
        label.setText("New Project");
        label.setTextColor(Color.parseColor("#64748B"));
        label.setTextSize(11);
        label.setGravity(android.view.Gravity.CENTER);
        label.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addCard.addView(label);

        addCard.setOnClickListener(v -> showCreateProjectDialog());
        projectsStrip.addView(addCard);

        if (projects.isEmpty()) {
            // Show dashed empty state already handled by the add card above
        }
    }

    private LinearLayout buildProjectCard(HubProject project, float dp) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(android.view.Gravity.BOTTOM);
        card.setBackground(getDrawable(R.drawable.hub_project_card_bg));
        card.setPadding((int)(14*dp), (int)(14*dp), (int)(14*dp), (int)(14*dp));
        card.setClickable(true);
        card.setFocusable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int)(140*dp), (int)(110*dp));
        lp.setMargins(0, 0, (int)(10*dp), 0);
        card.setLayoutParams(lp);
        { android.util.TypedValue tv = new android.util.TypedValue(); getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true); card.setForeground(getDrawable(tv.resourceId)); }

        TextView icon = new TextView(this);
        icon.setText(project.iconIdentifier != null ? project.iconIdentifier : "💼");
        icon.setTextSize(24);
        card.addView(icon);

        TextView name = new TextView(this);
        name.setText(project.name != null ? project.name : "Project");
        name.setTextColor(Color.WHITE);
        name.setTextSize(12);
        name.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        name.setMaxLines(1);
        name.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(name);

        TextView size = new TextView(this);
        size.setText(project.fileCount + " files · " + project.getFormattedSize());
        size.setTextColor(Color.parseColor("#94A3B8"));
        size.setTextSize(10);
        card.addView(size);

        card.setOnClickListener(v -> {
            Intent intent = new Intent(this, HubProjectDetailActivity.class);
            intent.putExtra(HubProjectDetailActivity.EXTRA_PROJECT_ID, project.id);
            startActivity(intent);
        });

        return card;
    }

    private void refreshActivityFeed() {
        activityFeedContainer.removeAllViews();
        List<FileActivity> activities = repo.getRecentActivities(8);
        if (activities.isEmpty()) {
            activityEmptyState.setVisibility(View.VISIBLE);
            return;
        }
        activityEmptyState.setVisibility(View.GONE);
        float dp = getResources().getDisplayMetrics().density;

        for (FileActivity activity : activities) {
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(android.view.Gravity.CENTER_VERTICAL);
            item.setBackground(getDrawable(R.drawable.hub_activity_item_bg));
            item.setPadding((int)(12*dp), (int)(10*dp), (int)(12*dp), (int)(10*dp));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, (int)(4*dp));
            item.setLayoutParams(lp);

            TextView emoji = new TextView(this);
            emoji.setText(activity.fileTypeEmoji != null ? activity.fileTypeEmoji : "📄");
            emoji.setTextSize(20);
            LinearLayout.LayoutParams emojiLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            emojiLp.setMargins(0, 0, (int)(10*dp), 0);
            emoji.setLayoutParams(emojiLp);
            item.addView(emoji);

            LinearLayout textCol = new LinearLayout(this);
            textCol.setOrientation(LinearLayout.VERTICAL);
            textCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView actionText = new TextView(this);
            String desc = activity.getActionDescription();
            String fname = activity.fileName != null ? activity.fileName : "file";
            actionText.setText(desc + " · " + fname);
            actionText.setTextColor(Color.parseColor("#E2E8F0"));
            actionText.setTextSize(12);
            actionText.setMaxLines(1);
            actionText.setEllipsize(TextUtils.TruncateAt.END);
            textCol.addView(actionText);

            TextView timeText = new TextView(this);
            timeText.setText(activity.getRelativeTime());
            timeText.setTextColor(Color.parseColor("#64748B"));
            timeText.setTextSize(10);
            textCol.addView(timeText);

            item.addView(textCol);
            activityFeedContainer.addView(item);
        }
    }

    private void refreshRecentFiles() {
        recentFilesStrip.removeAllViews();
        List<HubFile> recentFiles = repo.getRecentFiles(10);
        float dp = getResources().getDisplayMetrics().density;

        if (recentFiles.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No files yet. Import some files to get started.");
            empty.setTextColor(Color.parseColor("#64748B"));
            empty.setTextSize(12);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins((int)(4*dp), 0, 0, 0);
            empty.setLayoutParams(lp);
            recentFilesStrip.addView(empty);
            return;
        }

        for (HubFile file : recentFiles) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(android.view.Gravity.CENTER);
            card.setBackground(getDrawable(R.drawable.hub_recent_file_card_bg));
            card.setPadding((int)(10*dp), (int)(10*dp), (int)(10*dp), (int)(10*dp));
            card.setClickable(true);
            card.setFocusable(true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int)(90*dp), (int)(90*dp));
            lp.setMargins(0, 0, (int)(8*dp), 0);
            card.setLayoutParams(lp);
            { android.util.TypedValue tv = new android.util.TypedValue(); getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true); card.setForeground(getDrawable(tv.resourceId)); }

            TextView typeEmoji = new TextView(this);
            typeEmoji.setText(file.getTypeEmoji());
            typeEmoji.setTextSize(26);
            typeEmoji.setGravity(android.view.Gravity.CENTER);
            typeEmoji.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            card.addView(typeEmoji);

            String displayName = file.displayName != null ? file.displayName : file.originalFileName;
            if (displayName != null && displayName.length() > 12) displayName = displayName.substring(0, 10) + "…";
            TextView fileName = new TextView(this);
            fileName.setText(displayName != null ? displayName : "File");
            fileName.setTextColor(Color.parseColor("#CBD5E1"));
            fileName.setTextSize(9);
            fileName.setGravity(android.view.Gravity.CENTER);
            fileName.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            card.addView(fileName);

            HubFile finalFile = file;
            card.setOnClickListener(v -> {
                Intent intent = new Intent(this, HubFileViewerActivity.class);
                intent.putExtra(HubFileViewerActivity.EXTRA_FILE_ID, finalFile.id);
                startActivity(intent);
            });

            recentFilesStrip.addView(card);
        }
    }

    // ─── FAB Speed Dial ───────────────────────────────────────────────────────

    private void toggleFabSpeedDial() {
        if (fabExpanded) {
            collapseFab();
        } else {
            expandFab();
        }
    }

    private void expandFab() {
        fabExpanded = true;
        fabSpeedDial.setVisibility(View.VISIBLE);
        fabMain.setText("✕");
        fabMain.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                Color.parseColor("#EF4444")));
    }

    private void collapseFab() {
        fabExpanded = false;
        fabSpeedDial.setVisibility(View.GONE);
        fabMain.setText("+");
        fabMain.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                Color.parseColor("#7C3AED")));
    }

    // ─── Actions ──────────────────────────────────────────────────────────────

    private void openInbox() {
        Intent intent = new Intent(this, HubInboxActivity.class);
        startActivity(intent);
    }

    private void browseStorage(boolean preferExternal) {
        String path = null;
        String title;
        if (preferExternal) {
            // Try to find an SD card (second external storage directory)
            java.io.File[] externalDirs = getExternalFilesDirs(null);
            if (externalDirs != null && externalDirs.length > 1 && externalDirs[1] != null) {
                // Walk up 4 levels from app-specific dir to reach the SD card root:
                // .../files → .../com.package → .../data → .../Android → SD card root
                java.io.File sdDir = externalDirs[1];
                for (int i = 0; i < 4 && sdDir.getParentFile() != null; i++) {
                    sdDir = sdDir.getParentFile();
                }
                if (sdDir != null && sdDir.exists()) {
                    path = sdDir.getAbsolutePath();
                }
            }
            if (path == null) {
                Toast.makeText(this, "No external SD card detected", Toast.LENGTH_SHORT).show();
                return;
            }
            title = "SD Card / External Storage";
        } else {
            path = android.os.Environment.getExternalStorageDirectory().getAbsolutePath();
            title = "Internal Storage";
        }
        Intent intent = new Intent(this, StorageFileBrowserActivity.class);
        intent.putExtra(StorageFileBrowserActivity.EXTRA_ROOT_PATH, path);
        intent.putExtra(StorageFileBrowserActivity.EXTRA_TITLE, title);
        startActivity(intent);
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(intent, REQUEST_IMPORT_FILE);
    }

    private void triggerManualScan() {
        Toast.makeText(this, "Scanning for new files…", Toast.LENGTH_SHORT).show();
        repo.scanForNewFiles(() -> {
            refreshData();
            runOnUiThread(() -> {
                int count = repo.getPendingInboxCount();
                if (count > 0) {
                    Toast.makeText(this, "Found " + count + " new file" + (count == 1 ? "" : "s") + " in inbox", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, "No new files found", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void autoOrganizeHighConfidence() {
        List<InboxItem> pending = repo.getPendingInboxItems();
        int accepted = 0;
        for (InboxItem item : pending) {
            if (item.autoCategorizationConfidence >= 80) {
                repo.updateInboxItemStatus(item.id, InboxItem.Status.ACCEPTED);
                // Import the file
                importInboxItem(item);
                accepted++;
            }
        }
        if (accepted > 0) {
            refreshData();
            Toast.makeText(this, "Auto-organized " + accepted + " high-confidence file" + (accepted == 1 ? "" : "s"), Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "No high-confidence suggestions to auto-organize", Toast.LENGTH_SHORT).show();
        }
    }

    private void importInboxItem(InboxItem item) {
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

    private void showCreateFolderDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert);
        builder.setTitle("New Folder");
        EditText input = new EditText(this);
        input.setHint("Folder name");
        input.setPadding(40, 20, 40, 20);
        builder.setView(input);
        builder.setPositiveButton("Create", (dialog, which) -> {
            String name = input.getText().toString().trim();
            if (!name.isEmpty()) {
                HubFolder folder = new HubFolder(name, "#3B82F6", "📁");
                repo.addFolder(folder);
                refreshData();
                Toast.makeText(this, "Folder \"" + name + "\" created", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showCreateProjectDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert);
        builder.setTitle("New Project");
        EditText input = new EditText(this);
        input.setHint("Project name");
        input.setPadding(40, 20, 40, 20);
        builder.setView(input);
        builder.setPositiveButton("Create", (dialog, which) -> {
            String name = input.getText().toString().trim();
            if (!name.isEmpty()) {
                HubProject project = new HubProject(name, "", "#8B5CF6", "💼");
                repo.addProject(project);
                refreshData();
                Toast.makeText(this, "Project \"" + name + "\" created", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_MANAGE_STORAGE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
                triggerScan();
            } else {
                Toast.makeText(this, "All files access needed to scan files", Toast.LENGTH_LONG).show();
            }
            return;
        }
        if (requestCode == REQUEST_IMPORT_FILE && resultCode == RESULT_OK && data != null) {
            // Handle single or multiple files
            if (data.getClipData() != null) {
                int count = data.getClipData().getItemCount();
                for (int i = 0; i < count; i++) {
                    android.net.Uri uri = data.getClipData().getItemAt(i).getUri();
                    importFromUri(uri);
                }
            } else if (data.getData() != null) {
                importFromUri(data.getData());
            }
            refreshData();
        }
    }

    private void importFromUri(android.net.Uri uri) {
        try {
            String fileName = "";
            long fileSize = 0;
            try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    int sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE);
                    if (nameIndex >= 0) fileName = cursor.getString(nameIndex);
                    if (sizeIndex >= 0) fileSize = cursor.getLong(sizeIndex);
                }
            }
            String mime = getContentResolver().getType(uri);
            String ext = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.') + 1) : "";

            HubFile file = new HubFile();
            file.originalFileName = fileName;
            file.displayName = fileName;
            file.filePath = uri.toString();
            file.fileSize = fileSize;
            file.mimeType = mime;
            file.fileType = HubFile.fileTypeFromMime(mime, ext);
            file.source = HubFile.Source.MANUAL;
            file.fileExtension = ext;
            repo.addFile(file);
            Toast.makeText(this, "Imported: " + fileName, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Import failed", Toast.LENGTH_SHORT).show();
        }
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
