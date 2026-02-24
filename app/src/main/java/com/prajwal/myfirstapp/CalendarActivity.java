package com.prajwal.myfirstapp;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import android.app.AlertDialog;
import android.text.InputType;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.*;

public class CalendarActivity extends AppCompatActivity {

    private static final String TAG = "CalendarActivity";
    private static CalendarActivity instance;

    private String serverIp;
    private int currentYear, currentMonth; // month is 1-based
    private String selectedDate; // "YYYY-MM-DD"

    private TextView tvMonthYear, tvSelectedDate, tvSelectedDateSub, tvEventCount;
    private LinearLayout calendarGrid;
    private ListView eventsListView;

    private JSONArray allEvents = new JSONArray();
    private final SimpleDateFormat sdfDisplay = new SimpleDateFormat("MMMM d, yyyy", Locale.US);
    private final SimpleDateFormat sdfDayName = new SimpleDateFormat("EEEE", Locale.US);

    // Event colors matching Python side
    private static final Map<String, int[]> EVENT_COLORS = new HashMap<>();
    static {
        EVENT_COLORS.put("blue",   new int[]{Color.parseColor("#1E3A5F"), Color.parseColor("#3B82F6")});
        EVENT_COLORS.put("red",    new int[]{Color.parseColor("#7F1D1D"), Color.parseColor("#EF4444")});
        EVENT_COLORS.put("green",  new int[]{Color.parseColor("#14532D"), Color.parseColor("#22C55E")});
        EVENT_COLORS.put("purple", new int[]{Color.parseColor("#4C1D95"), Color.parseColor("#8B5CF6")});
        EVENT_COLORS.put("orange", new int[]{Color.parseColor("#7C2D12"), Color.parseColor("#F97316")});
        EVENT_COLORS.put("pink",   new int[]{Color.parseColor("#831843"), Color.parseColor("#EC4899")});
        EVENT_COLORS.put("yellow", new int[]{Color.parseColor("#713F12"), Color.parseColor("#EAB308")});
        EVENT_COLORS.put("teal",   new int[]{Color.parseColor("#134E4A"), Color.parseColor("#14B8A6")});
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calendar);
        instance = this;

        serverIp = getIntent().getStringExtra("server_ip");

        // Current date
        Calendar now = Calendar.getInstance();
        currentYear = now.get(Calendar.YEAR);
        currentMonth = now.get(Calendar.MONTH) + 1; // 1-based
        selectedDate = String.format(Locale.US, "%04d-%02d-%02d",
                currentYear, currentMonth, now.get(Calendar.DAY_OF_MONTH));

        // Views
        tvMonthYear = findViewById(R.id.tvMonthYear);
        tvSelectedDate = findViewById(R.id.tvSelectedDate);
        tvSelectedDateSub = findViewById(R.id.tvSelectedDateSub);
        tvEventCount = findViewById(R.id.tvEventCount);
        calendarGrid = findViewById(R.id.calendarGrid);
        eventsListView = findViewById(R.id.eventsListView);

        // Back button
        findViewById(R.id.btnBackCalendar).setOnClickListener(v -> finish());

        // Month navigation
        findViewById(R.id.btnPrevMonth).setOnClickListener(v -> {
            if (currentMonth == 1) { currentMonth = 12; currentYear--; }
            else currentMonth--;
            renderMonth();
        });
        findViewById(R.id.btnNextMonth).setOnClickListener(v -> {
            if (currentMonth == 12) { currentMonth = 1; currentYear++; }
            else currentMonth++;
            renderMonth();
        });

        // Today button
        findViewById(R.id.btnToday).setOnClickListener(v -> {
            Calendar today = Calendar.getInstance();
            currentYear = today.get(Calendar.YEAR);
            currentMonth = today.get(Calendar.MONTH) + 1;
            selectedDate = String.format(Locale.US, "%04d-%02d-%02d",
                    currentYear, currentMonth, today.get(Calendar.DAY_OF_MONTH));
            renderMonth();
            refreshEvents();
        });

        // Sync button
        findViewById(R.id.btnSyncCalendar).setOnClickListener(v -> {
            requestCalendarSync();
            Toast.makeText(this, "Syncing calendar...", Toast.LENGTH_SHORT).show();
        });

        // FAB: Add event
        findViewById(R.id.fabAddEvent).setOnClickListener(v -> showAddEventDialog());

        // Event list long click to delete
        eventsListView.setOnItemLongClickListener((parent, view, position, id) -> {
            try {
                JSONArray dayEvents = getEventsForDate(selectedDate);
                if (position < dayEvents.length()) {
                    JSONObject event = dayEvents.getJSONObject(position);
                    showDeleteDialog(event);
                }
            } catch (JSONException e) {
                Log.e(TAG, "Error on long click", e);
            }
            return true;
        });

        // Event list tap to edit
        eventsListView.setOnItemClickListener((parent, view, position, id) -> {
            try {
                JSONArray dayEvents = getEventsForDate(selectedDate);
                if (position < dayEvents.length()) {
                    JSONObject event = dayEvents.getJSONObject(position);
                    showEditEventDialog(event);
                }
            } catch (JSONException e) {
                Log.e(TAG, "Error on click", e);
            }
        });

        // Initial render
        renderMonth();
        refreshEvents();

        // Request sync on open
        requestCalendarSync();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (instance == this) instance = null;
    }

    public static CalendarActivity getInstance() {
        return instance;
    }

    // ═══ CALENDAR GRID RENDERING ═══

    private void renderMonth() {
        calendarGrid.removeAllViews();
        tvMonthYear.setText(getMonthName(currentMonth) + " " + currentYear);

        Calendar cal = Calendar.getInstance();
        cal.set(currentYear, currentMonth - 1, 1); // month 0-based for Calendar
        int firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK); // Sun=1, Mon=2...
        // Convert to Mon=0, Tue=1, ..., Sun=6
        int startOffset = (firstDayOfWeek + 5) % 7;
        int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);

        Calendar today = Calendar.getInstance();
        String todayStr = String.format(Locale.US, "%04d-%02d-%02d",
                today.get(Calendar.YEAR), today.get(Calendar.MONTH) + 1,
                today.get(Calendar.DAY_OF_MONTH));

        int day = 1;
        for (int row = 0; row < 6 && day <= daysInMonth; row++) {
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(48)));

            for (int col = 0; col < 7; col++) {
                LinearLayout cellLayout = new LinearLayout(this);
                cellLayout.setOrientation(LinearLayout.VERTICAL);
                cellLayout.setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams cellParams = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
                cellParams.setMargins(dpToPx(1), dpToPx(1), dpToPx(1), dpToPx(1));
                cellLayout.setLayoutParams(cellParams);

                if ((row == 0 && col < startOffset) || day > daysInMonth) {
                    // Empty cell
                    rowLayout.addView(cellLayout);
                    continue;
                }

                final String dateStr = String.format(Locale.US, "%04d-%02d-%02d",
                        currentYear, currentMonth, day);
                boolean isToday = dateStr.equals(todayStr);
                boolean isSelected = dateStr.equals(selectedDate);
                boolean hasEvents = hasEventsForDate(dateStr);

                // Background
                GradientDrawable bg = new GradientDrawable();
                bg.setCornerRadius(dpToPx(8));
                if (isSelected) {
                    bg.setColor(Color.parseColor("#1D4ED8"));
                } else if (isToday) {
                    bg.setColor(Color.parseColor("#164E63"));
                } else {
                    bg.setColor(Color.TRANSPARENT);
                }
                cellLayout.setBackground(bg);

                // Day number
                TextView dayTv = new TextView(this);
                dayTv.setText(String.valueOf(day));
                dayTv.setTextSize(14);
                dayTv.setGravity(Gravity.CENTER);
                dayTv.setTextColor(isSelected || isToday ?
                        Color.parseColor("#F1F5F9") : Color.parseColor("#CBD5E1"));
                if (isToday) dayTv.setTypeface(null, android.graphics.Typeface.BOLD);
                cellLayout.addView(dayTv);

                // Event indicator dot
                if (hasEvents) {
                    View dot = new View(this);
                    LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(
                            dpToPx(5), dpToPx(5));
                    dotParams.gravity = Gravity.CENTER;
                    dotParams.topMargin = dpToPx(1);
                    dot.setLayoutParams(dotParams);
                    GradientDrawable dotBg = new GradientDrawable();
                    dotBg.setShape(GradientDrawable.OVAL);
                    dotBg.setColor(isSelected ? Color.parseColor("#BFDBFE") :
                            Color.parseColor("#3B82F6"));
                    dot.setBackground(dotBg);
                    cellLayout.addView(dot);
                }

                // Click listener
                final int dayFinal = day;
                cellLayout.setOnClickListener(v -> {
                    selectedDate = dateStr;
                    renderMonth();
                    refreshEvents();
                });

                rowLayout.addView(cellLayout);
                day++;
            }

            calendarGrid.addView(rowLayout);
        }
    }

    // ═══ EVENT LIST ═══

    private void refreshEvents() {
        // Update selected date header
        try {
            Calendar cal = Calendar.getInstance();
            String[] parts = selectedDate.split("-");
            cal.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1,
                    Integer.parseInt(parts[2]));
            tvSelectedDate.setText(sdfDayName.format(cal.getTime()));
            tvSelectedDateSub.setText(sdfDisplay.format(cal.getTime()));
        } catch (Exception e) {
            tvSelectedDate.setText(selectedDate);
        }

        JSONArray dayEvents = getEventsForDate(selectedDate);
        int count = dayEvents.length();
        tvEventCount.setText(count + " event" + (count != 1 ? "s" : ""));

        // Populate list
        List<Map<String, String>> eventData = new ArrayList<>();
        for (int i = 0; i < dayEvents.length(); i++) {
            try {
                JSONObject ev = dayEvents.getJSONObject(i);
                Map<String, String> map = new HashMap<>();
                map.put("title", ev.optString("title", "Untitled"));
                String startTime = ev.optString("start_time", "");
                String endTime = ev.optString("end_time", "");
                if (!startTime.isEmpty() && !endTime.isEmpty()) {
                    map.put("time", startTime + " — " + endTime);
                } else if (!startTime.isEmpty()) {
                    map.put("time", startTime);
                } else {
                    map.put("time", "All day");
                }
                String recurring = ev.optString("recurring", "none");
                if (!recurring.equals("none")) {
                    map.put("time", map.get("time") + "  🔄 " + recurring);
                }
                map.put("description", ev.optString("description", ""));
                map.put("color", ev.optString("color", "blue"));
                eventData.add(map);
            } catch (JSONException e) {
                Log.e(TAG, "Event parse error", e);
            }
        }

        // Custom adapter for event display
        eventsListView.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return eventData.size(); }
            @Override public Object getItem(int pos) { return eventData.get(pos); }
            @Override public long getItemId(int pos) { return pos; }

            @Override
            public View getView(int pos, View convertView, ViewGroup parent) {
                Map<String, String> item = eventData.get(pos);
                String colorKey = item.get("color");
                int[] colors = EVENT_COLORS.containsKey(colorKey) ?
                        EVENT_COLORS.get(colorKey) : EVENT_COLORS.get("blue");

                LinearLayout card = new LinearLayout(CalendarActivity.this);
                card.setOrientation(LinearLayout.HORIZONTAL);
                card.setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12));

                GradientDrawable cardBg = new GradientDrawable();
                cardBg.setCornerRadius(dpToPx(12));
                cardBg.setColor(colors[0]);
                card.setBackground(cardBg);

                // Accent bar
                View accent = new View(CalendarActivity.this);
                LinearLayout.LayoutParams accentParams = new LinearLayout.LayoutParams(
                        dpToPx(4), ViewGroup.LayoutParams.MATCH_PARENT);
                accentParams.setMargins(0, 0, dpToPx(12), 0);
                accent.setLayoutParams(accentParams);
                GradientDrawable accentBg = new GradientDrawable();
                accentBg.setCornerRadius(dpToPx(2));
                accentBg.setColor(colors[1]);
                accent.setBackground(accentBg);
                card.addView(accent);

                // Text content
                LinearLayout textCol = new LinearLayout(CalendarActivity.this);
                textCol.setOrientation(LinearLayout.VERTICAL);
                textCol.setLayoutParams(new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                TextView titleTv = new TextView(CalendarActivity.this);
                titleTv.setText(item.get("title"));
                titleTv.setTextSize(15);
                titleTv.setTextColor(Color.parseColor("#F1F5F9"));
                titleTv.setTypeface(null, android.graphics.Typeface.BOLD);
                textCol.addView(titleTv);

                TextView timeTv = new TextView(CalendarActivity.this);
                timeTv.setText(item.get("time"));
                timeTv.setTextSize(12);
                timeTv.setTextColor(Color.parseColor("#BFDBFE"));
                timeTv.setPadding(0, dpToPx(2), 0, 0);
                textCol.addView(timeTv);

                String desc = item.get("description");
                if (desc != null && !desc.isEmpty()) {
                    TextView descTv = new TextView(CalendarActivity.this);
                    descTv.setText(desc.length() > 60 ? desc.substring(0, 60) + "..." : desc);
                    descTv.setTextSize(11);
                    descTv.setTextColor(Color.parseColor("#94A3B8"));
                    descTv.setPadding(0, dpToPx(2), 0, 0);
                    textCol.addView(descTv);
                }

                card.addView(textCol);
                return card;
            }
        });
    }

    // ═══ ADD / EDIT EVENT DIALOGS ═══

    private void showAddEventDialog() {
        showEventDialog(null);
    }

    private void showEditEventDialog(JSONObject existingEvent) {
        showEventDialog(existingEvent);
    }

    private void showEventDialog(JSONObject existingEvent) {
        boolean isEdit = existingEvent != null;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(isEdit ? "✏️ Edit Event" : "📅 New Event");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dpToPx(20), dpToPx(12), dpToPx(20), dpToPx(4));

        EditText etTitle = addDialogField(layout, "Title", isEdit ?
                existingEvent.optString("title", "") : "");
        EditText etDate = addDialogField(layout, "Date (YYYY-MM-DD)", isEdit ?
                existingEvent.optString("date", selectedDate) : selectedDate);
        EditText etStart = addDialogField(layout, "Start Time (HH:MM)", isEdit ?
                existingEvent.optString("start_time", "") : "");
        EditText etEnd = addDialogField(layout, "End Time (HH:MM)", isEdit ?
                existingEvent.optString("end_time", "") : "");
        EditText etDesc = addDialogField(layout, "Description", isEdit ?
                existingEvent.optString("description", "") : "");

        // Color selector
        TextView colorLabel = new TextView(this);
        colorLabel.setText("Color");
        colorLabel.setTextColor(Color.parseColor("#94A3B8"));
        colorLabel.setTextSize(12);
        colorLabel.setPadding(0, dpToPx(8), 0, dpToPx(4));
        layout.addView(colorLabel);

        final String[] selectedColor = {isEdit ? existingEvent.optString("color", "blue") : "blue"};
        String[] colorNames = {"blue", "red", "green", "purple", "orange", "pink", "yellow", "teal"};

        LinearLayout colorRow = new LinearLayout(this);
        colorRow.setOrientation(LinearLayout.HORIZONTAL);
        layout.addView(colorRow);

        for (String cName : colorNames) {
            int[] cVals = EVENT_COLORS.get(cName);
            Button cBtn = new Button(this);
            cBtn.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(36), dpToPx(36)));
            GradientDrawable cBg = new GradientDrawable();
            cBg.setShape(GradientDrawable.OVAL);
            cBg.setColor(cVals[1]);
            if (cName.equals(selectedColor[0])) {
                cBg.setStroke(dpToPx(3), Color.WHITE);
            }
            cBtn.setBackground(cBg);
            cBtn.setText("");
            cBtn.setOnClickListener(v -> {
                selectedColor[0] = cName;
                // Refresh color buttons
                for (int i = 0; i < colorRow.getChildCount(); i++) {
                    View child = colorRow.getChildAt(i);
                    GradientDrawable cbg = new GradientDrawable();
                    cbg.setShape(GradientDrawable.OVAL);
                    cbg.setColor(EVENT_COLORS.get(colorNames[i])[1]);
                    if (colorNames[i].equals(cName)) {
                        cbg.setStroke(dpToPx(3), Color.WHITE);
                    }
                    child.setBackground(cbg);
                }
            });
            colorRow.addView(cBtn);
        }

        // Recurring spinner
        TextView recurLabel = new TextView(this);
        recurLabel.setText("Recurring");
        recurLabel.setTextColor(Color.parseColor("#94A3B8"));
        recurLabel.setTextSize(12);
        recurLabel.setPadding(0, dpToPx(8), 0, dpToPx(4));
        layout.addView(recurLabel);

        Spinner spinRecurring = new Spinner(this);
        String[] recurOptions = {"none", "daily", "weekly", "monthly", "yearly"};
        ArrayAdapter<String> recurAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, recurOptions);
        recurAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinRecurring.setAdapter(recurAdapter);
        if (isEdit) {
            String recur = existingEvent.optString("recurring", "none");
            for (int i = 0; i < recurOptions.length; i++) {
                if (recurOptions[i].equals(recur)) {
                    spinRecurring.setSelection(i);
                    break;
                }
            }
        }
        layout.addView(spinRecurring);

        builder.setView(layout);

        builder.setPositiveButton(isEdit ? "Save" : "Add", (dialog, which) -> {
            String title = etTitle.getText().toString().trim();
            if (title.isEmpty()) {
                Toast.makeText(this, "Title is required", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                JSONObject eventObj = new JSONObject();
                if (isEdit) {
                    eventObj.put("id", existingEvent.optString("id"));
                }
                eventObj.put("title", title);
                eventObj.put("date", etDate.getText().toString().trim().isEmpty() ?
                        selectedDate : etDate.getText().toString().trim());
                eventObj.put("start_time", etStart.getText().toString().trim());
                eventObj.put("end_time", etEnd.getText().toString().trim());
                eventObj.put("description", etDesc.getText().toString().trim());
                eventObj.put("color", selectedColor[0]);
                eventObj.put("recurring", spinRecurring.getSelectedItem().toString());

                String command = isEdit ?
                        "CAL_UPDATE:" + eventObj.toString() :
                        "CAL_ADD:" + eventObj.toString();
                sendCommand(command);

                Toast.makeText(this, isEdit ? "Event updated" : "Event added",
                        Toast.LENGTH_SHORT).show();
            } catch (JSONException e) {
                Log.e(TAG, "Event JSON error", e);
            }
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showDeleteDialog(JSONObject event) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Event")
                .setMessage("Delete '" + event.optString("title", "Untitled") + "'?")
                .setPositiveButton("Delete", (d, w) -> {
                    String eventId = event.optString("id", "");
                    sendCommand("CAL_DELETE:" + eventId);
                    Toast.makeText(this, "Event deleted", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private EditText addDialogField(LinearLayout parent, String hint, String value) {
        TextView label = new TextView(this);
        label.setText(hint);
        label.setTextColor(Color.parseColor("#94A3B8"));
        label.setTextSize(12);
        label.setPadding(0, dpToPx(8), 0, dpToPx(2));
        parent.addView(label);

        EditText et = new EditText(this);
        et.setHint(hint);
        et.setTextColor(Color.parseColor("#E2E8F0"));
        et.setHintTextColor(Color.parseColor("#475569"));
        et.setTextSize(14);
        if (!value.isEmpty()) et.setText(value);
        et.setSingleLine();
        parent.addView(et);
        return et;
    }

    // ═══ DATA HELPERS ═══

    private JSONArray getEventsForDate(String dateStr) {
        JSONArray result = new JSONArray();
        for (int i = 0; i < allEvents.length(); i++) {
            try {
                JSONObject ev = allEvents.getJSONObject(i);
                if (dateStr.equals(ev.optString("date"))) {
                    result.put(ev);
                } else if (matchesRecurring(ev, dateStr)) {
                    result.put(ev);
                }
            } catch (JSONException e) {
                /* skip */
            }
        }
        return result;
    }

    private boolean hasEventsForDate(String dateStr) {
        return getEventsForDate(dateStr).length() > 0;
    }

    private boolean matchesRecurring(JSONObject event, String targetDate) {
        String recurrence = event.optString("recurring", "none");
        if ("none".equals(recurrence)) return false;

        try {
            String[] evParts = event.optString("date", "").split("-");
            String[] targetParts = targetDate.split("-");
            if (evParts.length != 3 || targetParts.length != 3) return false;

            int evYear = Integer.parseInt(evParts[0]);
            int evMonth = Integer.parseInt(evParts[1]);
            int evDay = Integer.parseInt(evParts[2]);
            int tYear = Integer.parseInt(targetParts[0]);
            int tMonth = Integer.parseInt(targetParts[1]);
            int tDay = Integer.parseInt(targetParts[2]);

            // Target must be after event date
            Calendar evCal = Calendar.getInstance();
            evCal.set(evYear, evMonth - 1, evDay);
            Calendar tCal = Calendar.getInstance();
            tCal.set(tYear, tMonth - 1, tDay);

            if (!tCal.after(evCal)) return false;

            switch (recurrence) {
                case "daily": return true;
                case "weekly":
                    return evCal.get(Calendar.DAY_OF_WEEK) == tCal.get(Calendar.DAY_OF_WEEK);
                case "monthly":
                    return evDay == tDay;
                case "yearly":
                    return evMonth == tMonth && evDay == tDay;
            }
        } catch (Exception e) {
            // ignore
        }
        return false;
    }

    // ═══ SYNC AND COMMUNICATION ═══

    public void onCalendarSyncReceived(String calendarJson) {
        Log.i(TAG, "Calendar sync received, length=" + calendarJson.length());
        try {
            allEvents = new JSONArray(calendarJson);
            runOnUiThread(() -> {
                renderMonth();
                refreshEvents();
                Toast.makeText(this, "Calendar synced ✓", Toast.LENGTH_SHORT).show();
            });
        } catch (JSONException e) {
            Log.e(TAG, "Calendar sync parse error", e);
        }
    }

    public void onCalendarEventReceived(String action, String eventId) {
        Log.i(TAG, "Calendar event: " + action + " id=" + eventId);
        // Request a full sync to get updated data
        requestCalendarSync();
    }

    private void requestCalendarSync() {
        sendCommand("CAL_SYNC");
    }

    private void sendCommand(String command) {
        if (serverIp == null || serverIp.isEmpty()) return;
        new Thread(() -> {
            try {
                DatagramSocket socket = new DatagramSocket();
                byte[] data = command.getBytes("UTF-8");
                InetAddress address = InetAddress.getByName(serverIp);
                DatagramPacket packet = new DatagramPacket(data, data.length, address, 6001);
                socket.send(packet);
                socket.close();
                Log.d(TAG, "Sent: " + (command.length() > 100 ?
                        command.substring(0, 100) + "..." : command));
            } catch (Exception e) {
                Log.e(TAG, "Send error: " + e.getMessage());
            }
        }).start();
    }

    // ═══ UTILS ═══

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    private String getMonthName(int month) {
        String[] months = {"", "January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December"};
        return months[month];
    }
}
