package com.prajwal.myfirstapp.vault;


import com.prajwal.myfirstapp.R;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Vault Activity Log screen — shows an audit log of vault access events.
 */
public class VaultActivityLogActivity extends AppCompatActivity {

    private MediaVaultRepository repo;
    private ActivityLogAdapter adapter;
    private ListView activityLogList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE);

        setContentView(R.layout.activity_vault_activity_log);

        repo = MediaVaultRepository.getInstance(this);
        if (!repo.isUnlocked()) { finish(); return; }

        activityLogList = findViewById(R.id.activityLogList);
        activityLogList.setBackgroundColor(Color.parseColor("#0A0E21"));

        loadLog();

        findViewById(R.id.btnActivityLogBack).setOnClickListener(v -> onBackPressed());
        findViewById(R.id.btnClearLog).setOnClickListener(v -> confirmClearLog());
    }

    private void loadLog() {
        List<VaultActivityLog> log = repo.getActivityLog();
        adapter = new ActivityLogAdapter(this, log);
        activityLogList.setAdapter(adapter);
    }

    private void confirmClearLog() {
        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("Clear Activity Log")
                .setMessage("All vault activity history will be permanently deleted. Continue?")
                .setPositiveButton("Clear Log", (d, w) -> {
                    repo.clearActivityLog();
                    loadLog();
                    Toast.makeText(this, "Activity log cleared", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ─── Adapter ─────────────────────────────────────────────────

    private static class ActivityLogAdapter extends BaseAdapter {

        private final Context context;
        private final List<VaultActivityLog> entries;
        private final SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault());

        ActivityLogAdapter(Context context, List<VaultActivityLog> entries) {
            this.context = context;
            this.entries = entries;
        }

        @Override public int getCount() { return entries.size(); }
        @Override public VaultActivityLog getItem(int pos) { return entries.get(pos); }
        @Override public long getItemId(int pos) { return pos; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(context).inflate(R.layout.item_vault_activity_log, parent, false);
            }

            VaultActivityLog entry = entries.get(position);

            TextView tvIcon = convertView.findViewById(R.id.tvLogIcon);
            TextView tvAction = convertView.findViewById(R.id.tvLogAction);
            TextView tvDetails = convertView.findViewById(R.id.tvLogDetails);
            TextView tvTime = convertView.findViewById(R.id.tvLogTime);

            tvIcon.setText(getActionIcon(entry.action));
            tvAction.setText(entry.getActionLabel());
            tvTime.setText(sdf.format(new Date(entry.timestamp)));

            if (entry.details != null && !entry.details.isEmpty()) {
                tvDetails.setText(entry.details);
                tvDetails.setVisibility(View.VISIBLE);
            } else {
                tvDetails.setVisibility(View.GONE);
            }

            // Highlight failed attempts in red
            if (entry.isFailedAttempt()) {
                tvAction.setTextColor(Color.parseColor("#EF4444"));
                convertView.setBackgroundColor(Color.parseColor("#1A0000"));
            } else {
                tvAction.setTextColor(Color.parseColor("#F1F5F9"));
                convertView.setBackgroundColor(Color.TRANSPARENT);
            }

            return convertView;
        }

        private String getActionIcon(VaultActivityLog.Action action) {
            if (action == null) return "❓";
            switch (action) {
                case UNLOCKED: return "🔓";
                case LOCKED: return "🔒";
                case FILE_IMPORTED: return "📥";
                case FILE_DELETED: return "🗑️";
                case FILE_VIEWED: return "👁️";
                case FAILED_ATTEMPT: return "⚠️";
                case PIN_CHANGED: return "🔑";
                case ALBUM_CREATED: return "📁";
                case ALBUM_DELETED: return "🗂️";
                case FILE_EXPORTED: return "📤";
                default: return "📋";
            }
        }
    }
}
