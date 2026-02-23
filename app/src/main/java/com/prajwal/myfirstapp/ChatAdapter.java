package com.prajwal.myfirstapp;

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
            holder.text.setText("📄 File: " + new File(msg.content).getName());
            holder.bubble.setOnClickListener(v -> openFile(msg.content));
        } else {
            holder.text.setText(msg.content);
            holder.bubble.setOnClickListener(null);
        }

        // 3. Timestamp
        String time = new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date(msg.timestamp));
        holder.time.setText(time);
    }

    private void openFile(String path) {
        try {
            File file = new File(path);
            if (!file.exists()) return;
            
            Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "*/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(intent);
        } catch (Exception e) { e.printStackTrace(); }
    }

    @Override
    public int getItemCount() { return messages.size(); }

    static class ChatViewHolder extends RecyclerView.ViewHolder {
        LinearLayout wrapper, bubble;
        TextView text, time;

        public ChatViewHolder(View itemView) {
            super(itemView);
            wrapper = itemView.findViewById(R.id.msg_wrapper);
            bubble = itemView.findViewById(R.id.msg_bubble);
            text = itemView.findViewById(R.id.msg_text);
            time = itemView.findViewById(R.id.msg_time);
        }
    }
}