package com.prajwal.myfirstapp.meetings;


import com.prajwal.myfirstapp.R;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.util.List;

/**
 * Notification scheduling service for Meeting reminders.
 *
 * Supports:
 *   - Multiple reminder offsets per meeting (minutes before start)
 *   - "Starting soon" alert 15 minutes before start
 *   - "Missed meeting" alert 30 minutes after start (shown only if still SCHEDULED)
 *   - Immediate "starting now" and joinable notifications
 *
 * Each alarm gets a unique PendingIntent request code derived from the
 * meeting ID and a per-alarm-type offset constant (mirrors TaskNotificationHelper).
 *
 * Channel ID: {@value #CHANNEL_ID} / Channel name: {@value #CHANNEL_NAME}
 */
public class MeetingNotificationHelper {

    private static final String TAG = "MeetingNotifHelper";

    // ─── Notification Channel ────────────────────────────────────

    public static final String CHANNEL_ID   = "meeting_notifications";
    public static final String CHANNEL_NAME = "Meeting Reminders";

    // ─── Broadcast Actions ───────────────────────────────────────

    public static final String ACTION_MEETING_REMINDER     = "com.prajwal.myfirstapp.ACTION_MEETING_REMINDER";
    public static final String ACTION_MEETING_STARTING_NOW = "com.prajwal.myfirstapp.ACTION_MEETING_STARTING_NOW";
    public static final String ACTION_MEETING_MISSED       = "com.prajwal.myfirstapp.ACTION_MEETING_MISSED";
    public static final String ACTION_JOIN_MEETING         = "com.prajwal.myfirstapp.ACTION_JOIN_MEETING";

    // ─── Intent Extras ───────────────────────────────────────────

    public static final String EXTRA_MEETING_ID          = "meeting_id";
    public static final String EXTRA_MEETING_TITLE       = "meeting_title";
    public static final String EXTRA_MEETING_START       = "meeting_start";
    public static final String EXTRA_MEETING_LINK        = "meeting_link";
    public static final String EXTRA_REMINDER_OFFSET_IDX = "reminder_offset_index";

    // ─── Request Code Offsets ────────────────────────────────────
    // Kept away from TaskNotificationHelper's 0 / 100 / 200 / 300 range.

    private static final int RC_OFFSET_REMINDER      =   0; // + reminder index (0–9)
    private static final int RC_OFFSET_STARTING_SOON = 500;
    private static final int RC_OFFSET_MISSED        = 600;

    // ─── Schedule / Cancel ───────────────────────────────────────

    /**
     * Schedules one alarm per {@link Meeting#reminderOffsets} entry and also
     * schedules the "starting soon" (−15 min) and "missed" (+30 min) alarms.
     * Any existing alarms for this meeting are cancelled first.
     */
    public static void scheduleMeetingReminders(Context context, Meeting meeting) {
        if (meeting == null || Meeting.STATUS_CANCELLED.equals(meeting.status)) return;

        cancelMeetingReminders(context, meeting.id);

        List<Long> offsets = meeting.reminderOffsets;
        if (offsets != null) {
            for (int i = 0; i < offsets.size(); i++) {
                long triggerMs = meeting.startDateTime - (offsets.get(i) * 60 * 1000L);
                if (triggerMs > System.currentTimeMillis()) {
                    scheduleAlarm(context, meeting, triggerMs,
                            ACTION_MEETING_REMINDER, getReminderRequestCode(meeting.id, i));
                }
            }
        }

        scheduleStartingSoonNotification(context, meeting);
        scheduleMissedMeetingNotification(context, meeting);
    }

    /**
     * Cancels all alarms associated with the given meeting ID:
     * up to 10 indexed reminder offsets, the starting-soon alarm, and the missed alarm.
     */
    public static void cancelMeetingReminders(Context context, String meetingId) {
        if (meetingId == null) return;

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        for (int i = 0; i < 10; i++) {
            cancelAlarm(context, am, meetingId, ACTION_MEETING_REMINDER,
                    getReminderRequestCode(meetingId, i));
        }
        cancelAlarm(context, am, meetingId, ACTION_MEETING_STARTING_NOW,
                getStartingSoonRequestCode(meetingId));
        cancelAlarm(context, am, meetingId, ACTION_MEETING_MISSED,
                getMissedRequestCode(meetingId));

        Log.i(TAG, "Cancelled all alarms for meeting: " + meetingId);
    }

    /** Schedules an alarm that fires 15 minutes before the meeting's start time. */
    public static void scheduleStartingSoonNotification(Context context, Meeting meeting) {
        if (meeting == null || Meeting.STATUS_CANCELLED.equals(meeting.status)) return;
        long triggerMs = meeting.startDateTime - (15 * 60 * 1000L);
        if (triggerMs > System.currentTimeMillis()) {
            scheduleAlarm(context, meeting, triggerMs,
                    ACTION_MEETING_STARTING_NOW, getStartingSoonRequestCode(meeting.id));
        }
    }

    /**
     * Schedules an alarm that fires 30 minutes after the meeting's start time.
     * The receiver will show a notification only if the status is still
     * {@link Meeting#STATUS_SCHEDULED} at that point.
     */
    public static void scheduleMissedMeetingNotification(Context context, Meeting meeting) {
        if (meeting == null || Meeting.STATUS_CANCELLED.equals(meeting.status)) return;
        long triggerMs = meeting.startDateTime + (30 * 60 * 1000L);
        if (triggerMs > System.currentTimeMillis()) {
            scheduleAlarm(context, meeting, triggerMs,
                    ACTION_MEETING_MISSED, getMissedRequestCode(meeting.id));
        }
    }

    /** Shows an immediate "Meeting is starting now" notification. */
    public static void showMeetingStartingNowNotification(Context context, Meeting meeting) {
        if (meeting == null) return;
        createNotificationChannel(context);

        String body = meeting.getFormattedDateRange();
        if (body.isEmpty()) body = "Starts now";

        NotificationCompat.Builder builder = buildBaseNotification(
                context, meeting, "🔔 " + meeting.title + " is starting now", body);

        addJoinAction(context, builder, meeting);
        showNotification(context, meeting, builder);
        Log.i(TAG, "Showed starting-now notification for: " + meeting.title);
    }

    /**
     * Shows a joinable notification with a "Join" action button.
     * Displayed when the meeting is about to start or a join link is available.
     * The "Join" button opens the meeting URL, dials the number, or falls back
     * to opening {@link MeetingDetailActivity}.
     */
    public static void showJoinableNotification(Context context, Meeting meeting) {
        if (meeting == null) return;
        createNotificationChannel(context);

        String body;
        if (meeting.hasMeetingLink()) {
            body = meeting.platform != null && !meeting.platform.isEmpty()
                    ? "Join via " + meeting.platform
                    : "Tap Join to enter the meeting";
        } else if (meeting.hasLocation()) {
            body = meeting.location;
        } else {
            body = "Tap Join to enter the meeting";
        }

        NotificationCompat.Builder builder = buildBaseNotification(
                context, meeting, "📹 " + meeting.title, body);

        addJoinAction(context, builder, meeting);
        showNotification(context, meeting, builder);
        Log.i(TAG, "Showed joinable notification for: " + meeting.title);
    }

    // ─── Internal Helpers ─────────────────────────────────────────

    private static void scheduleAlarm(Context context, Meeting meeting, long triggerMs,
                                      String action, int requestCode) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent intent = buildReceiverIntent(context, meeting, action);
        PendingIntent pi = PendingIntent.getBroadcast(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi);
            } else {
                am.set(AlarmManager.RTC_WAKEUP, triggerMs, pi);
                Log.w(TAG, "Exact alarms not permitted for '" + meeting.title
                        + "' — reminder may be delayed. Grant SCHEDULE_EXACT_ALARM in Settings > Apps for precise timing.");
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi);
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, triggerMs, pi);
        }

        Log.i(TAG, "Alarm scheduled for '" + meeting.title + "' [" + action + "] at " + triggerMs);
    }

    private static void cancelAlarm(Context context, AlarmManager am,
                                     String meetingId, String action, int requestCode) {
        Intent intent = new Intent(context, MeetingReminderReceiver.class);
        intent.setAction(action);
        intent.putExtra(EXTRA_MEETING_ID, meetingId);
        PendingIntent pi = PendingIntent.getBroadcast(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.cancel(pi);
    }

    private static Intent buildReceiverIntent(Context context, Meeting meeting, String action) {
        Intent intent = new Intent(context, MeetingReminderReceiver.class);
        intent.setAction(action);
        intent.putExtra(EXTRA_MEETING_ID, meeting.id);
        intent.putExtra(EXTRA_MEETING_TITLE, meeting.title);
        intent.putExtra(EXTRA_MEETING_START, meeting.startDateTime);
        if (meeting.meetingLink != null) {
            intent.putExtra(EXTRA_MEETING_LINK, meeting.meetingLink);
        }
        return intent;
    }

    // ─── Notification Builder Utilities ──────────────────────────

    /**
     * Builds a base notification with a tap-to-open deep link into
     * {@link MeetingDetailActivity}.
     */
    static NotificationCompat.Builder buildBaseNotification(Context context, Meeting meeting,
                                                             String title, String body) {
        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) pendingFlags |= PendingIntent.FLAG_IMMUTABLE;

        Intent detailIntent = new Intent(context, MeetingDetailActivity.class);
        detailIntent.putExtra(EXTRA_MEETING_ID, meeting.id);
        detailIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentPi = PendingIntent.getActivity(context,
                ("detail_" + meeting.id).hashCode(), detailIntent, pendingFlags);

        return new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setContentIntent(contentPi);
    }

    /**
     * Appends a "Join" action button to {@code builder}.
     *
     * <ul>
     *   <li>If the meeting has a link, the button opens the URL.</li>
     *   <li>If it's a phone-call type with a location, the button dials the number.</li>
     *   <li>Otherwise, a broadcast to {@link MeetingReminderReceiver} is used so the
     *       receiver can open {@link MeetingDetailActivity} as a fallback.</li>
     * </ul>
     */
    static void addJoinAction(Context context, NotificationCompat.Builder builder, Meeting meeting) {
        if (meeting == null) return;

        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) pendingFlags |= PendingIntent.FLAG_IMMUTABLE;

        PendingIntent joinPi;
        if (meeting.hasMeetingLink()) {
            Intent joinIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(meeting.meetingLink));
            joinIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            joinPi = PendingIntent.getActivity(context,
                    ("join_" + meeting.id).hashCode(), joinIntent, pendingFlags);
        } else if (Meeting.TYPE_PHONE_CALL.equals(meeting.type) && meeting.hasLocation()) {
            Intent dialIntent = new Intent(Intent.ACTION_DIAL,
                    Uri.parse("tel:" + meeting.location.trim()));
            dialIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            joinPi = PendingIntent.getActivity(context,
                    ("join_" + meeting.id).hashCode(), dialIntent, pendingFlags);
        } else {
            // Broadcast to receiver; receiver will open MeetingDetailActivity
            Intent broadcastIntent = new Intent(context, MeetingReminderReceiver.class);
            broadcastIntent.setAction(ACTION_JOIN_MEETING);
            broadcastIntent.putExtra(EXTRA_MEETING_ID, meeting.id);
            joinPi = PendingIntent.getBroadcast(context,
                    ("join_" + meeting.id).hashCode(), broadcastIntent, pendingFlags);
        }

        builder.addAction(android.R.drawable.ic_menu_send, "Join", joinPi);
    }

    /** Posts the notification built by {@code builder} for the given meeting. */
    static void showNotification(Context context, Meeting meeting,
                                  NotificationCompat.Builder builder) {
        NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(getNotificationId(meeting.id), builder.build());
        }
    }

    /**
     * Returns a stable, positive notification ID derived from the meeting's ID string.
     * Centralised here so that both the helper and the receiver use the same value
     * when posting or cancelling a notification.
     */
    public static int getNotificationId(String meetingId) {
        return meetingId.hashCode() & 0x7FFFFFFF;
    }

    /** Creates the meeting notification channel on Android O+. No-ops if already created. */
    static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = context.getSystemService(NotificationManager.class);
            if (nm == null || nm.getNotificationChannel(CHANNEL_ID) != null) return;
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Reminders for upcoming meetings");
            channel.enableVibration(true);
            nm.createNotificationChannel(channel);
        }
    }

    // ─── Request Code Helpers ─────────────────────────────────────

    private static int getReminderRequestCode(String meetingId, int index) {
        int base = meetingId.hashCode() & 0x7FFFFFFF;
        return (base + RC_OFFSET_REMINDER + index) & 0x7FFFFFFF;
    }

    private static int getStartingSoonRequestCode(String meetingId) {
        int base = meetingId.hashCode() & 0x7FFFFFFF;
        return (base + RC_OFFSET_STARTING_SOON) & 0x7FFFFFFF;
    }

    private static int getMissedRequestCode(String meetingId) {
        int base = meetingId.hashCode() & 0x7FFFFFFF;
        return (base + RC_OFFSET_MISSED) & 0x7FFFFFFF;
    }

    // ─── Reschedule All (after boot) ─────────────────────────────

    /**
     * Reschedule reminders for all upcoming non-cancelled meetings.
     * Should be called by a BootReceiver after device reboot.
     */
    public static void rescheduleAllReminders(Context context) {
        MeetingRepository repo = MeetingRepository.getInstance(context);
        List<Meeting> upcoming = repo.getUpcomingMeetings();
        int scheduled = 0;
        for (Meeting m : upcoming) {
            scheduleMeetingReminders(context, m);
            scheduled++;
        }
        Log.i(TAG, "Rescheduled reminders for " + scheduled + " meetings after boot");
    }
}
