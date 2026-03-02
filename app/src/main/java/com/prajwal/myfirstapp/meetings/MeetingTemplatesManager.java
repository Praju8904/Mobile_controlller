package com.prajwal.myfirstapp.meetings;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *  MEETING TEMPLATES MANAGER — Built-in meeting templates
 * ═══════════════════════════════════════════════════════════════════════════════════════
 */
public class MeetingTemplatesManager {

    private static final String TAG = "MeetingTemplatesManager";

    // ═══════════════════════════════════════════════════════════════════════════════
    //  TEMPLATE DATA CLASS
    // ═══════════════════════════════════════════════════════════════════════════════

    public static class MeetingTemplate {
        public String id;
        public String name;
        public int durationMinutes;
        public String recurrence;          // mirrors Meeting recurrence constants
        public List<String> agendaItems;   // titles of agenda items
        public int suggestedReminderMinutes;

        public MeetingTemplate(String id, String name, int durationMinutes,
                               String recurrence, List<String> agendaItems,
                               int suggestedReminderMinutes) {
            this.id = id;
            this.name = name;
            this.durationMinutes = durationMinutes;
            this.recurrence = recurrence;
            this.agendaItems = agendaItems != null ? agendaItems : new ArrayList<>();
            this.suggestedReminderMinutes = suggestedReminderMinutes;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  BUILT-IN TEMPLATES
    // ═══════════════════════════════════════════════════════════════════════════════

    public static List<MeetingTemplate> getBuiltinTemplates() {
        List<MeetingTemplate> templates = new ArrayList<>();

        List<String> standupAgenda = new ArrayList<>();
        standupAgenda.add("Updates");
        standupAgenda.add("Blockers");
        standupAgenda.add("Next steps");
        templates.add(new MeetingTemplate(
                "builtin_standup", "Weekly Team Standup",
                30, "weekly", standupAgenda, 5));

        List<String> oneOnOneAgenda = new ArrayList<>();
        oneOnOneAgenda.add("Personal check-in");
        oneOnOneAgenda.add("Work updates");
        oneOnOneAgenda.add("Feedback");
        oneOnOneAgenda.add("Action items");
        templates.add(new MeetingTemplate(
                "builtin_1on1", "1-on-1",
                30, "weekly", oneOnOneAgenda, 10));

        List<String> kickoffAgenda = new ArrayList<>();
        kickoffAgenda.add("Introductions");
        kickoffAgenda.add("Project overview");
        kickoffAgenda.add("Roles & responsibilities");
        kickoffAgenda.add("Timeline & milestones");
        kickoffAgenda.add("Q&A");
        templates.add(new MeetingTemplate(
                "builtin_kickoff", "Project Kickoff",
                60, "none", kickoffAgenda, 30));

        List<String> retroAgenda = new ArrayList<>();
        retroAgenda.add("What went well");
        retroAgenda.add("What could be better");
        retroAgenda.add("Action items");
        templates.add(new MeetingTemplate(
                "builtin_retro", "Retrospective",
                60, "none", retroAgenda, 15));

        List<String> salesAgenda = new ArrayList<>();
        salesAgenda.add("Introduction");
        salesAgenda.add("Discovery questions");
        salesAgenda.add("Demo");
        salesAgenda.add("Next steps");
        templates.add(new MeetingTemplate(
                "builtin_sales", "Sales Call",
                45, "none", salesAgenda, 15));

        return templates;
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  TEMPLATE APPLICATION
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Creates a new Meeting pre-filled from the given template.
     * The caller is responsible for setting startDateTime/endDateTime.
     */
    public static Meeting applyTemplate(MeetingTemplate template) {
        Meeting meeting = new Meeting();
        meeting.title = template.name;
        meeting.recurrence = template.recurrence;

        // Calculate end time from duration (uses current time as default start)
        meeting.startDateTime = System.currentTimeMillis();
        meeting.endDateTime = meeting.startDateTime + (template.durationMinutes * 60_000L);

        // Add reminder
        meeting.reminderOffsets = new ArrayList<>();
        meeting.reminderOffsets.add((long) template.suggestedReminderMinutes);

        // Add agenda items
        meeting.agenda = new ArrayList<>();
        for (int i = 0; i < template.agendaItems.size(); i++) {
            AgendaItem item = new AgendaItem(template.agendaItems.get(i),
                    template.durationMinutes / Math.max(1, template.agendaItems.size()));
            item.sortOrder = i;
            meeting.agenda.add(item);
        }

        return meeting;
    }
}
