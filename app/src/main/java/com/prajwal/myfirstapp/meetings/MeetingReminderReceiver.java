package com.prajwal.myfirstapp.meetings;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

import androidx.core.app.NotificationCompat;

/**
 * BroadcastReceiver that fires for meeting-related AlarmManager alarms and
 * in-notification action buttons.
 *
 * Handled actions:
 *   - {@link MeetingNotificationHelper#ACTION_MEETING_REMINDER}     — scheduled reminder before meeting
 *   - {@link MeetingNotificationHelper#ACTION_MEETING_STARTING_NOW} — 15 min before start
 *   - {@link MeetingNotificationHelper#ACTION_MEETING_MISSED}       — 30 min after start
 *   - {@link MeetingNotificationHelper#ACTION_JOIN_MEETING}         — user tapped "Join" in a notification
 */
public class MeetingReminderReceiver extends BroadcastReceiver {

    private static final String TAG = "MeetingReminderReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;

        String action = intent.getAction();
        if (action == null) return;

        MeetingNotificationHelper.createNotificationChannel(context);

        // ── Handle "Join" tapped from a notification action button ──────────
        if (MeetingNotificationHelper.ACTION_JOIN_MEETING.equals(action)) {
            handleJoinAction(context, intent);
            return;
        }

        // ── Load meeting for all alarm-triggered actions ─────────────────────
        String meetingId = intent.getStringExtra(MeetingNotificationHelper.EXTRA_MEETING_ID);
        if (meetingId == null) {
            Log.w(TAG, "Received alarm with no meeting_id, action=" + action);
            return;
        }

        MeetingRepository repo = MeetingRepository.getInstance(context);
        Meeting meeting = repo.getMeeting(meetingId);
        if (meeting == null) {
            Log.w(TAG, "Meeting not found for id=" + meetingId + ", action=" + action);
            return;
        }

        Log.i(TAG, "Alarm fired: action=" + action + " meeting='" + meeting.title + "'");

        switch (action) {
            case MeetingNotificationHelper.ACTION_MEETING_REMINDER:
                showReminderNotification(context, meeting);
                break;

            case MeetingNotificationHelper.ACTION_MEETING_STARTING_NOW:
                MeetingNotificationHelper.showMeetingStartingNowNotification(context, meeting);
                vibrate(context, 500);
                break;

            case MeetingNotificationHelper.ACTION_MEETING_MISSED:
                // Only alert if the user has not yet attended or updated the meeting
                if (Meeting.STATUS_SCHEDULED.equals(meeting.status)) {
                    showMissedNotification(context, meeting);
                    vibrate(context, 300);
                } else {
                    Log.i(TAG, "Skipping missed notification — status is " + meeting.status);
                }
                break;

            default:
                Log.w(TAG, "Unknown meeting alarm action: " + action);
                break;
        }
    }

    // ─── Notification Builders ────────────────────────────────────

    /**
     * Shows a pre-meeting reminder notification. Includes a "Join" action
     * when the meeting has a link or is a phone-call type.
     */
    private void showReminderNotification(Context context, Meeting meeting) {
        String timeText = meeting.getFormattedStartTime();
        String body = timeText.isEmpty() ? "Your meeting is coming up" : "Starts at " + timeText;

        if (Meeting.TYPE_IN_PERSON.equals(meeting.type) && meeting.hasLocation()) {
            body += " · " + meeting.location;
        } else if (meeting.platform != null && !meeting.platform.isEmpty()
                && !Meeting.TYPE_IN_PERSON.equals(meeting.type)) {
            body += " · " + meeting.platform;
        }

        NotificationCompat.Builder builder = MeetingNotificationHelper.buildBaseNotification(
                context, meeting, "🔔 Reminder: " + meeting.title, body);

        if (meeting.hasMeetingLink() || Meeting.TYPE_PHONE_CALL.equals(meeting.type)) {
            MeetingNotificationHelper.addJoinAction(context, builder, meeting);
        }

        MeetingNotificationHelper.showNotification(context, meeting, builder);
        vibrate(context, 500);
    }

    /**
     * Shows a "missed meeting" notification when the meeting has passed without
     * being attended (status was still {@link Meeting#STATUS_SCHEDULED} 30 min after start).
     */
    private void showMissedNotification(Context context, Meeting meeting) {
        String body = "The meeting started 30 minutes ago and has not been marked as attended.";
        NotificationCompat.Builder builder = MeetingNotificationHelper.buildBaseNotification(
                context, meeting, "⚠️ Missed: " + meeting.title, body);
        MeetingNotificationHelper.showNotification(context, meeting, builder);
    }

    // ─── Join Action Handler ──────────────────────────────────────

    /**
     * Handles a tap on the "Join" action button from any meeting notification.
     * Dismisses the notification then opens the meeting URL, dials, or falls
     * back to {@link MeetingDetailActivity}.
     */
    private void handleJoinAction(Context context, Intent intent) {
        String meetingId = intent.getStringExtra(MeetingNotificationHelper.EXTRA_MEETING_ID);
        if (meetingId == null) return;

        // Dismiss the originating notification
        NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(MeetingNotificationHelper.getNotificationId(meetingId));

        MeetingRepository repo = MeetingRepository.getInstance(context);
        Meeting meeting = repo.getMeeting(meetingId);
        if (meeting == null) return;

        Log.i(TAG, "Join action triggered for: " + meeting.title);

        if (meeting.hasMeetingLink()) {
            Intent joinIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(meeting.meetingLink));
            joinIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(joinIntent);
        } else if (Meeting.TYPE_PHONE_CALL.equals(meeting.type) && meeting.hasLocation()) {
            Intent dialIntent = new Intent(Intent.ACTION_DIAL,
                    Uri.parse("tel:" + meeting.location.trim()));
            dialIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(dialIntent);
        } else {
            // Fallback: open MeetingDetailActivity
            Intent detailIntent = new Intent(context, MeetingDetailActivity.class);
            detailIntent.putExtra(MeetingNotificationHelper.EXTRA_MEETING_ID, meetingId);
            detailIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(detailIntent);
        }
    }

    // ─── Vibrate ──────────────────────────────────────────────────

    private void vibrate(Context context, int durationMs) {
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs,
                    VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            vibrator.vibrate(durationMs);
        }
    }
}
