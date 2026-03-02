package com.prajwal.myfirstapp.meetings;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Repository for Meeting data — handles persistence, CRUD, filtering,
 * sorting, searching, recurring expansion, and change notifications.
 *
 * Uses a Singleton pattern; obtain via {@link #getInstance(Context)}.
 * Data is stored in SharedPreferences as a JSON array under the key
 * {@value #MEETINGS_KEY}.
 */
public class MeetingRepository {

    private static final String TAG = "MeetingRepository";

    private static final String PREFS_NAME  = "meeting_manager_prefs";
    private static final String MEETINGS_KEY = "meetings_data";

    // Maximum recurring instances that can be generated in one call
    private static final int MAX_EXPANSION_COUNT = 52;

    // ─── Singleton ────────────────────────────────────────────────

    private static MeetingRepository instance;

    public static synchronized MeetingRepository getInstance(Context context) {
        if (instance == null) {
            instance = new MeetingRepository(context.getApplicationContext());
        }
        return instance;
    }

    // ─── Change Listener ─────────────────────────────────────────

    /** Callback fired whenever the meeting list is mutated. */
    public interface MeetingChangeListener {
        void onMeetingsChanged();
    }

    private final List<MeetingChangeListener> changeListeners = new ArrayList<>();

    public void addChangeListener(MeetingChangeListener listener) {
        if (listener != null && !changeListeners.contains(listener)) {
            changeListeners.add(listener);
        }
    }

    public void removeChangeListener(MeetingChangeListener listener) {
        changeListeners.remove(listener);
    }

    private void notifyListeners() {
        // Iterate a copy to allow listeners to remove themselves safely
        for (MeetingChangeListener listener : new ArrayList<>(changeListeners)) {
            listener.onMeetingsChanged();
        }
    }

    // ─── State ───────────────────────────────────────────────────

    private final Context context;
    private final ArrayList<Meeting> meetings;

    // ─── Constructor ─────────────────────────────────────────────

    private MeetingRepository(Context context) {
        this.context = context;
        this.meetings = new ArrayList<>();
        load();
    }

    private SharedPreferences getPrefs() {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ─── Persistence ─────────────────────────────────────────────

    private void load() {
        meetings.clear();
        String json = getPrefs().getString(MEETINGS_KEY, "[]");
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                Meeting m = Meeting.fromJson(array.getJSONObject(i));
                if (m != null) meetings.add(m);
            }
            Log.i(TAG, "Loaded " + meetings.size() + " meetings");
        } catch (JSONException e) {
            Log.e(TAG, "Failed to load meetings: " + e.getMessage());
        }
    }

    private void persist() {
        JSONArray array = new JSONArray();
        for (Meeting m : meetings) {
            array.put(m.toJson());
        }
        getPrefs().edit().putString(MEETINGS_KEY, array.toString()).apply();
    }

    // ─── CRUD ─────────────────────────────────────────────────────

    /**
     * Add a new meeting or update an existing one (matched by {@link Meeting#id}).
     * The {@link Meeting#updatedAt} timestamp is always refreshed.
     */
    public void saveMeeting(Meeting meeting) {
        if (meeting == null) return;
        meeting.updatedAt = System.currentTimeMillis();
        for (int i = 0; i < meetings.size(); i++) {
            if (meetings.get(i).id.equals(meeting.id)) {
                meetings.set(i, meeting);
                persist();
                notifyListeners();
                return;
            }
        }
        // New meeting — prepend so it appears first in insertion-order lists
        meetings.add(0, meeting);
        persist();
        notifyListeners();
    }

    /** Permanently delete a meeting by its ID. */
    public void deleteMeeting(String id) {
        if (id == null) return;
        boolean removed = meetings.removeIf(m -> m.id.equals(id));
        if (removed) {
            persist();
            notifyListeners();
        }
    }

    /** Look up a single meeting by ID; returns {@code null} if not found. */
    public Meeting getMeeting(String id) {
        if (id == null) return null;
        for (Meeting m : meetings) {
            if (m.id.equals(id)) return m;
        }
        return null;
    }

    /** Returns a snapshot of every stored meeting (including cancelled ones). */
    public List<Meeting> getAllMeetings() {
        return new ArrayList<>(meetings);
    }

    // ─── Filtered Queries ─────────────────────────────────────────

    /**
     * Active meetings — not {@link Meeting#STATUS_CANCELLED} and not trashed
     * (seriesId {@code "__trash__"} is the soft-delete sentinel).
     */
    public List<Meeting> getActiveMeetings() {
        List<Meeting> result = new ArrayList<>();
        for (Meeting m : meetings) {
            if (!Meeting.STATUS_CANCELLED.equals(m.status)
                    && !"__trash__".equals(m.seriesId)) {
                result.add(m);
            }
        }
        return result;
    }

    /**
     * Future meetings ({@link Meeting#startDateTime} &gt; now), ordered by
     * {@link Meeting#startDateTime} ascending. Excludes cancelled and trashed meetings.
     */
    public List<Meeting> getUpcomingMeetings() {
        long now = System.currentTimeMillis();
        List<Meeting> result = new ArrayList<>();
        for (Meeting m : meetings) {
            if (m.startDateTime > now
                    && !Meeting.STATUS_CANCELLED.equals(m.status)
                    && !"__trash__".equals(m.seriesId)) {
                result.add(m);
            }
        }
        Collections.sort(result, (a, b) -> Long.compare(a.startDateTime, b.startDateTime));
        return result;
    }

    /**
     * Meetings whose start time falls on the given calendar date.
     *
     * @param date date string in {@code "yyyy-MM-dd"} format
     */
    public List<Meeting> getMeetingsForDate(String date) {
        if (date == null || date.isEmpty()) return new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        List<Meeting> result = new ArrayList<>();
        for (Meeting m : meetings) {
            if (m.startDateTime > 0) {
                String meetingDate = sdf.format(new Date(m.startDateTime));
                if (date.equals(meetingDate)) {
                    result.add(m);
                }
            }
        }
        Collections.sort(result, (a, b) -> Long.compare(a.startDateTime, b.startDateTime));
        return result;
    }

    /**
     * Full-text search across title, description, location, platform, notes,
     * attendees, agenda, and action items.
     * Returns all active meetings when {@code query} is empty.
     */
    public List<Meeting> searchMeetings(String query) {
        if (query == null || query.trim().isEmpty()) return getActiveMeetings();
        List<Meeting> result = new ArrayList<>();
        for (Meeting m : meetings) {
            if (!"__trash__".equals(m.seriesId) && m.matchesSearch(query)) {
                result.add(m);
            }
        }
        return result;
    }

    /** Filter meetings by {@link Meeting#status} (e.g. {@link Meeting#STATUS_SCHEDULED}). */
    public List<Meeting> getMeetingsByStatus(String status) {
        if (status == null) return new ArrayList<>();
        List<Meeting> result = new ArrayList<>();
        for (Meeting m : meetings) {
            if (status.equals(m.status)) result.add(m);
        }
        return result;
    }

    /**
     * All meetings that belong to a given recurring series, ordered by
     * {@link Meeting#startDateTime} ascending.
     */
    public List<Meeting> getMeetingsBySeries(String seriesId) {
        if (seriesId == null || seriesId.isEmpty()) return new ArrayList<>();
        List<Meeting> result = new ArrayList<>();
        for (Meeting m : meetings) {
            if (seriesId.equals(m.seriesId)) result.add(m);
        }
        Collections.sort(result, (a, b) -> Long.compare(a.startDateTime, b.startDateTime));
        return result;
    }

    // ─── Recurring Expansion ──────────────────────────────────────

    /**
     * Creates {@code count} future occurrences of a recurring meeting template
     * and saves them all. Each instance shares the same {@link Meeting#seriesId}
     * (set to the template's {@link Meeting#id} when not already assigned).
     *
     * <p>Generation stops early for {@link Meeting#RECURRENCE_CUSTOM} since the
     * interval cannot be determined automatically.
     *
     * @param template source meeting; must have a non-NONE recurrence and a valid
     *                 {@link Meeting#startDateTime}
     * @param count    number of future instances to generate; capped at
     *                 {@value #MAX_EXPANSION_COUNT}
     * @return the list of newly saved instances
     */
    public List<Meeting> expandRecurringMeeting(Meeting template, int count) {
        List<Meeting> created = new ArrayList<>();
        if (template == null || !template.isRecurring() || template.startDateTime <= 0) {
            return created;
        }

        int safeCount = Math.min(count, MAX_EXPANSION_COUNT);
        String seriesId = (template.seriesId != null && !template.seriesId.isEmpty())
                ? template.seriesId : template.id;

        long durationMs = template.endDateTime > template.startDateTime
                ? (template.endDateTime - template.startDateTime) : 0L;

        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(template.startDateTime);

        for (int i = 0; i < safeCount; i++) {
            switch (template.recurrence) {
                case Meeting.RECURRENCE_DAILY:
                    cal.add(Calendar.DAY_OF_YEAR, 1);
                    break;
                case Meeting.RECURRENCE_WEEKLY:
                    cal.add(Calendar.WEEK_OF_YEAR, 1);
                    break;
                case Meeting.RECURRENCE_MONTHLY:
                    cal.add(Calendar.MONTH, 1);
                    break;
                default:
                    // CUSTOM or unknown — cannot auto-advance
                    Log.w(TAG, "expandRecurringMeeting: unsupported recurrence '"
                            + template.recurrence + "', stopping at i=" + i);
                    if (!created.isEmpty()) {
                        persist();
                        notifyListeners();
                    }
                    return created;
            }

            Meeting instance = template.duplicate();
            instance.id = UUID.randomUUID().toString().substring(0, 12);
            // duplicate() appends " (copy)" to the title — restore the original title
            instance.title = template.title;
            instance.status = Meeting.STATUS_SCHEDULED;
            instance.seriesId = seriesId;
            instance.startDateTime = cal.getTimeInMillis();
            instance.endDateTime = durationMs > 0 ? (cal.getTimeInMillis() + durationMs) : 0L;
            instance.createdAt = System.currentTimeMillis();
            instance.updatedAt = System.currentTimeMillis();

            meetings.add(instance);
            created.add(instance);
        }

        if (!created.isEmpty()) {
            persist();
            notifyListeners();
        }

        return created;
    }

    // ─── Convenience ─────────────────────────────────────────────

    /** Force a reload from SharedPreferences (e.g. after out-of-process writes). */
    public void reload() {
        load();
    }
}
