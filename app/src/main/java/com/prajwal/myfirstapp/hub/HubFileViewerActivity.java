package com.prajwal.myfirstapp.hub;


import com.prajwal.myfirstapp.R;
import com.prajwal.myfirstapp.vault.VaultAudioPlayerActivity;
import com.prajwal.myfirstapp.vault.VaultVideoPlayerActivity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.Enumeration;

/**
 * Universal file viewer — dispatches to appropriate viewer based on file type.
 */
public class HubFileViewerActivity extends AppCompatActivity {

    public static final String EXTRA_FILE_ID = "file_id";
    private static final int MAX_FILE_LINES = 5000;

    private HubFileRepository repo;
    private HubFile file;

    private TextView tvViewerFileName;
    private View imageViewerSection;
    private View codeViewerSection;
    private View textViewerSection;
    private View unsupportedSection;
    private LinearLayout fileInfoPanel;

    private float codeFontSize = 13f;
    private TextView tvCodeContent;
    private boolean infoPanelVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hub_file_viewer);

        repo = HubFileRepository.getInstance(this);

        String fileId = getIntent().getStringExtra(EXTRA_FILE_ID);
        // Support "fileId" alias used by new activities
        if (fileId == null) fileId = getIntent().getStringExtra("fileId");
        if (fileId != null) file = repo.getFileById(fileId);

        bindViews();

        if (file == null) {
            showUnsupported("Unknown File", "File not found", "📄");
            return;
        }

        tvViewerFileName.setText(file.displayName != null ? file.displayName :
                (file.originalFileName != null ? file.originalFileName : "File"));

        populateFileInfo();
        dispatchViewer();

        // Log access
        repo.logActivity(new FileActivity(
                file.id,
                file.displayName != null ? file.displayName : file.originalFileName,
                file.getTypeEmoji(),
                FileActivity.Action.VIEWED,
                "Opened in viewer"
        ));

        // Record access in predictive engine
        repo.recordFileAccess(file.id);
        HubPredictiveEngine.getInstance(this).recordAccess(file.id);
    }

    private void bindViews() {
        tvViewerFileName = findViewById(R.id.tvViewerFileName);
        imageViewerSection = findViewById(R.id.imageViewerSection);
        codeViewerSection = findViewById(R.id.codeViewerSection);
        textViewerSection = findViewById(R.id.textViewerSection);
        unsupportedSection = findViewById(R.id.unsupportedSection);
        fileInfoPanel = findViewById(R.id.fileInfoPanel);
        tvCodeContent = findViewById(R.id.tvCodeContent);

        findViewById(R.id.btnViewerBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnViewerShare).setOnClickListener(v -> shareFile());
        findViewById(R.id.btnViewerMore).setOnClickListener(v -> showMoreMenu());
        findViewById(R.id.btnFileInfo).setOnClickListener(v -> toggleInfoPanel());

        // Code viewer controls
        View codeMinus = findViewById(R.id.btnCodeFontMinus);
        if (codeMinus != null) codeMinus.setOnClickListener(v -> {
            if (codeFontSize > 8) codeFontSize -= 1;
            tvCodeContent.setTextSize(codeFontSize);
        });

        View codePlus = findViewById(R.id.btnCodeFontPlus);
        if (codePlus != null) codePlus.setOnClickListener(v -> {
            if (codeFontSize < 24) codeFontSize += 1;
            tvCodeContent.setTextSize(codeFontSize);
        });

        View codeCopy = findViewById(R.id.btnCodeCopyAll);
        if (codeCopy != null) codeCopy.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null && tvCodeContent.getText() != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("code", tvCodeContent.getText()));
                Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void dispatchViewer() {
        if (file.fileType == null) { showUnsupported(); return; }

        switch (file.fileType) {
            case IMAGE:
            case SCREENSHOT:
                showImage();
                break;
            case CODE:
                showCode();
                break;
            case DOCUMENT:
            case SPREADSHEET:
            case PRESENTATION:
                showText();
                break;
            case PDF:
                showPdfOrExternal();
                break;
            case AUDIO:
                openAudioPlayer();
                break;
            case VIDEO:
                openVideoPlayer();
                break;
            case ARCHIVE:
                showArchiveContents();
                break;
            default:
                showUnsupported();
                break;
        }
    }

    private void showImage() {
        imageViewerSection.setVisibility(View.VISIBLE);
        codeViewerSection.setVisibility(View.GONE);
        textViewerSection.setVisibility(View.GONE);
        unsupportedSection.setVisibility(View.GONE);

        ImageView iv = findViewById(R.id.ivImageViewer);
        if (file.filePath != null && !file.filePath.isEmpty()) {
            try {
                File f = new File(file.filePath);
                if (f.exists()) {
                    iv.setImageURI(Uri.fromFile(f));
                    return;
                }
            } catch (Exception e) { /* fall through */ }
        }
        // Placeholder
        iv.setImageResource(android.R.drawable.ic_menu_gallery);
    }

    private void showCode() {
        imageViewerSection.setVisibility(View.GONE);
        codeViewerSection.setVisibility(View.VISIBLE);
        textViewerSection.setVisibility(View.GONE);
        unsupportedSection.setVisibility(View.GONE);

        // Set language badge
        TextView tvLang = findViewById(R.id.tvCodeLanguage);
        if (tvLang != null) tvLang.setText(detectLanguage(file.fileExtension));

        // Read file content
        String content = readFileContent(file.filePath);
        if (content != null) {
            // Add line numbers
            String[] lines = content.split("\n");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < lines.length; i++) {
                sb.append(String.format(Locale.getDefault(), "%4d  ", i + 1)).append(lines[i]).append("\n");
            }
            tvCodeContent.setTypeface(Typeface.MONOSPACE);
            tvCodeContent.setTextSize(codeFontSize);
            tvCodeContent.setText(sb.toString());
        } else {
            tvCodeContent.setText("(Could not read file)");
        }
    }

    private void showText() {
        imageViewerSection.setVisibility(View.GONE);
        codeViewerSection.setVisibility(View.GONE);
        textViewerSection.setVisibility(View.VISIBLE);
        unsupportedSection.setVisibility(View.GONE);

        String content = readFileContent(file.filePath);
        TextView tvText = findViewById(R.id.tvTextContent);
        tvText.setText(content != null ? content : "(Could not read file)");
    }

    private void showPdfOrExternal() {
        // PDFs cannot be rendered natively — always show the "Open with" button
        showUnsupported();
    }

    private void openAudioPlayer() {
        try {
            Intent intent = new Intent(this, VaultAudioPlayerActivity.class);
            intent.putExtra("file_path", file.filePath);
            startActivity(intent);
        } catch (Exception e) {
            showUnsupported();
        }
    }

    private void openVideoPlayer() {
        try {
            Intent intent = new Intent(this, VaultVideoPlayerActivity.class);
            intent.putExtra("file_path", file.filePath);
            startActivity(intent);
        } catch (Exception e) {
            showUnsupported();
        }
    }

    private void showArchiveContents() {
        imageViewerSection.setVisibility(View.GONE);
        codeViewerSection.setVisibility(View.GONE);
        textViewerSection.setVisibility(View.VISIBLE);
        unsupportedSection.setVisibility(View.GONE);

        StringBuilder sb = new StringBuilder("Archive Contents:\n\n");
        if (file.filePath != null && !file.filePath.isEmpty()) {
            try (ZipFile zip = new ZipFile(file.filePath)) {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                int count = 0;
                while (entries.hasMoreElements() && count < 200) {
                    ZipEntry entry = entries.nextElement();
                    sb.append(entry.isDirectory() ? "📁 " : "📄 ");
                    sb.append(entry.getName());
                    if (!entry.isDirectory()) {
                        sb.append(" (").append(formatBytes(entry.getSize())).append(")");
                    }
                    sb.append("\n");
                    count++;
                }
                if (entries.hasMoreElements()) sb.append("... and more");
            } catch (Exception e) {
                sb.append("(Could not read archive: ").append(e.getMessage()).append(")");
            }
        } else {
            sb.append("(No file path available)");
        }

        TextView tvText = findViewById(R.id.tvTextContent);
        tvText.setText(sb.toString());
    }

    private void showUnsupported() {
        String name = file.displayName != null ? file.displayName : file.originalFileName;
        String details = (file.fileType != null ? file.fileType.name() : "FILE") + " · " + file.getFormattedSize();
        showUnsupported(name, details, file.getTypeEmoji());
    }

    private void showUnsupported(String name, String details, String icon) {
        imageViewerSection.setVisibility(View.GONE);
        codeViewerSection.setVisibility(View.GONE);
        textViewerSection.setVisibility(View.GONE);
        unsupportedSection.setVisibility(View.VISIBLE);

        TextView tvIcon = findViewById(R.id.tvUnsupportedIcon);
        TextView tvName = findViewById(R.id.tvUnsupportedName);
        TextView tvDetails = findViewById(R.id.tvUnsupportedDetails);
        View btnOpen = findViewById(R.id.btnOpenExternal);

        tvIcon.setText(icon != null ? icon : "📄");
        tvName.setText(name != null ? name : "Unknown File");
        tvDetails.setText(details != null ? details : "");

        btnOpen.setOnClickListener(v -> openWithExternalApp());
    }

    private void openWithExternalApp() {
        if (file == null || file.filePath == null || file.filePath.isEmpty()) {
            Toast.makeText(this, "No file path available", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Uri uri;
            if (file.filePath.startsWith("content://")) {
                // Already a content URI (e.g. imported via file picker)
                uri = Uri.parse(file.filePath);
            } else {
                File f = new File(file.filePath);
                if (!f.exists()) {
                    Toast.makeText(this, "File not found on device", Toast.LENGTH_SHORT).show();
                    return;
                }
                uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", f);
            }
            String mime = file.mimeType != null && !file.mimeType.isEmpty() ? file.mimeType : "*/*";
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mime);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Open with"));
        } catch (Exception e) {
            Toast.makeText(this, "No app found to open this file", Toast.LENGTH_SHORT).show();
        }
    }

    private void shareFile() {
        if (file == null || file.filePath == null || file.filePath.isEmpty()) {
            Toast.makeText(this, "No file to share", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            File f = new File(file.filePath);
            Uri uri = FileProvider.getUriForFile(this,
                    getPackageName() + ".provider", f);
            Intent intent = new Intent(Intent.ACTION_SEND);
            String mime = file.mimeType != null && !file.mimeType.isEmpty() ? file.mimeType : "*/*";
            intent.setType(mime);
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Share"));
        } catch (Exception e) {
            Toast.makeText(this, "Cannot share file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showMoreMenu() {
        if (file == null) {
            android.widget.Toast.makeText(this, "No file loaded", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        String watchLabel = file.isWatchlisted ? "👁️ Remove from Watchlist" : "👁️ Add to Watchlist";
        String[] options = {
                "⭐ " + (file.isFavourited ? "Remove from Favourites" : "Add to Favourites"),
                watchLabel,
                "🔔 Set Expiry Reminder",
                "📦 Add to Collection",
                "🗑️ Delete File"
        };
        new android.app.AlertDialog.Builder(this)
                .setTitle("File Options")
                .setItems(options, (d, which) -> {
                    switch (which) {
                        case 0: // Toggle favourite
                            file.isFavourited = !file.isFavourited;
                            repo.updateFile(file);
                            android.widget.Toast.makeText(this,
                                    file.isFavourited ? "Added to Favourites" : "Removed from Favourites",
                                    android.widget.Toast.LENGTH_SHORT).show();
                            break;
                        case 1: // Toggle watchlist
                            file.isWatchlisted = !file.isWatchlisted;
                            repo.updateFile(file);
                            android.widget.Toast.makeText(this,
                                    file.isWatchlisted ? "Added to Watchlist" : "Removed from Watchlist",
                                    android.widget.Toast.LENGTH_SHORT).show();
                            break;
                        case 2: // Set expiry reminder
                            showExpiryReminderDialog();
                            break;
                        case 3: // Add to collection
                            showAddToCollectionDialog();
                            break;
                        case 4: // Delete
                            confirmDelete();
                            break;
                    }
                })
                .show();
    }

    private void showExpiryReminderDialog() {
        if (file == null) return;
        android.app.DatePickerDialog dpd = new android.app.DatePickerDialog(this,
                (view, year, month, day) -> {
                    java.util.Calendar cal = java.util.Calendar.getInstance();
                    cal.set(year, month, day, 9, 0, 0);
                    HubFileExpiryManager.schedule(this, file, cal.getTimeInMillis());
                    android.widget.Toast.makeText(this, "Expiry reminder set!", android.widget.Toast.LENGTH_SHORT).show();
                },
                java.util.Calendar.getInstance().get(java.util.Calendar.YEAR),
                java.util.Calendar.getInstance().get(java.util.Calendar.MONTH),
                java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_MONTH));
        dpd.setTitle("Set Expiry Reminder");
        dpd.show();
    }

    private void showAddToCollectionDialog() {
        if (file == null) return;
        java.util.List<HubCollection> cols = repo.getAllCollections();
        if (cols.isEmpty()) {
            android.widget.Toast.makeText(this,
                    "No collections yet — create one in the Collections screen",
                    android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        String[] names = new String[cols.size()];
        for (int i = 0; i < cols.size(); i++) names[i] = cols.get(i).name;
        new android.app.AlertDialog.Builder(this)
                .setTitle("Add to Collection")
                .setItems(names, (d, which) -> {
                    repo.addFileToCollection(file.id, cols.get(which).id);
                    android.widget.Toast.makeText(this,
                            "Added to " + cols.get(which).name, android.widget.Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void confirmDelete() {
        if (file == null) return;
        String name = file.displayName != null ? file.displayName : file.originalFileName;
        new android.app.AlertDialog.Builder(this)
                .setTitle("Delete File")
                .setMessage("Delete \"" + name + "\"? This cannot be undone.")
                .setPositiveButton("Delete", (d, w) -> {
                    repo.deleteFile(file.id);
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void toggleInfoPanel() {
        infoPanelVisible = !infoPanelVisible;
        fileInfoPanel.setVisibility(infoPanelVisible ? View.VISIBLE : View.GONE);
        TextView btnInfo = findViewById(R.id.btnFileInfo);
        btnInfo.setText(infoPanelVisible ? "File Info ▼" : "File Info ▲");
    }

    private void populateFileInfo() {
        if (file == null) return;
        TextView tvInfoName = findViewById(R.id.tvInfoName);
        TextView tvInfoType = findViewById(R.id.tvInfoType);
        TextView tvInfoSize = findViewById(R.id.tvInfoSize);
        TextView tvInfoDate = findViewById(R.id.tvInfoDate);
        TextView tvInfoPath = findViewById(R.id.tvInfoPath);

        String name = file.displayName != null ? file.displayName : file.originalFileName;
        if (tvInfoName != null) tvInfoName.setText(name != null ? name : "—");
        if (tvInfoType != null) tvInfoType.setText(file.fileType != null ? file.fileType.name() : "—");
        if (tvInfoSize != null) tvInfoSize.setText(file.getFormattedSize());
        if (tvInfoDate != null) tvInfoDate.setText(
                new SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()).format(new Date(file.importedAt)));
        if (tvInfoPath != null) tvInfoPath.setText(file.filePath != null ? file.filePath : "—");

        // Add File DNA view and mood tag picker to the info panel
        if (fileInfoPanel != null) {
            addFileDnaAndMoodPicker();
        }
    }

    private void addFileDnaAndMoodPicker() {
        // Check if already added
        if (fileInfoPanel.findViewWithTag("dna_section") != null) return;

        // File DNA
        android.widget.LinearLayout dnaSection = new android.widget.LinearLayout(this);
        dnaSection.setTag("dna_section");
        dnaSection.setOrientation(android.widget.LinearLayout.VERTICAL);
        dnaSection.setPadding(0, 16, 0, 0);

        android.widget.TextView tvDnaLabel = new android.widget.TextView(this);
        tvDnaLabel.setText("🧬 File DNA");
        tvDnaLabel.setTextColor(android.graphics.Color.parseColor("#9CA3AF"));
        tvDnaLabel.setTextSize(12);
        dnaSection.addView(tvDnaLabel);

        HubFileDnaView dnaView = new HubFileDnaView(this);
        // Generate a deterministic hash from file id + name for display
        String hashInput = (file.id != null ? file.id : "") +
                (file.originalFileName != null ? file.originalFileName : "") +
                file.fileSize;
        dnaView.setHash(simpleHash(hashInput));
        android.widget.LinearLayout.LayoutParams dnaLp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        dnaLp.topMargin = 8;
        dnaView.setLayoutParams(dnaLp);
        dnaSection.addView(dnaView);
        fileInfoPanel.addView(dnaSection);

        // Mood Tag Picker
        android.widget.LinearLayout moodSection = new android.widget.LinearLayout(this);
        moodSection.setTag("mood_section");
        moodSection.setOrientation(android.widget.LinearLayout.VERTICAL);
        moodSection.setPadding(0, 16, 0, 0);

        android.widget.TextView tvMoodLabel = new android.widget.TextView(this);
        tvMoodLabel.setText("😊 Mood Tag");
        tvMoodLabel.setTextColor(android.graphics.Color.parseColor("#9CA3AF"));
        tvMoodLabel.setTextSize(12);
        moodSection.addView(tvMoodLabel);

        String[] moods = {"🔥", "⭐", "💡", "😤", "✅", "🎯", "💤", "🚀"};
        android.widget.HorizontalScrollView moodScroll = new android.widget.HorizontalScrollView(this);
        moodScroll.setHorizontalScrollBarEnabled(false);
        android.widget.LinearLayout moodRow = new android.widget.LinearLayout(this);
        moodRow.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        moodRow.setPadding(0, 8, 0, 0);

        for (String mood : moods) {
            android.widget.Button moodBtn = new android.widget.Button(this);
            moodBtn.setText(mood);
            moodBtn.setTextSize(20);
            moodBtn.setPadding(12, 8, 12, 8);
            boolean isSelected = mood.equals(file.moodTag);
            android.graphics.drawable.GradientDrawable moodBg = new android.graphics.drawable.GradientDrawable();
            moodBg.setColor(isSelected ? android.graphics.Color.parseColor("#6366F1")
                    : android.graphics.Color.parseColor("#1E293B"));
            moodBg.setCornerRadius(20f);
            moodBtn.setBackground(moodBg);
            android.widget.LinearLayout.LayoutParams moodLp = new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
            moodLp.setMargins(0, 0, 8, 0);
            moodBtn.setLayoutParams(moodLp);
            moodBtn.setOnClickListener(v -> {
                if (mood.equals(file.moodTag)) {
                    file.moodTag = null;
                } else {
                    file.moodTag = mood;
                }
                repo.updateFile(file);
                // Refresh the panel
                removeTaggedView(fileInfoPanel, "mood_section");
                removeTaggedView(fileInfoPanel, "dna_section");
                addFileDnaAndMoodPicker();
                android.widget.Toast.makeText(this,
                        file.moodTag != null ? "Mood set to " + file.moodTag : "Mood cleared",
                        android.widget.Toast.LENGTH_SHORT).show();
                repo.logAudit("VIEW", "Set mood tag: " + file.moodTag + " on " + file.displayName);
            });
            moodRow.addView(moodBtn);
        }
        moodScroll.addView(moodRow);
        moodSection.addView(moodScroll);
        fileInfoPanel.addView(moodSection);
    }

    private String simpleHash(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(input.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { return input.length() > 32 ? input.substring(0, 32) : input; }
    }

    private void removeTaggedView(android.widget.LinearLayout parent, Object tag) {
        for (int i = parent.getChildCount() - 1; i >= 0; i--) {
            android.view.View child = parent.getChildAt(i);
            if (tag.equals(child.getTag())) { parent.removeViewAt(i); return; }
        }
    }

    private String readFileContent(String path) {
        if (path == null || path.isEmpty()) return null;
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            StringBuilder sb = new StringBuilder();
            String line;
            int lineCount = 0;
            while ((line = reader.readLine()) != null && lineCount < MAX_FILE_LINES) {
                sb.append(line).append("\n");
                lineCount++;
            }
            if (lineCount >= MAX_FILE_LINES) sb.append("\n... (file truncated at " + MAX_FILE_LINES + " lines)");
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private String detectLanguage(String ext) {
        if (ext == null) return "TEXT";
        switch (ext.toLowerCase()) {
            case "js": return "JS";
            case "ts": return "TypeScript";
            case "py": return "Python";
            case "java": return "Java";
            case "kt": return "Kotlin";
            case "cpp": case "cc": return "C++";
            case "c": return "C";
            case "cs": return "C#";
            case "go": return "Go";
            case "rs": return "Rust";
            case "rb": return "Ruby";
            case "php": return "PHP";
            case "swift": return "Swift";
            case "dart": return "Dart";
            case "html": case "htm": return "HTML";
            case "css": return "CSS";
            case "json": return "JSON";
            case "xml": return "XML";
            case "sh": case "bash": return "Shell";
            case "sql": return "SQL";
            default: return ext.toUpperCase(Locale.getDefault());
        }
    }

    private String formatBytes(long bytes) {
        if (bytes < 0) return "?";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }
}
