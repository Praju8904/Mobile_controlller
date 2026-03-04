package com.prajwal.myfirstapp.mathstudio;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.prajwal.myfirstapp.R;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class ProgrammerCalculatorActivity extends AppCompatActivity {

    // Current value stored as long
    private long currentValue = 0;
    // Active base: 10=DEC, 16=HEX, 8=OCT, 2=BIN
    private int activeBase = 10;
    // Bit width mask
    private int bitWidth = 32;
    // Pending operation
    private long operand = 0;
    private String pendingOp = null;
    private boolean freshEntry = false;

    // Bit visualization
    private TextView[] bitCells = new TextView[32];

    // Tab panels
    private View panelCalc, panelLogic, panelHash, panelNetwork;
    private TextView tabCalc, tabLogic, tabHash, tabNetwork;

    // Base display
    private TextView tvHex, tvDec, tvOct, tvBin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_programmer_calculator);

        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        initTabViews();
        initCalcTab();
        initLogicTab();
        initHashTab();
        initNetworkTab();

        showTab(0);
    }

    private void initTabViews() {
        tabCalc = findViewById(R.id.tabCalc);
        tabLogic = findViewById(R.id.tabLogic);
        tabHash = findViewById(R.id.tabHash);
        tabNetwork = findViewById(R.id.tabNetwork);
        panelCalc = findViewById(R.id.panelCalc);
        panelLogic = findViewById(R.id.panelLogic);
        panelHash = findViewById(R.id.panelHash);
        panelNetwork = findViewById(R.id.panelNetwork);

        tabCalc.setOnClickListener(v -> showTab(0));
        tabLogic.setOnClickListener(v -> showTab(1));
        tabHash.setOnClickListener(v -> showTab(2));
        tabNetwork.setOnClickListener(v -> showTab(3));
    }

    private void showTab(int idx) {
        panelCalc.setVisibility(idx == 0 ? View.VISIBLE : View.GONE);
        panelLogic.setVisibility(idx == 1 ? View.VISIBLE : View.GONE);
        panelHash.setVisibility(idx == 2 ? View.VISIBLE : View.GONE);
        panelNetwork.setVisibility(idx == 3 ? View.VISIBLE : View.GONE);

        int activeColor = 0xFF00FF41;
        int inactiveColor = 0xFF6B7280;
        tabCalc.setTextColor(idx == 0 ? activeColor : inactiveColor);
        tabLogic.setTextColor(idx == 1 ? activeColor : inactiveColor);
        tabHash.setTextColor(idx == 2 ? activeColor : inactiveColor);
        tabNetwork.setTextColor(idx == 3 ? activeColor : inactiveColor);
    }

    // ==================== CALC TAB ====================

    private void initCalcTab() {
        tvHex = findViewById(R.id.tvHex);
        tvDec = findViewById(R.id.tvDec);
        tvOct = findViewById(R.id.tvOct);
        tvBin = findViewById(R.id.tvBin);

        // Base row clicks
        findViewById(R.id.rowHex).setOnClickListener(v -> setActiveBase(16));
        findViewById(R.id.rowDec).setOnClickListener(v -> setActiveBase(10));
        findViewById(R.id.rowOct).setOnClickListener(v -> setActiveBase(8));
        findViewById(R.id.rowBin).setOnClickListener(v -> setActiveBase(2));

        // Bit width
        findViewById(R.id.btn8bit).setOnClickListener(v -> setBitWidth(8));
        findViewById(R.id.btn16bit).setOnClickListener(v -> setBitWidth(16));
        findViewById(R.id.btn32bit).setOnClickListener(v -> setBitWidth(32));
        findViewById(R.id.btn64bit).setOnClickListener(v -> setBitWidth(64));

        // Digit buttons
        int[] numBtnIds = {R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4,
                           R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9};
        int[] digits = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        for (int i = 0; i < numBtnIds.length; i++) {
            final int d = digits[i];
            Button b = findViewById(numBtnIds[i]);
            if (b != null) b.setOnClickListener(v -> inputDigit(d));
        }
        int[] hexBtnIds = {R.id.btnA, R.id.btnB, R.id.btnC, R.id.btnD, R.id.btnE, R.id.btnF};
        int[] hexVals = {10, 11, 12, 13, 14, 15};
        for (int i = 0; i < hexBtnIds.length; i++) {
            final int d = hexVals[i];
            Button b = findViewById(hexBtnIds[i]);
            if (b != null) b.setOnClickListener(v -> inputDigit(d));
        }

        // Del, Clr
        Button btnDel = findViewById(R.id.btnDel);
        if (btnDel != null) btnDel.setOnClickListener(v -> deleteLastDigit());
        Button btnClr = findViewById(R.id.btnClr);
        if (btnClr != null) btnClr.setOnClickListener(v -> { currentValue = 0; pendingOp = null; freshEntry = false; updateDisplays(); });
        Button btnPM = findViewById(R.id.btnPlusMinus);
        if (btnPM != null) btnPM.setOnClickListener(v -> { currentValue = -currentValue; applyMask(); updateDisplays(); });

        // Arithmetic
        Button btnAdd = findViewById(R.id.btnAdd);
        if (btnAdd != null) btnAdd.setOnClickListener(v -> setPendingOp("+"));
        Button btnSub = findViewById(R.id.btnSub);
        if (btnSub != null) btnSub.setOnClickListener(v -> setPendingOp("-"));
        Button btnMul = findViewById(R.id.btnMul);
        if (btnMul != null) btnMul.setOnClickListener(v -> setPendingOp("*"));
        Button btnDiv = findViewById(R.id.btnDiv);
        if (btnDiv != null) btnDiv.setOnClickListener(v -> setPendingOp("/"));
        Button btnMod = findViewById(R.id.btnMod);
        if (btnMod != null) btnMod.setOnClickListener(v -> setPendingOp("%"));
        Button btnEq = findViewById(R.id.btnEquals);
        if (btnEq != null) btnEq.setOnClickListener(v -> evaluate());

        // Bitwise ops (single operand stored, then next value applies op)
        Button btnAnd = findViewById(R.id.btnAnd);
        if (btnAnd != null) btnAnd.setOnClickListener(v -> setPendingOp("AND"));
        Button btnOr = findViewById(R.id.btnOr);
        if (btnOr != null) btnOr.setOnClickListener(v -> setPendingOp("OR"));
        Button btnXor = findViewById(R.id.btnXor);
        if (btnXor != null) btnXor.setOnClickListener(v -> setPendingOp("XOR"));
        Button btnNot = findViewById(R.id.btnNot);
        if (btnNot != null) btnNot.setOnClickListener(v -> { currentValue = ~currentValue; applyMask(); updateDisplays(); });
        Button btnNand = findViewById(R.id.btnNand);
        if (btnNand != null) btnNand.setOnClickListener(v -> setPendingOp("NAND"));
        Button btnNor = findViewById(R.id.btnNor);
        if (btnNor != null) btnNor.setOnClickListener(v -> setPendingOp("NOR"));
        Button btnLsh = findViewById(R.id.btnLsh);
        if (btnLsh != null) btnLsh.setOnClickListener(v -> setPendingOp("LSH"));
        Button btnRsh = findViewById(R.id.btnRsh);
        if (btnRsh != null) btnRsh.setOnClickListener(v -> setPendingOp("RSH"));

        buildBitGrid();
        updateDisplays();
    }

    private void setActiveBase(int base) {
        activeBase = base;
        updateButtonStates();
        updateDisplays();
    }

    private void setBitWidth(int bits) {
        bitWidth = bits;
        applyMask();
        updateDisplays();
        rebuildBitGrid();
    }

    private void applyMask() {
        if (bitWidth < 64) {
            long mask = (1L << bitWidth) - 1L;
            currentValue = currentValue & mask;
        }
    }

    private void inputDigit(int digit) {
        if (digit >= activeBase) return; // invalid for current base
        if (freshEntry) {
            currentValue = digit;
            freshEntry = false;
        } else {
            currentValue = currentValue * activeBase + digit;
        }
        applyMask();
        updateDisplays();
    }

    private void deleteLastDigit() {
        currentValue = currentValue / activeBase;
        updateDisplays();
    }

    private void setPendingOp(String op) {
        operand = currentValue;
        pendingOp = op;
        freshEntry = true;
    }

    private void evaluate() {
        if (pendingOp == null) return;
        long result;
        switch (pendingOp) {
            case "+": result = operand + currentValue; break;
            case "-": result = operand - currentValue; break;
            case "*": result = operand * currentValue; break;
            case "/": result = currentValue != 0 ? operand / currentValue : 0; break;
            case "%": result = currentValue != 0 ? operand % currentValue : 0; break;
            case "AND": result = operand & currentValue; break;
            case "OR":  result = operand | currentValue; break;
            case "XOR": result = operand ^ currentValue; break;
            case "NAND": result = ~(operand & currentValue); break;
            case "NOR":  result = ~(operand | currentValue); break;
            case "LSH": result = operand << (currentValue & 63); break;
            case "RSH": result = operand >> (currentValue & 63); break;
            default: result = currentValue;
        }
        currentValue = result;
        applyMask();
        pendingOp = null;
        freshEntry = false;
        updateDisplays();
    }

    private void updateDisplays() {
        tvHex.setText(Long.toHexString(currentValue).toUpperCase());
        tvDec.setText(Long.toString(currentValue));
        tvOct.setText(Long.toOctalString(currentValue));
        tvBin.setText(Long.toBinaryString(currentValue));
        updateBitCells();
        updateButtonStates();
    }

    private void updateButtonStates() {
        // Enable A-F only in HEX; disable 8,9 in OCT and BIN; disable 2-7 in BIN
        int[] hexBtnIds = {R.id.btnA, R.id.btnB, R.id.btnC, R.id.btnD, R.id.btnE, R.id.btnF};
        for (int id : hexBtnIds) {
            Button b = findViewById(id);
            if (b != null) b.setEnabled(activeBase == 16);
        }
        int[] decOnlyIds = {R.id.btn8, R.id.btn9};
        for (int id : decOnlyIds) {
            Button b = findViewById(id);
            if (b != null) b.setEnabled(activeBase >= 10);
        }
        int[] octalPlusIds = {R.id.btn2, R.id.btn3, R.id.btn4, R.id.btn5, R.id.btn6, R.id.btn7};
        for (int id : octalPlusIds) {
            Button b = findViewById(id);
            if (b != null) b.setEnabled(activeBase >= 8);
        }
    }

    private void buildBitGrid() {
        LinearLayout bitGrid = findViewById(R.id.bitGrid);
        if (bitGrid == null) return;
        bitGrid.removeAllViews();
        int cells = Math.min(bitWidth, 32);
        int dp4 = (int)(4 * getResources().getDisplayMetrics().density);
        int dp24 = (int)(24 * getResources().getDisplayMetrics().density);
        bitCells = new TextView[cells];
        // 4 rows of 8 cells each (for 32-bit)
        for (int row = 0; row < (cells + 7) / 8; row++) {
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rowLp.setMargins(0, 0, 0, dp4);
            rowLayout.setLayoutParams(rowLp);
            for (int col = 0; col < 8; col++) {
                int bitIdx = row * 8 + col;
                if (bitIdx >= cells) break;
                final int finalBitIdx = bitIdx;
                TextView cell = new TextView(this);
                cell.setText("0");
                cell.setTextColor(0xFF6B7280);
                cell.setBackgroundColor(0xFF1A1F3A);
                cell.setGravity(android.view.Gravity.CENTER);
                cell.setTextSize(12);
                cell.setClickable(true);
                cell.setFocusable(true);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp24);
                lp.weight = 1;
                lp.setMarginEnd(2);
                cell.setLayoutParams(lp);
                cell.setOnClickListener(v -> {
                    currentValue ^= (1L << (cells - 1 - finalBitIdx));
                    applyMask();
                    updateDisplays();
                });
                rowLayout.addView(cell);
                bitCells[bitIdx] = cell;
            }
            bitGrid.addView(rowLayout);
        }
    }

    private void rebuildBitGrid() {
        buildBitGrid();
    }

    private void updateBitCells() {
        if (bitCells == null) return;
        int cells = Math.min(bitWidth, 32);
        for (int i = 0; i < bitCells.length && i < cells; i++) {
            if (bitCells[i] == null) continue;
            int bitPos = cells - 1 - i;
            boolean lit = ((currentValue >> bitPos) & 1L) == 1L;
            bitCells[i].setText(lit ? "1" : "0");
            bitCells[i].setTextColor(lit ? 0xFF00FF41 : 0xFF6B7280);
            bitCells[i].setBackgroundColor(lit ? 0xFF0D2D0D : 0xFF1A1F3A);
        }
    }

    // ==================== LOGIC TAB ====================

    private void initLogicTab() {
        Button btnGenerate = findViewById(R.id.btnGenerateTruth);
        if (btnGenerate == null) return;
        btnGenerate.setOnClickListener(v -> generateTruthTable());
    }

    private void generateTruthTable() {
        EditText et = findViewById(R.id.etLogicExpr);
        TextView tvTable = findViewById(R.id.tvTruthTable);
        TextView tvSimplified = findViewById(R.id.tvLogicSimplified);
        if (et == null || tvTable == null) return;

        String expr = et.getText().toString().trim().toUpperCase();
        if (expr.isEmpty()) {
            tvTable.setText("Enter an expression");
            return;
        }

        // Extract variables (single capital letters)
        java.util.TreeSet<Character> varSet = new java.util.TreeSet<>();
        for (char c : expr.toCharArray()) {
            if (c >= 'A' && c <= 'Z') varSet.add(c);
        }
        java.util.List<Character> vars = new java.util.ArrayList<>(varSet);
        if (vars.size() > 4) {
            tvTable.setText("Max 4 variables supported");
            return;
        }

        int rows = 1 << vars.size();
        StringBuilder sb = new StringBuilder();
        // Header
        for (char v : vars) sb.append(v).append(" | ");
        sb.append("Result\n");
        sb.append("-".repeat(vars.size() * 4 + 8)).append("\n");

        for (int r = 0; r < rows; r++) {
            java.util.HashMap<Character, Boolean> vals = new java.util.HashMap<>();
            for (int i = 0; i < vars.size(); i++) {
                boolean val = ((r >> (vars.size() - 1 - i)) & 1) == 1;
                vals.put(vars.get(i), val);
                sb.append(val ? "1" : "0").append(" | ");
            }
            try {
                boolean result = evalLogic(expr, vals);
                sb.append(result ? "1" : "0").append("\n");
            } catch (Exception e) {
                sb.append("?").append("\n");
            }
        }
        tvTable.setText(sb.toString());
        tvSimplified.setText(expr); // simplified display as-is
    }

    private boolean evalLogic(String expr, java.util.HashMap<Character, Boolean> vals) {
        expr = expr.trim();
        // Simple token-based parser
        return evalTokens(tokenize(expr), vals, new int[]{0});
    }

    private String[] tokenize(String expr) {
        java.util.List<String> tokens = new java.util.ArrayList<>();
        int i = 0;
        while (i < expr.length()) {
            char c = expr.charAt(i);
            if (c == ' ') { i++; continue; }
            if (c == '(' || c == ')') { tokens.add(String.valueOf(c)); i++; continue; }
            if (c >= 'A' && c <= 'Z') {
                // check for keyword
                if (expr.startsWith("AND", i)) { tokens.add("AND"); i += 3; continue; }
                if (expr.startsWith("OR", i)) { tokens.add("OR"); i += 2; continue; }
                if (expr.startsWith("NOT", i)) { tokens.add("NOT"); i += 3; continue; }
                if (expr.startsWith("NAND", i)) { tokens.add("NAND"); i += 4; continue; }
                if (expr.startsWith("NOR", i)) { tokens.add("NOR"); i += 3; continue; }
                if (expr.startsWith("XOR", i)) { tokens.add("XOR"); i += 3; continue; }
                tokens.add(String.valueOf(c)); i++; continue;
            }
            i++;
        }
        return tokens.toArray(new String[0]);
    }

    // Recursive descent: OR < AND < NOT < atom
    private boolean evalTokens(String[] tokens, java.util.HashMap<Character, Boolean> vals, int[] pos) {
        return parseOr(tokens, vals, pos);
    }

    private boolean parseOr(String[] tokens, java.util.HashMap<Character, Boolean> vals, int[] pos) {
        boolean left = parseAnd(tokens, vals, pos);
        while (pos[0] < tokens.length && (tokens[pos[0]].equals("OR") || tokens[pos[0]].equals("NOR"))) {
            String op = tokens[pos[0]++];
            boolean right = parseAnd(tokens, vals, pos);
            left = op.equals("NOR") ? !(left || right) : (left || right);
        }
        return left;
    }

    private boolean parseAnd(String[] tokens, java.util.HashMap<Character, Boolean> vals, int[] pos) {
        boolean left = parseNot(tokens, vals, pos);
        while (pos[0] < tokens.length && (tokens[pos[0]].equals("AND") || tokens[pos[0]].equals("NAND") || tokens[pos[0]].equals("XOR"))) {
            String op = tokens[pos[0]++];
            boolean right = parseNot(tokens, vals, pos);
            if (op.equals("NAND")) left = !(left && right);
            else if (op.equals("XOR")) left = left ^ right;
            else left = left && right;
        }
        return left;
    }

    private boolean parseNot(String[] tokens, java.util.HashMap<Character, Boolean> vals, int[] pos) {
        if (pos[0] < tokens.length && tokens[pos[0]].equals("NOT")) {
            pos[0]++;
            return !parseAtom(tokens, vals, pos);
        }
        return parseAtom(tokens, vals, pos);
    }

    private boolean parseAtom(String[] tokens, java.util.HashMap<Character, Boolean> vals, int[] pos) {
        if (pos[0] >= tokens.length) return false;
        String t = tokens[pos[0]++];
        if (t.equals("(")) {
            boolean v = parseOr(tokens, vals, pos);
            if (pos[0] < tokens.length && tokens[pos[0]].equals(")")) pos[0]++;
            return v;
        }
        if (t.length() == 1 && t.charAt(0) >= 'A' && t.charAt(0) <= 'Z') {
            return vals.getOrDefault(t.charAt(0), false);
        }
        if (t.equals("0")) return false;
        if (t.equals("1")) return true;
        return false;
    }

    // ==================== HASH TAB ====================

    private void initHashTab() {
        Button btnHash = findViewById(R.id.btnComputeHash);
        if (btnHash == null) return;
        btnHash.setOnClickListener(v -> computeHashes());

        // Long-press to copy
        setupHashCopy(R.id.tvMd5, "MD5");
        setupHashCopy(R.id.tvSha1, "SHA-1");
        setupHashCopy(R.id.tvSha256, "SHA-256");
        setupHashCopy(R.id.tvSha512, "SHA-512");
    }

    private void setupHashCopy(int tvId, String label) {
        TextView tv = findViewById(tvId);
        if (tv == null) return;
        tv.setOnLongClickListener(v -> {
            String text = tv.getText().toString();
            if (!text.equals("—")) {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText(label, text));
                Toast.makeText(this, label + " copied", Toast.LENGTH_SHORT).show();
            }
            return true;
        });
        tv.setOnClickListener(v -> {
            String text = tv.getText().toString();
            if (!text.equals("—")) {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText(label, text));
                Toast.makeText(this, label + " copied", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void computeHashes() {
        EditText et = findViewById(R.id.etHashInput);
        if (et == null) return;
        String input = et.getText().toString();
        byte[] bytes = input.getBytes(StandardCharsets.UTF_8);

        setHashResult(R.id.tvMd5, "MD5", bytes);
        setHashResult(R.id.tvSha1, "SHA-1", bytes);
        setHashResult(R.id.tvSha256, "SHA-256", bytes);
        setHashResult(R.id.tvSha512, "SHA-512", bytes);
    }

    private void setHashResult(int tvId, String algo, byte[] data) {
        TextView tv = findViewById(tvId);
        if (tv == null) return;
        try {
            MessageDigest md = MessageDigest.getInstance(algo);
            byte[] digest = md.digest(data);
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) hex.append(String.format("%02x", b));
            tv.setText(hex.toString());
            tv.setTextColor(0xFF9CA3AF);
        } catch (NoSuchAlgorithmException e) {
            tv.setText("Unsupported");
        }
    }

    // ==================== NETWORK TAB ====================

    private void initNetworkTab() {
        NumberPicker cidr = findViewById(R.id.cidrPicker);
        if (cidr != null) {
            cidr.setMinValue(1);
            cidr.setMaxValue(32);
            cidr.setValue(24);
        }
        Button btnCalc = findViewById(R.id.btnCalcNetwork);
        if (btnCalc != null) btnCalc.setOnClickListener(v -> calculateNetwork());
    }

    private void calculateNetwork() {
        EditText etIp = findViewById(R.id.etIpAddress);
        NumberPicker cidrPicker = findViewById(R.id.cidrPicker);
        TextView tvInfo = findViewById(R.id.tvNetworkInfo);
        if (etIp == null || cidrPicker == null || tvInfo == null) return;

        String ipStr = etIp.getText().toString().trim();
        int cidr = cidrPicker.getValue();

        String[] parts = ipStr.split("\\.");
        if (parts.length != 4) {
            tvInfo.setText("Invalid IP address format");
            return;
        }
        try {
            int[] octets = new int[4];
            for (int i = 0; i < 4; i++) {
                octets[i] = Integer.parseInt(parts[i]);
                if (octets[i] < 0 || octets[i] > 255) throw new NumberFormatException();
            }
            long ipLong = ((long) octets[0] << 24) | ((long) octets[1] << 16)
                        | ((long) octets[2] << 8) | octets[3];
            long maskLong = cidr == 0 ? 0 : (0xFFFFFFFFL << (32 - cidr)) & 0xFFFFFFFFL;
            long networkLong = ipLong & maskLong;
            long broadcastLong = networkLong | (~maskLong & 0xFFFFFFFFL);
            long firstHost = networkLong + 1;
            long lastHost = broadcastLong - 1;
            long totalHosts = Math.max(0, broadcastLong - networkLong - 1);

            StringBuilder sb = new StringBuilder();
            sb.append("IP Address:    ").append(ipStr).append("/").append(cidr).append("\n");
            sb.append("Network Addr:  ").append(longToIp(networkLong)).append("\n");
            sb.append("Subnet Mask:   ").append(longToIp(maskLong)).append("\n");
            sb.append("Broadcast:     ").append(longToIp(broadcastLong)).append("\n");
            sb.append("First Host:    ").append(longToIp(firstHost)).append("\n");
            sb.append("Last Host:     ").append(longToIp(lastHost)).append("\n");
            sb.append("Total Hosts:   ").append(totalHosts).append("\n");
            sb.append("Host Bits:     ").append(32 - cidr);

            tvInfo.setText(sb.toString());
        } catch (NumberFormatException e) {
            tvInfo.setText("Invalid IP address");
        }
    }

    private String longToIp(long ip) {
        return ((ip >> 24) & 0xFF) + "." + ((ip >> 16) & 0xFF) + "."
             + ((ip >> 8) & 0xFF) + "." + (ip & 0xFF);
    }
}
