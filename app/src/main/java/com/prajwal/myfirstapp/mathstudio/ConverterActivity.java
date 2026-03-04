package com.prajwal.myfirstapp.mathstudio;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.prajwal.myfirstapp.R;
import java.util.ArrayList;
import java.util.List;

public class ConverterActivity extends AppCompatActivity {

    private static final int[][] CATEGORY_COLORS = {
        {0xFF1E3A5F, 0xFF2563EB}, // Length - blue
        {0xFF3B1F5F, 0xFF7C3AED}, // Weight - purple
        {0xFF5F1F1F, 0xFFDC2626}, // Temperature - red
        {0xFF1F4F2F, 0xFF16A34A}, // Area - green
        {0xFF1F3F5F, 0xFF0EA5E9}, // Volume - sky
        {0xFF4F3F1F, 0xFFF59E0B}, // Speed - amber
        {0xFF1F4F4F, 0xFF14B8A6}, // Time - teal
        {0xFF2F1F5F, 0xFF8B5CF6}, // Data Storage - violet
        {0xFF3F2F1F, 0xFFEA580C}, // Pressure - orange
        {0xFF1F5F3F, 0xFF059669}, // Energy - emerald
        {0xFF5F3F1F, 0xFFF97316}, // Power - orange
        {0xFF1F5F5F, 0xFF06B6D4}, // Currency - cyan
        {0xFF4F4F1F, 0xFFCAC531}, // Fuel Efficiency - yellow
        {0xFF3F1F4F, 0xFFEC4899}, // Angle - pink
    };

    private static final String[] CATEGORIES = {
        "Length", "Weight", "Temperature", "Area", "Volume", "Speed",
        "Time", "Data Storage", "Pressure", "Energy", "Power",
        "Currency", "Fuel Efficiency", "Angle"
    };

    private static final String[] CAT_ICONS = {
        "📏", "⚖️", "🌡️", "▭", "🧪", "💨",
        "⏱️", "💾", "🌡", "⚡", "🔋",
        "💱", "⛽", "📐"
    };

    // Units per category: {name, toBaseFactor} — base unit is first in list
    private static final String[][] LENGTH_UNITS = {
        {"Meter (m)", "1"}, {"Kilometer (km)", "1000"}, {"Centimeter (cm)", "0.01"},
        {"Millimeter (mm)", "0.001"}, {"Mile (mi)", "1609.344"}, {"Yard (yd)", "0.9144"},
        {"Foot (ft)", "0.3048"}, {"Inch (in)", "0.0254"}, {"Nautical Mile", "1852"}
    };
    private static final String[][] WEIGHT_UNITS = {
        {"Kilogram (kg)", "1"}, {"Gram (g)", "0.001"}, {"Milligram (mg)", "0.000001"},
        {"Pound (lb)", "0.453592"}, {"Ounce (oz)", "0.028349"}, {"Tonne (t)", "1000"},
        {"Stone (st)", "6.35029"}
    };
    private static final String[][] AREA_UNITS = {
        {"Square Meter (m²)", "1"}, {"Square Km (km²)", "1e6"}, {"Square Cm (cm²)", "0.0001"},
        {"Square Mile (mi²)", "2589988.11"}, {"Acre", "4046.856"}, {"Hectare (ha)", "10000"},
        {"Square Foot (ft²)", "0.092903"}, {"Square Inch (in²)", "0.00064516"}
    };
    private static final String[][] VOLUME_UNITS = {
        {"Liter (L)", "1"}, {"Milliliter (mL)", "0.001"}, {"Cubic Meter (m³)", "1000"},
        {"Gallon (US)", "3.78541"}, {"Quart (US)", "0.946353"}, {"Pint (US)", "0.473176"},
        {"Cup (US)", "0.236588"}, {"Fluid Ounce (fl oz)", "0.0295735"},
        {"Cubic Foot (ft³)", "28.3168"}, {"Cubic Inch (in³)", "0.0163871"}
    };
    private static final String[][] SPEED_UNITS = {
        {"km/h", "1"}, {"m/s", "3.6"}, {"mph", "1.60934"},
        {"Knot (kn)", "1.852"}, {"ft/s", "1.09728"}
    };
    private static final String[][] TIME_UNITS = {
        {"Second (s)", "1"}, {"Minute (min)", "60"}, {"Hour (h)", "3600"},
        {"Day", "86400"}, {"Week", "604800"}, {"Month (30d)", "2592000"},
        {"Year (365d)", "31536000"}, {"Millisecond (ms)", "0.001"},
        {"Microsecond (μs)", "0.000001"}
    };
    private static final String[][] DATA_UNITS = {
        {"Byte (B)", "1"}, {"Kilobyte (KB)", "1024"}, {"Megabyte (MB)", "1048576"},
        {"Gigabyte (GB)", "1073741824"}, {"Terabyte (TB)", "1.09951e12"},
        {"Bit (b)", "0.125"}, {"Kilobit (Kb)", "128"}, {"Megabit (Mb)", "131072"}
    };
    private static final String[][] PRESSURE_UNITS = {
        {"Pascal (Pa)", "1"}, {"Kilopascal (kPa)", "1000"}, {"Bar", "100000"},
        {"Atmosphere (atm)", "101325"}, {"PSI", "6894.757"}, {"mmHg (Torr)", "133.322"},
        {"Megapascal (MPa)", "1000000"}
    };
    private static final String[][] ENERGY_UNITS = {
        {"Joule (J)", "1"}, {"Kilojoule (kJ)", "1000"}, {"Calorie (cal)", "4.184"},
        {"Kilocalorie (kcal)", "4184"}, {"Watt-hour (Wh)", "3600"},
        {"kWh", "3600000"}, {"BTU", "1055.06"}, {"Electronvolt (eV)", "1.60218e-19"}
    };
    private static final String[][] POWER_UNITS = {
        {"Watt (W)", "1"}, {"Kilowatt (kW)", "1000"}, {"Megawatt (MW)", "1000000"},
        {"Horsepower (hp)", "745.7"}, {"BTU/hour", "0.293071"}
    };
    // Currency: base = USD
    // NOTE: These are sample/demonstration rates only (last updated: Jan 2025).
    // They are not real-time rates and should not be used for actual financial transactions.
    private static final String[][] CURRENCY_UNITS = {
        {"USD ($)", "1"}, {"EUR (€)", "1.08"}, {"GBP (£)", "1.27"},
        {"INR (₹)", "0.012"}, {"JPY (¥)", "0.0067"}, {"CAD (C$)", "0.74"},
        {"AUD (A$)", "0.65"}, {"CHF", "1.11"}, {"CNY (¥)", "0.14"},
        {"SGD", "0.74"}, {"BRL (R$)", "0.20"}, {"MXN", "0.058"}
    };
    private static final String[][] FUEL_UNITS = {
        {"km/L", "1"}, {"L/100km", "-1"}, // special: inverted
        {"mpg (US)", "0.425144"}, {"mpg (UK)", "0.354006"}
    };
    private static final String[][] ANGLE_UNITS = {
        {"Degree (°)", "1"}, {"Radian (rad)", "57.2958"}, {"Gradian (grad)", "0.9"},
        {"Arcminute (')", "0.016667"}, {"Arcsecond (\")", "0.000278"}
    };

    private static final String[][][] ALL_UNITS = {
        LENGTH_UNITS, WEIGHT_UNITS, null /*Temp*/, AREA_UNITS, VOLUME_UNITS, SPEED_UNITS,
        TIME_UNITS, DATA_UNITS, PRESSURE_UNITS, ENERGY_UNITS, POWER_UNITS,
        CURRENCY_UNITS, FUEL_UNITS, ANGLE_UNITS
    };

    private int selectedCategoryIdx = -1;
    private LinearLayout categoryGrid;
    private ScrollView scrollCategories;
    private LinearLayout panelConverter;
    private List<View> allCategoryCards = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_converter);

        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        categoryGrid = findViewById(R.id.categoryGrid);
        scrollCategories = findViewById(R.id.scrollCategories);
        panelConverter = findViewById(R.id.panelConverter);

        buildCategoryGrid("");
        setupSearch();
        setupConverterPanel();

        Button btnBackToCat = findViewById(R.id.btnBackToCategories);
        if (btnBackToCat != null) btnBackToCat.setOnClickListener(v -> showCategories());
    }

    private void setupSearch() {
        EditText etSearch = findViewById(R.id.etSearch);
        if (etSearch == null) return;
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                buildCategoryGrid(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void buildCategoryGrid(String filter) {
        if (categoryGrid == null) return;
        categoryGrid.removeAllViews();
        allCategoryCards.clear();

        int dp8 = dp(8);
        String lowerFilter = filter.toLowerCase();

        LinearLayout rowLayout = null;
        int colCount = 0;

        for (int i = 0; i < CATEGORIES.length; i++) {
            if (!filter.isEmpty() && !CATEGORIES[i].toLowerCase().contains(lowerFilter)) continue;
            final int catIdx = i;

            if (colCount % 2 == 0) {
                rowLayout = new LinearLayout(this);
                rowLayout.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                rowLp.setMargins(0, 0, 0, dp8);
                rowLayout.setLayoutParams(rowLp);
                categoryGrid.addView(rowLayout);
            }

            View card = buildCategoryCard(i, dp8);
            card.setOnClickListener(v -> openConverter(catIdx));
            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT);
            cardLp.weight = 1;
            if (colCount % 2 == 0) cardLp.setMarginEnd(dp8);
            card.setLayoutParams(cardLp);
            if (rowLayout != null) rowLayout.addView(card);
            allCategoryCards.add(card);
            colCount++;
        }
        // Fill empty slot if odd count
        if (colCount % 2 == 1 && rowLayout != null) {
            View spacer = new View(this);
            LinearLayout.LayoutParams spacerLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT);
            spacerLp.weight = 1;
            spacer.setLayoutParams(spacerLp);
            rowLayout.addView(spacer);
        }
    }

    private View buildCategoryCard(int idx, int dp8) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(CATEGORY_COLORS[idx][0]);
        card.setPadding(dp8 + 4, dp8 + 4, dp8 + 4, dp8 + 4);
        card.setClickable(true);
        card.setFocusable(true);

        TextView icon = new TextView(this);
        icon.setText(CAT_ICONS[idx]);
        icon.setTextSize(24);
        icon.setGravity(Gravity.CENTER);
        card.addView(icon);

        TextView name = new TextView(this);
        name.setText(CATEGORIES[idx]);
        name.setTextColor(0xFFFFFFFF);
        name.setTextSize(13);
        name.setGravity(Gravity.CENTER);
        name.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        card.addView(name);

        // Colored accent line
        View line = new View(this);
        LinearLayout.LayoutParams lineLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(3));
        lineLp.setMargins(0, dp8 / 2, 0, 0);
        line.setLayoutParams(lineLp);
        line.setBackgroundColor(CATEGORY_COLORS[idx][1]);
        card.addView(line);

        return card;
    }

    private void openConverter(int catIdx) {
        selectedCategoryIdx = catIdx;
        scrollCategories.setVisibility(View.GONE);
        panelConverter.setVisibility(View.VISIBLE);

        TextView tvTitle = findViewById(R.id.tvCategoryTitle);
        if (tvTitle != null) tvTitle.setText(CAT_ICONS[catIdx] + " " + CATEGORIES[catIdx]);

        boolean isTemp = catIdx == 2;
        boolean isCurrency = catIdx == 11;
        boolean isFuel = catIdx == 12;

        View tempPanel = findViewById(R.id.panelTempAll);
        View currencyNote = findViewById(R.id.tvCurrencyNote);
        if (tempPanel != null) tempPanel.setVisibility(isTemp ? View.VISIBLE : View.GONE);
        if (currencyNote != null) currencyNote.setVisibility(isCurrency ? View.VISIBLE : View.GONE);

        Spinner spinFrom = findViewById(R.id.spinnerFromUnit);
        Spinner spinTo = findViewById(R.id.spinnerToUnit);
        if (spinFrom == null || spinTo == null) return;

        if (isTemp) {
            String[] tempUnits = {"Celsius (°C)", "Fahrenheit (°F)", "Kelvin (K)"};
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, tempUnits);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinFrom.setAdapter(adapter);
            spinTo.setAdapter(adapter);
            spinTo.setSelection(1);
        } else {
            String[][] units = ALL_UNITS[catIdx];
            String[] names = new String[units.length];
            for (int i = 0; i < units.length; i++) names[i] = units[i][0];
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, names);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinFrom.setAdapter(adapter);
            spinTo.setAdapter(adapter);
            if (units.length > 1) spinTo.setSelection(1);
        }

        setupLiveConversion();
    }

    private void showCategories() {
        scrollCategories.setVisibility(View.VISIBLE);
        panelConverter.setVisibility(View.GONE);
        selectedCategoryIdx = -1;
    }

    private void setupConverterPanel() {
        Button btnSwap = findViewById(R.id.btnSwap);
        if (btnSwap != null) btnSwap.setOnClickListener(v -> {
            Spinner spinFrom = findViewById(R.id.spinnerFromUnit);
            Spinner spinTo = findViewById(R.id.spinnerToUnit);
            EditText etFrom = findViewById(R.id.etFromValue);
            EditText etTo = findViewById(R.id.etToValue);
            if (spinFrom == null || spinTo == null) return;
            int fromPos = spinFrom.getSelectedItemPosition();
            int toPos = spinTo.getSelectedItemPosition();
            spinFrom.setSelection(toPos);
            spinTo.setSelection(fromPos);
            String fromVal = etFrom != null ? etFrom.getText().toString() : "";
            String toVal = etTo != null ? etTo.getText().toString() : "";
            if (etFrom != null) etFrom.setText(toVal);
            if (etTo != null) etTo.setText(fromVal);
        });
    }

    private void setupLiveConversion() {
        EditText etFrom = findViewById(R.id.etFromValue);
        if (etFrom == null) return;
        etFrom.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                performConversion(true);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        EditText etTo = findViewById(R.id.etToValue);
        if (etTo == null) return;
        etTo.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                performConversion(false);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private boolean converting = false;

    private void performConversion(boolean fromToTo) {
        if (converting || selectedCategoryIdx < 0) return;
        converting = true;
        try {
            EditText etFrom = findViewById(R.id.etFromValue);
            EditText etTo = findViewById(R.id.etToValue);
            Spinner spinFrom = findViewById(R.id.spinnerFromUnit);
            Spinner spinTo = findViewById(R.id.spinnerToUnit);
            if (etFrom == null || etTo == null || spinFrom == null || spinTo == null) return;

            EditText source = fromToTo ? etFrom : etTo;
            EditText dest = fromToTo ? etTo : etFrom;
            Spinner srcSpin = fromToTo ? spinFrom : spinTo;
            Spinner dstSpin = fromToTo ? spinTo : spinFrom;

            String valStr = source.getText().toString().trim();
            if (valStr.isEmpty() || valStr.equals("-") || valStr.equals(".")
                    || valStr.equals("-.") || valStr.equals("+")) { dest.setText(""); return; }
            double val;
            try { val = Double.parseDouble(valStr); }
            catch (NumberFormatException e) { dest.setText(""); return; }

            boolean isTemp = selectedCategoryIdx == 2;
            boolean isFuel = selectedCategoryIdx == 12;
            double result;

            if (isTemp) {
                result = convertTemperature(val, srcSpin.getSelectedItemPosition(),
                    dstSpin.getSelectedItemPosition());
            } else if (isFuel) {
                result = convertFuel(val, srcSpin.getSelectedItemPosition(),
                    dstSpin.getSelectedItemPosition());
            } else {
                String[][] units = ALL_UNITS[selectedCategoryIdx];
                int srcIdx = srcSpin.getSelectedItemPosition();
                int dstIdx = dstSpin.getSelectedItemPosition();
                if (srcIdx < 0 || srcIdx >= units.length || dstIdx < 0 || dstIdx >= units.length) return;
                double toBase = Double.parseDouble(units[srcIdx][1]);
                double fromBase = Double.parseDouble(units[dstIdx][1]);
                result = val * toBase / fromBase;
            }

            String formatted = formatResult(result);
            dest.setText(formatted);

            if (isTemp) {
                updateTempAllPanel(val, srcSpin.getSelectedItemPosition());
            }
        } finally {
            converting = false;
        }
    }

    private double convertTemperature(double val, int fromIdx, int toIdx) {
        // 0=C, 1=F, 2=K
        double celsius;
        switch (fromIdx) {
            case 0: celsius = val; break;
            case 1: celsius = (val - 32) * 5.0 / 9.0; break;
            case 2: celsius = val - 273.15; break;
            default: celsius = val;
        }
        switch (toIdx) {
            case 0: return celsius;
            case 1: return celsius * 9.0 / 5.0 + 32;
            case 2: return celsius + 273.15;
            default: return celsius;
        }
    }

    private double convertFuel(double val, int fromIdx, int toIdx) {
        // 0=km/L, 1=L/100km, 2=mpg(US), 3=mpg(UK)
        // Convert to km/L first
        double kmPerL;
        switch (fromIdx) {
            case 0: kmPerL = val; break;
            case 1: kmPerL = val == 0 ? 0 : 100.0 / val; break;
            case 2: kmPerL = val * 0.425144; break;
            case 3: kmPerL = val * 0.354006; break;
            default: kmPerL = val;
        }
        switch (toIdx) {
            case 0: return kmPerL;
            case 1: return kmPerL == 0 ? 0 : 100.0 / kmPerL;
            case 2: return kmPerL / 0.425144;
            case 3: return kmPerL / 0.354006;
            default: return kmPerL;
        }
    }

    private void updateTempAllPanel(double val, int fromIdx) {
        TextView tvAll = findViewById(R.id.tvTempAll);
        if (tvAll == null) return;
        double celsius = convertTemperature(val, fromIdx, 0);
        double fahrenheit = convertTemperature(val, fromIdx, 1);
        double kelvin = convertTemperature(val, fromIdx, 2);
        tvAll.setText(String.format("°C = %.4f\n°F = %.4f\nK  = %.4f", celsius, fahrenheit, kelvin));
    }

    private String formatResult(double result) {
        if (Double.isNaN(result) || Double.isInfinite(result)) return "∞";
        if (result == Math.floor(result) && Math.abs(result) < 1e10) {
            return String.valueOf((long) result);
        }
        String s = String.format("%.8g", result);
        // trim trailing zeros after decimal
        if (s.contains(".") && !s.contains("e")) {
            s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return s;
    }

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}
