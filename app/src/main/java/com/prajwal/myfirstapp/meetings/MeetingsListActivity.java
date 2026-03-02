package com.prajwal.myfirstapp.meetings;


import com.prajwal.myfirstapp.R;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Shows all meetings grouped into time-bucket sections:
 * Today, Tomorrow, This Week, Next Week, Later, Past.
 *
 * Uses a RecyclerView with a two-view-type adapter (group headers + meeting cards).
 * Reloads data in onResume so the list stays fresh after edits.
 */
public class MeetingsListActivity extends AppCompatActivity {

    // ─── Data ────────────────────────────────────────────────────

    private MeetingRepository repo;

    /** Flat list fed to the adapter — alternating String headers and Meeting objects. */
    private List<Object> displayItems = new ArrayList<>();

    // ─── Views ───────────────────────────────────────────────────

    private RecyclerView recyclerMeetings;
    private MeetingsAdapter adapter;

    // ─── Group labels ────────────────────────────────────────────

    private static final String GROUP_TODAY     = "Today";
    private static final String GROUP_TOMORROW  = "Tomorrow";
    private static final String GROUP_THIS_WEEK = "This Week";
    private static final String GROUP_NEXT_WEEK = "Next Week";
    private static final String GROUP_LATER     = "Later";
    private static final String GROUP_PAST      = "Past";

    // ═════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═════════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_meetings_list);

        repo = MeetingRepository.getInstance(this);

        recyclerMeetings = findViewById(R.id.recyclerMeetings);
        recyclerMeetings.setLayoutManager(new LinearLayoutManager(this));

        adapter = new MeetingsAdapter();
        recyclerMeetings.setAdapter(adapter);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        View btnSearch = findViewById(R.id.btnSearch);
        if (btnSearch != null) {
            btnSearch.setOnClickListener(v ->
                    Toast.makeText(this, "Search coming soon", Toast.LENGTH_SHORT).show());
        }

        findViewById(R.id.fabAddMeeting).setOnClickListener(v -> openCreateMeeting(null));
    }

    @Override
    protected void onResume() {
        super.onResume();
        repo.reload();
        loadAndDisplay();
    }

    // ═════════════════════════════════════════════════════════════
    // DATA LOADING
    // ═════════════════════════════════════════════════════════════

    private void loadAndDisplay() {
        List<Meeting> all = repo.getActiveMeetings();

        // Sort by startDateTime ascending (unscheduled/0 goes to the end)
        Collections.sort(all, (a, b) -> {
            if (a.startDateTime == 0 && b.startDateTime == 0) return 0;
            if (a.startDateTime == 0) return 1;
            if (b.startDateTime == 0) return -1;
            return Long.compare(a.startDateTime, b.startDateTime);
        });

        // Group meetings
        Map<String, List<Meeting>> groups = new LinkedHashMap<>();
        for (Meeting m : all) {
            String group = getMeetingGroup(m);
            if (!groups.containsKey(group)) groups.put(group, new ArrayList<>());
            groups.get(group).add(m);
        }

        // Build displayItems in group-order
        List<String> orderedGroups = new ArrayList<>(groups.keySet());
        Collections.sort(orderedGroups, (a, b) ->
                Integer.compare(getGroupOrder(a), getGroupOrder(b)));

        displayItems.clear();
        for (String group : orderedGroups) {
            List<Meeting> groupMeetings = groups.get(group);
            if (groupMeetings == null || groupMeetings.isEmpty()) continue;
            displayItems.add(group);
            displayItems.addAll(groupMeetings);
        }

        adapter.notifyDataSetChanged();
    }

    // ─── Group classification ─────────────────────────────────────

    private String getMeetingGroup(Meeting m) {
        if (m.startDateTime == 0) return GROUP_LATER;

        long now = System.currentTimeMillis();
        if (m.isPast()) return GROUP_PAST;

        Calendar startCal = Calendar.getInstance();
        startCal.setTimeInMillis(m.startDateTime);

        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);

        Calendar tomorrow = (Calendar) today.clone();
        tomorrow.add(Calendar.DAY_OF_YEAR, 1);

        Calendar endOfWeek = (Calendar) today.clone();
        endOfWeek.set(Calendar.DAY_OF_WEEK, endOfWeek.getFirstDayOfWeek());
        endOfWeek.add(Calendar.WEEK_OF_YEAR, 1); // start of next week

        Calendar endOfNextWeek = (Calendar) endOfWeek.clone();
        endOfNextWeek.add(Calendar.WEEK_OF_YEAR, 1);

        // Normalise startCal to midnight for day-level comparison
        Calendar startDay = (Calendar) startCal.clone();
        startDay.set(Calendar.HOUR_OF_DAY, 0);
        startDay.set(Calendar.MINUTE, 0);
        startDay.set(Calendar.SECOND, 0);
        startDay.set(Calendar.MILLISECOND, 0);

        if (startDay.equals(today))    return GROUP_TODAY;
        if (startDay.equals(tomorrow)) return GROUP_TOMORROW;
        if (startCal.before(endOfWeek))     return GROUP_THIS_WEEK;
        if (startCal.before(endOfNextWeek)) return GROUP_NEXT_WEEK;
        return GROUP_LATER;
    }

    /** Lower value = earlier in the list. Past is displayed last. */
    private int getGroupOrder(String group) {
        switch (group) {
            case GROUP_TODAY:     return 0;
            case GROUP_TOMORROW:  return 1;
            case GROUP_THIS_WEEK: return 2;
            case GROUP_NEXT_WEEK: return 3;
            case GROUP_LATER:     return 4;
            case GROUP_PAST:      return 5;
            default:              return 6;
        }
    }

    // ═════════════════════════════════════════════════════════════
    // NAVIGATION
    // ═════════════════════════════════════════════════════════════

    private void openCreateMeeting(String meetingId) {
        Intent intent = new Intent(this, CreateMeetingActivity.class);
        if (meetingId != null) intent.putExtra(CreateMeetingActivity.EXTRA_MEETING_ID, meetingId);
        startActivity(intent);
    }

    private void openMeetingDetail(Meeting m) {
        Intent intent = new Intent(this, MeetingDetailActivity.class);
        intent.putExtra(MeetingNotificationHelper.EXTRA_MEETING_ID, m.id);
        startActivity(intent);
    }

    private void confirmDelete(Meeting m) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Meeting")
                .setMessage("Delete \"" + m.title + "\"? This cannot be undone.")
                .setPositiveButton("Delete", (d, w) -> {
                    MeetingNotificationHelper.cancelMeetingReminders(this, m.id);
                    repo.deleteMeeting(m.id);
                    loadAndDisplay();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void joinMeeting(Meeting m) {
        if (m.hasMeetingLink()) {
            try {
                String link = m.meetingLink;
                if (!link.startsWith("http://") && !link.startsWith("https://")) {
                    link = "https://" + link;
                }
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(link)));
            } catch (Exception e) {
                Toast.makeText(this, "Could not open link", Toast.LENGTH_SHORT).show();
            }
        } else if (Meeting.TYPE_PHONE_CALL.equals(m.type) && m.hasLocation()) {
            startActivity(new Intent(Intent.ACTION_DIAL,
                    Uri.parse("tel:" + m.location.trim())));
        } else {
            openMeetingDetail(m);
        }
    }

    // ═════════════════════════════════════════════════════════════
    // ADAPTER
    // ═════════════════════════════════════════════════════════════

    private class MeetingsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private static final int VIEW_TYPE_HEADER  = 0;
        private static final int VIEW_TYPE_MEETING = 1;

        @Override
        public int getItemViewType(int position) {
            return displayItems.get(position) instanceof String
                    ? VIEW_TYPE_HEADER : VIEW_TYPE_MEETING;
        }

        @Override
        public int getItemCount() {
            return displayItems.size();
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (viewType == VIEW_TYPE_HEADER) {
                View v = inflater.inflate(R.layout.item_task_group_header, parent, false);
                return new HeaderViewHolder(v);
            } else {
                View v = inflater.inflate(R.layout.item_meeting_card_full, parent, false);
                return new MeetingViewHolder(v);
            }
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            Object item = displayItems.get(position);
            if (holder instanceof HeaderViewHolder) {
                ((HeaderViewHolder) holder).bind((String) item, position);
            } else if (holder instanceof MeetingViewHolder) {
                ((MeetingViewHolder) holder).bind((Meeting) item);
            }
        }
    }

    // ─── Header ViewHolder ────────────────────────────────────────

    private class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView tvGroupIcon, tvGroupName, tvGroupCount, tvGroupChevron;

        HeaderViewHolder(View v) {
            super(v);
            tvGroupIcon    = v.findViewById(R.id.tvGroupIcon);
            tvGroupName    = v.findViewById(R.id.tvGroupName);
            tvGroupCount   = v.findViewById(R.id.tvGroupCount);
            tvGroupChevron = v.findViewById(R.id.tvGroupChevron);
        }

        void bind(String group, int position) {
            tvGroupName.setText(group);

            // Count the meetings in this group (items after the header until next header)
            int count = 0;
            for (int i = position + 1; i < displayItems.size(); i++) {
                if (displayItems.get(i) instanceof Meeting) count++;
                else break;
            }
            tvGroupCount.setText(String.valueOf(count));

            // Set group icon
            String icon;
            switch (group) {
                case GROUP_TODAY:     icon = "🔴"; break;
                case GROUP_TOMORROW:  icon = "🟡"; break;
                case GROUP_THIS_WEEK: icon = "📅"; break;
                case GROUP_NEXT_WEEK: icon = "📆"; break;
                case GROUP_LATER:     icon = "🗓️"; break;
                case GROUP_PAST:      icon = "✅"; break;
                default:              icon = "📌"; break;
            }
            tvGroupIcon.setText(icon);

            if (tvGroupChevron != null) tvGroupChevron.setText("▾");
        }
    }

    // ─── Meeting ViewHolder ───────────────────────────────────────

    private class MeetingViewHolder extends RecyclerView.ViewHolder {
        View        viewColorAccent;
        ImageView   ivCardTypeIcon;
        TextView    tvCardTitle, tvCardDateTimeRange, tvCardDuration;
        TextView    tvCardPlatformBadge, tvCardLocation;
        FrameLayout attendeeAvatarsContainer;
        TextView    tvCardAgendaCount, tvCardActionCount;
        TextView    tvCardStatusBadge, tvCardLiveBadge;
        TextView    btnCardJoin, btnCardViewNotes;
        ImageView   btnCardMenu;

        MeetingViewHolder(View v) {
            super(v);
            viewColorAccent          = v.findViewById(R.id.viewColorAccent);
            ivCardTypeIcon           = v.findViewById(R.id.ivCardTypeIcon);
            tvCardTitle              = v.findViewById(R.id.tvCardTitle);
            tvCardDateTimeRange      = v.findViewById(R.id.tvCardDateTimeRange);
            tvCardDuration           = v.findViewById(R.id.tvCardDuration);
            tvCardPlatformBadge      = v.findViewById(R.id.tvCardPlatformBadge);
            tvCardLocation           = v.findViewById(R.id.tvCardLocation);
            attendeeAvatarsContainer = v.findViewById(R.id.attendeeAvatarsContainer);
            tvCardAgendaCount        = v.findViewById(R.id.tvCardAgendaCount);
            tvCardActionCount        = v.findViewById(R.id.tvCardActionCount);
            tvCardStatusBadge        = v.findViewById(R.id.tvCardStatusBadge);
            tvCardLiveBadge          = v.findViewById(R.id.tvCardLiveBadge);
            btnCardJoin              = v.findViewById(R.id.btnCardJoin);
            btnCardViewNotes         = v.findViewById(R.id.btnCardViewNotes);
            btnCardMenu              = v.findViewById(R.id.btnCardMenu);
        }

        void bind(Meeting m) {
            // Title
            tvCardTitle.setText(m.title);

            // Color accent bar
            if (viewColorAccent != null && m.colorHex != null && !m.colorHex.isEmpty()) {
                try {
                    viewColorAccent.setBackgroundColor(Color.parseColor(m.colorHex));
                } catch (Exception ignored) {}
            }

            // Type icon (using emoji text description)
            setTypeIcon(m.type);

            // Date/time range
            String dateRange = m.getFormattedDateRange();
            tvCardDateTimeRange.setText(dateRange.isEmpty() ? "No date set" : dateRange);

            // Duration chip
            String dur = m.getDurationText();
            if (!dur.isEmpty()) {
                tvCardDuration.setText(dur);
                tvCardDuration.setVisibility(View.VISIBLE);
            } else {
                tvCardDuration.setVisibility(View.GONE);
            }

            // Platform badge
            if (m.platform != null && !m.platform.isEmpty()
                    && !Meeting.PLATFORM_IN_PERSON.equals(m.platform)) {
                tvCardPlatformBadge.setText(m.platform);
                tvCardPlatformBadge.setVisibility(View.VISIBLE);
            } else {
                tvCardPlatformBadge.setVisibility(View.GONE);
            }

            // Location
            if (m.hasLocation()) {
                tvCardLocation.setText("📍 " + m.location);
                tvCardLocation.setVisibility(View.VISIBLE);
            } else {
                tvCardLocation.setVisibility(View.GONE);
            }

            // Attendee avatars (up to 4)
            buildAttendeeAvatars(m);

            // Agenda / action counts
            int agendaCount = m.agenda != null ? m.agenda.size() : 0;
            tvCardAgendaCount.setText(agendaCount + " agenda");
            tvCardAgendaCount.setVisibility(agendaCount > 0 ? View.VISIBLE : View.GONE);

            int actionCount = m.getOpenActionItemCount();
            tvCardActionCount.setText(actionCount + " actions");
            tvCardActionCount.setVisibility(actionCount > 0 ? View.VISIBLE : View.GONE);

            // Status badge
            tvCardStatusBadge.setText(m.getStatusLabel());
            tvCardStatusBadge.setBackgroundColor(getStatusColor(m.status));

            // LIVE badge
            boolean isLive  = m.isHappeningNow();
            boolean isSoon  = m.isStartingSoon();
            tvCardLiveBadge.setVisibility(isLive ? View.VISIBLE : View.GONE);
            if (!isLive && isSoon) {
                tvCardLiveBadge.setText("⏰ Starting soon");
                tvCardLiveBadge.setVisibility(View.VISIBLE);
            }

            // Join button
            boolean canJoin = (isLive || isSoon) && (m.hasMeetingLink()
                    || Meeting.TYPE_PHONE_CALL.equals(m.type));
            btnCardJoin.setVisibility(canJoin ? View.VISIBLE : View.GONE);
            btnCardJoin.setOnClickListener(v -> joinMeeting(m));

            // View Notes button (past meetings)
            btnCardViewNotes.setVisibility(m.isPast() ? View.VISIBLE : View.GONE);
            btnCardViewNotes.setOnClickListener(v -> openMeetingDetail(m));

            // Card click → detail
            itemView.setOnClickListener(v -> openMeetingDetail(m));

            // Three-dot menu
            btnCardMenu.setOnClickListener(v -> showCardMenu(m));
        }

        private void setTypeIcon(String type) {
            // Use system drawable as background; set content description as emoji hint
            if (ivCardTypeIcon == null) return;
            switch (type != null ? type : "") {
                case Meeting.TYPE_VIDEO_CALL:
                    ivCardTypeIcon.setImageResource(android.R.drawable.ic_menu_camera);
                    ivCardTypeIcon.setContentDescription("📹 Video call");
                    break;
                case Meeting.TYPE_PHONE_CALL:
                    ivCardTypeIcon.setImageResource(android.R.drawable.ic_menu_call);
                    ivCardTypeIcon.setContentDescription("📞 Phone call");
                    break;
                case Meeting.TYPE_IN_PERSON:
                    ivCardTypeIcon.setImageResource(android.R.drawable.ic_menu_compass);
                    ivCardTypeIcon.setContentDescription("🏢 In person");
                    break;
                default:
                    ivCardTypeIcon.setImageResource(android.R.drawable.ic_menu_agenda);
                    ivCardTypeIcon.setContentDescription("Other");
                    break;
            }
        }

        private void buildAttendeeAvatars(Meeting m) {
            if (attendeeAvatarsContainer == null) return;
            attendeeAvatarsContainer.removeAllViews();
            if (!m.hasAttendees()) return;

            int max = Math.min(m.attendees.size(), 4);
            int size = dp(28);

            for (int i = 0; i < max; i++) {
                Attendee a = m.attendees.get(i);

                TextView avatar = new TextView(MeetingsListActivity.this);
                avatar.setText(a.getInitials());
                avatar.setTextColor(Color.WHITE);
                avatar.setTextSize(10);
                avatar.setGravity(android.view.Gravity.CENTER);

                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size);
                lp.leftMargin = i * (size - dp(6));
                avatar.setLayoutParams(lp);

                GradientDrawable circle = new GradientDrawable();
                circle.setShape(GradientDrawable.OVAL);
                circle.setStroke(dp(2), Color.parseColor("#1E1E3A"));
                try { circle.setColor(Color.parseColor(a.getAvatarColor())); }
                catch (Exception ignored) { circle.setColor(Color.parseColor("#4A90E2")); }
                avatar.setBackground(circle);

                attendeeAvatarsContainer.addView(avatar);
            }

            if (m.attendees.size() > 4) {
                int extra = m.attendees.size() - 4;
                TextView more = new TextView(MeetingsListActivity.this);
                more.setText("+" + extra);
                more.setTextColor(Color.parseColor("#94A3B8"));
                more.setTextSize(10);
                more.setGravity(android.view.Gravity.CENTER);

                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size);
                lp.leftMargin = 4 * (size - dp(6));
                more.setLayoutParams(lp);

                GradientDrawable circle = new GradientDrawable();
                circle.setShape(GradientDrawable.OVAL);
                circle.setColor(Color.parseColor("#2A2A4A"));
                more.setBackground(circle);

                attendeeAvatarsContainer.addView(more);
            }
        }

        private int getStatusColor(String status) {
            if (status == null) return Color.parseColor("#1A3B82F6");
            switch (status) {
                case Meeting.STATUS_IN_PROGRESS: return Color.parseColor("#1A16A34A");
                case Meeting.STATUS_COMPLETED:   return Color.parseColor("#1A166534");
                case Meeting.STATUS_CANCELLED:   return Color.parseColor("#1AEF4444");
                case Meeting.STATUS_POSTPONED:   return Color.parseColor("#1AF59E0B");
                default:                         return Color.parseColor("#1A3B82F6");
            }
        }

        private void showCardMenu(Meeting m) {
            String[] options = {"Edit", "Delete"};
            new AlertDialog.Builder(MeetingsListActivity.this)
                    .setItems(options, (d, which) -> {
                        if (which == 0) openCreateMeeting(m.id);
                        else            confirmDelete(m);
                    })
                    .show();
        }
    }

    // ═════════════════════════════════════════════════════════════
    // UTILITY
    // ═════════════════════════════════════════════════════════════

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
