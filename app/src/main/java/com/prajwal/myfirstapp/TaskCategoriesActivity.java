package com.prajwal.myfirstapp;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Categories management screen — view, create, edit, and delete task categories.
 * Default categories can't be edited/deleted; custom categories can.
 */
public class TaskCategoriesActivity extends AppCompatActivity {

    private TaskRepository repo;
    private RecyclerView recyclerCategories;
    private LinearLayout emptyStateCategories;
    private CategoryAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_task_categories);

        repo = new TaskRepository(this);

        ImageView btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        recyclerCategories = findViewById(R.id.recyclerCategories);
        emptyStateCategories = findViewById(R.id.emptyStateCategories);

        recyclerCategories.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CategoryAdapter(this);
        recyclerCategories.setAdapter(adapter);

        TextView fabAddCategory = findViewById(R.id.fabAddCategory);
        fabAddCategory.setOnClickListener(v -> showCreateCategoryDialog());

        refreshList();
    }

    @Override
    protected void onResume() {
        super.onResume();
        repo.reload();
        refreshList();
    }

    private void refreshList() {
        List<TaskCategory> all = repo.getAllCategories();
        adapter.setCategories(all);

        // Only show empty state if there are zero custom categories
        // (defaults are always there)
        if (repo.getCustomCategories().isEmpty()) {
            // Still show defaults, empty state only shown if nothing at all
        }
        emptyStateCategories.setVisibility(View.GONE);
        recyclerCategories.setVisibility(View.VISIBLE);

        TextView subtitle = findViewById(R.id.tvCategorySubtitle);
        subtitle.setText(all.size() + " categories");
    }

    // ═══════════════════════════════════════════════════════════════
    // CREATE / EDIT DIALOG
    // ═══════════════════════════════════════════════════════════════

    private void showCreateCategoryDialog() {
        showCategoryDialog(null);
    }

    private void showEditCategoryDialog(TaskCategory category) {
        showCategoryDialog(category);
    }

    private void showCategoryDialog(TaskCategory existing) {
        boolean isEdit = existing != null;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(isEdit ? "Edit Category" : "New Category");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        layout.setPadding(pad, pad, pad, dp(8));

        // Name input
        EditText etName = new EditText(this);
        etName.setHint("Category name");
        etName.setTextColor(Color.BLACK);
        etName.setHintTextColor(Color.GRAY);
        if (isEdit) etName.setText(existing.name);
        layout.addView(etName);

        // Icon input
        EditText etIcon = new EditText(this);
        etIcon.setHint("Emoji icon (e.g. 🎨)");
        etIcon.setTextColor(Color.BLACK);
        etIcon.setHintTextColor(Color.GRAY);
        if (isEdit) etIcon.setText(existing.icon);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        iconLp.topMargin = dp(12);
        etIcon.setLayoutParams(iconLp);
        layout.addView(etIcon);

        // Color label
        TextView tvColorLabel = new TextView(this);
        tvColorLabel.setText("Pick a color:");
        tvColorLabel.setTextColor(Color.DKGRAY);
        tvColorLabel.setTextSize(13);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelLp.topMargin = dp(16);
        tvColorLabel.setLayoutParams(labelLp);
        layout.addView(tvColorLabel);

        // Color grid
        final String[] selectedColor = { isEdit ? existing.colorHex : "#3B82F6" };
        GridLayout colorGrid = new GridLayout(this);
        colorGrid.setColumnCount(5);
        LinearLayout.LayoutParams gridLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gridLp.topMargin = dp(8);
        colorGrid.setLayoutParams(gridLp);

        final List<View> colorSwatches = new ArrayList<>();
        for (String hex : TaskCategory.PRESET_COLORS) {
            View swatch = new View(this);
            int size = dp(36);
            GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
            glp.width = size;
            glp.height = size;
            glp.setMargins(dp(4), dp(4), dp(4), dp(4));
            swatch.setLayoutParams(glp);

            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(Color.parseColor(hex));
            if (hex.equals(selectedColor[0])) {
                bg.setStroke(dp(3), Color.WHITE);
            }
            swatch.setBackground(bg);
            swatch.setTag(hex);

            swatch.setOnClickListener(v -> {
                selectedColor[0] = hex;
                // Update all swatches
                for (View sv : colorSwatches) {
                    String svHex = (String) sv.getTag();
                    GradientDrawable svBg = new GradientDrawable();
                    svBg.setShape(GradientDrawable.OVAL);
                    svBg.setColor(Color.parseColor(svHex));
                    if (svHex.equals(hex)) {
                        svBg.setStroke(dp(3), Color.WHITE);
                    }
                    sv.setBackground(svBg);
                }
            });

            colorSwatches.add(swatch);
            colorGrid.addView(swatch);
        }
        layout.addView(colorGrid);

        builder.setView(layout);

        builder.setPositiveButton(isEdit ? "Save" : "Create", (d, w) -> {
            String name = etName.getText().toString().trim();
            String icon = etIcon.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, "Name is required", Toast.LENGTH_SHORT).show();
                return;
            }
            if (icon.isEmpty()) icon = "📌";

            if (isEdit) {
                existing.name = name;
                existing.icon = icon;
                existing.colorHex = selectedColor[0];
                repo.updateCustomCategory(existing);
                Toast.makeText(this, "Category updated", Toast.LENGTH_SHORT).show();
            } else {
                // Check for duplicates
                for (TaskCategory cat : repo.getAllCategories()) {
                    if (cat.name.equalsIgnoreCase(name)) {
                        Toast.makeText(this, "Category already exists", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
                TaskCategory newCat = new TaskCategory(name, selectedColor[0], icon);
                repo.addCustomCategory(newCat);
                Toast.makeText(this, "Category created", Toast.LENGTH_SHORT).show();
            }
            refreshList();
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    // ═══════════════════════════════════════════════════════════════
    // DELETE
    // ═══════════════════════════════════════════════════════════════

    private void confirmDeleteCategory(TaskCategory category) {
        int taskCount = repo.getTaskCountByCategory(category.name);
        String message = "Delete \"" + category.name + "\"?";
        if (taskCount > 0) {
            message += "\n\n" + taskCount + " task(s) will be moved to \"Others\".";
        }

        new AlertDialog.Builder(this)
                .setTitle("Delete Category")
                .setMessage(message)
                .setPositiveButton("Delete", (d, w) -> {
                    repo.deleteCustomCategory(category.id);
                    Toast.makeText(this, "Category deleted", Toast.LENGTH_SHORT).show();
                    refreshList();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ═══════════════════════════════════════════════════════════════
    // ADAPTER
    // ═══════════════════════════════════════════════════════════════

    class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.VH> {
        private final Context ctx;
        private List<TaskCategory> categories = new ArrayList<>();

        CategoryAdapter(Context ctx) { this.ctx = ctx; }

        void setCategories(List<TaskCategory> cats) {
            this.categories = cats;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(ctx).inflate(R.layout.item_task_category, parent, false);
            return new VH(v);
        }

        @Override
        public int getItemCount() { return categories.size(); }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            TaskCategory cat = categories.get(pos);

            h.tvCategoryName.setText(cat.name);
            h.tvCategoryIcon.setText(cat.icon);

            int taskCount = repo.getTaskCountByCategory(cat.name);
            h.tvCategoryTaskCount.setText(taskCount + " task" + (taskCount != 1 ? "s" : ""));

            // Color indicator
            try {
                GradientDrawable gd = new GradientDrawable();
                gd.setShape(GradientDrawable.RECTANGLE);
                gd.setCornerRadius(dp(4));
                gd.setColor(cat.getColorInt());
                h.viewCategoryColor.setBackground(gd);
            } catch (Exception ignored) {}

            // Default badge
            h.tvDefaultBadge.setVisibility(cat.isDefault ? View.VISIBLE : View.GONE);

            // Menu
            h.btnCategoryMenu.setOnClickListener(v -> {
                if (cat.isDefault) {
                    Toast.makeText(ctx, "Default categories can't be modified", Toast.LENGTH_SHORT).show();
                    return;
                }
                PopupMenu popup = new PopupMenu(ctx, v);
                popup.getMenu().add("Edit");
                popup.getMenu().add("Delete");
                popup.setOnMenuItemClickListener(item -> {
                    if ("Edit".equals(item.getTitle())) {
                        showEditCategoryDialog(cat);
                    } else if ("Delete".equals(item.getTitle())) {
                        confirmDeleteCategory(cat);
                    }
                    return true;
                });
                popup.show();
            });
        }

        class VH extends RecyclerView.ViewHolder {
            View viewCategoryColor;
            TextView tvCategoryIcon, tvCategoryName, tvCategoryTaskCount, tvDefaultBadge;
            ImageView btnCategoryMenu;

            VH(View v) {
                super(v);
                viewCategoryColor = v.findViewById(R.id.viewCategoryColor);
                tvCategoryIcon = v.findViewById(R.id.tvCategoryIcon);
                tvCategoryName = v.findViewById(R.id.tvCategoryName);
                tvCategoryTaskCount = v.findViewById(R.id.tvCategoryTaskCount);
                tvDefaultBadge = v.findViewById(R.id.tvDefaultBadge);
                btnCategoryMenu = v.findViewById(R.id.btnCategoryMenu);
            }
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
