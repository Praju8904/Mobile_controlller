package com.prajwal.myfirstapp.passwords;


import com.prajwal.myfirstapp.R;
import com.prajwal.myfirstapp.vault.SecurityScoreView;
import com.prajwal.myfirstapp.vault.VaultCryptoManager;
import com.prajwal.myfirstapp.notes.Note;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.view.HapticFeedbackConstants;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Full-featured Password Manager vault activity.
 * Handles authentication, vault list, add/edit, password generator,
 * health dashboard, import/export, trash, breach check, and auto-lock.
 */
public class PasswordManagerActivity extends AppCompatActivity {

    private static final String TAG = "PasswordMgr";
    private static final int IMPORT_CSV_REQUEST = 5001;
    private static final int EXPORT_FILE_REQUEST = 5002;
    private static final String PREFS_BIO = "vault_bio_prefs";
    private static final String KEY_BIO_ENABLED = "biometric_enabled";
    private static final String KEY_BIO_CREDENTIAL = "bio_credential";

    private PasswordRepository repo;
    private Handler autoLockHandler;
    private Runnable autoLockRunnable;
    private long lastInteractionTime;

    // Auth screen views
    private LinearLayout authScreen, mainScreen;
    private EditText etMasterPassword, etConfirmPassword;
    private Button btnAuthSubmit, btnBiometric;
    private TextView tvAuthTitle, tvAuthSubtitle;

    // Vault screen views
    private EditText etSearch;
    private LinearLayout filterChipsContainer;
    private ListView vaultListView;
    private LinearLayout emptyState;
    private Button fabAdd;
    private TextView tvVaultTitle;

    // State
    private String currentFilter = "All";
    private String currentSort = "recent";
    private ArrayList<PasswordEntry> displayedEntries = new ArrayList<>();
    private VaultAdapter adapter;
    private boolean showingTrash = false;
    private boolean showingHealth = false;

    // Password generator history for session
    private ArrayList<String> generatorHistory = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Prevent screenshots / app switcher preview
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE);

        setContentView(R.layout.activity_password_manager);

        repo = new PasswordRepository(this);
        autoLockHandler = new Handler(Looper.getMainLooper());
        autoLockRunnable = this::lockVault;

        initViews();
        setupAuthScreen();
    }

    // ═══════════════════════════════════════════════════════════════
    //  VIEW INIT
    // ═══════════════════════════════════════════════════════════════

    private void initViews() {
        authScreen = findViewById(R.id.vaultAuthScreen);
        mainScreen = findViewById(R.id.vaultMainScreen);
        etMasterPassword = findViewById(R.id.etMasterPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        btnAuthSubmit = findViewById(R.id.btnAuthSubmit);
        btnBiometric = findViewById(R.id.btnBiometric);
        tvAuthTitle = findViewById(R.id.tvAuthTitle);
        tvAuthSubtitle = findViewById(R.id.tvAuthSubtitle);

        etSearch = findViewById(R.id.etVaultSearch);
        filterChipsContainer = findViewById(R.id.vaultFilterChips);
        vaultListView = findViewById(R.id.vaultListView);
        emptyState = findViewById(R.id.vaultEmptyState);
        fabAdd = findViewById(R.id.fabAddPassword);
        tvVaultTitle = findViewById(R.id.tvVaultTitle);

        // Top bar buttons
        findViewById(R.id.btnVaultBack).setOnClickListener(v -> onBackPressed());
        findViewById(R.id.btnVaultHealth).setOnClickListener(v -> showHealthDashboard());
        findViewById(R.id.btnVaultTrash).setOnClickListener(v -> toggleTrashView());
        findViewById(R.id.btnVaultMore).setOnClickListener(v -> showMoreMenu());

        fabAdd.setOnClickListener(v -> showAddEditDialog(null));

        // Search
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) { refreshList(); }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    // ═══════════════════════════════════════════════════════════════
    //  AUTHENTICATION
    // ═══════════════════════════════════════════════════════════════

    private void setupAuthScreen() {
        boolean isSetup = !repo.isMasterPasswordSet();

        if (isSetup) {
            tvAuthTitle.setText("Create Vault");
            tvAuthSubtitle.setText("Set a master password to protect your vault");
            btnAuthSubmit.setText("Create Vault");
            etConfirmPassword.setVisibility(View.VISIBLE);
            btnBiometric.setVisibility(View.GONE);
        } else {
            tvAuthTitle.setText("Unlock Vault");
            tvAuthSubtitle.setText("Enter your master password");
            btnAuthSubmit.setText("Unlock");
            etConfirmPassword.setVisibility(View.GONE);

            if (isBiometricAvailable() && isBiometricEnabled()) {
                btnBiometric.setVisibility(View.VISIBLE);
                btnBiometric.setOnClickListener(v -> promptBiometric());
                // Auto-prompt biometric
                new Handler(Looper.getMainLooper()).postDelayed(this::promptBiometric, 400);
            }
        }

        btnAuthSubmit.setOnClickListener(v -> {
            String password = etMasterPassword.getText().toString();
            if (password.length() < 4) {
                Toast.makeText(this, "Password must be at least 4 characters", Toast.LENGTH_SHORT).show();
                return;
            }

            if (isSetup) {
                String confirm = etConfirmPassword.getText().toString();
                if (!password.equals(confirm)) {
                    Toast.makeText(this, "Passwords don't match", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (repo.setupMasterPassword(password)) {
                    saveBioCredential(password);
                    onVaultUnlocked();
                } else {
                    Toast.makeText(this, "Failed to create vault", Toast.LENGTH_SHORT).show();
                }
            } else {
                if (repo.verifyAndUnlock(password)) {
                    saveBioCredential(password);
                    onVaultUnlocked();
                } else {
                    Toast.makeText(this, "Incorrect password", Toast.LENGTH_SHORT).show();
                    etMasterPassword.setText("");
                }
            }
        });
    }

    private boolean isBiometricAvailable() {
        try {
            BiometricManager bm = BiometricManager.from(this);
            return bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG
                    | BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isBiometricEnabled() {
        return getSharedPreferences(PREFS_BIO, MODE_PRIVATE).contains(KEY_BIO_CREDENTIAL);
    }

    private void saveBioCredential(String password) {
        getSharedPreferences(PREFS_BIO, MODE_PRIVATE).edit()
                .putString(KEY_BIO_CREDENTIAL, password)
                .putBoolean(KEY_BIO_ENABLED, true)
                .apply();
    }

    private String getBioCredential() {
        return getSharedPreferences(PREFS_BIO, MODE_PRIVATE).getString(KEY_BIO_CREDENTIAL, null);
    }

    private void promptBiometric() {
        try {
            BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Unlock Password Vault")
                    .setSubtitle("Use your fingerprint or face to unlock")
                    .setNegativeButtonText("Use Password")
                    .build();

            BiometricPrompt biometricPrompt = new BiometricPrompt(this,
                    ContextCompat.getMainExecutor(this),
                    new BiometricPrompt.AuthenticationCallback() {
                        @Override
                        public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                            String credential = getBioCredential();
                            if (credential != null && repo.unlockWithBiometric(credential)) {
                                onVaultUnlocked();
                            } else {
                                Toast.makeText(PasswordManagerActivity.this,
                                        "Biometric unlock failed. Please use password.", Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                            if (errorCode != BiometricPrompt.ERROR_USER_CANCELED
                                    && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                                Toast.makeText(PasswordManagerActivity.this,
                                        errString, Toast.LENGTH_SHORT).show();
                            }
                        }
                    });

            biometricPrompt.authenticate(promptInfo);
        } catch (Exception e) {
            Log.e(TAG, "Biometric prompt failed", e);
        }
    }

    private void onVaultUnlocked() {
        authScreen.setVisibility(View.GONE);
        mainScreen.setVisibility(View.VISIBLE);
        showingTrash = false;
        showingHealth = false;
        setupFilterChips();
        refreshList();
        resetAutoLock();
    }

    private void lockVault() {
        repo.lock();
        mainScreen.setVisibility(View.GONE);
        authScreen.setVisibility(View.VISIBLE);
        etMasterPassword.setText("");
        setupAuthScreen();
    }

    // ═══════════════════════════════════════════════════════════════
    //  AUTO-LOCK
    // ═══════════════════════════════════════════════════════════════

    private void resetAutoLock() {
        lastInteractionTime = System.currentTimeMillis();
        autoLockHandler.removeCallbacks(autoLockRunnable);
        long lockMs = repo.getAutoLockMs();
        if (lockMs > 0) {
            autoLockHandler.postDelayed(autoLockRunnable, lockMs);
        }
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        if (repo.isUnlocked()) resetAutoLock();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Lock after a shorter timeout when app goes background
        if (repo.isUnlocked()) {
            autoLockHandler.removeCallbacks(autoLockRunnable);
            autoLockHandler.postDelayed(autoLockRunnable, 10000); // 10s when backgrounded
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (repo.isUnlocked()) {
            resetAutoLock();
            refreshList();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        autoLockHandler.removeCallbacks(autoLockRunnable);
        repo.lock();
    }

    // ═══════════════════════════════════════════════════════════════
    //  FILTER CHIPS
    // ═══════════════════════════════════════════════════════════════

    private void setupFilterChips() {
        filterChipsContainer.removeAllViews();
        String[] filters = {"All", "Favourites", "Weak", "Reused", "Recent", "Notes",
                "Social Media", "Banking", "Work", "Shopping", "Email", "Gaming", "Streaming", "Others"};

        for (String filter : filters) {
            TextView chip = new TextView(this);
            chip.setText(filter);
            chip.setTextSize(13);
            chip.setPadding(dp(14), dp(8), dp(14), dp(8));
            chip.setTextColor(filter.equals(currentFilter) ? Color.WHITE : Color.parseColor("#94A3B8"));
            chip.setBackground(getDrawable(filter.equals(currentFilter) ?
                    R.drawable.vault_chip_selected_bg : R.drawable.vault_chip_bg));

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMarginEnd(dp(8));
            chip.setLayoutParams(params);

            chip.setOnClickListener(v -> {
                currentFilter = filter;
                setupFilterChips();
                refreshList();
            });

            filterChipsContainer.addView(chip);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  LIST / REFRESH
    // ═══════════════════════════════════════════════════════════════

    private void refreshList() {
        if (!repo.isUnlocked()) return;

        String query = etSearch.getText().toString().trim();

        if (showingTrash) {
            displayedEntries = repo.getTrash();
            tvVaultTitle.setText("Trash");
        } else {
            switch (currentFilter) {
                case "Favourites":
                    displayedEntries = repo.getFavourites();
                    break;
                case "Weak":
                    displayedEntries = repo.getWeakPasswords();
                    break;
                case "Reused":
                    displayedEntries = repo.getReusedPasswords();
                    break;
                case "Recent":
                    displayedEntries = repo.getActiveEntries();
                    // already sorted by modifiedAt desc
                    if (displayedEntries.size() > 20)
                        displayedEntries = new ArrayList<>(displayedEntries.subList(0, 20));
                    break;
                case "Notes":
                    displayedEntries = repo.getSecureNotes();
                    break;
                default:
                    if (currentFilter.equals("All")) {
                        displayedEntries = query.isEmpty() ? repo.getActiveEntries() : repo.search(query);
                    } else {
                        // Category filter
                        displayedEntries = repo.getByCategory(currentFilter);
                    }
                    break;
            }
            tvVaultTitle.setText("Password Vault");

            // Apply search on top of filter
            if (!query.isEmpty() && !currentFilter.equals("All")) {
                String q = query.toLowerCase();
                ArrayList<PasswordEntry> filtered = new ArrayList<>();
                for (PasswordEntry e : displayedEntries) {
                    if ((e.siteName != null && e.siteName.toLowerCase().contains(q)) ||
                            (e.username != null && e.username.toLowerCase().contains(q))) {
                        filtered.add(e);
                    }
                }
                displayedEntries = filtered;
            }
        }

        // Sort
        sortEntries();

        // Update UI
        emptyState.setVisibility(displayedEntries.isEmpty() ? View.VISIBLE : View.GONE);
        vaultListView.setVisibility(displayedEntries.isEmpty() ? View.GONE : View.VISIBLE);

        adapter = new VaultAdapter(this, displayedEntries);
        vaultListView.setAdapter(adapter);
    }

    private void sortEntries() {
        switch (currentSort) {
            case "az":
                Collections.sort(displayedEntries, (a, b) -> {
                    String na = a.siteName != null ? a.siteName : "";
                    String nb = b.siteName != null ? b.siteName : "";
                    return na.compareToIgnoreCase(nb);
                });
                break;
            case "used":
                Collections.sort(displayedEntries, (a, b) -> Long.compare(b.lastUsedAt, a.lastUsedAt));
                break;
            default: // "recent" — by modifiedAt desc (already default from repo)
                break;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  VAULT ADAPTER
    // ═══════════════════════════════════════════════════════════════

    private class VaultAdapter extends ArrayAdapter<PasswordEntry> {
        VaultAdapter(Context ctx, ArrayList<PasswordEntry> entries) {
            super(ctx, 0, entries);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            PasswordEntry entry = getItem(position);
            if (entry == null) return new View(getContext());

            LinearLayout card = new LinearLayout(getContext());
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(getDrawable(R.drawable.vault_item_bg));
            card.setPadding(dp(16), dp(14), dp(16), dp(14));
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cardParams.setMargins(0, 0, 0, dp(10));
            card.setLayoutParams(cardParams);

            // Top row: Icon + Name + Strength indicator
            LinearLayout topRow = new LinearLayout(getContext());
            topRow.setOrientation(LinearLayout.HORIZONTAL);
            topRow.setGravity(Gravity.CENTER_VERTICAL);

            // Category icon
            TextView icon = new TextView(getContext());
            icon.setText("secure_note".equals(entry.type) ? "📝" : PasswordEntry.getCategoryIcon(entry.category));
            icon.setTextSize(28);
            icon.setPadding(0, 0, dp(12), 0);
            topRow.addView(icon);

            // Name + Username column
            LinearLayout nameCol = new LinearLayout(getContext());
            nameCol.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            nameCol.setLayoutParams(nameParams);

            TextView tvName = new TextView(getContext());
            tvName.setText(entry.siteName != null ? entry.siteName : "Untitled");
            tvName.setTextColor(Color.parseColor("#F1F5F9"));
            tvName.setTextSize(16);
            tvName.setTypeface(null, Typeface.BOLD);
            tvName.setSingleLine(true);
            nameCol.addView(tvName);

            if ("password".equals(entry.type) && entry.username != null && !entry.username.isEmpty()) {
                TextView tvUser = new TextView(getContext());
                tvUser.setText(entry.username);
                tvUser.setTextColor(Color.parseColor("#64748B"));
                tvUser.setTextSize(13);
                tvUser.setSingleLine(true);
                nameCol.addView(tvUser);
            }

            topRow.addView(nameCol);

            // Strength indicator (colored dot)
            if ("password".equals(entry.type)) {
                View dot = new View(getContext());
                int dotSize = dp(10);
                LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dotSize, dotSize);
                dotParams.setMarginStart(dp(8));
                dot.setLayoutParams(dotParams);
                dot.setBackgroundColor(entry.getStrengthColor());
                // Make it round-ish
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    dot.setClipToOutline(true);
                    dot.setBackground(new android.graphics.drawable.GradientDrawable() {{
                        setShape(OVAL);
                        setColor(entry.getStrengthColor());
                    }});
                }
                topRow.addView(dot);
            }

            // Favourite star
            if (entry.isFavourite) {
                TextView star = new TextView(getContext());
                star.setText("⭐");
                star.setTextSize(14);
                star.setPadding(dp(6), 0, 0, 0);
                topRow.addView(star);
            }

            card.addView(topRow);

            // Password row (masked + reveal toggle)
            if ("password".equals(entry.type) && entry.password != null && !entry.password.isEmpty()) {
                LinearLayout passRow = new LinearLayout(getContext());
                passRow.setOrientation(LinearLayout.HORIZONTAL);
                passRow.setGravity(Gravity.CENTER_VERTICAL);
                passRow.setPadding(0, dp(8), 0, 0);

                TextView tvPass = new TextView(getContext());
                tvPass.setText("••••••••••");
                tvPass.setTextColor(Color.parseColor("#475569"));
                tvPass.setTextSize(14);
                LinearLayout.LayoutParams passParams = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                tvPass.setLayoutParams(passParams);
                passRow.addView(tvPass);

                // Reveal toggle
                Button btnReveal = createActionBtn("👁");
                btnReveal.setOnClickListener(v -> {
                    boolean revealed = tvPass.getTag() != null && (boolean) tvPass.getTag();
                    if (revealed) {
                        tvPass.setText("••••••••••");
                        tvPass.setTag(false);
                    } else {
                        tvPass.setText(entry.password);
                        tvPass.setTag(true);
                        // Auto-hide after 5 seconds
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            tvPass.setText("••••••••••");
                            tvPass.setTag(false);
                        }, 5000);
                    }
                });
                passRow.addView(btnReveal);

                card.addView(passRow);
            }

            // Action buttons row
            LinearLayout actionRow = new LinearLayout(getContext());
            actionRow.setOrientation(LinearLayout.HORIZONTAL);
            actionRow.setGravity(Gravity.END);
            actionRow.setPadding(0, dp(8), 0, 0);

            if (showingTrash) {
                Button btnRestore = createActionBtn("♻️ Restore");
                btnRestore.setOnClickListener(v -> {
                    repo.restoreFromTrash(entry.id);
                    Toast.makeText(PasswordManagerActivity.this, "Restored", Toast.LENGTH_SHORT).show();
                    refreshList();
                });
                actionRow.addView(btnRestore);

                Button btnPermDelete = createActionBtn("🗑️ Delete");
                btnPermDelete.setTextColor(Color.parseColor("#EF4444"));
                btnPermDelete.setOnClickListener(v -> confirmAction("Permanently delete?",
                        () -> { repo.permanentDelete(entry.id); refreshList(); }));
                actionRow.addView(btnPermDelete);
            } else {
                if ("password".equals(entry.type)) {
                    Button btnCopyUser = createActionBtn("📋 User");
                    btnCopyUser.setOnClickListener(v -> {
                        copyToClipboard("Username", entry.username);
                        v.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
                    });
                    actionRow.addView(btnCopyUser);

                    Button btnCopyPass = createActionBtn("🔑 Pass");
                    btnCopyPass.setOnClickListener(v -> {
                        copyToClipboard("Password", entry.password);
                        repo.markUsed(entry.id);
                        v.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
                    });
                    actionRow.addView(btnCopyPass);

                    if (entry.siteUrl != null && !entry.siteUrl.isEmpty()) {
                        Button btnOpen = createActionBtn("🌐");
                        btnOpen.setOnClickListener(v -> {
                            String url = entry.siteUrl;
                            if (!url.startsWith("http")) url = "https://" + url;
                            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
                            catch (Exception ex) { Toast.makeText(PasswordManagerActivity.this, "Can't open URL", Toast.LENGTH_SHORT).show(); }
                        });
                        actionRow.addView(btnOpen);
                    }
                }

                Button btnFav = createActionBtn(entry.isFavourite ? "★" : "☆");
                btnFav.setTextColor(entry.isFavourite ? Color.parseColor("#F59E0B") : Color.parseColor("#64748B"));
                btnFav.setOnClickListener(v -> {
                    repo.toggleFavourite(entry.id);
                    refreshList();
                });
                actionRow.addView(btnFav);

                Button btnEdit = createActionBtn("✏️");
                btnEdit.setOnClickListener(v -> showAddEditDialog(entry));
                actionRow.addView(btnEdit);

                Button btnDelete = createActionBtn("🗑");
                btnDelete.setOnClickListener(v -> confirmAction("Move to trash?",
                        () -> { repo.softDelete(entry.id); refreshList();
                            Toast.makeText(PasswordManagerActivity.this, "Moved to trash", Toast.LENGTH_SHORT).show(); }));
                actionRow.addView(btnDelete);
            }

            card.addView(actionRow);

            return card;
        }
    }

    private Button createActionBtn(String text) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextSize(12);
        btn.setTextColor(Color.parseColor("#94A3B8"));
        btn.setBackground(null);
        btn.setPadding(dp(8), dp(4), dp(8), dp(4));
        btn.setAllCaps(false);
        btn.setMinWidth(0);
        btn.setMinimumWidth(0);
        btn.setMinHeight(0);
        btn.setMinimumHeight(0);
        return btn;
    }

    // ═══════════════════════════════════════════════════════════════
    //  ADD / EDIT DIALOG
    // ═══════════════════════════════════════════════════════════════

    private void showAddEditDialog(@Nullable PasswordEntry existing) {
        boolean isEdit = existing != null;
        boolean isNote = isEdit && "secure_note".equals(existing.type);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(Color.parseColor("#0F172A"));
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(24), dp(20), dp(24), dp(20));
        scrollView.addView(layout);

        // Type selector for new entries
        final boolean[] isSecureNote = {isNote};

        if (!isEdit) {
            LinearLayout typeRow = new LinearLayout(this);
            typeRow.setOrientation(LinearLayout.HORIZONTAL);
            typeRow.setGravity(Gravity.CENTER);
            typeRow.setPadding(0, 0, 0, dp(16));

            Button btnTypePassword = new Button(this);
            btnTypePassword.setText("🔑 Password");
            btnTypePassword.setTextColor(Color.WHITE);
            btnTypePassword.setTextSize(14);
            btnTypePassword.setAllCaps(false);
            btnTypePassword.setBackgroundColor(Color.parseColor("#00D4AA"));
            btnTypePassword.setPadding(dp(16), dp(8), dp(16), dp(8));

            Button btnTypeNote = new Button(this);
            btnTypeNote.setText("📝 Secure Note");
            btnTypeNote.setTextColor(Color.parseColor("#94A3B8"));
            btnTypeNote.setTextSize(14);
            btnTypeNote.setAllCaps(false);
            btnTypeNote.setBackgroundColor(Color.parseColor("#1E293B"));
            btnTypeNote.setPadding(dp(16), dp(8), dp(16), dp(8));

            typeRow.addView(btnTypePassword);
            typeRow.addView(btnTypeNote);
            layout.addView(typeRow);

            // Will toggle visibility of fields below
            btnTypePassword.setOnClickListener(v -> {
                isSecureNote[0] = false;
                btnTypePassword.setBackgroundColor(Color.parseColor("#00D4AA"));
                btnTypePassword.setTextColor(Color.WHITE);
                btnTypeNote.setBackgroundColor(Color.parseColor("#1E293B"));
                btnTypeNote.setTextColor(Color.parseColor("#94A3B8"));
            });
            btnTypeNote.setOnClickListener(v -> {
                isSecureNote[0] = true;
                btnTypeNote.setBackgroundColor(Color.parseColor("#00D4AA"));
                btnTypeNote.setTextColor(Color.WHITE);
                btnTypePassword.setBackgroundColor(Color.parseColor("#1E293B"));
                btnTypePassword.setTextColor(Color.parseColor("#94A3B8"));
            });
        }

        // Fields
        EditText etSiteName = createField(layout, "Site / App Name", isEdit ? existing.siteName : "");
        EditText etUrl = createField(layout, "URL", isEdit ? existing.siteUrl : "");
        EditText etUsername = createField(layout, "Username / Email", isEdit ? existing.username : "");

        // Password field with generator
        EditText etPassword = createField(layout, "Password", isEdit ? existing.password : "");
        etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        // Strength meter
        TextView tvStrength = new TextView(this);
        tvStrength.setTextSize(12);
        tvStrength.setPadding(0, dp(2), 0, dp(8));
        layout.addView(tvStrength);

        ProgressBar strengthBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        strengthBar.setMax(100);
        strengthBar.setPadding(0, 0, 0, dp(8));
        layout.addView(strengthBar);

        etPassword.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                int strength = VaultCryptoManager.calculateStrength(s.toString());
                strengthBar.setProgress(strength);
                tvStrength.setText(VaultCryptoManager.getStrengthLabel(strength) + " (" + strength + "/100)");
                tvStrength.setTextColor(VaultCryptoManager.getStrengthColor(strength));
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        // Generate Password button
        Button btnGenerate = new Button(this);
        btnGenerate.setText("⚡ Generate Strong Password");
        btnGenerate.setTextColor(Color.parseColor("#00D4AA"));
        btnGenerate.setBackgroundColor(Color.parseColor("#1E293B"));
        btnGenerate.setAllCaps(false);
        btnGenerate.setTextSize(14);
        btnGenerate.setPadding(dp(16), dp(10), dp(16), dp(10));
        layout.addView(btnGenerate);

        btnGenerate.setOnClickListener(v -> showGeneratorDialog(password -> {
            etPassword.setText(password);
            etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        }));

        // Spacer
        View spacer1 = new View(this);
        spacer1.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(12)));
        layout.addView(spacer1);

        // Category spinner
        TextView catLabel = new TextView(this);
        catLabel.setText("Category");
        catLabel.setTextColor(Color.parseColor("#94A3B8"));
        catLabel.setTextSize(13);
        catLabel.setPadding(0, 0, 0, dp(4));
        layout.addView(catLabel);

        Spinner spCategory = new Spinner(this);
        ArrayAdapter<String> catAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, PasswordEntry.CATEGORIES);
        spCategory.setAdapter(catAdapter);
        spCategory.setBackgroundColor(Color.parseColor("#1E293B"));
        if (isEdit && existing.category != null) {
            for (int i = 0; i < PasswordEntry.CATEGORIES.length; i++) {
                if (PasswordEntry.CATEGORIES[i].equals(existing.category)) {
                    spCategory.setSelection(i);
                    break;
                }
            }
        }
        layout.addView(spCategory);

        View spacer2 = new View(this);
        spacer2.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(12)));
        layout.addView(spacer2);

        // Notes
        EditText etNotes = createField(layout, "Notes (security questions, PINs, etc.)", isEdit ? existing.notes : "");
        etNotes.setMinLines(3);
        etNotes.setGravity(Gravity.TOP);
        etNotes.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);

        // Trigger strength display for existing
        if (isEdit && existing.password != null) {
            int str = VaultCryptoManager.calculateStrength(existing.password);
            strengthBar.setProgress(str);
            tvStrength.setText(VaultCryptoManager.getStrengthLabel(str) + " (" + str + "/100)");
            tvStrength.setTextColor(VaultCryptoManager.getStrengthColor(str));
        }

        AlertDialog dialog = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog)
                .setTitle(isEdit ? "Edit Entry" : "Add New Entry")
                .setView(scrollView)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.parseColor("#0F172A")));

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.parseColor("#00D4AA"));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.parseColor("#94A3B8"));

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String siteName = etSiteName.getText().toString().trim();
                if (siteName.isEmpty()) {
                    Toast.makeText(this, "Name is required", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (isEdit) {
                    existing.siteName = siteName;
                    existing.siteUrl = etUrl.getText().toString().trim();
                    existing.username = etUsername.getText().toString().trim();
                    existing.password = etPassword.getText().toString();
                    existing.category = PasswordEntry.CATEGORIES[spCategory.getSelectedItemPosition()];
                    existing.notes = etNotes.getText().toString();
                    repo.updateEntry(existing);
                    Toast.makeText(this, "Updated", Toast.LENGTH_SHORT).show();
                } else {
                    if (isSecureNote[0]) {
                        PasswordEntry note = PasswordEntry.createSecureNote(
                                siteName, etNotes.getText().toString(),
                                PasswordEntry.CATEGORIES[spCategory.getSelectedItemPosition()]);
                        repo.addEntry(note);
                    } else {
                        PasswordEntry entry = PasswordEntry.createPassword(
                                siteName,
                                etUrl.getText().toString().trim(),
                                etUsername.getText().toString().trim(),
                                etPassword.getText().toString(),
                                PasswordEntry.CATEGORIES[spCategory.getSelectedItemPosition()],
                                etNotes.getText().toString());
                        repo.addEntry(entry);
                    }
                    Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
                }
                refreshList();
                dialog.dismiss();
            });
        });

        dialog.show();
    }

    private EditText createField(LinearLayout parent, String hint, String value) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setHintTextColor(Color.parseColor("#475569"));
        et.setTextColor(Color.parseColor("#F1F5F9"));
        et.setTextSize(15);
        et.setBackground(getDrawable(R.drawable.vault_input_bg));
        et.setPadding(dp(14), dp(12), dp(14), dp(12));
        et.setSingleLine(true);
        if (value != null) et.setText(value);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(10));
        et.setLayoutParams(lp);

        parent.addView(et);
        return et;
    }

    // ═══════════════════════════════════════════════════════════════
    //  PASSWORD GENERATOR DIALOG
    // ═══════════════════════════════════════════════════════════════

    interface OnPasswordGenerated {
        void onGenerated(String password);
    }

    private void showGeneratorDialog(@Nullable OnPasswordGenerated callback) {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(Color.parseColor("#0F172A"));
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(24), dp(20), dp(24), dp(20));
        scrollView.addView(layout);

        // Length slider
        TextView tvLength = new TextView(this);
        tvLength.setText("Length: 16");
        tvLength.setTextColor(Color.parseColor("#F1F5F9"));
        tvLength.setTextSize(15);
        layout.addView(tvLength);

        SeekBar seekLength = new SeekBar(this);
        seekLength.setMax(56); // 8–64
        seekLength.setProgress(8); // default 16
        layout.addView(seekLength);

        // Options
        CheckBox cbUpper = new CheckBox(this);
        cbUpper.setText("Uppercase (A-Z)");
        cbUpper.setTextColor(Color.parseColor("#CBD5E1"));
        cbUpper.setChecked(true);
        layout.addView(cbUpper);

        CheckBox cbLower = new CheckBox(this);
        cbLower.setText("Lowercase (a-z)");
        cbLower.setTextColor(Color.parseColor("#CBD5E1"));
        cbLower.setChecked(true);
        layout.addView(cbLower);

        CheckBox cbNumbers = new CheckBox(this);
        cbNumbers.setText("Numbers (0-9)");
        cbNumbers.setTextColor(Color.parseColor("#CBD5E1"));
        cbNumbers.setChecked(true);
        layout.addView(cbNumbers);

        CheckBox cbSymbols = new CheckBox(this);
        cbSymbols.setText("Symbols (!@#$...)");
        cbSymbols.setTextColor(Color.parseColor("#CBD5E1"));
        cbSymbols.setChecked(true);
        layout.addView(cbSymbols);

        // Generated password display
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(16)));
        layout.addView(spacer);

        TextView tvGenerated = new TextView(this);
        tvGenerated.setTextColor(Color.parseColor("#00D4AA"));
        tvGenerated.setTextSize(18);
        tvGenerated.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        tvGenerated.setPadding(dp(12), dp(12), dp(12), dp(12));
        tvGenerated.setBackground(getDrawable(R.drawable.vault_input_bg));
        tvGenerated.setTextIsSelectable(true);
        layout.addView(tvGenerated);

        // Strength display
        TextView tvStrength = new TextView(this);
        tvStrength.setTextSize(12);
        tvStrength.setPadding(0, dp(6), 0, dp(8));
        layout.addView(tvStrength);

        // Generate button
        Button btnGen = new Button(this);
        btnGen.setText("🔄 Generate");
        btnGen.setTextColor(Color.WHITE);
        btnGen.setBackgroundColor(Color.parseColor("#00D4AA"));
        btnGen.setAllCaps(false);
        btnGen.setTextSize(15);
        layout.addView(btnGen);

        // Copy button
        Button btnCopy = new Button(this);
        btnCopy.setText("📋 Copy to Clipboard");
        btnCopy.setTextColor(Color.parseColor("#00D4AA"));
        btnCopy.setBackgroundColor(Color.parseColor("#1E293B"));
        btnCopy.setAllCaps(false);
        btnCopy.setTextSize(14);
        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        copyLp.setMargins(0, dp(8), 0, 0);
        btnCopy.setLayoutParams(copyLp);
        layout.addView(btnCopy);

        // History section
        if (!generatorHistory.isEmpty()) {
            TextView tvHistLabel = new TextView(this);
            tvHistLabel.setText("Recent Generations:");
            tvHistLabel.setTextColor(Color.parseColor("#64748B"));
            tvHistLabel.setTextSize(12);
            tvHistLabel.setPadding(0, dp(16), 0, dp(4));
            layout.addView(tvHistLabel);

            for (int i = generatorHistory.size() - 1; i >= Math.max(0, generatorHistory.size() - 10); i--) {
                TextView tvHist = new TextView(this);
                tvHist.setText(generatorHistory.get(i));
                tvHist.setTextColor(Color.parseColor("#475569"));
                tvHist.setTextSize(12);
                tvHist.setTypeface(Typeface.MONOSPACE);
                final String histPw = generatorHistory.get(i);
                tvHist.setOnClickListener(v -> copyToClipboard("Password", histPw));
                layout.addView(tvHist);
            }
        }

        seekLength.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) {
                tvLength.setText("Length: " + (p + 8));
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });

        Runnable generate = () -> {
            int len = seekLength.getProgress() + 8;
            String pw = VaultCryptoManager.generatePassword(len,
                    cbUpper.isChecked(), cbLower.isChecked(),
                    cbNumbers.isChecked(), cbSymbols.isChecked());
            tvGenerated.setText(pw);
            int str = VaultCryptoManager.calculateStrength(pw);
            tvStrength.setText(VaultCryptoManager.getStrengthLabel(str) + " (" + str + "/100)");
            tvStrength.setTextColor(VaultCryptoManager.getStrengthColor(str));
            generatorHistory.add(pw);
            if (generatorHistory.size() > 10) generatorHistory.remove(0);
        };

        // Initial generation
        generate.run();
        btnGen.setOnClickListener(v -> generate.run());

        btnCopy.setOnClickListener(v -> {
            String pw = tvGenerated.getText().toString();
            if (!pw.isEmpty()) {
                copyToClipboard("Password", pw);
                v.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
            }
        });

        AlertDialog.Builder builder = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog)
                .setTitle("Password Generator")
                .setView(scrollView)
                .setNegativeButton("Close", null);

        if (callback != null) {
            builder.setPositiveButton("Use Password", (d, w) -> {
                callback.onGenerated(tvGenerated.getText().toString());
            });
        }

        AlertDialog dialog = builder.create();
        dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.parseColor("#0F172A")));
        dialog.show();

        if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.parseColor("#00D4AA"));
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.parseColor("#94A3B8"));
    }

    // ═══════════════════════════════════════════════════════════════
    //  HEALTH DASHBOARD
    // ═══════════════════════════════════════════════════════════════

    private void showHealthDashboard() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(Color.parseColor("#0A0E1A"));
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(24), dp(20), dp(24), dp(20));
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        scrollView.addView(layout);

        // Security Score Ring
        SecurityScoreView scoreView = new SecurityScoreView(this);
        LinearLayout.LayoutParams scoreLp = new LinearLayout.LayoutParams(dp(180), dp(180));
        scoreLp.gravity = Gravity.CENTER_HORIZONTAL;
        scoreLp.setMargins(0, 0, 0, dp(20));
        scoreView.setLayoutParams(scoreLp);
        layout.addView(scoreView);

        int score = repo.getSecurityScore();
        scoreView.setScore(score);

        // Stats cards
        addHealthStat(layout, "Total Passwords", String.valueOf(repo.getPasswords().size()), "#3B82F6");
        addHealthStat(layout, "Weak Passwords", String.valueOf(repo.getWeakCount()), "#EF4444");
        addHealthStat(layout, "Reused Passwords", String.valueOf(repo.getReusedCount()), "#F59E0B");
        addHealthStat(layout, "Old Passwords (6+ months)", String.valueOf(repo.getOldCount()), "#8B5CF6");
        addHealthStat(layout, "Secure Notes", String.valueOf(repo.getSecureNotes().size()), "#06B6D4");
        addHealthStat(layout, "Items in Trash", String.valueOf(repo.getTrash().size()), "#64748B");

        // Weak passwords list
        ArrayList<PasswordEntry> weakList = repo.getWeakPasswords();
        if (!weakList.isEmpty()) {
            TextView weakLabel = new TextView(this);
            weakLabel.setText("⚠️ Weak Passwords — Update These:");
            weakLabel.setTextColor(Color.parseColor("#EF4444"));
            weakLabel.setTextSize(15);
            weakLabel.setTypeface(null, Typeface.BOLD);
            weakLabel.setPadding(0, dp(20), 0, dp(8));
            layout.addView(weakLabel);

            for (PasswordEntry e : weakList) {
                TextView item = new TextView(this);
                item.setText("• " + e.siteName + " (" + e.getStrengthLabel() + ")");
                item.setTextColor(Color.parseColor("#CBD5E1"));
                item.setTextSize(14);
                item.setPadding(0, dp(4), 0, dp(4));
                item.setOnClickListener(v -> {
                    // Jump to edit
                    showAddEditDialog(e);
                });
                layout.addView(item);
            }
        }

        // Reused passwords
        ArrayList<PasswordEntry> reusedList = repo.getReusedPasswords();
        if (!reusedList.isEmpty()) {
            TextView reusedLabel = new TextView(this);
            reusedLabel.setText("🔁 Reused Passwords:");
            reusedLabel.setTextColor(Color.parseColor("#F59E0B"));
            reusedLabel.setTextSize(15);
            reusedLabel.setTypeface(null, Typeface.BOLD);
            reusedLabel.setPadding(0, dp(16), 0, dp(8));
            layout.addView(reusedLabel);

            for (PasswordEntry e : reusedList) {
                TextView item = new TextView(this);
                item.setText("• " + e.siteName);
                item.setTextColor(Color.parseColor("#CBD5E1"));
                item.setTextSize(14);
                item.setPadding(0, dp(4), 0, dp(4));
                item.setOnClickListener(v -> showAddEditDialog(e));
                layout.addView(item);
            }
        }

        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog)
                .setTitle("🛡️ Password Health")
                .setView(scrollView)
                .setPositiveButton("Close", null)
                .show();
    }

    private void addHealthStat(LinearLayout parent, String label, String value, String colorHex) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(getDrawable(R.drawable.vault_item_bg));
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.setMargins(0, 0, 0, dp(8));
        row.setLayoutParams(rowLp);

        TextView tvLabel = new TextView(this);
        tvLabel.setText(label);
        tvLabel.setTextColor(Color.parseColor("#CBD5E1"));
        tvLabel.setTextSize(14);
        tvLabel.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(tvLabel);

        TextView tvValue = new TextView(this);
        tvValue.setText(value);
        tvValue.setTextColor(Color.parseColor(colorHex));
        tvValue.setTextSize(18);
        tvValue.setTypeface(null, Typeface.BOLD);
        row.addView(tvValue);

        parent.addView(row);
    }

    // ═══════════════════════════════════════════════════════════════
    //  TRASH VIEW
    // ═══════════════════════════════════════════════════════════════

    private void toggleTrashView() {
        showingTrash = !showingTrash;
        if (showingTrash) {
            currentFilter = "All";
            fabAdd.setVisibility(View.GONE);
        } else {
            fabAdd.setVisibility(View.VISIBLE);
        }
        setupFilterChips();
        refreshList();
    }

    // ═══════════════════════════════════════════════════════════════
    //  MORE MENU (Sort, Import, Export, Generator, Breach Check, Settings)
    // ═══════════════════════════════════════════════════════════════

    private void showMoreMenu() {
        String[] items = {
                "🔤 Sort: A-Z",
                "🕐 Sort: Recently Used",
                "📅 Sort: Date Added",
                "⚡ Password Generator",
                "📥 Import from CSV",
                "📤 Export as Encrypted JSON",
                "📤 Export as CSV (Unencrypted!)",
                "🔍 Check for Breaches",
                "🔑 Change Master Password",
                "⏱️ Auto-Lock Settings",
                "⚠️ Clear Entire Vault"
        };

        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog)
                .setTitle("Vault Options")
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0: currentSort = "az"; refreshList(); break;
                        case 1: currentSort = "used"; refreshList(); break;
                        case 2: currentSort = "recent"; refreshList(); break;
                        case 3: showGeneratorDialog(null); break;
                        case 4: importCsv(); break;
                        case 5: exportEncryptedJson(); break;
                        case 6: exportCsv(); break;
                        case 7: checkBreaches(); break;
                        case 8: showChangeMasterPasswordDialog(); break;
                        case 9: showAutoLockSettings(); break;
                        case 10: confirmAction("This will permanently delete ALL vault data. Are you sure?",
                                () -> { repo.clearVault(); refreshList(); }); break;
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ═══════════════════════════════════════════════════════════════
    //  IMPORT / EXPORT
    // ═══════════════════════════════════════════════════════════════

    private void importCsv() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("text/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "Select CSV File"), IMPORT_CSV_REQUEST);
    }

    private void exportEncryptedJson() {
        String encrypted = repo.exportToEncryptedJson();
        if (encrypted == null) {
            Toast.makeText(this, "Export failed", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "vault_backup_" + System.currentTimeMillis() + ".json");
        startActivityForResult(intent, EXPORT_FILE_REQUEST);

        // Store export data temporarily
        getSharedPreferences("vault_temp", MODE_PRIVATE).edit()
                .putString("pending_export", encrypted)
                .putString("pending_export_type", "json")
                .apply();
    }

    private void exportCsv() {
        confirmAction("⚠️ CSV export is UNENCRYPTED. Your passwords will be in plain text. Continue?", () -> {
            String csv = repo.exportToCsv();
            if (csv == null) {
                Toast.makeText(this, "Export failed", Toast.LENGTH_SHORT).show();
                return;
            }

            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("text/csv");
            intent.putExtra(Intent.EXTRA_TITLE, "vault_export_" + System.currentTimeMillis() + ".csv");
            startActivityForResult(intent, EXPORT_FILE_REQUEST);

            getSharedPreferences("vault_temp", MODE_PRIVATE).edit()
                    .putString("pending_export", csv)
                    .putString("pending_export_type", "csv")
                    .apply();
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;

        if (requestCode == IMPORT_CSV_REQUEST) {
            try {
                InputStream is = getContentResolver().openInputStream(data.getData());
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line).append("\n");
                reader.close();

                int count = repo.importFromCsv(sb.toString());
                Toast.makeText(this, "Imported " + count + " passwords", Toast.LENGTH_SHORT).show();
                refreshList();
            } catch (Exception e) {
                Toast.makeText(this, "Import failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == EXPORT_FILE_REQUEST) {
            try {
                SharedPreferences temp = getSharedPreferences("vault_temp", MODE_PRIVATE);
                String exportData = temp.getString("pending_export", null);
                if (exportData != null) {
                    OutputStream os = getContentResolver().openOutputStream(data.getData());
                    os.write(exportData.getBytes());
                    os.close();
                    Toast.makeText(this, "Exported successfully", Toast.LENGTH_SHORT).show();
                    temp.edit().remove("pending_export").remove("pending_export_type").apply();
                }
            } catch (Exception e) {
                Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  BREACH CHECK (HaveIBeenPwned)
    // ═══════════════════════════════════════════════════════════════

    private void checkBreaches() {
        ArrayList<PasswordEntry> passwords = repo.getPasswords();
        if (passwords.isEmpty()) {
            Toast.makeText(this, "No passwords to check", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Checking " + passwords.size() + " passwords...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            ArrayList<String> breached = new ArrayList<>();
            for (PasswordEntry entry : passwords) {
                if (entry.password == null || entry.password.isEmpty()) continue;
                try {
                    String prefix = VaultCryptoManager.getBreachCheckPrefix(entry.password);
                    String suffix = VaultCryptoManager.getBreachCheckSuffix(entry.password);
                    if (prefix == null || suffix == null) continue;

                    URL url = new URL("https://api.pwnedpasswords.com/range/" + prefix);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);

                    if (conn.getResponseCode() == 200) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.toUpperCase().startsWith(suffix)) {
                                breached.add(entry.siteName + " — " +
                                        line.split(":")[1].trim() + " breaches");
                                break;
                            }
                        }
                        reader.close();
                    }
                    conn.disconnect();

                    Thread.sleep(200); // Rate limiting
                } catch (Exception e) {
                    Log.e(TAG, "Breach check failed for " + entry.siteName, e);
                }
            }

            runOnUiThread(() -> {
                if (breached.isEmpty()) {
                    new AlertDialog.Builder(this)
                            .setTitle("✅ All Clear!")
                            .setMessage("None of your passwords appear in known data breaches.")
                            .setPositiveButton("Great!", null)
                            .show();
                } else {
                    StringBuilder msg = new StringBuilder("The following passwords were found in data breaches:\n\n");
                    for (String b : breached) msg.append("⚠️ ").append(b).append("\n");
                    msg.append("\nWe strongly recommend changing these passwords immediately.");

                    new AlertDialog.Builder(this)
                            .setTitle("⚠️ Breached Passwords Found!")
                            .setMessage(msg.toString())
                            .setPositiveButton("OK", null)
                            .show();
                }
            });
        }).start();
    }

    // ═══════════════════════════════════════════════════════════════
    //  CHANGE MASTER PASSWORD
    // ═══════════════════════════════════════════════════════════════

    private void showChangeMasterPasswordDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(24), dp(20), dp(24), dp(10));

        EditText etOld = new EditText(this);
        etOld.setHint("Current Password");
        etOld.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        etOld.setTextColor(Color.parseColor("#F1F5F9"));
        etOld.setHintTextColor(Color.parseColor("#475569"));
        layout.addView(etOld);

        EditText etNew = new EditText(this);
        etNew.setHint("New Password");
        etNew.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        etNew.setTextColor(Color.parseColor("#F1F5F9"));
        etNew.setHintTextColor(Color.parseColor("#475569"));
        layout.addView(etNew);

        EditText etConfirm = new EditText(this);
        etConfirm.setHint("Confirm New Password");
        etConfirm.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        etConfirm.setTextColor(Color.parseColor("#F1F5F9"));
        etConfirm.setHintTextColor(Color.parseColor("#475569"));
        layout.addView(etConfirm);

        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog)
                .setTitle("Change Master Password")
                .setView(layout)
                .setPositiveButton("Change", (d, w) -> {
                    String oldPw = etOld.getText().toString();
                    String newPw = etNew.getText().toString();
                    String confirmPw = etConfirm.getText().toString();

                    if (newPw.length() < 4) {
                        Toast.makeText(this, "New password must be at least 4 characters", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!newPw.equals(confirmPw)) {
                        Toast.makeText(this, "Passwords don't match", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (repo.changeMasterPassword(oldPw, newPw)) {
                        saveBioCredential(newPw);
                        Toast.makeText(this, "Master password changed", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Failed — incorrect current password", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ═══════════════════════════════════════════════════════════════
    //  AUTO-LOCK SETTINGS
    // ═══════════════════════════════════════════════════════════════

    private void showAutoLockSettings() {
        String[] options = {"30 seconds", "1 minute (Default)", "2 minutes", "5 minutes", "Never"};
        long[] values = {30000, 60000, 120000, 300000, 0};

        long current = repo.getAutoLockMs();
        int selected = 1;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) { selected = i; break; }
        }

        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog)
                .setTitle("Auto-Lock Timeout")
                .setSingleChoiceItems(options, selected, (d, which) -> {
                    repo.setAutoLockMs(values[which]);
                    resetAutoLock();
                    Toast.makeText(this, "Auto-lock updated", Toast.LENGTH_SHORT).show();
                    d.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ═══════════════════════════════════════════════════════════════
    //  UTILITIES
    // ═══════════════════════════════════════════════════════════════

    private void copyToClipboard(String label, String text) {
        if (text == null || text.isEmpty()) {
            Toast.makeText(this, "Nothing to copy", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text));
        Toast.makeText(this, label + " copied", Toast.LENGTH_SHORT).show();

        // Auto-clear clipboard after 30 seconds
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    clipboard.clearPrimaryClip();
                }
            } catch (Exception ignored) {}
        }, 30000);
    }

    private void confirmAction(String message, Runnable action) {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog)
                .setTitle("Confirm")
                .setMessage(message)
                .setPositiveButton("Yes", (d, w) -> action.run())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }

    @Override
    public void onBackPressed() {
        if (showingTrash) {
            toggleTrashView();
        } else {
            super.onBackPressed();
        }
    }
}
