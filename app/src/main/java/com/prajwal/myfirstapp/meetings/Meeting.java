package com.prajwal.myfirstapp.meetings;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Complete Meeting data model for the Task Manager Meeting feature.
 *
 * Supports: meeting types, video platforms, attendees, agenda items,
 * action items, recurrence, multiple reminders, starring, status
 * lifecycle, and full JSON serialization / deserialization.
 */
public class Meeting {

    // ─── Fields ──────────────────────────────────────────────────

    public String id;
    public String title;
    public String description;
    public String type;                      // "IN_PERSON", "VIDEO_CALL", "PHONE_CALL", "OTHER"
    public String platform;                  // See PLATFORM_* constants
    public String meetingLink;
    public String location;
    public long   startDateTime;             // Unix millis
    public long   endDateTime;               // Unix millis
    public List<Attendee>   attendees;
    public List<AgendaItem> agenda;
    public String notes;
    public List<ActionItem> actionItems;
    public String status;                    // "SCHEDULED", "IN_PROGRESS", "COMPLETED", "CANCELLED", "POSTPONED"
    public String recurrence;                // "none", "daily", "weekly", "monthly", "custom"
    public String recurrenceRule;            // Human description when recurrence == "custom"
    public List<Long> reminderOffsets;       // Minutes before meeting start to fire each reminder
    public String colorHex;                  // Default "#4A90E2"
    public String categoryId;
    public boolean isStarred;
    public long createdAt;
    public long updatedAt;
    public String seriesId;                  // Groups recurring meeting instances

    // ─── Type Constants ──────────────────────────────────────────

    public static final String TYPE_IN_PERSON  = "IN_PERSON";
    public static final String TYPE_VIDEO_CALL = "VIDEO_CALL";
    public static final String TYPE_PHONE_CALL = "PHONE_CALL";
    public static final String TYPE_OTHER      = "OTHER";

    public static final String[] TYPE_OPTIONS = {
        TYPE_IN_PERSON, TYPE_VIDEO_CALL, TYPE_PHONE_CALL, TYPE_OTHER
    };

    // ─── Platform Constants ──────────────────────────────────────

    public static final String PLATFORM_ZOOM       = "Zoom";
    public static final String PLATFORM_MEET       = "Google Meet";
    public static final String PLATFORM_TEAMS      = "Microsoft Teams";
    public static final String PLATFORM_WEBEX      = "Webex";
    public static final String PLATFORM_PHONE      = "Phone";
    public static final String PLATFORM_IN_PERSON  = "In-Person";
    public static final String PLATFORM_OTHER      = "Other";

    public static final String[] PLATFORM_OPTIONS = {
        PLATFORM_ZOOM, PLATFORM_MEET, PLATFORM_TEAMS, PLATFORM_WEBEX,
        PLATFORM_PHONE, PLATFORM_IN_PERSON, PLATFORM_OTHER
    };

    // ─── Status Constants ────────────────────────────────────────

    public static final String STATUS_SCHEDULED   = "SCHEDULED";
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_COMPLETED   = "COMPLETED";
    public static final String STATUS_CANCELLED   = "CANCELLED";
    public static final String STATUS_POSTPONED   = "POSTPONED";

    public static final String[] STATUS_OPTIONS = {
        STATUS_SCHEDULED, STATUS_IN_PROGRESS, STATUS_COMPLETED,
        STATUS_CANCELLED, STATUS_POSTPONED
    };

    // ─── Recurrence Constants (mirrors Task) ─────────────────────

    public static final String RECURRENCE_NONE    = "none";
    public static final String RECURRENCE_DAILY   = "daily";
    public static final String RECURRENCE_WEEKLY  = "weekly";
    public static final String RECURRENCE_MONTHLY = "monthly";
    public static final String RECURRENCE_CUSTOM  = "custom";

    // ─── Default Color ───────────────────────────────────────────

    public static final String DEFAULT_COLOR = "#4A90E2";

    // ─── Constructors ────────────────────────────────────────────

    public Meeting() {
        this.id = UUID.randomUUID().toString().substring(0, 12);
        this.title = "";
        this.description = "";
        this.type = TYPE_VIDEO_CALL;
        this.platform = PLATFORM_MEET;
        this.meetingLink = "";
        this.location = "";
        this.startDateTime = 0L;
        this.endDateTime = 0L;
        this.attendees = new ArrayList<>();
        this.agenda = new ArrayList<>();
        this.notes = "";
        this.actionItems = new ArrayList<>();
        this.status = STATUS_SCHEDULED;
        this.recurrence = RECURRENCE_NONE;
        this.recurrenceRule = "";
        this.reminderOffsets = new ArrayList<>();
        this.colorHex = DEFAULT_COLOR;
        this.categoryId = "";
        this.isStarred = false;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
        this.seriesId = "";
    }

    public Meeting(String title, long startDateTime, long endDateTime) {
        this();
        this.title = title != null ? title : "";
        this.startDateTime = startDateTime;
        this.endDateTime = endDateTime;
    }

    // ─── Duration Helpers ────────────────────────────────────────

    /** Returns meeting length in minutes. */
    public long getDurationMinutes() {
        if (endDateTime <= startDateTime) return 0;
        return (endDateTime - startDateTime) / 60000;
    }

    /**
     * Returns a human-readable duration string.
     * E.g. "30 min", "1 hr", "1 hr 30 min".
     */
    public String getDurationText() {
        long minutes = getDurationMinutes();
        if (minutes <= 0) return "";
        if (minutes < 60) return minutes + " min";
        long h = minutes / 60;
        long m = minutes % 60;
        return m > 0 ? h + " hr " + m + " min" : h + " hr";
    }

    // ─── Date/Time Formatting ────────────────────────────────────

    /**
     * Returns the start time formatted as "3:00 PM".
     */
    public String getFormattedStartTime() {
        if (startDateTime == 0) return "";
        SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.US);
        return sdf.format(new Date(startDateTime));
    }

    /**
     * Returns a date-range label, e.g. "Feb 27, 3:00 PM – 4:00 PM".
     * If the end falls on a different day the end date is also shown.
     */
    public String getFormattedDateRange() {
        if (startDateTime == 0) return "";
        SimpleDateFormat dateFmt  = new SimpleDateFormat("MMM d",   Locale.US);
        SimpleDateFormat timeFmt  = new SimpleDateFormat("h:mm a",  Locale.US);
        SimpleDateFormat dateFmt2 = new SimpleDateFormat("MMM d, h:mm a", Locale.US);

        String startDate = dateFmt.format(new Date(startDateTime));
        String startTime = timeFmt.format(new Date(startDateTime));

        if (endDateTime <= startDateTime) {
            return startDate + ", " + startTime;
        }

        // Check if start and end are on the same calendar day
        SimpleDateFormat dayFmt = new SimpleDateFormat("yyyyMMdd", Locale.US);
        boolean sameDay = dayFmt.format(new Date(startDateTime))
                                .equals(dayFmt.format(new Date(endDateTime)));

        String endPart = sameDay
                ? timeFmt.format(new Date(endDateTime))
                : dateFmt2.format(new Date(endDateTime));

        return startDate + ", " + startTime + " – " + endPart;
    }

    // ─── Status Helpers ──────────────────────────────────────────

    /** Returns true if the meeting is currently in progress. */
    public boolean isHappeningNow() {
        long now = System.currentTimeMillis();
        return startDateTime <= now && now <= endDateTime;
    }

    /** Returns true if the meeting starts within the next 15 minutes. */
    public boolean isStartingSoon() {
        long now = System.currentTimeMillis();
        long diff = startDateTime - now;
        return diff > 0 && diff <= 15L * 60 * 1000;
    }

    /** Returns true if the meeting has already ended. */
    public boolean isPast() {
        return endDateTime > 0 && endDateTime < System.currentTimeMillis();
    }

    public boolean isScheduled() {
        return STATUS_SCHEDULED.equals(status);
    }

    public boolean isCompleted() {
        return STATUS_COMPLETED.equals(status);
    }

    public boolean isCancelled() {
        return STATUS_CANCELLED.equals(status);
    }

    public boolean isRecurring() {
        return recurrence != null && !RECURRENCE_NONE.equals(recurrence);
    }

    public boolean isPartOfSeries() {
        return seriesId != null && !seriesId.isEmpty();
    }

    public String getStatusLabel() {
        if (status == null) return "";
        switch (status) {
            case STATUS_SCHEDULED:   return "Scheduled";
            case STATUS_IN_PROGRESS: return "In Progress";
            case STATUS_COMPLETED:   return "Completed";
            case STATUS_CANCELLED:   return "Cancelled";
            case STATUS_POSTPONED:   return "Postponed";
            default:                 return status;
        }
    }

    public String getTypeLabel() {
        if (type == null) return "";
        switch (type) {
            case TYPE_IN_PERSON:  return "In Person";
            case TYPE_VIDEO_CALL: return "Video Call";
            case TYPE_PHONE_CALL: return "Phone Call";
            case TYPE_OTHER:      return "Other";
            default:              return type;
        }
    }

    public String getRecurrenceLabel() {
        if (recurrence == null) return "";
        switch (recurrence) {
            case RECURRENCE_DAILY:   return "Daily";
            case RECURRENCE_WEEKLY:  return "Weekly";
            case RECURRENCE_MONTHLY: return "Monthly";
            case RECURRENCE_CUSTOM:  return recurrenceRule != null ? recurrenceRule : "Custom";
            default:                 return "";
        }
    }

    // ─── Agenda Helpers ──────────────────────────────────────────

    /**
     * Returns the fraction of agenda items marked complete, in [0, 1].
     * Returns 0 if there are no agenda items.
     */
    public float getAgendaCompletionRate() {
        if (agenda == null || agenda.isEmpty()) return 0f;
        int completed = 0;
        for (AgendaItem item : agenda) {
            if (item.isCompleted) completed++;
        }
        return (float) completed / agenda.size();
    }

    /** Returns the sum of all agenda item durations in minutes. */
    public int getTotalAgendaDuration() {
        if (agenda == null) return 0;
        int total = 0;
        for (AgendaItem item : agenda) {
            total += item.durationMinutes;
        }
        return total;
    }

    public boolean hasAgenda() {
        return agenda != null && !agenda.isEmpty();
    }

    // ─── Attendee Helpers ────────────────────────────────────────

    public int getAttendeeCount() {
        return attendees != null ? attendees.size() : 0;
    }

    /** Returns the first attendee with role ORGANIZER, or null. */
    public Attendee getOrganizer() {
        if (attendees == null) return null;
        for (Attendee a : attendees) {
            if (Attendee.ROLE_ORGANIZER.equals(a.role)) return a;
        }
        return null;
    }

    public boolean hasAttendees() {
        return attendees != null && !attendees.isEmpty();
    }

    // ─── Action Item Helpers ─────────────────────────────────────

    public int getOpenActionItemCount() {
        if (actionItems == null) return 0;
        int count = 0;
        for (ActionItem ai : actionItems) {
            if (!ai.isCompleted) count++;
        }
        return count;
    }

    public boolean hasActionItems() {
        return actionItems != null && !actionItems.isEmpty();
    }

    // ─── Link / Location Helpers ─────────────────────────────────

    public boolean hasMeetingLink() {
        return meetingLink != null && !meetingLink.trim().isEmpty();
    }

    public boolean hasLocation() {
        return location != null && !location.trim().isEmpty();
    }

    // ─── Search Matching ─────────────────────────────────────────

    public boolean matchesSearch(String query) {
        if (query == null || query.isEmpty()) return true;
        String q = query.toLowerCase(Locale.US);
        if (title != null && title.toLowerCase(Locale.US).contains(q)) return true;
        if (description != null && description.toLowerCase(Locale.US).contains(q)) return true;
        if (location != null && location.toLowerCase(Locale.US).contains(q)) return true;
        if (platform != null && platform.toLowerCase(Locale.US).contains(q)) return true;
        if (notes != null && notes.toLowerCase(Locale.US).contains(q)) return true;
        if (attendees != null) {
            for (Attendee a : attendees) {
                if (a.name != null && a.name.toLowerCase(Locale.US).contains(q)) return true;
                if (a.email != null && a.email.toLowerCase(Locale.US).contains(q)) return true;
            }
        }
        if (agenda != null) {
            for (AgendaItem item : agenda) {
                if (item.title != null && item.title.toLowerCase(Locale.US).contains(q)) return true;
            }
        }
        if (actionItems != null) {
            for (ActionItem ai : actionItems) {
                if (ai.title != null && ai.title.toLowerCase(Locale.US).contains(q)) return true;
                if (ai.assigneeName != null && ai.assigneeName.toLowerCase(Locale.US).contains(q)) return true;
            }
        }
        return false;
    }

    // ─── Copy ────────────────────────────────────────────────────

    /**
     * Creates a deep copy of this meeting with a new ID and "(copy)" appended to title.
     * The copy is reset to SCHEDULED status and cleared of its seriesId.
     */
    public Meeting duplicate() {
        Meeting copy = new Meeting();
        copy.title = this.title + " (copy)";
        copy.description = this.description;
        copy.type = this.type;
        copy.platform = this.platform;
        copy.meetingLink = this.meetingLink;
        copy.location = this.location;
        copy.startDateTime = this.startDateTime;
        copy.endDateTime = this.endDateTime;
        copy.notes = this.notes;
        copy.status = STATUS_SCHEDULED;
        copy.recurrence = this.recurrence;
        copy.recurrenceRule = this.recurrenceRule;
        copy.colorHex = this.colorHex;
        copy.categoryId = this.categoryId;
        copy.isStarred = false;
        copy.seriesId = "";

        copy.reminderOffsets = this.reminderOffsets != null
                ? new ArrayList<>(this.reminderOffsets) : new ArrayList<>();

        // Deep-copy attendees
        copy.attendees = new ArrayList<>();
        if (this.attendees != null) {
            for (Attendee a : this.attendees) {
                Attendee aCopy = Attendee.fromJson(a.toJson());
                if (aCopy != null) {
                    aCopy.id = UUID.randomUUID().toString().substring(0, 12);
                    aCopy.meetingId = copy.id;
                    copy.attendees.add(aCopy);
                }
            }
        }

        // Deep-copy agenda items
        copy.agenda = new ArrayList<>();
        if (this.agenda != null) {
            for (AgendaItem item : this.agenda) {
                AgendaItem iCopy = AgendaItem.fromJson(item.toJson());
                if (iCopy != null) {
                    iCopy.id = UUID.randomUUID().toString().substring(0, 12);
                    iCopy.meetingId = copy.id;
                    iCopy.isCompleted = false;
                    copy.agenda.add(iCopy);
                }
            }
        }

        // Action items are not copied — they belong to the original meeting
        copy.actionItems = new ArrayList<>();

        return copy;
    }

    // ─── JSON Serialization ──────────────────────────────────────

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("id", id);
            json.put("title", title != null ? title : "");
            json.put("description", description != null ? description : "");
            json.put("type", type != null ? type : TYPE_VIDEO_CALL);
            json.put("platform", platform != null ? platform : PLATFORM_OTHER);
            json.put("meetingLink", meetingLink != null ? meetingLink : "");
            json.put("location", location != null ? location : "");
            json.put("startDateTime", startDateTime);
            json.put("endDateTime", endDateTime);

            JSONArray attendeesArr = new JSONArray();
            if (attendees != null) for (Attendee a : attendees) attendeesArr.put(a.toJson());
            json.put("attendees", attendeesArr);

            JSONArray agendaArr = new JSONArray();
            if (agenda != null) for (AgendaItem item : agenda) agendaArr.put(item.toJson());
            json.put("agenda", agendaArr);

            json.put("notes", notes != null ? notes : "");

            JSONArray actionItemsArr = new JSONArray();
            if (actionItems != null) for (ActionItem ai : actionItems) actionItemsArr.put(ai.toJson());
            json.put("actionItems", actionItemsArr);

            json.put("status", status != null ? status : STATUS_SCHEDULED);
            json.put("recurrence", recurrence != null ? recurrence : RECURRENCE_NONE);
            json.put("recurrenceRule", recurrenceRule != null ? recurrenceRule : "");

            JSONArray remindersArr = new JSONArray();
            if (reminderOffsets != null) for (Long offset : reminderOffsets) remindersArr.put(offset);
            json.put("reminderOffsets", remindersArr);

            json.put("colorHex", colorHex != null ? colorHex : DEFAULT_COLOR);
            json.put("categoryId", categoryId != null ? categoryId : "");
            json.put("isStarred", isStarred);
            json.put("createdAt", createdAt);
            json.put("updatedAt", updatedAt);
            json.put("seriesId", seriesId != null ? seriesId : "");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return json;
    }

    public static Meeting fromJson(JSONObject json) {
        if (json == null) return null;
        try {
            Meeting meeting = new Meeting();
            meeting.id = json.optString("id", meeting.id);
            meeting.title = json.optString("title", "");
            meeting.description = json.optString("description", "");
            meeting.type = json.optString("type", TYPE_VIDEO_CALL);
            meeting.platform = json.optString("platform", PLATFORM_OTHER);
            meeting.meetingLink = json.optString("meetingLink", "");
            meeting.location = json.optString("location", "");
            meeting.startDateTime = json.optLong("startDateTime", 0L);
            meeting.endDateTime = json.optLong("endDateTime", 0L);

            meeting.attendees = new ArrayList<>();
            JSONArray attendeesArr = json.optJSONArray("attendees");
            if (attendeesArr != null) {
                for (int i = 0; i < attendeesArr.length(); i++) {
                    Attendee a = Attendee.fromJson(attendeesArr.getJSONObject(i));
                    if (a != null) meeting.attendees.add(a);
                }
            }

            meeting.agenda = new ArrayList<>();
            JSONArray agendaArr = json.optJSONArray("agenda");
            if (agendaArr != null) {
                for (int i = 0; i < agendaArr.length(); i++) {
                    AgendaItem item = AgendaItem.fromJson(agendaArr.getJSONObject(i));
                    if (item != null) meeting.agenda.add(item);
                }
            }

            meeting.notes = json.optString("notes", "");

            meeting.actionItems = new ArrayList<>();
            JSONArray actionItemsArr = json.optJSONArray("actionItems");
            if (actionItemsArr != null) {
                for (int i = 0; i < actionItemsArr.length(); i++) {
                    ActionItem ai = ActionItem.fromJson(actionItemsArr.getJSONObject(i));
                    if (ai != null) meeting.actionItems.add(ai);
                }
            }

            meeting.status = json.optString("status", STATUS_SCHEDULED);
            meeting.recurrence = json.optString("recurrence", RECURRENCE_NONE);
            meeting.recurrenceRule = json.optString("recurrenceRule", "");

            meeting.reminderOffsets = new ArrayList<>();
            JSONArray remindersArr = json.optJSONArray("reminderOffsets");
            if (remindersArr != null) {
                for (int i = 0; i < remindersArr.length(); i++) {
                    meeting.reminderOffsets.add(remindersArr.getLong(i));
                }
            }

            meeting.colorHex = json.optString("colorHex", DEFAULT_COLOR);
            meeting.categoryId = json.optString("categoryId", "");
            meeting.isStarred = json.optBoolean("isStarred", false);
            meeting.createdAt = json.optLong("createdAt", System.currentTimeMillis());
            meeting.updatedAt = json.optLong("updatedAt", System.currentTimeMillis());
            meeting.seriesId = json.optString("seriesId", "");

            return meeting;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
