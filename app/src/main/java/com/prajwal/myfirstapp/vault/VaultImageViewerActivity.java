package com.prajwal.myfirstapp.vault;


import com.prajwal.myfirstapp.R;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Full-screen secure image viewer with pinch-zoom, pan, swipe navigation.
 */
public class VaultImageViewerActivity extends Activity {

    private static final String TAG = "VaultImageViewer";
    private static final String EXTRA_FILE_ID = "file_id";
    private static final String EXTRA_LIST_TYPE = "file_list_type";

    private MediaVaultRepository repo;
    private VaultFileItem currentFile;
    private List<VaultFileItem> fileList = new ArrayList<>();
    private int currentIndex = 0;

    private ImageView imageView;
    private LinearLayout topBar, bottomBar;
    private TextView tvFileName, btnBack, btnInfo, btnMore;
    private TextView btnFavourite, btnAddAlbum, btnShare, btnExport, btnEdit, btnDelete;

    // Zoom/pan state
    private final Matrix matrix = new Matrix();
    private float scaleFactor = 1f;
    private float lastX, lastY;
    private boolean isZoomed = false;

    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;

    // Swipe-down dismiss
    private float touchStartY;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable hideUiRunnable = this::hideBars;
    private boolean barsVisible = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
        );
        setContentView(R.layout.activity_vault_image_viewer);
        setImmersive();

        repo = MediaVaultRepository.getInstance(this);
        if (!repo.isUnlocked()) { finish(); return; }

        imageView = findViewById(R.id.imageView);
        topBar = findViewById(R.id.topBar);
        bottomBar = findViewById(R.id.bottomBar);
        tvFileName = findViewById(R.id.tvFileName);
        btnBack = findViewById(R.id.btnBack);
        btnInfo = findViewById(R.id.btnInfo);
        btnMore = findViewById(R.id.btnMore);
        btnFavourite = findViewById(R.id.btnFavourite);
        btnAddAlbum = findViewById(R.id.btnAddAlbum);
        btnShare = findViewById(R.id.btnShare);
        btnExport = findViewById(R.id.btnExport);
        btnEdit = findViewById(R.id.btnEdit);
        btnDelete = findViewById(R.id.btnDelete);

        String fileId = getIntent().getStringExtra(EXTRA_FILE_ID);
        String listType = getIntent().getStringExtra(EXTRA_LIST_TYPE);
        if (fileId == null) { finish(); return; }

        buildFileList(listType);
        currentIndex = indexOfId(fileId);
        if (currentIndex < 0) currentIndex = 0;

        scaleDetector = new ScaleGestureDetector(this, new PinchListener());
        gestureDetector = new GestureDetector(this, new TapListener());

        setupClickListeners();
        loadCurrentFile();
        scheduleBarsHide();
    }

    private void buildFileList(String listType) {
        if (listType == null || listType.equals("all")) {
            fileList = repo.getAllFiles();
        } else if (listType.equals("image")) {
            fileList = repo.getFilesByType(VaultFileItem.FileType.IMAGE);
        } else if (listType.equals("video")) {
            fileList = repo.getFilesByType(VaultFileItem.FileType.VIDEO);
        } else if (listType.equals("audio")) {
            fileList = repo.getFilesByType(VaultFileItem.FileType.AUDIO);
        } else if (listType.equals("document")) {
            fileList = repo.getFilesByType(VaultFileItem.FileType.DOCUMENT);
        } else {
            fileList = repo.getFilesByAlbum(listType);
        }
        // keep only images
        List<VaultFileItem> images = new ArrayList<>();
        for (VaultFileItem f : fileList) {
            if (f.fileType == VaultFileItem.FileType.IMAGE) images.add(f);
        }
        if (!images.isEmpty()) fileList = images;
    }

    private int indexOfId(String id) {
        for (int i = 0; i < fileList.size(); i++) {
            if (fileList.get(i).id.equals(id)) return i;
        }
        return -1;
    }

    private void loadCurrentFile() {
        if (fileList.isEmpty()) { finish(); return; }
        currentFile = fileList.get(currentIndex);
        tvFileName.setText(currentFile.originalFileName);
        updateFavButton();
        repo.logFileViewed(currentFile);

        new Thread(() -> {
            try {
                // Decrypt directly into memory — never write unencrypted bytes to disk
                byte[] imageBytes = repo.decryptFileToMemory(currentFile);
                if (imageBytes == null || imageBytes.length == 0) {
                    Log.e(TAG, "Decryption returned null/empty for: " + currentFile.vaultFileName);
                    runOnUiThread(() -> Toast.makeText(this,
                            "Cannot decrypt image", Toast.LENGTH_SHORT).show());
                    return;
                }
                final Bitmap bm = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                if (bm == null) {
                    Log.e(TAG, "BitmapFactory failed to decode bytes for: " + currentFile.originalFileName);
                    runOnUiThread(() -> Toast.makeText(this,
                            "Cannot decode image", Toast.LENGTH_SHORT).show());
                    return;
                }
                runOnUiThread(() -> {
                    imageView.setImageBitmap(bm);
                    resetZoom();
                });
            } catch (Exception e) {
                Log.e(TAG, "Image load failed for: " + (currentFile != null ? currentFile.originalFileName : "null"), e);
                runOnUiThread(() -> Toast.makeText(this,
                        "Failed to load image", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void resetZoom() {
        scaleFactor = 1f;
        matrix.reset();
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        isZoomed = false;
    }

    private void setupClickListeners() {
        btnBack.setOnClickListener(v -> finish());
        btnInfo.setOnClickListener(v -> showInfoDialog());
        btnMore.setOnClickListener(v -> showMoreMenu());
        btnFavourite.setOnClickListener(v -> toggleFavourite());
        btnAddAlbum.setOnClickListener(v -> showAddToAlbumDialog());
        btnShare.setOnClickListener(v -> shareFile());
        btnExport.setOnClickListener(v -> exportToDownloads());
        btnEdit.setOnClickListener(v -> openImageEditor());
        btnDelete.setOnClickListener(v -> confirmDelete());

        imageView.setOnTouchListener((v, event) -> handleTouch(event));
    }

    private boolean handleTouch(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);

        if (event.getPointerCount() == 1 && !scaleDetector.isInProgress()) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    touchStartY = event.getY();
                    lastX = event.getX();
                    lastY = event.getY();
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (scaleFactor <= 1f) {
                        float dy = event.getY() - touchStartY;
                        if (dy > 0) {
                            imageView.setTranslationY(dy);
                            float alpha = Math.max(0f, 1f - dy / 400f);
                            imageView.setAlpha(alpha);
                        }
                    } else {
                        float dx = event.getX() - lastX;
                        float dy2 = event.getY() - lastY;
                        matrix.postTranslate(dx, dy2);
                        imageView.setImageMatrix(matrix);
                        lastX = event.getX();
                        lastY = event.getY();
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    float dy = event.getY() - touchStartY;
                    if (dy > 200 && scaleFactor <= 1f) {
                        finish();
                    } else {
                        imageView.setTranslationY(0);
                        imageView.setAlpha(1f);
                    }
                    break;
            }
        }
        return true;
    }

    private class PinchListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            scaleFactor *= detector.getScaleFactor();
            scaleFactor = Math.max(0.5f, Math.min(scaleFactor, 8f));
            imageView.setScaleType(ImageView.ScaleType.MATRIX);
            matrix.postScale(detector.getScaleFactor(), detector.getScaleFactor(),
                    detector.getFocusX(), detector.getFocusY());
            imageView.setImageMatrix(matrix);
            isZoomed = scaleFactor > 1.1f;
            return true;
        }
    }

    private class TapListener extends GestureDetector.SimpleOnGestureListener {
        private static final int SWIPE_THRESHOLD = 100;

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            toggleBars();
            return true;
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            if (isZoomed) {
                resetZoom();
            } else {
                scaleFactor = 2.5f;
                imageView.setScaleType(ImageView.ScaleType.MATRIX);
                matrix.postScale(2.5f, 2.5f, e.getX(), e.getY());
                imageView.setImageMatrix(matrix);
                isZoomed = true;
            }
            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float vX, float vY) {
            if (e1 == null || e2 == null) return false;
            float dX = e2.getX() - e1.getX();
            float dY = e2.getY() - e1.getY();
            if (Math.abs(dX) > Math.abs(dY) && Math.abs(dX) > SWIPE_THRESHOLD && scaleFactor <= 1f) {
                if (dX < 0) navigateNext();
                else navigatePrev();
                return true;
            }
            return false;
        }
    }

    private void navigateNext() {
        if (currentIndex < fileList.size() - 1) {
            currentIndex++;
            loadCurrentFile();
        }
    }

    private void navigatePrev() {
        if (currentIndex > 0) {
            currentIndex--;
            loadCurrentFile();
        }
    }

    private void toggleBars() {
        if (barsVisible) {
            hideBars();
        } else {
            showBars();
            scheduleBarsHide();
        }
    }

    private void showBars() {
        topBar.setVisibility(View.VISIBLE);
        bottomBar.setVisibility(View.VISIBLE);
        barsVisible = true;
    }

    private void hideBars() {
        topBar.setVisibility(View.GONE);
        bottomBar.setVisibility(View.GONE);
        barsVisible = false;
        setImmersive();
    }

    private void scheduleBarsHide() {
        handler.removeCallbacks(hideUiRunnable);
        handler.postDelayed(hideUiRunnable, 2000);
    }

    private void setImmersive() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        );
    }

    private void showInfoDialog() {
        if (currentFile == null) return;
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
        String dateImported = sdf.format(new Date(currentFile.importedAt));
        String dateTaken = (currentFile.originalCreatedAt > 0)
                ? sdf.format(new Date(currentFile.originalCreatedAt)) : "Not available";
        String dims = (currentFile.width > 0 && currentFile.height > 0)
                ? currentFile.width + " × " + currentFile.height + " px" : "Unknown";
        String tags = (currentFile.tags != null && !currentFile.tags.isEmpty())
                ? String.join(", ", currentFile.tags) : "None";
        String note = (currentFile.notes != null && !currentFile.notes.isEmpty())
                ? currentFile.notes : "—";

        // Resolve album name
        String albumName = "None";
        if (currentFile.albumId != null) {
            for (VaultAlbum album : repo.getAlbums()) {
                if (album.id.equals(currentFile.albumId)) {
                    albumName = album.name;
                    break;
                }
            }
        }

        String sep = "\n─────────────────\n";
        String msg = "📄 Name: " + currentFile.originalFileName
                + sep
                + "💾 Size: " + currentFile.getFormattedSize()
                + "\n📐 Dimensions: " + dims
                + sep
                + "📅 Imported: " + dateImported
                + "\n📸 Date Taken: " + dateTaken
                + sep
                + "🏷 Tags: " + tags
                + "\n📂 Album: " + albumName
                + sep
                + "📝 Note: " + note;

        new AlertDialog.Builder(this)
                .setTitle("File Info")
                .setMessage(msg)
                .setPositiveButton("OK", null)
                .show();
    }

    private void showMoreMenu() {
        String favLabel = (currentFile != null && currentFile.isFavourited)
                ? "Remove from Favourites" : "Add to Favourites";
        String[] items = {favLabel, "Add to Album", "Set as Cover", "Share", "Export to Downloads",
                "Edit (Rotate/Flip)", "Add Note", "Set Expiry Reminder", "Delete"};
        new AlertDialog.Builder(this)
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0: toggleFavourite(); break;
                        case 1: showAddToAlbumDialog(); break;
                        case 2: setAsAlbumCover(); break;
                        case 3: shareFile(); break;
                        case 4: exportToDownloads(); break;
                        case 5: showEditDialog(); break;
                        case 6: showEditNoteDialog(); break;
                        case 7: showSetExpiryDialog(); break;
                        case 8: confirmDelete(); break;
                    }
                })
                .show();
    }

    private void showEditNoteDialog() {
        if (currentFile == null) return;
        EditText editText = new EditText(this);
        editText.setText(currentFile.notes != null ? currentFile.notes : "");
        editText.setHint("Add a note about this file…");
        editText.setMaxLines(5);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        editText.setPadding(pad, pad, pad, pad);
        new AlertDialog.Builder(this)
                .setTitle("Edit Note")
                .setView(editText)
                .setPositiveButton("Save", (d, w) -> {
                    currentFile.notes = editText.getText().toString().trim();
                    repo.updateFile(currentFile);
                    Toast.makeText(this, "Note saved", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void toggleFavourite() {
        if (currentFile == null) return;
        currentFile.isFavourited = !currentFile.isFavourited;
        repo.updateFile(currentFile);
        updateFavButton();
        Toast.makeText(this,
                currentFile.isFavourited ? "Added to favourites" : "Removed from favourites",
                Toast.LENGTH_SHORT).show();
    }

    private void updateFavButton() {
        if (currentFile != null) {
            btnFavourite.setText(currentFile.isFavourited ? "♥" : "♡");
        }
    }

    private void showAddToAlbumDialog() {
        List<VaultAlbum> albums = repo.getAlbums();
        if (albums.isEmpty()) {
            Toast.makeText(this, "No albums found", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[albums.size()];
        for (int i = 0; i < albums.size(); i++) names[i] = albums.get(i).name;
        new AlertDialog.Builder(this)
                .setTitle("Add to Album")
                .setItems(names, (d, which) -> {
                    repo.addFileToAlbum(currentFile.id, albums.get(which).id);
                    Toast.makeText(this, "Added to " + albums.get(which).name, Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void shareFile() {
        if (currentFile == null) return;
        new Thread(() -> {
            try {
                File shareTemp = File.createTempFile("share_", "_" + currentFile.originalFileName, getCacheDir());
                if (!repo.exportFile(currentFile, shareTemp)) throw new Exception("export failed");
                runOnUiThread(() -> {
                    Uri uri = FileProvider.getUriForFile(this,
                            getPackageName() + ".provider", shareTemp);
                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.setType(currentFile.mimeType != null ? currentFile.mimeType : "image/*");
                    intent.putExtra(Intent.EXTRA_STREAM, uri);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(intent, "Share via"));
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Share failed", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void exportToDownloads() {
        if (currentFile == null) return;
        if (VaultWatermarkHelper.isEnabled(this)) {
            new AlertDialog.Builder(this)
                    .setTitle("Export with Watermark?")
                    .setMessage("A watermark will be applied to the exported image. Proceed?")
                    .setPositiveButton("Yes", (d, w) -> doExport(true))
                    .setNegativeButton("No", (d, w) -> doExport(false))
                    .show();
        } else {
            doExport(false);
        }
    }

    private void doExport(boolean withWatermark) {
        new Thread(() -> {
            try {
                File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                File dest = new File(downloads, currentFile.originalFileName);
                if (withWatermark) {
                    byte[] imageBytes = repo.decryptFileToMemory(currentFile);
                    if (imageBytes == null) throw new Exception("decrypt failed");
                    Bitmap bm = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                    if (bm == null) throw new Exception("decode failed");
                    String text = VaultWatermarkHelper.getDefaultWatermarkText(this);
                    Bitmap watermarked = VaultWatermarkHelper.applyWatermark(bm, text, "bottom_right", 0.5f);
                    try (FileOutputStream fos = new FileOutputStream(dest)) {
                        watermarked.compress(Bitmap.CompressFormat.JPEG, 95, fos);
                    }
                    runOnUiThread(() -> Toast.makeText(this, "Exported with watermark", Toast.LENGTH_SHORT).show());
                } else {
                    boolean ok = repo.exportFile(currentFile, dest);
                    runOnUiThread(() -> Toast.makeText(this,
                            ok ? "Exported to Downloads" : "Export failed", Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Export failed", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("Delete File")
                .setMessage("Delete \"" + currentFile.originalFileName + "\" permanently?")
                .setPositiveButton("Delete", (d, w) -> {
                    repo.deleteFile(currentFile);
                    Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showEditDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Basic Editing")
                .setMessage("Basic editing — Rotate, Flip, and Crop available")
                .setPositiveButton("Rotate Left", (d, w) -> applyRotation(-90f))
                .setNeutralButton("Rotate Right", (d, w) -> applyRotation(90f))
                .setNegativeButton("Flip H", (d, w) -> applyFlipHorizontal())
                .show();
    }

    private void applyRotation(float degrees) {
        Bitmap current = imageView.getDrawable() == null ? null
                : android.graphics.drawable.BitmapDrawable.class.isInstance(imageView.getDrawable())
                ? ((android.graphics.drawable.BitmapDrawable) imageView.getDrawable()).getBitmap() : null;
        if (current == null) return;
        Matrix m = new Matrix();
        m.postRotate(degrees);
        Bitmap rotated = Bitmap.createBitmap(current, 0, 0, current.getWidth(), current.getHeight(), m, true);
        imageView.setImageBitmap(rotated);
        resetZoom();
    }

    private void applyFlipHorizontal() {
        Bitmap current = imageView.getDrawable() == null ? null
                : android.graphics.drawable.BitmapDrawable.class.isInstance(imageView.getDrawable())
                ? ((android.graphics.drawable.BitmapDrawable) imageView.getDrawable()).getBitmap() : null;
        if (current == null) return;
        Matrix m = new Matrix();
        m.postScale(-1f, 1f, current.getWidth() / 2f, current.getHeight() / 2f);
        Bitmap flipped = Bitmap.createBitmap(current, 0, 0, current.getWidth(), current.getHeight(), m, true);
        imageView.setImageBitmap(flipped);
    }

    private void openImageEditor() {
        if (currentFile == null) return;
        Intent intent = new Intent(this, VaultImageEditorActivity.class);
        intent.putExtra("file_id", currentFile.id);
        startActivity(intent);
    }

    private void setAsAlbumCover() {
        if (currentFile == null || currentFile.albumId == null) {
            Toast.makeText(this, "Image is not in an album", Toast.LENGTH_SHORT).show();
            return;
        }
        for (VaultAlbum album : repo.getAlbums()) {
            if (album.id.equals(currentFile.albumId)) {
                album.coverFileId = currentFile.id;
                repo.updateAlbum(album);
                Toast.makeText(this, "Set as cover for \"" + album.name + "\"", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        Toast.makeText(this, "Album not found", Toast.LENGTH_SHORT).show();
    }

    private void showSetExpiryDialog() {
        if (currentFile == null) return;
        Calendar cal = Calendar.getInstance();
        new DatePickerDialog(this, (view, year, month, day) -> {
            cal.set(year, month, day, 0, 0, 0);
            cal.set(Calendar.MILLISECOND, 0);
            long timestamp = cal.getTimeInMillis();
            repo.setFileExpiry(currentFile.id, timestamp);
            String formatted = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                    .format(new Date(timestamp));
            Toast.makeText(this, "Expiry reminder set for " + formatted, Toast.LENGTH_SHORT).show();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) setImmersive();
    }
}
