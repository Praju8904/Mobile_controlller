package com.prajwal.myfirstapp.timecapsule;


import com.prajwal.myfirstapp.R;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *  TIME CAPSULE ACTIVITY — Create, view, and open note time capsules.
 * ═══════════════════════════════════════════════════════════════════════════════════════
 */
public class TimeCapsuleActivity extends AppCompatActivity {

    // ═══════════════════════════════════════════════════════════════════════════════
    //  FIELDS
    // ═══════════════════════════════════════════════════════════════════════════════

    private TimeCapsuleManager capsuleManager;
    private RecyclerView rvCapsules;
    private LinearLayout emptyState;
    private TabLayout tabLayout;
    private CapsuleAdapter adapter;
    private int currentTab = 0; // 0=Locked, 1=Ready, 2=Opened

    // ═══════════════════════════════════════════════════════════════════════════════
    //  LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_time_capsule);
        getWindow().setStatusBarColor(0xFF0A0E21);
        getWindow().setNavigationBarColor(0xFF0A0E21);

        capsuleManager = new TimeCapsuleManager(this);

        rvCapsules = findViewById(R.id.rvCapsules);
        emptyState = findViewById(R.id.emptyState);
        tabLayout = findViewById(R.id.tabLayout);

        rvCapsules.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CapsuleAdapter();
        rvCapsules.setAdapter(adapter);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnCreate).setOnClickListener(v -> showCreateDialog());

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                currentTab = tab.getPosition();
                loadCapsules();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        loadCapsules();
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  DATA
    // ═══════════════════════════════════════════════════════════════════════════════

    private void loadCapsules() {
        List<TimeCapsuleManager.TimeCapsule> capsules;
        switch (currentTab) {
            case 1: capsules = capsuleManager.getReadyCapsules(); break;
            case 2: capsules = capsuleManager.getOpenedCapsules(); break;
            default: capsules = capsuleManager.getLockedCapsules(); break;
        }

        adapter.setCapsules(capsules);
        emptyState.setVisibility(capsules.isEmpty() ? View.VISIBLE : View.GONE);

        // Update empty state text
        if (capsules.isEmpty()) {
            TextView tvEmoji = findViewById(R.id.tvEmptyEmoji);
            TextView tvTitle = findViewById(R.id.tvEmptyTitle);
            switch (currentTab) {
                case 1:
                    tvEmoji.setText("🎁");
                    tvTitle.setText("No capsules ready to open");
                    break;
                case 2:
                    tvEmoji.setText("📭");
                    tvTitle.setText("No opened capsules");
                    break;
                default:
                    tvEmoji.setText("🔒");
                    tvTitle.setText("No locked capsules");
                    break;
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  CREATE CAPSULE
    // ═══════════════════════════════════════════════════════════════════════════════

    private void showCreateDialog() {
        String[] labels = TimeCapsuleManager.DURATION_LABELS;
        new MaterialAlertDialogBuilder(this)
                .setTitle("Create Time Capsule")
                .setItems(labels, (dialog, which) -> {
                    // For now, create capsule without note association
                    // In real usage, this would be triggered from NoteEditorActivity
                    int days = TimeCapsuleManager.DURATION_DAYS[which];
                    String message = "Time capsule locked for " + labels[which];
                    capsuleManager.createCapsuleIn("general", "General Capsule", days, message);
                    Toast.makeText(this, "Time capsule created — locked for " + labels[which], Toast.LENGTH_SHORT).show();
                    loadCapsules();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  ADAPTER
    // ═══════════════════════════════════════════════════════════════════════════════

    private class CapsuleAdapter extends RecyclerView.Adapter<CapsuleAdapter.VH> {
        private List<TimeCapsuleManager.TimeCapsule> capsules = new ArrayList<>();

        void setCapsules(List<TimeCapsuleManager.TimeCapsule> list) {
            this.capsules = list;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_time_capsule, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            TimeCapsuleManager.TimeCapsule capsule = capsules.get(position);

            holder.tvStatusEmoji.setText(capsule.getStatusEmoji());
            holder.tvNoteTitle.setText("Note: " + capsule.noteId);
            holder.tvMessage.setText(capsule.message);

            SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());

            if (capsule.isOpened) {
                holder.tvDateInfo.setText("Opened");
                holder.tvDateInfo.setTextColor(0xFF10B981);
            } else if (capsule.openDate <= System.currentTimeMillis()) {
                holder.tvDateInfo.setText("Ready to open!");
                holder.tvDateInfo.setTextColor(0xFFF59E0B);
            } else {
                long daysLeft = (capsule.openDate - System.currentTimeMillis()) / (1000 * 60 * 60 * 24);
                holder.tvDateInfo.setText("Opens in " + daysLeft + " days  (" + sdf.format(new Date(capsule.openDate)) + ")");
                holder.tvDateInfo.setTextColor(0xFF94A3B8);
            }

            holder.itemView.setOnClickListener(v -> {
                if (!capsule.isOpened && capsule.openDate <= System.currentTimeMillis()) {
                    // Open the capsule
                    capsuleManager.openCapsule(capsule.id);
                    Toast.makeText(TimeCapsuleActivity.this, "🎉 Time capsule opened!", Toast.LENGTH_SHORT).show();
                    loadCapsules();
                }
            });
        }

        @Override
        public int getItemCount() { return capsules.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tvStatusEmoji, tvNoteTitle, tvMessage, tvDateInfo;
            VH(View v) {
                super(v);
                tvStatusEmoji = v.findViewById(R.id.tvStatusEmoji);
                tvNoteTitle = v.findViewById(R.id.tvNoteTitle);
                tvMessage = v.findViewById(R.id.tvMessage);
                tvDateInfo = v.findViewById(R.id.tvDateInfo);
            }
        }
    }
}
