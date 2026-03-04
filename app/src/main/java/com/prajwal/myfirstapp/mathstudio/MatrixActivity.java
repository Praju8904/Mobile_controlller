package com.prajwal.myfirstapp.mathstudio;

import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.prajwal.myfirstapp.R;
import java.util.Locale;

public class MatrixActivity extends AppCompatActivity {

    private GridLayout matrixGrid;
    private TextView tvMatrixResult;
    private int rows = 2, cols = 2;
    private int rowsB = 2, colsB = 2;
    private boolean showingA = true;
    private TextView tvRows, tvCols;
    private EditText[][] cellsA = new EditText[6][6];
    private EditText[][] cellsB = new EditText[6][6];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_matrix);

        matrixGrid = findViewById(R.id.matrixGrid);
        tvMatrixResult = findViewById(R.id.tvMatrixResult);
        tvRows = findViewById(R.id.tvRows);
        tvCols = findViewById(R.id.tvCols);

        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        setupDimensionControls();
        setupTabClicks();
        setupOperationButtons();
        buildGrid();
    }

    private void setupDimensionControls() {
        View btnRowMinus = findViewById(R.id.btnRowMinus);
        View btnRowPlus = findViewById(R.id.btnRowPlus);
        View btnColMinus = findViewById(R.id.btnColMinus);
        View btnColPlus = findViewById(R.id.btnColPlus);

        if (btnRowMinus != null) btnRowMinus.setOnClickListener(v -> { if (rows > 1) { rows--; updateDimDisplay(); buildGrid(); } });
        if (btnRowPlus != null) btnRowPlus.setOnClickListener(v -> { if (rows < 6) { rows++; updateDimDisplay(); buildGrid(); } });
        if (btnColMinus != null) btnColMinus.setOnClickListener(v -> { if (cols > 1) { cols--; updateDimDisplay(); buildGrid(); } });
        if (btnColPlus != null) btnColPlus.setOnClickListener(v -> { if (cols < 6) { cols++; updateDimDisplay(); buildGrid(); } });
    }

    private void updateDimDisplay() {
        if (tvRows != null) tvRows.setText(String.valueOf(rows));
        if (tvCols != null) tvCols.setText(String.valueOf(cols));
    }

    private void setupTabClicks() {
        TextView tabA = findViewById(R.id.tabMatrixA);
        TextView tabB = findViewById(R.id.tabMatrixB);
        TextView tabSys = findViewById(R.id.tabSystem);
        if (tabA != null) tabA.setOnClickListener(v -> { showingA = true; updateTabUI(); buildGrid(); });
        if (tabB != null) tabB.setOnClickListener(v -> { showingA = false; updateTabUI(); buildGrid(); });
        if (tabSys != null) tabSys.setOnClickListener(v -> android.widget.Toast.makeText(this, "System solver coming soon", android.widget.Toast.LENGTH_SHORT).show());
    }

    private void updateTabUI() {
        TextView tabA = findViewById(R.id.tabMatrixA);
        TextView tabB = findViewById(R.id.tabMatrixB);
        int activeRes = R.drawable.math_deg_btn_active_bg;
        int inactiveRes = R.drawable.math_deg_btn_inactive_bg;
        if (tabA != null) tabA.setBackground(getDrawable(showingA ? activeRes : inactiveRes));
        if (tabB != null) tabB.setBackground(getDrawable(showingA ? inactiveRes : activeRes));
    }

    private void buildGrid() {
        if (matrixGrid == null) return;
        matrixGrid.removeAllViews();
        matrixGrid.setRowCount(rows);
        matrixGrid.setColumnCount(cols);

        EditText[][] cells = showingA ? cellsA : cellsB;
        int cellSize = (int)(48 * getResources().getDisplayMetrics().density);

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (cells[r][c] == null) {
                    cells[r][c] = new EditText(this);
                    cells[r][c].setTextColor(0xFFFFFFFF);
                    cells[r][c].setHint("0");
                    cells[r][c].setHintTextColor(0xFF4A5568);
                    cells[r][c].setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
                    cells[r][c].setGravity(Gravity.CENTER);
                    cells[r][c].setBackground(getDrawable(R.drawable.math_input_bg));
                    cells[r][c].setTextSize(16f);
                }
                GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
                lp.width = cellSize;
                lp.height = cellSize;
                lp.setMargins(4, 4, 4, 4);
                matrixGrid.addView(cells[r][c], lp);
            }
        }
    }

    private void setupOperationButtons() {
        opBtn(R.id.btnDet, () -> computeDeterminant());
        opBtn(R.id.btnInverse, () -> computeInverse());
        opBtn(R.id.btnTranspose, () -> computeTranspose());
        opBtn(R.id.btnTrace, () -> computeTrace());
        opBtn(R.id.btnRank, () -> computeRank());
        opBtn(R.id.btnAPlusB, () -> computeAdd(false));
        opBtn(R.id.btnAMinusB, () -> computeAdd(true));
        opBtn(R.id.btnATimesB, () -> computeMultiply());
        opBtn(R.id.btnScalar, () -> askScalarAndMultiply());
    }

    private void opBtn(int id, Runnable action) {
        View v = findViewById(id);
        if (v != null) v.setOnClickListener(view -> action.run());
    }

    private double[][] readMatrix(EditText[][] cells, int r, int c) {
        double[][] m = new double[r][c];
        for (int i = 0; i < r; i++) {
            for (int j = 0; j < c; j++) {
                try {
                    String txt = (cells[i][j] != null && cells[i][j].getText() != null) ? cells[i][j].getText().toString().trim() : "";
                    m[i][j] = txt.isEmpty() ? 0 : Double.parseDouble(txt);
                } catch (NumberFormatException e) {
                    m[i][j] = 0;
                }
            }
        }
        return m;
    }

    private void computeDeterminant() {
        if (rows != cols) { showResult("Matrix must be square for determinant."); return; }
        double[][] m = readMatrix(cellsA, rows, cols);
        double det = determinant(m, rows);
        StringBuilder sb = new StringBuilder("Determinant of A:\n\ndet(A) = ");
        sb.append(formatNum(det));
        showResult(sb.toString());
    }

    private void computeInverse() {
        if (rows != cols) { showResult("Matrix must be square for inverse."); return; }
        double[][] m = readMatrix(cellsA, rows, cols);
        double det = determinant(m, rows);
        if (Math.abs(det) < 1e-12) { showResult("Matrix is singular (det = 0), inverse does not exist."); return; }
        double[][] inv = inverse(m, rows);
        StringBuilder sb = new StringBuilder("Inverse of A:\n\n");
        sb.append(matrixToString(inv, rows, rows));
        sb.append("\ndet(A) = ").append(formatNum(det));
        showResult(sb.toString());
    }

    private void computeTranspose() {
        double[][] m = readMatrix(cellsA, rows, cols);
        double[][] t = new double[cols][rows];
        for (int i = 0; i < rows; i++)
            for (int j = 0; j < cols; j++)
                t[j][i] = m[i][j];
        showResult("Transpose of A:\n\n" + matrixToString(t, cols, rows));
    }

    private void computeTrace() {
        if (rows != cols) { showResult("Trace requires a square matrix."); return; }
        double[][] m = readMatrix(cellsA, rows, cols);
        double trace = 0;
        for (int i = 0; i < rows; i++) trace += m[i][i];
        showResult("Trace of A:\n\ntr(A) = " + formatNum(trace));
    }

    private void computeRank() {
        double[][] m = readMatrix(cellsA, rows, cols);
        int rank = rank(m, rows, cols);
        showResult("Rank of A:\n\nrank(A) = " + rank);
    }

    private void computeAdd(boolean subtract) {
        if (rows != rowsB || cols != colsB) {
            rowsB = rows; colsB = cols;
        }
        double[][] a = readMatrix(cellsA, rows, cols);
        double[][] b = readMatrix(cellsB, rows, cols);
        double[][] res = new double[rows][cols];
        for (int i = 0; i < rows; i++)
            for (int j = 0; j < cols; j++)
                res[i][j] = subtract ? a[i][j] - b[i][j] : a[i][j] + b[i][j];
        showResult("A " + (subtract ? "−" : "+") + " B:\n\n" + matrixToString(res, rows, cols));
    }

    private void computeMultiply() {
        if (cols != rowsB) {
            rowsB = cols;
        }
        double[][] a = readMatrix(cellsA, rows, cols);
        double[][] b = readMatrix(cellsB, cols, colsB > 0 ? colsB : cols);
        int bCols = colsB > 0 ? colsB : cols;
        double[][] res = new double[rows][bCols];
        for (int i = 0; i < rows; i++)
            for (int j = 0; j < bCols; j++)
                for (int k = 0; k < cols; k++)
                    res[i][j] += a[i][k] * b[k][j];
        showResult("A × B:\n\n" + matrixToString(res, rows, bCols));
    }

    private void askScalarAndMultiply() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Scalar Multiply");
        final EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        et.setHint("Enter scalar k");
        builder.setView(et);
        builder.setPositiveButton("Multiply", (d, w) -> {
            try {
                double k = Double.parseDouble(et.getText().toString());
                double[][] m = readMatrix(cellsA, rows, cols);
                double[][] res = new double[rows][cols];
                for (int i = 0; i < rows; i++)
                    for (int j = 0; j < cols; j++)
                        res[i][j] = k * m[i][j];
                showResult(formatNum(k) + " × A:\n\n" + matrixToString(res, rows, cols));
            } catch (Exception e) {
                showResult("Invalid scalar");
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showResult(String text) {
        if (tvMatrixResult != null) tvMatrixResult.setText(text);
    }

    private String matrixToString(double[][] m, int r, int c) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < r; i++) {
            sb.append("[ ");
            for (int j = 0; j < c; j++) {
                sb.append(String.format(Locale.US, "%8.4f", m[i][j]));
                if (j < c - 1) sb.append("  ");
            }
            sb.append(" ]\n");
        }
        return sb.toString();
    }

    private String formatNum(double v) {
        if (v == Math.floor(v) && Math.abs(v) < 1e10) return String.valueOf((long) v);
        return String.format(Locale.US, "%.6f", v);
    }

    // Determinant via cofactor expansion
    private double determinant(double[][] m, int n) {
        if (n == 1) return m[0][0];
        if (n == 2) return m[0][0] * m[1][1] - m[0][1] * m[1][0];
        double det = 0;
        for (int j = 0; j < n; j++) {
            det += (j % 2 == 0 ? 1 : -1) * m[0][j] * determinant(minor(m, 0, j, n), n - 1);
        }
        return det;
    }

    private double[][] minor(double[][] m, int row, int col, int n) {
        double[][] result = new double[n - 1][n - 1];
        int ri = 0;
        for (int i = 0; i < n; i++) {
            if (i == row) continue;
            int rj = 0;
            for (int j = 0; j < n; j++) {
                if (j == col) continue;
                result[ri][rj++] = m[i][j];
            }
            ri++;
        }
        return result;
    }

    // Inverse via adjugate
    private double[][] inverse(double[][] m, int n) {
        double det = determinant(m, n);
        double[][] adj = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                adj[j][i] = ((i + j) % 2 == 0 ? 1 : -1) * determinant(minor(m, i, j, n), n - 1);
            }
        }
        double[][] inv = new double[n][n];
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++)
                inv[i][j] = adj[i][j] / det;
        return inv;
    }

    // Rank via Gaussian elimination
    private int rank(double[][] mat, int r, int c) {
        double[][] m = new double[r][c];
        for (int i = 0; i < r; i++) m[i] = mat[i].clone();
        int rank = 0;
        boolean[] rowSelected = new boolean[r];
        for (int col = 0; col < c; col++) {
            int pivotRow = -1;
            for (int row = 0; row < r; row++) {
                if (!rowSelected[row] && Math.abs(m[row][col]) > 1e-9) {
                    pivotRow = row;
                    break;
                }
            }
            if (pivotRow == -1) continue;
            rowSelected[pivotRow] = true;
            rank++;
            double pivot = m[pivotRow][col];
            for (int j = col; j < c; j++) m[pivotRow][j] /= pivot;
            for (int row = 0; row < r; row++) {
                if (row != pivotRow && Math.abs(m[row][col]) > 1e-9) {
                    double factor = m[row][col];
                    for (int j = col; j < c; j++) m[row][j] -= factor * m[pivotRow][j];
                }
            }
        }
        return rank;
    }
}
