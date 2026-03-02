package com.prajwal.myfirstapp.notes;


import com.prajwal.myfirstapp.R;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * TagsManagerActivity — View, create, rename, and delete tags.
 */
public class TagsManagerActivity extends AppCompatActivity {

    private NoteRepository repository;
    private RecyclerView tagsRecyclerView;
    private TagsAdapter adapter;
    private LinearLayout emptyStateContainer;
    private TextView tvTagsCount;
    private EditText etNewTag;
    private ImageButton btnAddTag;

    private List<TagItem> tagItems = new ArrayList<>();

    // Data class for tag with count
    static class TagItem {
        String name;
        int count;

        TagItem(String name, int count) {
            this.name = name;
            this.count = count;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tags_manager);

        repository = new NoteRepository(this);

        initViews();
        setupRecyclerView();
        refreshTags();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshTags();
    }

    private void initViews() {
        tagsRecyclerView = findViewById(R.id.tagsRecyclerView);
        emptyStateContainer = findViewById(R.id.emptyStateContainer);
        tvTagsCount = findViewById(R.id.tvTagsCount);
        etNewTag = findViewById(R.id.etNewTag);
        btnAddTag = findViewById(R.id.btnAddTag);

        ImageButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        btnAddTag.setOnClickListener(v -> createNewTag());

        etNewTag.setOnEditorActionListener((v, actionId, event) -> {
            createNewTag();
            return true;
        });
    }

    private void setupRecyclerView() {
        tagsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TagsAdapter();
        tagsRecyclerView.setAdapter(adapter);
    }

    private void refreshTags() {
        Map<String, Integer> tagCounts = repository.getTagCounts();
        
        tagItems.clear();
        for (Map.Entry<String, Integer> entry : tagCounts.entrySet()) {
            tagItems.add(new TagItem(entry.getKey(), entry.getValue()));
        }

        // Sort alphabetically
        tagItems.sort((a, b) -> a.name.compareToIgnoreCase(b.name));

        adapter.notifyDataSetChanged();

        // Update count
        int count = tagItems.size();
        String countText = count + (count == 1 ? " tag" : " tags");
        tvTagsCount.setText(countText);

        // Show/hide empty state
        boolean isEmpty = tagItems.isEmpty();
        emptyStateContainer.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        tagsRecyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    private void createNewTag() {
        String tagName = etNewTag.getText().toString().trim();
        
        if (TextUtils.isEmpty(tagName)) {
            Toast.makeText(this, "Enter a tag name", Toast.LENGTH_SHORT).show();
            return;
        }

        // Check if tag already exists
        for (TagItem item : tagItems) {
            if (item.name.equalsIgnoreCase(tagName)) {
                Toast.makeText(this, "Tag already exists", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        // Create a placeholder note with this tag to establish the tag
        // Or we can show a message that the tag will appear when added to a note
        tagItems.add(new TagItem(tagName, 0));
        tagItems.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        adapter.notifyDataSetChanged();

        etNewTag.setText("");
        hideKeyboard();

        Toast.makeText(this, "Tag created! Add it to notes to use.", Toast.LENGTH_SHORT).show();
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && getCurrentFocus() != null) {
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
    }

    private void showRenameDialog(TagItem tag) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Rename Tag");

        EditText input = new EditText(this);
        input.setText(tag.name);
        input.setTextColor(0xFFE2E8F0);
        input.setSelection(tag.name.length());
        input.setPadding(48, 24, 48, 24);

        builder.setView(input);
        builder.setPositiveButton("Rename", (d, w) -> {
            String newName = input.getText().toString().trim();
            if (TextUtils.isEmpty(newName)) {
                Toast.makeText(this, "Enter a name", Toast.LENGTH_SHORT).show();
                return;
            }
            if (newName.equals(tag.name)) {
                return;
            }

            // Check for duplicates
            for (TagItem item : tagItems) {
                if (item.name.equalsIgnoreCase(newName) && !item.name.equals(tag.name)) {
                    Toast.makeText(this, "Tag already exists", Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            repository.renameTag(tag.name, newName);
            refreshTags();
            Toast.makeText(this, "Tag renamed", Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void confirmDeleteTag(TagItem tag) {
        String message = tag.count > 0
            ? "Remove \"" + tag.name + "\" from " + tag.count + " notes?"
            : "Delete tag \"" + tag.name + "\"?";

        new AlertDialog.Builder(this)
            .setTitle("Delete Tag")
            .setMessage(message)
            .setPositiveButton("Delete", (d, w) -> {
                repository.deleteTag(tag.name);
                refreshTags();
                Toast.makeText(this, "Tag deleted", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    // ─── RecyclerView Adapter ────────────────────────────────────

    class TagsAdapter extends RecyclerView.Adapter<TagsAdapter.TagViewHolder> {

        class TagViewHolder extends RecyclerView.ViewHolder {
            TextView tvTagName, tvTagCount;
            ImageButton btnRenameTag, btnDeleteTag;

            TagViewHolder(View itemView) {
                super(itemView);
                tvTagName = itemView.findViewById(R.id.tvTagName);
                tvTagCount = itemView.findViewById(R.id.tvTagCount);
                btnRenameTag = itemView.findViewById(R.id.btnRenameTag);
                btnDeleteTag = itemView.findViewById(R.id.btnDeleteTag);
            }
        }

        @NonNull
        @Override
        public TagViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(TagsManagerActivity.this)
                .inflate(R.layout.item_tag_manager, parent, false);
            return new TagViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull TagViewHolder holder, int position) {
            TagItem tag = tagItems.get(position);

            holder.tvTagName.setText(tag.name);
            holder.tvTagCount.setText(tag.count + (tag.count == 1 ? " note" : " notes"));

            holder.btnRenameTag.setOnClickListener(v -> showRenameDialog(tag));
            holder.btnDeleteTag.setOnClickListener(v -> confirmDeleteTag(tag));

            holder.itemView.setOnClickListener(v -> {
                // Could navigate to filtered notes view - for now just show count
                Toast.makeText(TagsManagerActivity.this, 
                    tag.name + ": " + tag.count + " notes", Toast.LENGTH_SHORT).show();
            });
        }

        @Override
        public int getItemCount() {
            return tagItems.size();
        }
    }
}
