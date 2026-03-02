package com.prajwal.myfirstapp.chat;


import com.prajwal.myfirstapp.R;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ChatViewHolder> {
    private ArrayList<ChatMessage> messages;
    private Context context;

    public ChatAdapter(Context context, ArrayList<ChatMessage> messages) {
        this.context = context;
        this.messages = messages;
    }

    @Override
    public ChatViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        // Use a simple layout containing a wrapper LinearLayout
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_msg, parent, false);
        return new ChatViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ChatViewHolder holder, int position) {
        ChatMessage msg = messages.get(position);

        // 1. Style based on sender
        if (msg.isMe) {
            holder.wrapper.setGravity(Gravity.END);
            holder.bubble.setBackgroundResource(R.drawable.bg_bubble_me); // You need this drawable
            holder.text.setTextColor(Color.WHITE);
        } else {
            holder.wrapper.setGravity(Gravity.START);
            holder.bubble.setBackgroundResource(R.drawable.bg_bubble_them); // You need this drawable
            holder.text.setTextColor(Color.parseColor("#E0E0E0"));
        }

        // 2. Content based on type
        if (msg.type.equals("file")) {
            String fileName = getDisplayName(msg.content);
            holder.text.setText("📄 File: " + fileName);
            holder.bubble.setOnClickListener(null);

            // Show the Open File button
            holder.openFileBtn.setVisibility(View.VISIBLE);
            holder.openFileBtn.setOnClickListener(v -> openFile(msg.content));
        } else {
            holder.text.setText(msg.content);
            holder.bubble.setOnClickListener(null);

            // Hide the Open File button for non-file messages
            holder.openFileBtn.setVisibility(View.GONE);
        }

        // 3. Timestamp
        String time = new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date(msg.timestamp));
        holder.time.setText(time);
    }

    /**
     * Extract display name from a path or content URI.
     */
    private String getDisplayName(String pathOrUri) {
        if (pathOrUri.startsWith("content://")) {
            try {
                Uri uri = Uri.parse(pathOrUri);
                android.database.Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0) {
                        String name = cursor.getString(idx);
                        cursor.close();
                        return name;
                    }
                    cursor.close();
                }
            } catch (Exception e) { /* fall through to path-based */ }
        }
        return new File(pathOrUri).getName();
    }

    private void openFile(String path) {
        try {
            // Case 1: Content URI (files sent FROM this phone)
            if (path.startsWith("content://")) {
                Uri uri = Uri.parse(path);
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(uri, "*/*");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                return;
            }

            // Case 2: File path (files received FROM laptop)
            File file = new File(path);

            if (!file.exists()) {
                // Try app-specific Documents folder
                File docFolder = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS);
                File fallback = new File(new File(docFolder, "Received_Files"), file.getName());
                if (fallback.exists()) {
                    file = fallback;
                } else {
                    // Try public Documents folder
                    File publicDoc = android.os.Environment.getExternalStoragePublicDirectory(
                            android.os.Environment.DIRECTORY_DOCUMENTS);
                    File publicFallback = new File(new File(publicDoc, "Received_Files"), file.getName());
                    if (publicFallback.exists()) {
                        file = publicFallback;
                    } else {
                        android.widget.Toast.makeText(context, "File not found: " + file.getName(),
                                android.widget.Toast.LENGTH_LONG).show();
                        return;
                    }
                }
            }

            Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "*/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            e.printStackTrace();
            android.widget.Toast.makeText(context, "Cannot open file: " + e.getMessage(),
                    android.widget.Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public int getItemCount() { return messages.size(); }

    static class ChatViewHolder extends RecyclerView.ViewHolder {
        LinearLayout wrapper, bubble;
        TextView text, time, openFileBtn;

        public ChatViewHolder(View itemView) {
            super(itemView);
            wrapper = itemView.findViewById(R.id.msg_wrapper);
            bubble = itemView.findViewById(R.id.msg_bubble);
            text = itemView.findViewById(R.id.msg_text);
            time = itemView.findViewById(R.id.msg_time);
            openFileBtn = itemView.findViewById(R.id.btn_open_file);
        }
    }
}