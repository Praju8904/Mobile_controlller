package com.prajwal.myfirstapp.vault;


import com.prajwal.myfirstapp.R;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Vault Home Screen — shown after successful vault unlock.
 *
 * Sections:
 * 1. Top bar with session timer
 * 2. Storage summary card with segmented bar
 * 3. Quick access row (Photos, Videos, Audio, Docs)
 * 4. Albums horizontal strip
 * 5. Favourites strip
 * 6. Recently added grid
 */
public class VaultHomeActivity extends AppCompatActivity {

    private static final int IMPORT_FILE_REQUEST = 9001;
    private static final int RECENT_FILES_LIMIT = 12;

    private MediaVaultRepository repo;
    private Handler autoLockHandler;
    private Runnable autoLockRunnable;
    private long lastInteractionTime;
    private CountDownTimer sessionTimer;

    // Views
    private TextView tvSessionTimer;
    private TextView tvTotalFiles;
    private LinearLayout storageBar;
    private LinearLayout storageLegend;
    private TextView tvStorageUsed;
    private TextView tvStorageAvailable;
    private TextView tvPhotosCount;
    private TextView tvVideosCount;
    private TextView tvAudioCount;
    private TextView tvDocsCount;
    private LinearLayout albumsStrip;
    private LinearLayout favouritesSection;
    private LinearLayout favouritesStrip;
    private LinearLayout collectionsSection;
    private LinearLayout collectionsStrip;
    private GridLayout recentFilesGrid;
    private LinearLayout vaultEmptyState;
    private TextView tvHealthScore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Secure screen flag
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE);

        setContentView(R.layout.activity_vault_home);

        repo = MediaVaultRepository.getInstance(this);

        if (!repo.isUnlocked()) {
            // Vault was locked — go back to unlock screen
            navigateToUnlock();
            return;
        }

        bindViews();
        setupAutoLock();
        loadData();
    }

    private void bindViews() {
        tvSessionTimer = findViewById(R.id.tvSessionTimer);
        tvTotalFiles = findViewById(R.id.tvTotalFiles);
        storageBar = findViewById(R.id.storageBar);
        storageLegend = findViewById(R.id.storageLegend);
        tvStorageUsed = findViewById(R.id.tvStorageUsed);
        tvStorageAvailable = findViewById(R.id.tvStorageAvailable);
        tvPhotosCount = findViewById(R.id.tvPhotosCount);
        tvVideosCount = findViewById(R.id.tvVideosCount);
        tvAudioCount = findViewById(R.id.tvAudioCount);
        tvDocsCount = findViewById(R.id.tvDocsCount);
        albumsStrip = findViewById(R.id.albumsStrip);
        favouritesSection = findViewById(R.id.favouritesSection);
        favouritesStrip = findViewById(R.id.favouritesStrip);
        collectionsSection = findViewById(R.id.collectionsSection);
        collectionsStrip = findViewById(R.id.collectionsStrip);
        recentFilesGrid = findViewById(R.id.recentFilesGrid);
        vaultEmptyState = findViewById(R.id.vaultEmptyState);
        tvHealthScore = findViewById(R.id.tvHealthScore);

        // Top bar buttons
        findViewById(R.id.btnVaultBack).setOnClickListener(v -> {
            repo.lock();
            finish();
        });
        findViewById(R.id.btnVaultSearch).setOnClickListener(v -> {
            resetAutoLock();
            startActivity(new Intent(this, VaultSearchActivity.class));
        });
        findViewById(R.id.btnVaultSettings).setOnClickListener(v -> {
            resetAutoLock();
            startActivity(new Intent(this, VaultSettingsActivity.class));
        });
        tvSessionTimer.setOnClickListener(v -> showSessionOptions());

        // Health score widget
        if (tvHealthScore != null) {
            tvHealthScore.setOnClickListener(v -> { resetAutoLock(); showHealthScoreDialog(); });
        }

        // Auto Organize button
        View btnAutoOrganize = findViewById(R.id.btnAutoOrganize);
        if (btnAutoOrganize != null) {
            btnAutoOrganize.setOnClickListener(v -> {
                resetAutoLock();
                startActivity(new Intent(this, VaultSmartOrganizeActivity.class));
            });
        }

        // Collections "View All"
        View tvViewAllCollections = findViewById(R.id.tvViewAllCollections);
        if (tvViewAllCollections != null) {
            tvViewAllCollections.setOnClickListener(v -> {
                resetAutoLock();
                startActivity(new Intent(this, VaultCollectionActivity.class));
            });
        }

        // Quick access
        findViewById(R.id.btnQuickPhotos).setOnClickListener(v -> openBrowser(VaultFileItem.FileType.IMAGE));
        findViewById(R.id.btnQuickVideos).setOnClickListener(v -> openBrowser(VaultFileItem.FileType.VIDEO));
        findViewById(R.id.btnQuickAudio).setOnClickListener(v -> openBrowser(VaultFileItem.FileType.AUDIO));
        findViewById(R.id.btnQuickDocs).setOnClickListener(v -> openBrowser(VaultFileItem.FileType.DOCUMENT));

        // View all
        findViewById(R.id.tvViewAllAlbums).setOnClickListener(v -> {
            resetAutoLock();
            showAllAlbums();
        });
        findViewById(R.id.tvViewAllFiles).setOnClickListener(v -> openBrowser(null));

        // FAB: import
        findViewById(R.id.fabImportFile).setOnClickListener(v -> {
            resetAutoLock();
            showImportOptions();
        });
    }

    // ─── Auto-Lock / Session ──────────────────────────────────────

    private void setupAutoLock() {
        autoLockHandler = new Handler(Looper.getMainLooper());
        autoLockRunnable = this::lockAndReturn;
        lastInteractionTime = System.currentTimeMillis();
        startSessionTimer();
    }

    private void startSessionTimer() {
        if (sessionTimer != null) sessionTimer.cancel();
        long duration = repo.getSessionDurationMs();

        sessionTimer = new CountDownTimer(duration, 1000) {
            @Override
            public void onTick(long ms) {
                long secs = ms / 1000;
                long mins = secs / 60;
                secs = secs % 60;
                if (tvSessionTimer != null) {
                    tvSessionTimer.setText("Locks in " + mins + ":" + String.format("%02d", secs));
                    tvSessionTimer.setTextColor(ms < 30000 ? Color.parseColor("#EF4444") : Color.parseColor("#10B981"));
                }
            }
            @Override
            public void onFinish() {
                lockAndReturn();
            }
        }.start();
    }

    private void resetAutoLock() {
        lastInteractionTime = System.currentTimeMillis();
        startSessionTimer();
    }

    private void lockAndReturn() {
        repo.lock();
        Toast.makeText(this, "Vault locked due to inactivity", Toast.LENGTH_SHORT).show();
        navigateToUnlock();
    }

    private void navigateToUnlock() {
        Intent intent = new Intent(this, VaultUnlockActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!repo.isUnlocked()) {
            navigateToUnlock();
            return;
        }
        resetAutoLock();
        loadData();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Don't lock vault when navigating to other vault activities
        // The auto-lock timer will handle locking after inactivity
        if (sessionTimer != null) sessionTimer.cancel();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sessionTimer != null) sessionTimer.cancel();
        if (autoLockHandler != null) autoLockHandler.removeCallbacks(autoLockRunnable);
    }

    // ─── Data Loading ─────────────────────────────────────────────

    private void loadData() {
        if (!repo.isUnlocked()) return;

        List<VaultFileItem> allFiles = repo.getAllFiles();
        int totalFiles = allFiles.size();

        // Update counts
        int photosCount = repo.getCountByType(VaultFileItem.FileType.IMAGE);
        int videosCount = repo.getCountByType(VaultFileItem.FileType.VIDEO);
        int audioCount = repo.getCountByType(VaultFileItem.FileType.AUDIO);
        int docsCount = repo.getCountByType(VaultFileItem.FileType.DOCUMENT);

        tvTotalFiles.setText(totalFiles + " file" + (totalFiles == 1 ? "" : "s"));
        tvPhotosCount.setText(String.valueOf(photosCount));
        tvVideosCount.setText(String.valueOf(videosCount));
        tvAudioCount.setText(String.valueOf(audioCount));
        tvDocsCount.setText(String.valueOf(docsCount));

        // Storage summary
        loadStorageSummary(photosCount, videosCount, audioCount, docsCount, totalFiles);

        // Albums strip
        loadAlbumsStrip();

        // Collections strip
        loadCollectionsStrip();

        // Favourites
        loadFavouritesStrip();

        // Recent files grid
        loadRecentGrid();

        // Health score
        int healthScore = VaultHealthScoreHelper.calculateScore(this, repo);
        if (tvHealthScore != null) {
            tvHealthScore.setText("🛡 " + healthScore);
            try { tvHealthScore.setTextColor(Color.parseColor(VaultHealthScoreHelper.getScoreColor(healthScore))); }
            catch (Exception ignored) {}
        }

        // Expiry reminder
        List<VaultFileItem> expiringFiles = repo.getUpcomingExpiryFiles(7);
        if (!expiringFiles.isEmpty()) {
            Toast.makeText(this, "⚠️ " + expiringFiles.size() + " file(s) expiring within 7 days", Toast.LENGTH_LONG).show();
        }

        // Empty state
        vaultEmptyState.setVisibility(totalFiles == 0 ? View.VISIBLE : View.GONE);
    }

    private void loadStorageSummary(int photos, int videos, int audio, int docs, int total) {
        long usedBytes = repo.getTotalStorageUsed();
        long availableBytes = new File(getFilesDir().getAbsolutePath()).getFreeSpace();

        tvStorageUsed.setText(formatBytes(usedBytes) + " used");
        tvStorageAvailable.setText(formatBytes(availableBytes) + " available");

        // Segmented bar
        storageBar.removeAllViews();
        storageLegend.removeAllViews();
        if (total == 0) {
            View emptyBar = new View(this);
            emptyBar.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
            emptyBar.setBackgroundColor(Color.parseColor("#1E293B"));
            storageBar.addView(emptyBar);
            return;
        }

        addBarSegment(photos, total, "#3B82F6", "Photos");
        addBarSegment(videos, total, "#8B5CF6", "Videos");
        addBarSegment(audio, total, "#10B981", "Audio");
        addBarSegment(docs, total, "#F59E0B", "Docs");
        int others = total - photos - videos - audio - docs;
        if (others > 0) addBarSegment(others, total, "#64748B", "Other");
    }

    private void addBarSegment(int count, int total, String color, String label) {
        if (count <= 0) return;
        float weight = (float) count / total;

        // Bar segment
        View seg = new View(this);
        seg.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight));
        try { seg.setBackgroundColor(Color.parseColor(color)); } catch (Exception ignored) {}
        storageBar.addView(seg);

        // Legend item
        LinearLayout legendItem = new LinearLayout(this);
        legendItem.setOrientation(LinearLayout.HORIZONTAL);
        legendItem.setPadding(0, 0, dpToPx(12), 0);
        legendItem.setGravity(android.view.Gravity.CENTER_VERTICAL);

        View dot = new View(this);
        dot.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(8), dpToPx(8)));
        try { dot.setBackgroundColor(Color.parseColor(color)); } catch (Exception ignored) {}
        dot.setPadding(0, 0, dpToPx(4), 0);

        TextView tv = new TextView(this);
        tv.setText(label + " " + count);
        tv.setTextColor(Color.parseColor("#94A3B8"));
        tv.setTextSize(10);

        legendItem.addView(dot);
        legendItem.addView(tv);
        storageLegend.addView(legendItem);
    }

    private void loadAlbumsStrip() {
        albumsStrip.removeAllViews();
        List<VaultAlbum> albums = repo.getAlbums();

        for (VaultAlbum album : albums) {
            View albumView = createAlbumCard(album);
            albumsStrip.addView(albumView);
        }

        // Add "+" button
        View addAlbum = createAddAlbumCard();
        albumsStrip.addView(addAlbum);
    }

    private View createAlbumCard(VaultAlbum album) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dpToPx(88), dpToPx(112));
        lp.setMargins(dpToPx(4), 0, dpToPx(4), 0);
        card.setLayoutParams(lp);
        card.setBackgroundColor(Color.parseColor("#1E293B"));

        // Color box / thumbnail
        FrameLayout coverBox = new FrameLayout(this);
        LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(dpToPx(64), dpToPx(64));
        boxLp.setMargins(0, dpToPx(8), 0, dpToPx(6));
        coverBox.setLayoutParams(boxLp);
        try { coverBox.setBackgroundColor(Color.parseColor(album.colorHex)); } catch (Exception ignored) {}

        TextView iconTv = new TextView(this);
        iconTv.setText("📁");
        iconTv.setTextSize(28);
        iconTv.setGravity(android.view.Gravity.CENTER);
        iconTv.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        coverBox.addView(iconTv);

        // Load cover thumbnail if available
        if (album.coverFileId != null) {
            for (VaultFileItem f : repo.getAllFiles()) {
                if (f.id.equals(album.coverFileId) && f.thumbnailPath != null) {
                    ImageView iv = new ImageView(this);
                    iv.setLayoutParams(new FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
                    iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    coverBox.addView(iv);
                    final VaultFileItem finalF = f;
                    new Thread(() -> {
                        Bitmap b = repo.decryptThumbnail(finalF);
                        runOnUiThread(() -> { if (b != null) iv.setImageBitmap(b); });
                    }).start();
                    break;
                }
            }
        }

        card.addView(coverBox);

        TextView nameView = new TextView(this);
        nameView.setText(album.name);
        nameView.setTextColor(Color.parseColor("#F1F5F9"));
        nameView.setTextSize(11);
        nameView.setGravity(android.view.Gravity.CENTER);
        nameView.setMaxLines(1);
        nameView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        nameView.setPadding(dpToPx(4), 0, dpToPx(4), 0);
        card.addView(nameView);

        TextView countView = new TextView(this);
        countView.setText(album.fileCount + " files");
        countView.setTextColor(Color.parseColor("#64748B"));
        countView.setTextSize(10);
        countView.setGravity(android.view.Gravity.CENTER);
        countView.setPadding(0, 0, 0, dpToPx(4));
        card.addView(countView);

        card.setOnClickListener(v -> {
            resetAutoLock();
            Intent intent = new Intent(this, VaultAlbumDetailActivity.class);
            intent.putExtra("album_id", album.id);
            startActivity(intent);
        });

        return card;
    }

    private View createAddAlbumCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dpToPx(88), dpToPx(112));
        lp.setMargins(dpToPx(4), 0, dpToPx(4), 0);
        card.setLayoutParams(lp);
        card.setBackgroundColor(Color.parseColor("#1E293B"));

        TextView icon = new TextView(this);
        icon.setText("➕");
        icon.setTextSize(32);
        icon.setGravity(android.view.Gravity.CENTER);
        card.addView(icon);

        TextView label = new TextView(this);
        label.setText("New Album");
        label.setTextColor(Color.parseColor("#94A3B8"));
        label.setTextSize(11);
        label.setGravity(android.view.Gravity.CENTER);
        card.addView(label);

        card.setOnClickListener(v -> { resetAutoLock(); showCreateAlbumDialog(); });
        return card;
    }

    private void loadCollectionsStrip() {
        if (collectionsSection == null || collectionsStrip == null) return;
        List<VaultCollection> collections = repo.getCollections();
        if (collections.isEmpty()) {
            collectionsSection.setVisibility(View.GONE);
            return;
        }
        collectionsSection.setVisibility(View.VISIBLE);
        collectionsStrip.removeAllViews();

        for (VaultCollection collection : collections) {
            View chip = createCollectionChip(collection);
            collectionsStrip.addView(chip);
        }
    }

    private View createCollectionChip(VaultCollection collection) {
        TextView chip = new TextView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(36));
        lp.setMargins(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));
        chip.setLayoutParams(lp);
        chip.setText(collection.name);
        chip.setTextColor(Color.WHITE);
        chip.setTextSize(13);
        chip.setPadding(dpToPx(14), dpToPx(6), dpToPx(14), dpToPx(6));
        chip.setGravity(android.view.Gravity.CENTER_VERTICAL);
        try {
            chip.setBackgroundColor(Color.parseColor(collection.colorHex));
        } catch (Exception ignored) {
            chip.setBackgroundColor(Color.parseColor("#607D8B"));
        }
        chip.setOnClickListener(v -> {
            resetAutoLock();
            Intent intent = new Intent(this, VaultCollectionActivity.class);
            intent.putExtra("collection_id", collection.id);
            startActivity(intent);
        });
        return chip;
    }

    private void showHealthScoreDialog() {
        int score = VaultHealthScoreHelper.calculateScore(this, repo);
        String color = VaultHealthScoreHelper.getScoreColor(score);
        List<String> suggestions = VaultHealthScoreHelper.getImprovementSuggestions(this, repo);

        StringBuilder message = new StringBuilder();
        message.append("Your vault health score is ").append(score).append("/100\n\n");
        if (suggestions.isEmpty()) {
            message.append("✅ Great job! Your vault is well-secured.");
        } else {
            message.append("Improvement suggestions:\n");
            for (String suggestion : suggestions) {
                message.append("• ").append(suggestion).append("\n");
            }
        }

        AlertDialog dialog = new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("🛡 Vault Health Score")
                .setMessage(message.toString())
                .setPositiveButton("OK", null)
                .create();
        dialog.show();
        try {
            dialog.findViewById(android.R.id.message)
                    .setBackgroundColor(Color.TRANSPARENT);
        } catch (Exception ignored) {}
        TextView tvScore = new TextView(this);
        tvScore.setText(String.valueOf(score));
        tvScore.setTextSize(48);
        tvScore.setGravity(android.view.Gravity.CENTER);
        try { tvScore.setTextColor(Color.parseColor(color)); } catch (Exception ignored) {}
    }

    private void loadFavouritesStrip() {
        List<VaultFileItem> favs = repo.getFavourites();
        if (favs.isEmpty()) {
            favouritesSection.setVisibility(View.GONE);
            return;
        }
        favouritesSection.setVisibility(View.VISIBLE);
        favouritesStrip.removeAllViews();

        for (VaultFileItem f : favs) {
            View cell = createSmallThumbnailCell(f);
            favouritesStrip.addView(cell);
        }
    }

    private View createSmallThumbnailCell(VaultFileItem file) {
        FrameLayout cell = new FrameLayout(this);
        int size = dpToPx(72);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.setMargins(dpToPx(4), 0, dpToPx(4), 0);
        cell.setLayoutParams(lp);
        cell.setBackgroundColor(Color.parseColor("#1E293B"));

        ImageView iv = new ImageView(this);
        iv.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cell.addView(iv);

        TextView typeIcon = new TextView(this);
        typeIcon.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        typeIcon.setGravity(android.view.Gravity.CENTER);
        typeIcon.setTextSize(28);
        typeIcon.setText(getFileTypeEmoji(file.fileType));
        cell.addView(typeIcon);

        if (file.thumbnailPath != null && !file.thumbnailPath.isEmpty()) {
            final VaultFileItem fileToLoad = file;
            new Thread(() -> {
                Bitmap b = repo.decryptThumbnail(fileToLoad);
                runOnUiThread(() -> { if (b != null) { iv.setImageBitmap(b); typeIcon.setVisibility(View.GONE); } });
            }).start();
        }

        cell.setOnClickListener(v -> { resetAutoLock(); openFileBrowser(file); });
        return cell;
    }

    private void loadRecentGrid() {
        recentFilesGrid.removeAllViews();
        List<VaultFileItem> recent = repo.getRecentFiles(RECENT_FILES_LIMIT);

        if (recent.isEmpty()) return;

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int cellSize = screenWidth / 3 - dpToPx(4);

        for (VaultFileItem file : recent) {
            FrameLayout cell = new FrameLayout(this);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = cellSize;
            lp.height = cellSize;
            lp.setMargins(dpToPx(1), dpToPx(1), dpToPx(1), dpToPx(1));
            cell.setLayoutParams(lp);
            cell.setBackgroundColor(Color.parseColor("#1E293B"));

            ImageView iv = new ImageView(this);
            iv.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            cell.addView(iv);

            TextView typeIcon = new TextView(this);
            typeIcon.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            typeIcon.setGravity(android.view.Gravity.CENTER);
            typeIcon.setTextSize(32);
            typeIcon.setText(getFileTypeEmoji(file.fileType));
            cell.addView(typeIcon);

            if (file.fileType == VaultFileItem.FileType.VIDEO) {
                TextView playIcon = new TextView(this);
                playIcon.setLayoutParams(new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
                playIcon.setGravity(android.view.Gravity.CENTER);
                playIcon.setText("▶");
                playIcon.setTextSize(24);
                playIcon.setTextColor(Color.parseColor("#CCFFFFFF"));
                cell.addView(playIcon);
            }

            if (file.duration > 0) {
                TextView dur = new TextView(this);
                FrameLayout.LayoutParams durLp = new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
                durLp.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.END;
                dur.setLayoutParams(durLp);
                dur.setText(file.getFormattedDuration());
                dur.setTextColor(Color.WHITE);
                dur.setTextSize(9);
                dur.setBackgroundColor(Color.parseColor("#80000000"));
                dur.setPadding(dpToPx(3), dpToPx(1), dpToPx(3), dpToPx(1));
                cell.addView(dur);
            }

            if (file.isFavourited) {
                TextView star = new TextView(this);
                FrameLayout.LayoutParams starLp = new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
                starLp.gravity = android.view.Gravity.TOP | android.view.Gravity.START;
                star.setLayoutParams(starLp);
                star.setText("⭐");
                star.setTextSize(12);
                star.setPadding(dpToPx(2), dpToPx(2), 0, 0);
                cell.addView(star);
            }

            if (file.thumbnailPath != null && !file.thumbnailPath.isEmpty()) {
                final VaultFileItem fileToLoad = file;
                new Thread(() -> {
                    Bitmap b = repo.decryptThumbnail(fileToLoad);
                    runOnUiThread(() -> { if (b != null) { iv.setImageBitmap(b); typeIcon.setVisibility(View.GONE); } });
                }).start();
            }

            cell.setOnClickListener(v -> { resetAutoLock(); openFileBrowser(file); });
            recentFilesGrid.addView(cell);
        }
    }

    // ─── Import ───────────────────────────────────────────────────

    private void showImportOptions() {
        String[] options = {"📷 Import from Photos/Gallery", "🎬 Import Videos", "🎵 Import Audio", "📄 Import Documents", "📁 Import Any File"};
        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("Import to Vault")
                .setItems(options, (d, which) -> {
                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    switch (which) {
                        case 0: intent.setType("image/*"); break;
                        case 1: intent.setType("video/*"); break;
                        case 2: intent.setType("audio/*"); break;
                        case 3: intent.setType("application/*"); break;
                        default: intent.setType("*/*"); break;
                    }
                    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    startActivityForResult(intent, IMPORT_FILE_REQUEST);
                })
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == IMPORT_FILE_REQUEST && resultCode == RESULT_OK && data != null) {
            // Collect URIs and pass to VaultImportActivity for the full import flow
            ArrayList<String> uriStrings = new ArrayList<>();
            if (data.getClipData() != null) {
                for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                    uriStrings.add(data.getClipData().getItemAt(i).getUri().toString());
                }
            } else if (data.getData() != null) {
                uriStrings.add(data.getData().toString());
            }
            if (!uriStrings.isEmpty()) {
                Intent importIntent = new Intent(this, VaultImportActivity.class);
                importIntent.putStringArrayListExtra(VaultImportActivity.EXTRA_IMPORT_URIS, uriStrings);
                if (data.getData() != null) {
                    importIntent.putExtra(VaultImportActivity.EXTRA_IMPORT_MIME,
                            getContentResolver().getType(data.getData()));
                }
                startActivity(importIntent);
            }
        }
    }

    // ─── Navigation ───────────────────────────────────────────────

    private void openBrowser(VaultFileItem.FileType type) {
        resetAutoLock();
        Intent intent = new Intent(this, VaultFileBrowserActivity.class);
        if (type != null) intent.putExtra("file_type", type.name());
        startActivity(intent);
    }

    private void openFileBrowser(VaultFileItem file) {
        repo.logFileViewed(file);
        // Open the appropriate viewer for the file type
        Intent intent;
        switch (file.fileType) {
            case IMAGE:
                intent = new Intent(this, VaultImageViewerActivity.class);
                intent.putExtra("file_id", file.id);
                intent.putExtra("file_list_type", "recent");
                startActivity(intent);
                break;
            case VIDEO:
                intent = new Intent(this, VaultVideoPlayerActivity.class);
                intent.putExtra("file_id", file.id);
                intent.putExtra("file_list_type", "recent");
                startActivity(intent);
                break;
            case AUDIO:
                intent = new Intent(this, VaultAudioPlayerActivity.class);
                intent.putExtra("file_id", file.id);
                intent.putExtra("file_list_type", "recent");
                startActivity(intent);
                break;
            case DOCUMENT:
            default:
                intent = new Intent(this, VaultDocumentViewerActivity.class);
                intent.putExtra("file_id", file.id);
                startActivity(intent);
                break;
        }
    }

    private void showAllAlbums() {
        // Open a file browser in albums mode — simplified by launching FileBrowser with mode=albums
        Intent intent = new Intent(this, VaultFileBrowserActivity.class);
        intent.putExtra("mode", "albums");
        startActivity(intent);
    }

    // ─── Dialogs ─────────────────────────────────────────────────

    private void showCreateAlbumDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dpToPx(24), dpToPx(16), dpToPx(24), dpToPx(8));

        EditText etName = new EditText(this);
        etName.setHint("Album name");
        etName.setTextColor(Color.parseColor("#F1F5F9"));
        etName.setHintTextColor(Color.parseColor("#64748B"));
        etName.setBackgroundColor(Color.parseColor("#1E293B"));
        etName.setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12));
        layout.addView(etName);

        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("Create Album")
                .setView(layout)
                .setPositiveButton("Create", (d, w) -> {
                    String name = etName.getText().toString().trim();
                    if (!name.isEmpty()) {
                        repo.createAlbum(name, "#3B82F6");
                        loadData();
                        Toast.makeText(this, "Album created", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showSessionOptions() {
        String[] options = {"Extend session (+5 min)", "Lock Vault Now"};
        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("Session Options")
                .setItems(options, (d, which) -> {
                    if (which == 0) {
                        repo.setSessionDurationMs(repo.getSessionDurationMs() + 5 * 60 * 1000L);
                        startSessionTimer();
                        Toast.makeText(this, "Session extended by 5 minutes", Toast.LENGTH_SHORT).show();
                    } else {
                        lockAndReturn();
                    }
                })
                .show();
    }

    private void showVaultSettings() {
        String[] options = {
            "📋 Activity Log",
            "🔑 Change Vault PIN",
            "⏱️ Session Duration",
            "🎭 Decoy PIN (Advanced)",
            "🔒 Lock Vault Now"
        };
        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("Vault Settings")
                .setItems(options, (d, which) -> {
                    switch (which) {
                        case 0:
                            startActivity(new Intent(this, VaultActivityLogActivity.class));
                            break;
                        case 1:
                            showChangePinDialog();
                            break;
                        case 2:
                            showSessionDurationDialog();
                            break;
                        case 3:
                            showDecoyPinSetup();
                            break;
                        case 4:
                            lockAndReturn();
                            break;
                    }
                })
                .show();
    }

    private void showChangePinDialog() {
        // Navigate to unlock activity which handles PIN change flow
        Toast.makeText(this, "Lock vault then re-open to change PIN", Toast.LENGTH_LONG).show();
    }

    private void showSessionDurationDialog() {
        String[] options = {"1 minute", "2 minutes (default)", "5 minutes", "10 minutes", "30 minutes"};
        long[] durations = {60_000L, 120_000L, 300_000L, 600_000L, 1_800_000L};
        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("Auto-Lock Duration")
                .setItems(options, (d, which) -> {
                    repo.setSessionDurationMs(durations[which]);
                    startSessionTimer();
                    Toast.makeText(this, "Session duration: " + options[which], Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void showDecoyPinSetup() {
        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("Decoy PIN")
                .setMessage("A Decoy PIN opens an empty vault. If someone forces you to reveal your PIN, " +
                        "you can give the decoy PIN instead.\n\nThis is an advanced security feature.")
                .setPositiveButton("Set Up Decoy PIN", (d, w) -> {
                    Toast.makeText(this, "Coming soon — lock and unlock to use setup flow", Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ─── Utilities ────────────────────────────────────────────────

    private String getFileTypeEmoji(VaultFileItem.FileType type) {
        if (type == null) return "📎";
        switch (type) {
            case IMAGE: return "🖼️";
            case VIDEO: return "🎬";
            case AUDIO: return "🎵";
            case DOCUMENT: return "📄";
            default: return "📎";
        }
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
