package com.prajwal.myfirstapp;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Complete CalendarEvent data model.
 *
 * Supports: titles, descriptions, locations, date/time ranges, all-day events,
 * colors, categories, event types, recurrence rules (with custom schedules),
 * multiple reminder offsets, attachments, rich notes, completion (for reminders),
 * cancellation, starring, and recurring event instance tracking.
 */
public class CalendarEvent {

    // ─── Fields ──────────────────────────────────────────────────

    public String id;
    public String title;
    public String description;
    public String location;                    // Text-based location
    public String startDate;                   // "YYYY-MM-DD"
    public String startTime;                   // "HH:MM" (24h) or null for all-day
    public String endDate;                     // "YYYY-MM-DD"
    public String endTime;                     // "HH:MM" (24h) or null for all-day
    public boolean isAllDay;
    public String colorHex;                    // e.g. "#3B82F6"
    public String categoryId;                  // References EventCategory.id
    public String eventType;                   // personal/work/study/health/social/reminder/birthday/anniversary/holiday/other
    public String recurrence;                  // none/daily/weekly/monthly/yearly/custom
    public String recurrenceRule;              // JSON string: {"interval":1,"daysOfWeek":[1,3,5],"endDate":"2026-12-31","endCount":10}
    public String recurrenceEndDate;           // "YYYY-MM-DD" or null
    public int recurrenceCount;                // 0 = infinite
    public List<Integer> reminderOffsets;       // Minutes before event (e.g. [10, 60, 1440])
    public List<String> attachmentPaths;        // File paths
    public String notes;                       // Rich text notes
    public boolean isCompleted;                // For reminder-type events
    public boolean isCancelled;
    public boolean isStarred;
    public String originalRecurrenceDate;      // "YYYY-MM-DD" for edited single instances of recurring events
    public long createdAt;
    public long updatedAt;

    // ─── Compatibility Aliases ───────────────────────────────────
    // These provide backward compatibility for code using old field names

    /** Alias for startDate (backward compatibility) */
    public String date;

    /** Alias for categoryId (backward compatibility) */
    public String category;

    /** Computed status field (backward compatibility) */
    public String status;

    // ─── Constants: Event Types ──────────────────────────────────

    public static final String TYPE_PERSONAL    = "personal";
    public static final String TYPE_WORK        = "work";
    public static final String TYPE_STUDY       = "study";
    public static final String TYPE_HEALTH      = "health";
    public static final String TYPE_SOCIAL      = "social";
    public static final String TYPE_REMINDER    = "reminder";
    public static final String TYPE_BIRTHDAY    = "birthday";
    public static final String TYPE_ANNIVERSARY = "anniversary";
    public static final String TYPE_HOLIDAY     = "holiday";
    public static final String TYPE_OTHER       = "other";

    // Compatibility aliases
    public static final String TYPE_EVENT       = TYPE_PERSONAL;  // Alias for backward compatibility
    public static final String TYPE_BUSINESS    = TYPE_WORK;       // Alias for backward compatibility

    public static final String[] EVENT_TYPES = {
        TYPE_PERSONAL, TYPE_WORK, TYPE_STUDY, TYPE_HEALTH, TYPE_SOCIAL,
        TYPE_REMINDER, TYPE_BIRTHDAY, TYPE_ANNIVERSARY, TYPE_HOLIDAY, TYPE_OTHER
    };

    public static final String[] EVENT_TYPE_LABELS = {
        "Personal", "Work", "Study", "Health", "Social",
        "Reminder", "Birthday", "Anniversary", "Holiday", "Other"
    };

    public static final String[] EVENT_TYPE_ICONS = {
        "👤", "💼", "📚", "💪", "🎉",
        "⏰", "🎂", "💍", "🎄", "📌"
    };

    // ─── Constants: Recurrence ───────────────────────────────────

    public static final String RECURRENCE_NONE    = "none";
    public static final String RECURRENCE_DAILY   = "daily";
    public static final String RECURRENCE_WEEKLY  = "weekly";
    public static final String RECURRENCE_MONTHLY = "monthly";
    public static final String RECURRENCE_YEARLY  = "yearly";
    public static final String RECURRENCE_CUSTOM  = "custom";

    public static final String[] RECURRENCE_OPTIONS = {
        RECURRENCE_NONE, RECURRENCE_DAILY, RECURRENCE_WEEKLY,
        RECURRENCE_MONTHLY, RECURRENCE_YEARLY, RECURRENCE_CUSTOM
    };

    public static final String[] RECURRENCE_LABELS = {
        "None", "Daily", "Weekly", "Monthly", "Yearly", "Custom"
    };

    // ─── Default Colors ──────────────────────────────────────────

    public static final String COLOR_BLUE   = "#3B82F6";
    public static final String COLOR_RED    = "#EF4444";
    public static final String COLOR_GREEN  = "#22C55E";
    public static final String COLOR_PURPLE = "#8B5CF6";
    public static final String COLOR_ORANGE = "#F97316";
    public static final String COLOR_PINK   = "#EC4899";
    public static final String COLOR_YELLOW = "#EAB308";
    public static final String COLOR_TEAL   = "#14B8A6";
    public static final String COLOR_INDIGO = "#6366F1";
    public static final String COLOR_AMBER  = "#F59E0B";
    public static final String COLOR_GREY   = "#6B7280";
    public static final String COLOR_CYAN   = "#06B6D4";

    // ─── Common Reminder Offsets (minutes) ───────────────────────

    public static final int REMINDER_NONE       = -1;
    public static final int REMINDER_AT_TIME    = 0;
    public static final int REMINDER_5_MIN      = 5;
    public static final int REMINDER_10_MIN     = 10;
    public static final int REMINDER_15_MIN     = 15;
    public static final int REMINDER_30_MIN     = 30;
    public static final int REMINDER_1_HOUR     = 60;
    public static final int REMINDER_2_HOURS    = 120;
    public static final int REMINDER_1_DAY      = 1440;
    public static final int REMINDER_2_DAYS     = 2880;
    public static final int REMINDER_1_WEEK     = 10080;

    public static final int[] REMINDER_PRESETS = {
        REMINDER_AT_TIME, REMINDER_5_MIN, REMINDER_10_MIN, REMINDER_15_MIN,
        REMINDER_30_MIN, REMINDER_1_HOUR, REMINDER_2_HOURS, REMINDER_1_DAY,
        REMINDER_2_DAYS, REMINDER_1_WEEK
    };

    public static final String[] REMINDER_LABELS = {
        "At time of event", "5 minutes before", "10 minutes before", "15 minutes before",
        "30 minutes before", "1 hour before", "2 hours before", "1 day before",
        "2 days before", "1 week before"
    };

    // ─── Constructors ────────────────────────────────────────────

    public CalendarEvent() {
        this.id = "evt_" + UUID.randomUUID().toString().substring(0, 8);
        this.title = "";
        this.description = "";
        this.location = "";
        this.startDate = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        this.startTime = null;
        this.endDate = this.startDate;
        this.endTime = null;
        this.isAllDay = false;
        this.colorHex = COLOR_BLUE;
        this.categoryId = "cat_personal";
        this.eventType = TYPE_PERSONAL;
        this.recurrence = RECURRENCE_NONE;
        this.recurrenceRule = "";
        this.recurrenceEndDate = null;
        this.recurrenceCount = 0;
        this.reminderOffsets = new ArrayList<>();
        this.attachmentPaths = new ArrayList<>();
        this.notes = "";
        this.isCompleted = false;
        this.isCancelled = false;
        this.isStarred = false;
        this.originalRecurrenceDate = null;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();

        // Initialize compatibility aliases
        this.date = this.startDate;
        this.category = this.categoryId;
        this.status = "active";
    }

    public CalendarEvent(String title, String startDate) {
        this();
        this.title = title != null ? title : "";
        this.startDate = startDate != null ? startDate : this.startDate;
        this.endDate = this.startDate;
        syncAliases();
    }

    /**
     * Synchronize compatibility alias fields with primary fields.
     * Call this after modifying primary fields to keep aliases in sync.
     */
    public void syncAliases() {
        // date <-> startDate
        this.date = this.startDate;
        // category <-> categoryId
        this.category = this.categoryId;
        // status based on isCompleted/isCancelled
        if (this.isCancelled) {
            this.status = "cancelled";
        } else if (this.isCompleted) {
            this.status = "completed";
        } else {
            this.status = "active";
        }
    }

    // ─── Date / Time Helpers ─────────────────────────────────────

    public boolean hasStartTime() {
        return startTime != null && !startTime.isEmpty() && !"null".equals(startTime);
    }

    public boolean hasEndTime() {
        return endTime != null && !endTime.isEmpty() && !"null".equals(endTime);
    }

    public boolean hasLocation() {
        return location != null && !location.isEmpty();
    }

    public boolean hasDescription() {
        return description != null && !description.isEmpty();
    }

    public boolean hasNotes() {
        return notes != null && !notes.isEmpty();
    }

    public boolean hasAttachments() {
        return attachmentPaths != null && !attachmentPaths.isEmpty();
    }

    public boolean isRecurring() {
        return recurrence != null && !RECURRENCE_NONE.equals(recurrence);
    }

    public boolean hasReminders() {
        return reminderOffsets != null && !reminderOffsets.isEmpty();
    }

    public boolean isReminderType() {
        return TYPE_REMINDER.equals(eventType);
    }

    public boolean isBirthdayType() {
        return TYPE_BIRTHDAY.equals(eventType);
    }

    public boolean isAnniversaryType() {
        return TYPE_ANNIVERSARY.equals(eventType);
    }

    public boolean isHolidayType() {
        return TYPE_HOLIDAY.equals(eventType);
    }

    public boolean isEditedRecurrenceInstance() {
        return originalRecurrenceDate != null && !originalRecurrenceDate.isEmpty();
    }

    /**
     * Get the start datetime as a Calendar object.
     */
    public Calendar getStartCalendar() {
        return parseDateTime(startDate, startTime);
    }

    /**
     * Get the end datetime as a Calendar object.
     */
    public Calendar getEndCalendar() {
        return parseDateTime(endDate, endTime);
    }

    /**
     * Get duration in minutes. Returns 0 for all-day events.
     */
    public int getDurationMinutes() {
        if (isAllDay || !hasStartTime() || !hasEndTime()) return 0;
        Calendar start = getStartCalendar();
        Calendar end = getEndCalendar();
        if (start == null || end == null) return 0;
        long diffMs = end.getTimeInMillis() - start.getTimeInMillis();
        return (int) (diffMs / (60 * 1000));
    }

    /**
     * Get a formatted time range string like "10:00 AM – 11:30 AM" or "All Day".
     */
    public String getFormattedTimeRange() {
        if (isAllDay) return "All Day";
        if (!hasStartTime()) return "All Day";
        String start = formatTime(startTime);
        if (hasEndTime()) {
            return start + " – " + formatTime(endTime);
        }
        return start;
    }

    /**
     * Get formatted start date like "Feb 26, 2026".
     */
    public String getFormattedStartDate() {
        return formatDate(startDate);
    }

    /**
     * Get formatted date range for multi-day events.
     */
    public String getFormattedDateRange() {
        if (startDate.equals(endDate)) return getFormattedStartDate();
        return formatDate(startDate) + " – " + formatDate(endDate);
    }

    /**
     * Get display label for event type.
     */
    public String getEventTypeLabel() {
        return getEventTypeLabel(this.eventType);
    }

    /**
     * Get display label for a given event type string (static overload).
     */
    public static String getEventTypeLabel(String type) {
        if (type == null) return "Other";
        for (int i = 0; i < EVENT_TYPES.length; i++) {
            if (EVENT_TYPES[i].equals(type)) return EVENT_TYPE_LABELS[i];
        }
        return "Other";
    }

    /**
     * Get icon for event type.
     */
    public String getEventTypeIcon() {
        return getEventTypeIcon(this.eventType);
    }

    /**
     * Get icon for a given event type string (static overload).
     */
    public static String getEventTypeIcon(String type) {
        if (type == null) return "📌";
        for (int i = 0; i < EVENT_TYPES.length; i++) {
            if (EVENT_TYPES[i].equals(type)) return EVENT_TYPE_ICONS[i];
        }
        return "📌";
    }

    /**
     * Get recurrence label.
     */
    public String getRecurrenceLabel() {
        for (int i = 0; i < RECURRENCE_OPTIONS.length; i++) {
            if (RECURRENCE_OPTIONS[i].equals(recurrence)) return RECURRENCE_LABELS[i];
        }
        return "None";
    }

    /**
     * Get a human-readable duration label like "1 hour 30 minutes".
     */
    public String getDurationLabel() {
        int minutes = getDurationMinutes();
        if (minutes <= 0) return "";
        if (minutes < 60) return minutes + " minute" + (minutes != 1 ? "s" : "");
        int hours = minutes / 60;
        int mins = minutes % 60;
        if (mins == 0) return hours + " hour" + (hours > 1 ? "s" : "");
        return hours + " hour" + (hours > 1 ? "s" : "") + " " + mins + " min";
    }

    /**
     * Get a human-readable recurrence summary.
     * E.g. "Repeats every 2 weeks on Monday and Wednesday, until December 31 2026"
     */
    public String getRecurrenceSummary() {
        if (!isRecurring()) return "Does not repeat";

        StringBuilder sb = new StringBuilder("Repeats ");

        if (RECURRENCE_CUSTOM.equals(recurrence) && recurrenceRule != null && !recurrenceRule.isEmpty()) {
            try {
                JSONObject rule = new JSONObject(recurrenceRule);
                int interval = rule.optInt("interval", 1);
                String unit = rule.optString("unit", "days");
                JSONArray daysArr = rule.optJSONArray("daysOfWeek");

                if (interval == 1) {
                    String singular = unit.endsWith("s") ? unit.substring(0, unit.length() - 1) : unit;
                    sb.append("every ").append(singular);
                } else {
                    sb.append("every ").append(interval).append(" ").append(unit);
                }

                if (daysArr != null && daysArr.length() > 0) {
                    String[] dayNames = {"", "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
                    sb.append(" on ");
                    for (int i = 0; i < daysArr.length(); i++) {
                        if (i > 0) sb.append(i == daysArr.length() - 1 ? " and " : ", ");
                        int dayNum = daysArr.optInt(i);
                        if (dayNum >= 1 && dayNum <= 7) sb.append(dayNames[dayNum]);
                    }
                }
            } catch (JSONException e) {
                sb.append("custom schedule");
            }
        } else {
            switch (recurrence) {
                case RECURRENCE_DAILY:
                    sb.append("every day");
                    break;
                case RECURRENCE_WEEKLY:
                    sb.append("every week");
                    if (startDate != null) {
                        Calendar cal = getStartCalendar();
                        if (cal != null) {
                            String dayName = new SimpleDateFormat("EEEE", Locale.US).format(cal.getTime());
                            sb.append(" on ").append(dayName);
                        }
                    }
                    break;
                case RECURRENCE_MONTHLY:
                    sb.append("every month");
                    if (startDate != null) {
                        try {
                            int day = Integer.parseInt(startDate.split("-")[2]);
                            sb.append(" on the ").append(day).append(getOrdinalSuffix(day));
                        } catch (Exception e) { /* ignore */ }
                    }
                    break;
                case RECURRENCE_YEARLY:
                    sb.append("every year");
                    if (startDate != null) sb.append(" on ").append(formatDate(startDate));
                    break;
                default:
                    sb.append(recurrence);
                    break;
            }
        }

        if (recurrenceEndDate != null && !recurrenceEndDate.isEmpty()) {
            sb.append(", until ").append(formatDate(recurrenceEndDate));
        } else if (recurrenceCount > 0) {
            sb.append(", ").append(recurrenceCount).append(" time").append(recurrenceCount != 1 ? "s" : "");
        }

        return sb.toString();
    }

    private static String getOrdinalSuffix(int n) {
        if (n >= 11 && n <= 13) return "th";
        switch (n % 10) {
            case 1: return "st";
            case 2: return "nd";
            case 3: return "rd";
            default: return "th";
        }
    }

    /**
     * Check if this event falls on a specific date (ignoring recurrence).
     */
    public boolean isOnDate(String dateStr) {
        if (startDate == null) return false;
        // For multi-day events, check if date falls within range
        if (!startDate.equals(endDate) && endDate != null) {
            return dateStr.compareTo(startDate) >= 0 && dateStr.compareTo(endDate) <= 0;
        }
        return startDate.equals(dateStr);
    }

    /**
     * Check if this event is happening now.
     */
    public boolean isHappeningNow() {
        if (isCancelled || isCompleted) return false;
        Calendar now = Calendar.getInstance();
        Calendar start = getStartCalendar();
        Calendar end = getEndCalendar();
        if (start == null) return false;
        if (end == null) {
            // No end time — consider it a 1-hour event
            end = (Calendar) start.clone();
            end.add(Calendar.HOUR_OF_DAY, 1);
        }
        return now.after(start) && now.before(end);
    }

    /**
     * Get background color (darker shade) from event color.
     */
    public int getBackgroundColor() {
        try {
            int color = android.graphics.Color.parseColor(colorHex);
            int r = (android.graphics.Color.red(color) * 40) / 255;
            int g = (android.graphics.Color.green(color) * 40) / 255;
            int b = (android.graphics.Color.blue(color) * 40) / 255;
            return android.graphics.Color.rgb(r, g, b);
        } catch (Exception e) {
            return 0xFF1E293B;
        }
    }

    /**
     * Get the accent color parsed as int.
     */
    public int getAccentColor() {
        try {
            return android.graphics.Color.parseColor(colorHex);
        } catch (Exception e) {
            return 0xFF3B82F6;
        }
    }

    // ─── Search Matching ─────────────────────────────────────────

    public boolean matchesSearch(String query) {
        if (query == null || query.isEmpty()) return true;
        String q = query.toLowerCase();
        if (title != null && title.toLowerCase().contains(q)) return true;
        if (description != null && description.toLowerCase().contains(q)) return true;
        if (location != null && location.toLowerCase().contains(q)) return true;
        if (notes != null && notes.toLowerCase().contains(q)) return true;
        if (eventType != null && getEventTypeLabel().toLowerCase().contains(q)) return true;
        return false;
    }

    // ─── Duplicate ───────────────────────────────────────────────

    public CalendarEvent duplicate() {
        CalendarEvent copy = new CalendarEvent();
        copy.title = this.title + " (Copy)";
        copy.description = this.description;
        copy.location = this.location;
        copy.startDate = this.startDate;
        copy.startTime = this.startTime;
        copy.endDate = this.endDate;
        copy.endTime = this.endTime;
        copy.isAllDay = this.isAllDay;
        copy.colorHex = this.colorHex;
        copy.categoryId = this.categoryId;
        copy.eventType = this.eventType;
        copy.recurrence = RECURRENCE_NONE; // Don't duplicate recurrence
        copy.recurrenceRule = "";
        copy.recurrenceEndDate = null;
        copy.recurrenceCount = 0;
        copy.reminderOffsets = new ArrayList<>(this.reminderOffsets);
        copy.attachmentPaths = new ArrayList<>(this.attachmentPaths);
        copy.notes = this.notes;
        copy.isCompleted = false;
        copy.isCancelled = false;
        copy.isStarred = this.isStarred;
        copy.originalRecurrenceDate = null;
        return copy;
    }

    // ─── JSON Serialization ──────────────────────────────────────

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("id", id);
            json.put("title", title);
            json.put("description", description != null ? description : "");
            json.put("location", location != null ? location : "");
            json.put("startDate", startDate);
            json.put("startTime", startTime != null ? startTime : "");
            json.put("endDate", endDate != null ? endDate : startDate);
            json.put("endTime", endTime != null ? endTime : "");
            json.put("isAllDay", isAllDay);
            json.put("colorHex", colorHex);
            json.put("categoryId", categoryId != null ? categoryId : "");
            json.put("eventType", eventType != null ? eventType : TYPE_PERSONAL);
            json.put("recurrence", recurrence != null ? recurrence : RECURRENCE_NONE);
            json.put("recurrenceRule", recurrenceRule != null ? recurrenceRule : "");
            json.put("recurrenceEndDate", recurrenceEndDate != null ? recurrenceEndDate : "");
            json.put("recurrenceCount", recurrenceCount);

            // Reminder offsets as JSON array
            JSONArray remArr = new JSONArray();
            if (reminderOffsets != null) {
                for (Integer offset : reminderOffsets) {
                    remArr.put(offset);
                }
            }
            json.put("reminderOffsets", remArr);

            // Attachment paths as JSON array
            JSONArray attArr = new JSONArray();
            if (attachmentPaths != null) {
                for (String path : attachmentPaths) {
                    attArr.put(path);
                }
            }
            json.put("attachmentPaths", attArr);

            json.put("notes", notes != null ? notes : "");
            json.put("isCompleted", isCompleted);
            json.put("isCancelled", isCancelled);
            json.put("isStarred", isStarred);
            json.put("originalRecurrenceDate", originalRecurrenceDate != null ? originalRecurrenceDate : "");
            json.put("createdAt", createdAt);
            json.put("updatedAt", updatedAt);
        } catch (JSONException e) {
            // Should not happen with put()
        }
        return json;
    }

    /**
     * Deserialize from JSON.
     */
    public static CalendarEvent fromJson(JSONObject json) {
        if (json == null) return null;
        try {
            CalendarEvent event = new CalendarEvent();
            event.id = json.optString("id", event.id);
            event.title = json.optString("title", "");
            event.description = json.optString("description", "");
            event.location = json.optString("location", "");
            event.startDate = json.optString("startDate", event.startDate);
            event.startTime = emptyToNull(json.optString("startTime", ""));
            event.endDate = json.optString("endDate", event.startDate);
            event.endTime = emptyToNull(json.optString("endTime", ""));
            event.isAllDay = json.optBoolean("isAllDay", false);
            event.colorHex = json.optString("colorHex", COLOR_BLUE);
            event.categoryId = json.optString("categoryId", "cat_personal");
            event.eventType = json.optString("eventType", TYPE_PERSONAL);
            event.recurrence = json.optString("recurrence", RECURRENCE_NONE);
            event.recurrenceRule = json.optString("recurrenceRule", "");
            event.recurrenceEndDate = emptyToNull(json.optString("recurrenceEndDate", ""));
            event.recurrenceCount = json.optInt("recurrenceCount", 0);

            // Parse reminder offsets
            event.reminderOffsets = new ArrayList<>();
            JSONArray remArr = json.optJSONArray("reminderOffsets");
            if (remArr != null) {
                for (int i = 0; i < remArr.length(); i++) {
                    event.reminderOffsets.add(remArr.optInt(i, 0));
                }
            }

            // Parse attachment paths
            event.attachmentPaths = new ArrayList<>();
            JSONArray attArr = json.optJSONArray("attachmentPaths");
            if (attArr != null) {
                for (int i = 0; i < attArr.length(); i++) {
                    event.attachmentPaths.add(attArr.optString(i, ""));
                }
            }

            event.notes = json.optString("notes", "");
            event.isCompleted = json.optBoolean("isCompleted", false);
            event.isCancelled = json.optBoolean("isCancelled", false);
            event.isStarred = json.optBoolean("isStarred", false);
            event.originalRecurrenceDate = emptyToNull(json.optString("originalRecurrenceDate", ""));
            event.createdAt = json.optLong("createdAt", System.currentTimeMillis());
            event.updatedAt = json.optLong("updatedAt", System.currentTimeMillis());

            // Sync compatibility aliases
            event.syncAliases();

            return event;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Parse from legacy calendar format (old CalendarActivity / Python server format).
     */
    public static CalendarEvent fromLegacyJson(JSONObject json) {
        if (json == null) return null;
        try {
            CalendarEvent event = new CalendarEvent();
            event.id = json.optString("id", event.id);
            event.title = json.optString("title", "");
            event.description = json.optString("description", "");

            // Legacy uses "date" instead of "startDate"
            String date = json.optString("date", "");
            if (!date.isEmpty()) {
                event.startDate = date;
                event.endDate = date;
            }

            // Legacy uses "start_time" / "end_time"
            event.startTime = emptyToNull(json.optString("start_time", ""));
            event.endTime = emptyToNull(json.optString("end_time", ""));
            event.isAllDay = !event.hasStartTime();

            // Legacy "color" is a name like "blue", map to hex
            String colorName = json.optString("color", "blue");
            event.colorHex = legacyColorToHex(colorName);

            // Legacy "recurring" maps to recurrence
            String recurring = json.optString("recurring", "none");
            event.recurrence = recurring;

            // Legacy "reminder" maps to a single reminderOffset
            String reminder = json.optString("reminder", "none");
            event.reminderOffsets = new ArrayList<>();
            int reminderMinutes = legacyReminderToMinutes(reminder);
            if (reminderMinutes >= 0) {
                event.reminderOffsets.add(reminderMinutes);
            }

            event.eventType = TYPE_PERSONAL;
            event.categoryId = "cat_personal";

            long timestamp = json.optLong("timestamp", 0);
            if (timestamp > 0) {
                event.createdAt = (long) (timestamp * 1000); // Python time.time() is in seconds
                event.updatedAt = event.createdAt;
            }

            // Sync compatibility aliases
            event.syncAliases();

            return event;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Convert to legacy format for Python server backward compatibility.
     */
    public JSONObject toLegacyJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("id", id);
            json.put("title", title);
            json.put("date", startDate);
            json.put("start_time", startTime != null ? startTime : "");
            json.put("end_time", endTime != null ? endTime : "");
            json.put("description", description != null ? description : "");
            json.put("color", hexToLegacyColor(colorHex));
            json.put("recurring", recurrence != null ? recurrence : "none");

            // Convert first reminder offset to legacy format
            String legacyReminder = "none";
            if (reminderOffsets != null && !reminderOffsets.isEmpty()) {
                legacyReminder = minutesToLegacyReminder(reminderOffsets.get(0));
            }
            json.put("reminder", legacyReminder);

            // Also include full data for newer server versions
            json.put("full_data", toJson().toString());
        } catch (JSONException e) {
            // ignore
        }
        return json;
    }

    // ─── Static Utility Methods ──────────────────────────────────

    private static String emptyToNull(String s) {
        if (s == null || s.isEmpty() || "null".equals(s)) return null;
        return s;
    }

    private static Calendar parseDateTime(String date, String time) {
        if (date == null || date.isEmpty()) return null;
        try {
            String[] dateParts = date.split("-");
            Calendar cal = Calendar.getInstance();
            cal.set(Integer.parseInt(dateParts[0]), Integer.parseInt(dateParts[1]) - 1,
                    Integer.parseInt(dateParts[2]));
            if (time != null && !time.isEmpty() && !"null".equals(time)) {
                String[] timeParts = time.split(":");
                cal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(timeParts[0]));
                cal.set(Calendar.MINUTE, Integer.parseInt(timeParts[1]));
            } else {
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
            }
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            return cal;
        } catch (Exception e) {
            return null;
        }
    }

    public static String formatTime(String time24) {
        if (time24 == null || time24.isEmpty()) return "";
        try {
            String[] parts = time24.split(":");
            int hour = Integer.parseInt(parts[0]);
            int min = Integer.parseInt(parts[1]);
            String ampm = hour >= 12 ? "PM" : "AM";
            if (hour > 12) hour -= 12;
            if (hour == 0) hour = 12;
            return String.format(Locale.US, "%d:%02d %s", hour, min, ampm);
        } catch (Exception e) {
            return time24;
        }
    }

    public static String formatDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return "";
        try {
            String[] parts = dateStr.split("-");
            Calendar cal = Calendar.getInstance();
            cal.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1, Integer.parseInt(parts[2]));
            return new SimpleDateFormat("MMM dd, yyyy", Locale.US).format(cal.getTime());
        } catch (Exception e) {
            return dateStr;
        }
    }

    public static String formatReminderOffset(int minutes) {
        if (minutes < 0) return "None";
        if (minutes == 0) return "At time of event";
        if (minutes < 60) return minutes + " min before";
        if (minutes < 1440) {
            int hours = minutes / 60;
            return hours + " hour" + (hours > 1 ? "s" : "") + " before";
        }
        int days = minutes / 1440;
        if (days < 7) return days + " day" + (days > 1 ? "s" : "") + " before";
        int weeks = days / 7;
        return weeks + " week" + (weeks > 1 ? "s" : "") + " before";
    }

    private static String legacyColorToHex(String colorName) {
        if (colorName == null) return COLOR_BLUE;
        switch (colorName.toLowerCase()) {
            case "blue":   return COLOR_BLUE;
            case "red":    return COLOR_RED;
            case "green":  return COLOR_GREEN;
            case "purple": return COLOR_PURPLE;
            case "orange": return COLOR_ORANGE;
            case "pink":   return COLOR_PINK;
            case "yellow": return COLOR_YELLOW;
            case "teal":   return COLOR_TEAL;
            default:       return COLOR_BLUE;
        }
    }

    private static String hexToLegacyColor(String hex) {
        if (hex == null) return "blue";
        switch (hex.toUpperCase()) {
            case "#3B82F6": return "blue";
            case "#EF4444": return "red";
            case "#22C55E": return "green";
            case "#8B5CF6": return "purple";
            case "#F97316": return "orange";
            case "#EC4899": return "pink";
            case "#EAB308": return "yellow";
            case "#14B8A6": return "teal";
            default:        return "blue";
        }
    }

    private static int legacyReminderToMinutes(String reminder) {
        if (reminder == null || "none".equals(reminder)) return -1;
        switch (reminder) {
            case "5min":  return 5;
            case "15min": return 15;
            case "30min": return 30;
            case "1hour": return 60;
            case "1day":  return 1440;
            default:      return -1;
        }
    }

    private static String minutesToLegacyReminder(int minutes) {
        if (minutes <= 0) return "none";
        if (minutes <= 5)  return "5min";
        if (minutes <= 15) return "15min";
        if (minutes <= 30) return "30min";
        if (minutes <= 60) return "1hour";
        return "1day";
    }
}
