package com.prajwal.myfirstapp.meetings;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Represents a meeting attendee with role and RSVP response status.
 *
 * Supports: roles (organizer/required/optional), response statuses,
 * avatar color auto-assignment, and initials generation.
 */
public class Attendee {

    // ─── Fields ──────────────────────────────────────────────────

    public String id;
    public String meetingId;
    public String name;
    public String email;               // Optional
    public String role;                // "ORGANIZER", "REQUIRED", "OPTIONAL"
    public String responseStatus;      // "PENDING", "ACCEPTED", "DECLINED", "TENTATIVE"
    public String avatarColorHex;      // Auto-assigned from palette

    // ─── Constants ───────────────────────────────────────────────

    public static final String ROLE_ORGANIZER = "ORGANIZER";
    public static final String ROLE_REQUIRED  = "REQUIRED";
    public static final String ROLE_OPTIONAL  = "OPTIONAL";

    public static final String RESPONSE_PENDING   = "PENDING";
    public static final String RESPONSE_ACCEPTED  = "ACCEPTED";
    public static final String RESPONSE_DECLINED  = "DECLINED";
    public static final String RESPONSE_TENTATIVE = "TENTATIVE";

    public static final String[] ROLE_OPTIONS = {
        ROLE_ORGANIZER, ROLE_REQUIRED, ROLE_OPTIONAL
    };

    public static final String[] RESPONSE_OPTIONS = {
        RESPONSE_PENDING, RESPONSE_ACCEPTED, RESPONSE_DECLINED, RESPONSE_TENTATIVE
    };

    // ─── Avatar Color Palette ────────────────────────────────────

    private static final String[] AVATAR_COLORS = {
        "#E53935", "#D81B60", "#8E24AA", "#5E35B1",
        "#3949AB", "#1E88E5", "#039BE5", "#00897B",
        "#43A047", "#7CB342", "#F4511E", "#FB8C00"
    };

    private static final AtomicInteger sColorIndex = new AtomicInteger(0);

    private static String nextAvatarColor() {
        int idx = sColorIndex.getAndIncrement();
        return AVATAR_COLORS[idx % AVATAR_COLORS.length];
    }

    // ─── Constructors ────────────────────────────────────────────

    public Attendee() {
        this.id = UUID.randomUUID().toString().substring(0, 12);
        this.meetingId = "";
        this.name = "";
        this.email = "";
        this.role = ROLE_REQUIRED;
        this.responseStatus = RESPONSE_PENDING;
        this.avatarColorHex = nextAvatarColor();
    }

    public Attendee(String name, String email, String role) {
        this();
        this.name = name != null ? name : "";
        this.email = email != null ? email : "";
        this.role = role != null ? role : ROLE_REQUIRED;
    }

    // ─── Helper Methods ──────────────────────────────────────────

    /**
     * Returns up to two capital initials derived from the attendee's name.
     * E.g. "John Doe" → "JD", "Alice" → "A".
     */
    public String getInitials() {
        if (name == null || name.trim().isEmpty()) return "?";
        String trimmed = name.trim();
        String[] parts = trimmed.split("\\s+");
        if (parts.length == 1) {
            return String.valueOf(parts[0].charAt(0)).toUpperCase();
        }
        return (String.valueOf(parts[0].charAt(0)) +
                String.valueOf(parts[parts.length - 1].charAt(0))).toUpperCase();
    }

    /** Returns the hex color string assigned to this attendee's avatar. */
    public String getAvatarColor() {
        return avatarColorHex != null ? avatarColorHex : AVATAR_COLORS[0];
    }

    public boolean isOrganizer() {
        return ROLE_ORGANIZER.equals(role);
    }

    public boolean hasAccepted() {
        return RESPONSE_ACCEPTED.equals(responseStatus);
    }

    public boolean hasDeclined() {
        return RESPONSE_DECLINED.equals(responseStatus);
    }

    public boolean hasPendingResponse() {
        return RESPONSE_PENDING.equals(responseStatus);
    }

    public String getRoleLabel() {
        if (role == null) return "";
        switch (role) {
            case ROLE_ORGANIZER: return "Organizer";
            case ROLE_REQUIRED:  return "Required";
            case ROLE_OPTIONAL:  return "Optional";
            default:             return role;
        }
    }

    public String getResponseLabel() {
        if (responseStatus == null) return "";
        switch (responseStatus) {
            case RESPONSE_ACCEPTED:  return "Accepted";
            case RESPONSE_DECLINED:  return "Declined";
            case RESPONSE_TENTATIVE: return "Tentative";
            case RESPONSE_PENDING:   return "Pending";
            default:                 return responseStatus;
        }
    }

    // ─── JSON Serialization ──────────────────────────────────────

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("id", id);
            json.put("meetingId", meetingId != null ? meetingId : "");
            json.put("name", name != null ? name : "");
            json.put("email", email != null ? email : "");
            json.put("role", role != null ? role : ROLE_REQUIRED);
            json.put("responseStatus", responseStatus != null ? responseStatus : RESPONSE_PENDING);
            json.put("avatarColorHex", avatarColorHex != null ? avatarColorHex : AVATAR_COLORS[0]);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return json;
    }

    public static Attendee fromJson(JSONObject json) {
        if (json == null) return null;
        try {
            Attendee attendee = new Attendee();
            attendee.id = json.optString("id", attendee.id);
            attendee.meetingId = json.optString("meetingId", "");
            attendee.name = json.optString("name", "");
            attendee.email = json.optString("email", "");
            attendee.role = json.optString("role", ROLE_REQUIRED);
            attendee.responseStatus = json.optString("responseStatus", RESPONSE_PENDING);
            attendee.avatarColorHex = json.optString("avatarColorHex", AVATAR_COLORS[0]);
            return attendee;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
