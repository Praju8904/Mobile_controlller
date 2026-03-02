package com.prajwal.myfirstapp.calendar;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Repository for CalendarEvent and EventCategory data.
 *
 * Handles: persistence (SharedPreferences), CRUD, migration from legacy format,
 * recurring event expansion, single/future/all occurrence editing,
 * querying by date/week/month/category, search, and category management.
 */
public class CalendarRepository {

    private static final String TAG = "CalendarRepository";

    // Prefs
    private static final String PREFS_NAME = "calendar_v2_prefs";
    private static final String EVENTS_KEY = "events_data";
    private static final String CATEGORIES_KEY = "categories_data";
    private static final String SETTINGS_KEY = "calendar_settings";
    private static final String CATEGORIES_SEEDED_KEY = "categories_seeded";
    private static final String LEGACY_MIGRATED_KEY = "legacy_migrated";
    private static final String RECENT_SEARCHES_KEY = "recent_searches";
    private static final int MAX_RECENT_SEARCHES = 10;

    private final Context context;
    private ArrayList<CalendarEvent> events;
    private ArrayList<EventCategory> categories;

    // ─── Constructor ─────────────────────────────────────────────

    public CalendarRepository(Context context) {
        this.context = context;
        this.events = new ArrayList<>();
        this.categories = new ArrayList<>();
        loadCategories();
        seedDefaultCategoriesIfNeeded();
        loadEvents();
        migrateLegacyEventsIfNeeded();
    }

    private SharedPreferences getPrefs() {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ─── Persistence ─────────────────────────────────────────────

    private void loadEvents() {
        events.clear();
        String json = getPrefs().getString(EVENTS_KEY, "[]");
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                CalendarEvent event = CalendarEvent.fromJson(array.getJSONObject(i));
                if (event != null) events.add(event);
            }
            Log.i(TAG, "Loaded " + events.size() + " events");
        } catch (JSONException e) {
            Log.e(TAG, "Failed to load events: " + e.getMessage());
        }
    }

    private void saveEvents() {
        JSONArray array = new JSONArray();
        for (CalendarEvent event : events) {
            array.put(event.toJson());
        }
        getPrefs().edit().putString(EVENTS_KEY, array.toString()).apply();
    }

    private void loadCategories() {
        categories.clear();
        String json = getPrefs().getString(CATEGORIES_KEY, "[]");
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                EventCategory cat = EventCategory.fromJson(array.getJSONObject(i));
                if (cat != null) categories.add(cat);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Failed to load categories: " + e.getMessage());
        }
    }

    private void saveCategories() {
        JSONArray array = new JSONArray();
        for (EventCategory cat : categories) {
            array.put(cat.toJson());
        }
        getPrefs().edit().putString(CATEGORIES_KEY, array.toString()).apply();
    }

    private void seedDefaultCategoriesIfNeeded() {
        if (getPrefs().getBoolean(CATEGORIES_SEEDED_KEY, false)) return;
        if (categories.isEmpty()) {
            EventCategory[] defaults = EventCategory.getDefaultCategories();
            for (EventCategory cat : defaults) {
                categories.add(cat);
            }
            saveCategories();
            Log.i(TAG, "Seeded " + defaults.length + " default categories");
        }
        getPrefs().edit().putBoolean(CATEGORIES_SEEDED_KEY, true).apply();
    }

    // ─── Legacy Migration ────────────────────────────────────────

    private void migrateLegacyEventsIfNeeded() {
        if (getPrefs().getBoolean(LEGACY_MIGRATED_KEY, false)) return;

        // Check old calendar SharedPreferences (if any exist from older code)
        SharedPreferences oldPrefs = context.getSharedPreferences("calendar_prefs", Context.MODE_PRIVATE);
        String oldJson = oldPrefs.getString("events_json", "[]");

        try {
            JSONArray oldArray = new JSONArray(oldJson);
            int migrated = 0;
            for (int i = 0; i < oldArray.length(); i++) {
                CalendarEvent event = CalendarEvent.fromLegacyJson(oldArray.getJSONObject(i));
                if (event != null) {
                    boolean exists = false;
                    for (CalendarEvent existing : events) {
                        if (existing.id.equals(event.id)) {
                            exists = true;
                            break;
                        }
                    }
                    if (!exists) {
                        events.add(event);
                        migrated++;
                    }
                }
            }
            if (migrated > 0) {
                saveEvents();
                Log.i(TAG, "Migrated " + migrated + " legacy events");
            }
        } catch (JSONException e) {
            Log.e(TAG, "Legacy migration error: " + e.getMessage());
        }

        getPrefs().edit().putBoolean(LEGACY_MIGRATED_KEY, true).apply();
    }

    // ═══════════════════════════════════════════════════════════════
    // EVENT CRUD
    // ═══════════════════════════════════════════════════════════════

    public void addEvent(CalendarEvent event) {
        events.add(0, event);
        saveEvents();
    }

    public void updateEvent(CalendarEvent event) {
        event.updatedAt = System.currentTimeMillis();
        for (int i = 0; i < events.size(); i++) {
            if (events.get(i).id.equals(event.id)) {
                events.set(i, event);
                break;
            }
        }
        saveEvents();
    }

    public void deleteEvent(String eventId) {
        events.removeIf(e -> e.id.equals(eventId));
        saveEvents();
    }

    public CalendarEvent getEventById(String id) {
        for (CalendarEvent event : events) {
            if (event.id.equals(id)) return event;
        }
        return null;
    }

    public CalendarEvent duplicateEvent(String id) {
        CalendarEvent original = getEventById(id);
        if (original == null) return null;
        CalendarEvent copy = original.duplicate();
        events.add(0, copy);
        saveEvents();
        return copy;
    }

    public List<CalendarEvent> getAllEvents() {
        return new ArrayList<>(events);
    }

    public int getEventCount() {
        return events.size();
    }

    // ═══════════════════════════════════════════════════════════════
    // RECURRING EVENT EDITING MODES
    // ═══════════════════════════════════════════════════════════════

    /**
     * Edit a single occurrence of a recurring event.
     * Creates a new non-recurring event with originalRecurrenceDate set.
     */
    public CalendarEvent editSingleOccurrence(String recurringEventId, String occurrenceDate, CalendarEvent editedData) {
        CalendarEvent original = getEventById(recurringEventId);
        if (original == null) return null;

        // Create a new event for this specific occurrence
        CalendarEvent instance = new CalendarEvent();
        instance.title = editedData.title;
        instance.description = editedData.description;
        instance.location = editedData.location;
        instance.startDate = occurrenceDate;
        instance.startTime = editedData.startTime;
        instance.endDate = occurrenceDate;
        instance.endTime = editedData.endTime;
        instance.isAllDay = editedData.isAllDay;
        instance.colorHex = editedData.colorHex;
        instance.categoryId = editedData.categoryId;
        instance.eventType = editedData.eventType;
        instance.recurrence = CalendarEvent.RECURRENCE_NONE;
        instance.reminderOffsets = new ArrayList<>(editedData.reminderOffsets);
        instance.attachmentPaths = new ArrayList<>(editedData.attachmentPaths);
        instance.notes = editedData.notes;
        instance.isStarred = editedData.isStarred;
        instance.originalRecurrenceDate = occurrenceDate;

        events.add(0, instance);
        saveEvents();
        return instance;
    }

    /**
     * Edit this and all future occurrences of a recurring event.
     * Truncates the original recurring event's end date and creates a new recurring event
     * starting from the occurrence date.
     */
    public CalendarEvent editFutureOccurrences(String recurringEventId, String fromDate, CalendarEvent editedData) {
        CalendarEvent original = getEventById(recurringEventId);
        if (original == null) return null;

        // Set the original event's recurrence end date to the day before fromDate
        Calendar fromCal = parseDateStr(fromDate);
        if (fromCal != null) {
            fromCal.add(Calendar.DAY_OF_MONTH, -1);
            original.recurrenceEndDate = String.format(java.util.Locale.US, "%04d-%02d-%02d",
                    fromCal.get(Calendar.YEAR), fromCal.get(Calendar.MONTH) + 1,
                    fromCal.get(Calendar.DAY_OF_MONTH));
            original.updatedAt = System.currentTimeMillis();
        }

        // Create a new recurring event from the fromDate
        CalendarEvent newRecurring = new CalendarEvent();
        newRecurring.title = editedData.title;
        newRecurring.description = editedData.description;
        newRecurring.location = editedData.location;
        newRecurring.startDate = fromDate;
        newRecurring.startTime = editedData.startTime;
        newRecurring.endDate = fromDate;
        newRecurring.endTime = editedData.endTime;
        newRecurring.isAllDay = editedData.isAllDay;
        newRecurring.colorHex = editedData.colorHex;
        newRecurring.categoryId = editedData.categoryId;
        newRecurring.eventType = editedData.eventType;
        newRecurring.recurrence = original.recurrence;
        newRecurring.recurrenceRule = editedData.recurrenceRule != null ? editedData.recurrenceRule : original.recurrenceRule;
        newRecurring.recurrenceEndDate = original.recurrenceEndDate; // original's old end date
        newRecurring.recurrenceCount = original.recurrenceCount;
        newRecurring.reminderOffsets = new ArrayList<>(editedData.reminderOffsets);
        newRecurring.attachmentPaths = new ArrayList<>(editedData.attachmentPaths);
        newRecurring.notes = editedData.notes;
        newRecurring.isStarred = editedData.isStarred;

        // Fix the original's end date (we modified it above)
        events.add(0, newRecurring);

        // Update original in list
        for (int i = 0; i < events.size(); i++) {
            if (events.get(i).id.equals(original.id)) {
                events.set(i, original);
                break;
            }
        }

        saveEvents();
        return newRecurring;
    }

    /**
     * Edit all occurrences of a recurring event (updates the master event).
     */
    public void editAllOccurrences(String recurringEventId, CalendarEvent editedData) {
        CalendarEvent original = getEventById(recurringEventId);
        if (original == null) return;

        original.title = editedData.title;
        original.description = editedData.description;
        original.location = editedData.location;
        original.startTime = editedData.startTime;
        original.endTime = editedData.endTime;
        original.isAllDay = editedData.isAllDay;
        original.colorHex = editedData.colorHex;
        original.categoryId = editedData.categoryId;
        original.eventType = editedData.eventType;
        original.reminderOffsets = new ArrayList<>(editedData.reminderOffsets);
        original.notes = editedData.notes;
        original.isStarred = editedData.isStarred;
        original.updatedAt = System.currentTimeMillis();

        // Update recurrence if changed
        if (editedData.recurrence != null) {
            original.recurrence = editedData.recurrence;
            original.recurrenceRule = editedData.recurrenceRule;
            original.recurrenceEndDate = editedData.recurrenceEndDate;
            original.recurrenceCount = editedData.recurrenceCount;
        }

        updateEvent(original);

        // Remove any single-instance overrides
        events.removeIf(e -> e.originalRecurrenceDate != null && isInstanceOf(e, recurringEventId));
        saveEvents();
    }

    /**
     * Delete a single occurrence of a recurring event.
     * Stores the cancelled date so the expansion logic can skip it.
     */
    public void deleteSingleOccurrence(String recurringEventId, String occurrenceDate) {
        // Create a cancelled instance marker
        CalendarEvent cancelled = new CalendarEvent();
        cancelled.title = "";
        cancelled.startDate = occurrenceDate;
        cancelled.endDate = occurrenceDate;
        cancelled.isCancelled = true;
        cancelled.originalRecurrenceDate = occurrenceDate;
        // Store parent ID in notes field as a reference
        cancelled.notes = "CANCELLED_INSTANCE_OF:" + recurringEventId;
        events.add(cancelled);
        saveEvents();
    }

    /**
     * Delete this and all future occurrences of a recurring event.
     */
    public void deleteFutureOccurrences(String recurringEventId, String fromDate) {
        CalendarEvent original = getEventById(recurringEventId);
        if (original == null) return;

        Calendar fromCal = parseDateStr(fromDate);
        if (fromCal != null) {
            fromCal.add(Calendar.DAY_OF_MONTH, -1);
            original.recurrenceEndDate = String.format(java.util.Locale.US, "%04d-%02d-%02d",
                    fromCal.get(Calendar.YEAR), fromCal.get(Calendar.MONTH) + 1,
                    fromCal.get(Calendar.DAY_OF_MONTH));
            original.updatedAt = System.currentTimeMillis();
            updateEvent(original);
        }
    }

    /**
     * Delete all occurrences of a recurring event (deletes the master + all instances).
     */
    public void deleteAllOccurrences(String recurringEventId) {
        events.removeIf(e -> e.id.equals(recurringEventId) ||
                (e.notes != null && e.notes.contains("CANCELLED_INSTANCE_OF:" + recurringEventId)));
        saveEvents();
    }

    private boolean isInstanceOf(CalendarEvent instance, String parentId) {
        // Check if this event is an edited instance of the given parent recurring event
        return instance.notes != null && instance.notes.contains("CANCELLED_INSTANCE_OF:" + parentId);
    }

    // ═══════════════════════════════════════════════════════════════
    // RECURRING EVENT EXPANSION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Expand all events (including recurring) for a date range.
     * Returns individual occurrence entries suitable for display.
     */
    public List<CalendarEvent> getExpandedEventsForRange(String startDateStr, String endDateStr) {
        List<CalendarEvent> expanded = new ArrayList<>();
        List<String> cancelledDates = getCancelledDates();

        for (CalendarEvent event : events) {
            if (event.isCancelled) continue;

            if (!event.isRecurring()) {
                // Non-recurring: check if it falls within range
                if (eventOverlapsRange(event, startDateStr, endDateStr)) {
                    expanded.add(event);
                }
            } else {
                // Recurring: expand occurrences within range
                List<CalendarEvent> occurrences = expandRecurringEvent(event, startDateStr, endDateStr, cancelledDates);
                expanded.addAll(occurrences);
            }
        }

        // Add single-instance overrides (they replace the expanded occurrence)
        // Already in the events list as non-recurring events with originalRecurrenceDate set

        // Sort by start date/time
        sortEventsByStartTime(expanded);

        return expanded;
    }

    /**
     * Get all events for a specific date (including recurring expansions).
     */
    public List<CalendarEvent> getEventsForDate(String dateStr) {
        return getExpandedEventsForRange(dateStr, dateStr);
    }

    /**
     * Get events for a full week starting from the given date.
     */
    public List<CalendarEvent> getEventsForWeek(String startDateStr) {
        Calendar cal = parseDateStr(startDateStr);
        if (cal == null) return new ArrayList<>();
        cal.add(Calendar.DAY_OF_MONTH, 6);
        String endDateStr = formatCalendarDate(cal);
        return getExpandedEventsForRange(startDateStr, endDateStr);
    }

    /**
     * Get events for a full month.
     */
    public List<CalendarEvent> getEventsForMonth(int year, int month) {
        String startDate = String.format(java.util.Locale.US, "%04d-%02d-01", year, month);
        Calendar cal = Calendar.getInstance();
        cal.set(year, month - 1, 1);
        int lastDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        String endDate = String.format(java.util.Locale.US, "%04d-%02d-%02d", year, month, lastDay);
        return getExpandedEventsForRange(startDate, endDate);
    }

    /**
     * Get events by category.
     */
    public List<CalendarEvent> getEventsByCategory(String categoryId) {
        List<CalendarEvent> result = new ArrayList<>();
        for (CalendarEvent event : events) {
            if (!event.isCancelled && categoryId.equals(event.categoryId)) {
                result.add(event);
            }
        }
        return result;
    }

    /**
     * Get upcoming events starting from now, sorted by start time.
     */
    public List<CalendarEvent> getUpcomingEvents(int limit) {
        String today = getTodayStr();
        // Expand next 90 days
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, 90);
        String endDate = formatCalendarDate(cal);

        List<CalendarEvent> expanded = getExpandedEventsForRange(today, endDate);

        // Filter out past events for today
        Calendar now = Calendar.getInstance();
        List<CalendarEvent> upcoming = new ArrayList<>();
        for (CalendarEvent event : expanded) {
            if (event.isAllDay || !event.hasStartTime()) {
                upcoming.add(event);
            } else {
                Calendar eventStart = event.getStartCalendar();
                if (eventStart != null && !eventStart.before(now)) {
                    upcoming.add(event);
                } else if (event.startDate.compareTo(today) > 0) {
                    upcoming.add(event);
                }
            }
        }

        if (limit > 0 && upcoming.size() > limit) {
            return upcoming.subList(0, limit);
        }
        return upcoming;
    }

    /**
     * Search events by title and description.
     */
    public List<CalendarEvent> searchEvents(String query) {
        if (query == null || query.trim().isEmpty()) return getAllEvents();
        List<CalendarEvent> results = new ArrayList<>();
        for (CalendarEvent event : events) {
            if (!event.isCancelled && event.matchesSearch(query)) {
                results.add(event);
            }
        }
        sortEventsByStartTime(results);
        return results;
    }

    /**
     * Get upcoming occurrences of a recurring event for "View all occurrences".
     */
    public List<String> getUpcomingOccurrencesOfEvent(String eventId, int limit) {
        List<String> dates = new ArrayList<>();
        CalendarEvent event = getEventById(eventId);
        if (event == null || !event.isRecurring()) return dates;

        Calendar baseCal = parseDateStr(event.startDate);
        if (baseCal == null) return dates;

        Calendar endLimit = Calendar.getInstance();
        endLimit.add(Calendar.YEAR, 2);

        Calendar recurrenceEndCal = null;
        if (event.recurrenceEndDate != null && !event.recurrenceEndDate.isEmpty()) {
            recurrenceEndCal = parseDateStr(event.recurrenceEndDate);
        }

        int maxCount = event.recurrenceCount > 0 ? event.recurrenceCount : (limit > 0 ? limit : 50);
        int interval = 1;
        List<Integer> daysOfWeek = null;

        if (CalendarEvent.RECURRENCE_CUSTOM.equals(event.recurrence) && event.recurrenceRule != null) {
            try {
                JSONObject rule = new JSONObject(event.recurrenceRule);
                interval = rule.optInt("interval", 1);
                JSONArray daysArr = rule.optJSONArray("daysOfWeek");
                if (daysArr != null) {
                    daysOfWeek = new ArrayList<>();
                    for (int i = 0; i < daysArr.length(); i++) daysOfWeek.add(daysArr.getInt(i));
                }
            } catch (JSONException e) { /* ignore */ }
        }

        Calendar current = (Calendar) baseCal.clone();
        int count = 0;

        while (count < maxCount && dates.size() < (limit > 0 ? limit : 50)) {
            if (recurrenceEndCal != null && current.after(recurrenceEndCal)) break;
            if (current.after(endLimit)) break;

            boolean matches = true;
            if (daysOfWeek != null) {
                matches = daysOfWeek.contains(current.get(Calendar.DAY_OF_WEEK));
            }

            if (matches) {
                dates.add(formatCalendarDate(current));
            }

            switch (event.recurrence) {
                case CalendarEvent.RECURRENCE_DAILY: current.add(Calendar.DAY_OF_YEAR, interval); break;
                case CalendarEvent.RECURRENCE_WEEKLY: current.add(Calendar.WEEK_OF_YEAR, interval); break;
                case CalendarEvent.RECURRENCE_MONTHLY: current.add(Calendar.MONTH, interval); break;
                case CalendarEvent.RECURRENCE_YEARLY: current.add(Calendar.YEAR, interval); break;
                case CalendarEvent.RECURRENCE_CUSTOM:
                    if (daysOfWeek != null) current.add(Calendar.DAY_OF_YEAR, 1);
                    else current.add(Calendar.DAY_OF_YEAR, interval);
                    break;
                default: count = maxCount; break;
            }
            count++;
        }
        return dates;
    }

    // ─── Recent Searches ─────────────────────────────────────────

    public List<String> getRecentSearches() {
        List<String> searches = new ArrayList<>();
        String json = getPrefs().getString(RECENT_SEARCHES_KEY, "[]");
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) searches.add(array.getString(i));
        } catch (JSONException e) { /* ignore */ }
        return searches;
    }

    public void addRecentSearch(String query) {
        if (query == null || query.trim().isEmpty()) return;
        List<String> searches = getRecentSearches();
        searches.remove(query);
        searches.add(0, query);
        while (searches.size() > MAX_RECENT_SEARCHES) searches.remove(searches.size() - 1);
        JSONArray array = new JSONArray();
        for (String s : searches) array.put(s);
        getPrefs().edit().putString(RECENT_SEARCHES_KEY, array.toString()).apply();
    }

    public void clearRecentSearches() {
        getPrefs().edit().putString(RECENT_SEARCHES_KEY, "[]").apply();
    }

    /**
     * Get the count of events on a specific date (optimized for calendar grid display).
     */
    public int getEventCountForDate(String dateStr) {
        return getEventsForDate(dateStr).size();
    }

    /**
     * Get a map of date -> event count for a month (optimized for rendering month grid).
     */
    public Map<String, List<CalendarEvent>> getEventMapForMonth(int year, int month) {
        Map<String, List<CalendarEvent>> map = new LinkedHashMap<>();
        List<CalendarEvent> monthEvents = getEventsForMonth(year, month);
        for (CalendarEvent event : monthEvents) {
            String date = event.startDate;
            if (!map.containsKey(date)) {
                map.put(date, new ArrayList<>());
            }
            map.get(date).add(event);
        }
        return map;
    }

    /**
     * Get today's event count (for the header pill).
     */
    public int getTodayEventCount() {
        return getEventsForDate(getTodayStr()).size();
    }

    // ─── Recurring Expansion Logic ───────────────────────────────

    private List<CalendarEvent> expandRecurringEvent(CalendarEvent event, String rangeStart, String rangeEnd,
                                                      List<String> cancelledDates) {
        List<CalendarEvent> occurrences = new ArrayList<>();
        if (!event.isRecurring()) return occurrences;

        Calendar baseCal = parseDateStr(event.startDate);
        if (baseCal == null) return occurrences;

        Calendar rangeStartCal = parseDateStr(rangeStart);
        Calendar rangeEndCal = parseDateStr(rangeEnd);
        if (rangeStartCal == null || rangeEndCal == null) return occurrences;

        // Set end of the range to end of day
        rangeEndCal.set(Calendar.HOUR_OF_DAY, 23);
        rangeEndCal.set(Calendar.MINUTE, 59);

        // Determine recurrence end
        Calendar recurrenceEndCal = null;
        if (event.recurrenceEndDate != null && !event.recurrenceEndDate.isEmpty()) {
            recurrenceEndCal = parseDateStr(event.recurrenceEndDate);
        }

        int maxCount = event.recurrenceCount > 0 ? event.recurrenceCount : 1000;
        int count = 0;

        // Parse custom recurrence rule
        int interval = 1;
        List<Integer> daysOfWeek = null;
        if (CalendarEvent.RECURRENCE_CUSTOM.equals(event.recurrence) && event.recurrenceRule != null && !event.recurrenceRule.isEmpty()) {
            try {
                JSONObject rule = new JSONObject(event.recurrenceRule);
                interval = rule.optInt("interval", 1);
                JSONArray daysArr = rule.optJSONArray("daysOfWeek");
                if (daysArr != null) {
                    daysOfWeek = new ArrayList<>();
                    for (int i = 0; i < daysArr.length(); i++) {
                        daysOfWeek.add(daysArr.getInt(i));
                    }
                }
            } catch (JSONException e) {
                // ignore, use defaults
            }
        }

        Calendar current = (Calendar) baseCal.clone();

        while (count < maxCount) {
            // Check if past recurrence end
            if (recurrenceEndCal != null && current.after(recurrenceEndCal)) break;

            // Check if past range end
            if (current.after(rangeEndCal)) break;

            String currentDateStr = formatCalendarDate(current);

            // Check if within range and not cancelled
            if (!current.before(rangeStartCal) && !current.after(rangeEndCal)) {
                if (!cancelledDates.contains(event.id + ":" + currentDateStr) &&
                        !hasEditedInstance(event.id, currentDateStr)) {

                    // For custom recurrence with daysOfWeek, check if current day matches
                    boolean matches = true;
                    if (daysOfWeek != null) {
                        int dayOfWeek = current.get(Calendar.DAY_OF_WEEK);
                        matches = daysOfWeek.contains(dayOfWeek);
                    }

                    if (matches) {
                        CalendarEvent occurrence = createOccurrence(event, currentDateStr);
                        occurrences.add(occurrence);
                    }
                }
            }

            // Advance to next occurrence
            switch (event.recurrence) {
                case CalendarEvent.RECURRENCE_DAILY:
                    current.add(Calendar.DAY_OF_YEAR, interval);
                    break;
                case CalendarEvent.RECURRENCE_WEEKLY:
                    current.add(Calendar.WEEK_OF_YEAR, interval);
                    break;
                case CalendarEvent.RECURRENCE_MONTHLY:
                    current.add(Calendar.MONTH, interval);
                    break;
                case CalendarEvent.RECURRENCE_YEARLY:
                    current.add(Calendar.YEAR, interval);
                    break;
                case CalendarEvent.RECURRENCE_CUSTOM:
                    if (daysOfWeek != null) {
                        // For custom with days of week, advance day by day
                        current.add(Calendar.DAY_OF_YEAR, 1);
                    } else {
                        current.add(Calendar.DAY_OF_YEAR, interval);
                    }
                    break;
                default:
                    count = maxCount; // Stop
                    break;
            }
            count++;
        }

        return occurrences;
    }

    /**
     * Create a display occurrence from a recurring event for a specific date.
     * The occurrence shares the master event's ID but shows the specific date.
     */
    private CalendarEvent createOccurrence(CalendarEvent master, String occurrenceDate) {
        CalendarEvent occ = new CalendarEvent();
        occ.id = master.id; // Keep same ID (identifies the master)
        occ.title = master.title;
        occ.description = master.description;
        occ.location = master.location;
        occ.startDate = occurrenceDate;
        occ.startTime = master.startTime;
        occ.endDate = occurrenceDate;
        occ.endTime = master.endTime;
        occ.isAllDay = master.isAllDay;
        occ.colorHex = master.colorHex;
        occ.categoryId = master.categoryId;
        occ.eventType = master.eventType;
        occ.recurrence = master.recurrence;
        occ.recurrenceRule = master.recurrenceRule;
        occ.reminderOffsets = master.reminderOffsets;
        occ.attachmentPaths = master.attachmentPaths;
        occ.notes = master.notes;
        occ.isStarred = master.isStarred;
        occ.createdAt = master.createdAt;
        occ.updatedAt = master.updatedAt;
        return occ;
    }

    private boolean hasEditedInstance(String masterEventId, String date) {
        for (CalendarEvent event : events) {
            if (event.originalRecurrenceDate != null && event.originalRecurrenceDate.equals(date) &&
                    !event.isCancelled) {
                return true;
            }
        }
        return false;
    }

    private List<String> getCancelledDates() {
        List<String> cancelled = new ArrayList<>();
        for (CalendarEvent event : events) {
            if (event.isCancelled && event.originalRecurrenceDate != null) {
                // Extract parent ID from notes
                String parentId = "";
                if (event.notes != null && event.notes.startsWith("CANCELLED_INSTANCE_OF:")) {
                    parentId = event.notes.substring("CANCELLED_INSTANCE_OF:".length());
                }
                cancelled.add(parentId + ":" + event.originalRecurrenceDate);
            }
        }
        return cancelled;
    }

    // ═══════════════════════════════════════════════════════════════
    // CATEGORY CRUD
    // ═══════════════════════════════════════════════════════════════

    public List<EventCategory> getAllCategories() {
        return new ArrayList<>(categories);
    }

    public EventCategory getCategoryById(String id) {
        for (EventCategory cat : categories) {
            if (cat.id.equals(id)) return cat;
        }
        return null;
    }

    public EventCategory getCategoryByName(String name) {
        for (EventCategory cat : categories) {
            if (cat.name.equalsIgnoreCase(name)) return cat;
        }
        return null;
    }

    public void addCategory(EventCategory category) {
        categories.add(category);
        saveCategories();
    }

    public void updateCategory(EventCategory category) {
        for (int i = 0; i < categories.size(); i++) {
            if (categories.get(i).id.equals(category.id)) {
                categories.set(i, category);
                break;
            }
        }
        saveCategories();
    }

    /**
     * Delete a category and reassign all its events to "Others".
     */
    public void deleteCategory(String categoryId) {
        // Don't allow deleting "Others"
        if (EventCategory.CAT_OTHERS.equals(categoryId)) return;

        // Reassign events
        for (CalendarEvent event : events) {
            if (categoryId.equals(event.categoryId)) {
                event.categoryId = EventCategory.CAT_OTHERS;
                event.updatedAt = System.currentTimeMillis();
            }
        }
        saveEvents();

        categories.removeIf(c -> c.id.equals(categoryId));
        saveCategories();
    }

    /**
     * Get the count of events using a specific category.
     */
    public int getCategoryEventCount(String categoryId) {
        int count = 0;
        for (CalendarEvent event : events) {
            if (!event.isCancelled && categoryId.equals(event.categoryId)) count++;
        }
        return count;
    }

    // ═══════════════════════════════════════════════════════════════
    // CALENDAR SETTINGS
    // ═══════════════════════════════════════════════════════════════

    public CalendarSettings getSettings() {
        String json = getPrefs().getString(SETTINGS_KEY, "");
        if (json.isEmpty()) return new CalendarSettings();
        try {
            return CalendarSettings.fromJson(new JSONObject(json));
        } catch (JSONException e) {
            return new CalendarSettings();
        }
    }

    public void saveSettings(CalendarSettings settings) {
        getPrefs().edit().putString(SETTINGS_KEY, settings.toJson().toString()).apply();
    }

    // ═══════════════════════════════════════════════════════════════
    // SERVER SYNC HELPERS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Replace all events with data from server sync.
     * Converts from legacy format if needed.
     */
    public void replaceAllFromServerSync(JSONArray serverEvents) {
        events.clear();
        for (int i = 0; i < serverEvents.length(); i++) {
            try {
                JSONObject json = serverEvents.getJSONObject(i);
                CalendarEvent event;
                // Try new format first, fall back to legacy
                if (json.has("startDate")) {
                    event = CalendarEvent.fromJson(json);
                } else {
                    event = CalendarEvent.fromLegacyJson(json);
                }
                if (event != null) events.add(event);
            } catch (JSONException e) {
                Log.e(TAG, "Sync parse error at index " + i, e);
            }
        }
        saveEvents();
        Log.i(TAG, "Synced " + events.size() + " events from server");
    }

    /**
     * Get all events as JSON array for sending to server.
     */
    public JSONArray getAllEventsAsJson() {
        JSONArray array = new JSONArray();
        for (CalendarEvent event : events) {
            array.put(event.toJson());
        }
        return array;
    }

    /**
     * Get all events in legacy format for backward-compatible server sync.
     */
    public JSONArray getAllEventsAsLegacyJson() {
        JSONArray array = new JSONArray();
        for (CalendarEvent event : events) {
            if (!event.isCancelled) {
                array.put(event.toLegacyJson());
            }
        }
        return array;
    }

    // ═══════════════════════════════════════════════════════════════
    // UTILITY METHODS
    // ═══════════════════════════════════════════════════════════════

    private boolean eventOverlapsRange(CalendarEvent event, String rangeStart, String rangeEnd) {
        if (event.startDate == null) return false;
        String eventEnd = event.endDate != null ? event.endDate : event.startDate;
        // Event overlaps range if: event.start <= rangeEnd AND event.end >= rangeStart
        return event.startDate.compareTo(rangeEnd) <= 0 && eventEnd.compareTo(rangeStart) >= 0;
    }

    public void sortEventsByStartTime(List<CalendarEvent> list) {
        Collections.sort(list, (a, b) -> {
            // All-day events first
            if (a.isAllDay != b.isAllDay) return a.isAllDay ? -1 : 1;

            // Compare dates first
            int dateCompare = a.startDate.compareTo(b.startDate);
            if (dateCompare != 0) return dateCompare;

            // Compare times
            String aTime = a.startTime != null ? a.startTime : "00:00";
            String bTime = b.startTime != null ? b.startTime : "00:00";
            return aTime.compareTo(bTime);
        });
    }

    private static Calendar parseDateStr(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return null;
        try {
            String[] parts = dateStr.split("-");
            Calendar cal = Calendar.getInstance();
            cal.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1, Integer.parseInt(parts[2]));
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            return cal;
        } catch (Exception e) {
            return null;
        }
    }

    private static String formatCalendarDate(Calendar cal) {
        return String.format(java.util.Locale.US, "%04d-%02d-%02d",
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH));
    }

    private static String getTodayStr() {
        return formatCalendarDate(Calendar.getInstance());
    }

    // ═══════════════════════════════════════════════════════════════
    // WIDGET API
    // ═══════════════════════════════════════════════════════════════

    /**
     * Widget API: Get today's events for a home screen widget.
     * @return List of events for today, sorted by time
     */
    public List<CalendarEvent> getWidgetTodayEvents() {
        return getEventsForDate(getTodayStr());
    }

    /**
     * Widget API: Get the next upcoming event.
     * @return The next upcoming event, or null if none
     */
    public CalendarEvent getWidgetNextEvent() {
        List<CalendarEvent> upcoming = getUpcomingEvents(1);
        return upcoming.isEmpty() ? null : upcoming.get(0);
    }

    /**
     * Widget API: Get a summary of upcoming events count by day for the next N days.
     * @param days Number of days to look ahead
     * @return Map of date strings to event counts
     */
    public java.util.Map<String, Integer> getWidgetDayEventCounts(int days) {
        java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        Calendar cal = Calendar.getInstance();
        for (int i = 0; i < days; i++) {
            String dateStr = formatCalendarDate(cal);
            counts.put(dateStr, getEventsForDate(dateStr).size());
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
        return counts;
    }

    /**
     * Widget API: Get upcoming birthdays and anniversaries within the next N days.
     * @param days Number of days to look ahead
     * @return List of birthday/anniversary events
     */
    public List<CalendarEvent> getWidgetCelebrations(int days) {
        List<CalendarEvent> celebrations = new java.util.ArrayList<>();
        Calendar today = Calendar.getInstance();
        Calendar limit = (Calendar) today.clone();
        limit.add(Calendar.DAY_OF_YEAR, days);

        for (CalendarEvent event : events) {
            if (event.isBirthdayType() || event.isAnniversaryType()) {
                Calendar evCal = parseDateStr(event.startDate);
                if (evCal != null) {
                    evCal.set(Calendar.YEAR, today.get(Calendar.YEAR));
                    if (evCal.before(today)) evCal.add(Calendar.YEAR, 1);
                    if (!evCal.after(limit)) {
                        celebrations.add(event);
                    }
                }
            }
        }
        return celebrations;
    }

    /**
     * Widget API: Get event counts by category for the current month.
     * @return Map of category names to event counts
     */
    public java.util.Map<String, Integer> getWidgetCategoryCounts() {
        Calendar cal = Calendar.getInstance();
        List<CalendarEvent> monthEvents = getEventsForMonth(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1);
        java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        for (CalendarEvent ev : monthEvents) {
            String cat = ev.category != null && !ev.category.isEmpty() ? ev.category : "Uncategorized";
            counts.put(cat, counts.getOrDefault(cat, 0) + 1);
        }
        return counts;
    }
}
