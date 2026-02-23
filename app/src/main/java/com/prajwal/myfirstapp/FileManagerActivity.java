package com.prajwal.myfirstapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class FileManagerActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private FileAdapter adapter;
    private TextView txtPath;
    private String currentPath = "ROOT";
    private ConnectionManager connectionManager; // Assuming you pass IP via Intent

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_manager);

        String ip = getIntent().getStringExtra("server_ip");
        connectionManager = new ConnectionManager(ip);

        txtPath = findViewById(R.id.txtCurrentPath);
        recyclerView = findViewById(R.id.recyclerFiles);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FileAdapter();
        recyclerView.setAdapter(adapter);

        findViewById(R.id.btnBack).setOnClickListener(v -> {
            if (currentPath.equals("D:/")) {
                finish(); // Exit if at root
            } else {
                // Ideally, implement "Up" navigation logic here
                // For now, let's just go back to root
                loadDirectory("ROOT");
            }
        });

        // Register Receiver
        LocalBroadcastManager.getInstance(this).registerReceiver(fileReceiver,
                new IntentFilter("com.prajwal.myfirstapp.FILE_LIST_UPDATE"));

        // Load initial list
        loadDirectory("ROOT");
    }

    private void loadDirectory(String path) {
        connectionManager.sendCommand("GET_FILES:" + path);
        // Show loading?
    }

    private final BroadcastReceiver fileReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // 1. Check if we actually got data
            String jsonString = intent.getStringExtra("data");
            if (jsonString == null) {
                Toast.makeText(FileManagerActivity.this, "Error: Received NULL data", Toast.LENGTH_SHORT).show();
                return;
            }

            // 2. Debug Popup: Show us the first 50 characters
            String preview = jsonString.length() > 50 ? jsonString.substring(0, 50) : jsonString;
            Toast.makeText(FileManagerActivity.this, "Received: " + preview, Toast.LENGTH_LONG).show();

            try {
                JSONObject root = new JSONObject(jsonString);
                currentPath = root.getString("current_path");
                txtPath.setText(currentPath);

                JSONArray items = root.getJSONArray("items");
                List<FileItem> fileList = new ArrayList<>();

                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.getJSONObject(i);
                    fileList.add(new FileItem(
                            item.getString("name"),
                            item.getString("type"),
                            item.getString("path")
                    ));
                }

                // 3. Update Adapter
                adapter.setFiles(fileList);
                Toast.makeText(FileManagerActivity.this, "Updated List: " + fileList.size() + " items", Toast.LENGTH_SHORT).show();

            } catch (Exception e) {
                // 4. Catch Parsing Errors
                Toast.makeText(FileManagerActivity.this, "JSON Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                e.printStackTrace();
            }
        }
    };

    // ─── Adapter Class ───
    class FileAdapter extends RecyclerView.Adapter<FileAdapter.ViewHolder> {
        private List<FileItem> files = new ArrayList<>();

        void setFiles(List<FileItem> newFiles) {
            files = newFiles;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_file, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            FileItem item = files.get(position);
            holder.name.setText(item.name);

            if (item.type.equals("FOLDER")) {
                holder.icon.setImageResource(android.R.drawable.ic_menu_agenda); // Folder Icon
                holder.icon.setColorFilter(0xFFFFC107); // Gold color
            } else {
                holder.icon.setImageResource(android.R.drawable.ic_menu_sort_by_size); // File Icon
                holder.icon.setColorFilter(0xFFB0BEC5); // Grey color
            }

            holder.itemView.setOnClickListener(v -> {
                if (item.type.equals("FOLDER")) {
                    loadDirectory(item.path); // Open Folder
                } else {
                    Toast.makeText(FileManagerActivity.this, "Download not implemented yet", Toast.LENGTH_SHORT).show();
                    // Next step: Implement DOWNLOAD logic
                }
            });
        }

        @Override
        public int getItemCount() { return files.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView name;
            ImageView icon;
            ViewHolder(View v) {
                super(v);
                name = v.findViewById(R.id.txtName);
                icon = v.findViewById(R.id.imgIcon);
            }
        }
    }

    // Data Model
    static class FileItem {
        String name, type, path;
        FileItem(String n, String t, String p) { name = n; type = t; path = p; }
    }
}