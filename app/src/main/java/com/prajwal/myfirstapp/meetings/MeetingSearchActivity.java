package com.prajwal.myfirstapp.meetings;


import com.prajwal.myfirstapp.R;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Search activity for meetings.
 */
public class MeetingSearchActivity extends AppCompatActivity {

    private MeetingRepository repo;
    private List<Meeting> allMeetings = new ArrayList<>();
    private List<Meeting> results = new ArrayList<>();
    private ResultAdapter adapter;
    private TextView tvResultCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        repo = MeetingRepository.getInstance(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#1A1A2E"));

        // ── Toolbar + Search bar ──────────────────────────────────
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setBackgroundColor(Color.parseColor("#252545"));
        toolbar.setPadding(dp(8), dp(10), dp(12), dp(10));
        toolbar.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        ImageView btnBack = new ImageView(this);
        btnBack.setImageResource(android.R.drawable.ic_media_previous);
        btnBack.setColorFilter(Color.WHITE);
        LinearLayout.LayoutParams backLp = new LinearLayout.LayoutParams(dp(40), dp(40));
        btnBack.setLayoutParams(backLp);
        btnBack.setOnClickListener(v -> finish());
        toolbar.addView(btnBack);

        EditText etSearch = new EditText(this);
        etSearch.setHint("Search meetings…");
        etSearch.setHintTextColor(Color.parseColor("#9E9E9E"));
        etSearch.setTextColor(Color.WHITE);
        etSearch.setTextSize(16);
        etSearch.setBackgroundColor(Color.TRANSPARENT);
        etSearch.setSingleLine(true);
        etSearch.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        etSearch.setPadding(dp(8), 0, dp(8), 0);
        toolbar.addView(etSearch);

        root.addView(toolbar);

        // ── Result count ──────────────────────────────────────────
        tvResultCount = new TextView(this);
        tvResultCount.setTextColor(Color.parseColor("#9E9E9E"));
        tvResultCount.setTextSize(12);
        tvResultCount.setPadding(dp(16), dp(8), dp(16), dp(4));
        root.addView(tvResultCount);

        // ── Results RecyclerView ──────────────────────────────────
        RecyclerView recycler = new RecyclerView(this);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setPadding(dp(8), dp(4), dp(8), dp(16));
        recycler.setClipToPadding(false);
        recycler.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        adapter = new ResultAdapter();
        recycler.setAdapter(adapter);
        root.addView(recycler);

        setContentView(root);

        // Load and show all meetings initially
        allMeetings = repo.getAllMeetings();
        results.addAll(allMeetings);
        updateCount();
        adapter.notifyDataSetChanged();

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filter(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void filter(String query) {
        results.clear();
        String q = query.trim().toLowerCase();
        if (q.isEmpty()) {
            results.addAll(allMeetings);
        } else {
            for (Meeting m : allMeetings) {
                if (matches(m, q)) results.add(m);
            }
        }
        updateCount();
        adapter.notifyDataSetChanged();
    }

    private boolean matches(Meeting m, String q) {
        if (m.title != null && m.title.toLowerCase().contains(q)) return true;
        if (m.location != null && m.location.toLowerCase().contains(q)) return true;
        if (m.notes != null && m.notes.toLowerCase().contains(q)) return true;
        if (m.attendees != null) {
            for (Attendee a : m.attendees) {
                if (a.name != null && a.name.toLowerCase().contains(q)) return true;
            }
        }
        if (m.agenda != null) {
            for (AgendaItem ai : m.agenda) {
                if (ai.title != null && ai.title.toLowerCase().contains(q)) return true;
            }
        }
        return false;
    }

    private void updateCount() {
        tvResultCount.setText(results.size() + " meeting" + (results.size() != 1 ? "s" : ""));
    }

    // ── Adapter ───────────────────────────────────────────────────

    private class ResultAdapter extends RecyclerView.Adapter<ResultAdapter.VH> {

        @Override
        public VH onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            LinearLayout card = new LinearLayout(MeetingSearchActivity.this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(14), dp(12), dp(14), dp(12));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.parseColor("#252545"));
            bg.setCornerRadius(dp(10));
            card.setBackground(bg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(dp(4), dp(5), dp(4), dp(5));
            card.setLayoutParams(lp);
            return new VH(card);
        }

        @Override
        public void onBindViewHolder(VH holder, int position) {
            Meeting m = results.get(position);
            holder.tvTitle.setText(m.title);

            // Date
            String dateStr = "—";
            if (m.startDateTime > 0) {
                dateStr = new SimpleDateFormat("MMM dd, yyyy  HH:mm", Locale.US)
                        .format(new Date(m.startDateTime));
            }
            holder.tvDate.setText(dateStr);

            // Type
            String typeLabel;
            switch (m.type != null ? m.type : Meeting.TYPE_OTHER) {
                case Meeting.TYPE_VIDEO_CALL:  typeLabel = "📹 Video";     break;
                case Meeting.TYPE_IN_PERSON:   typeLabel = "🏢 In-Person"; break;
                case Meeting.TYPE_PHONE_CALL:  typeLabel = "📞 Phone";     break;
                default:                       typeLabel = "📌 Other";     break;
            }
            holder.tvType.setText(typeLabel);

            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(MeetingSearchActivity.this,
                        MeetingDetailActivity.class);
                intent.putExtra(MeetingDetailActivity.EXTRA_MEETING_ID, m.id);
                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() { return results.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tvTitle, tvDate, tvType;
            VH(LinearLayout card) {
                super(card);
                tvTitle = new TextView(MeetingSearchActivity.this);
                tvTitle.setTextColor(Color.WHITE);
                tvTitle.setTextSize(15);
                tvTitle.setTypeface(null, Typeface.BOLD);
                card.addView(tvTitle);

                LinearLayout meta = new LinearLayout(MeetingSearchActivity.this);
                meta.setOrientation(LinearLayout.HORIZONTAL);
                meta.setGravity(Gravity.CENTER_VERTICAL);
                LinearLayout.LayoutParams metaLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                metaLp.topMargin = dp(4);
                meta.setLayoutParams(metaLp);

                tvDate = new TextView(MeetingSearchActivity.this);
                tvDate.setTextColor(Color.parseColor("#9E9E9E"));
                tvDate.setTextSize(12);
                tvDate.setLayoutParams(new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                meta.addView(tvDate);

                tvType = new TextView(MeetingSearchActivity.this);
                tvType.setTextColor(Color.parseColor("#6C63FF"));
                tvType.setTextSize(12);
                meta.addView(tvType);

                card.addView(meta);
            }
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
