package com.prajwal.myfirstapp.chat;


import com.prajwal.myfirstapp.R;
import com.prajwal.myfirstapp.connectivity.ConnectionManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.content.Context;
import java.util.ArrayList;

public class ChatActivity extends AppCompatActivity {

    private static final int PICK_FILE_REQUEST = 101; // Request Code

    private RecyclerView chatList;
    private EditText chatInput;
    private ChatAdapter adapter;
    private ArrayList<ChatMessage> messages;
    private ChatRepository repository;
    private ConnectionManager connectionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_chat);

        // Get IP from Intent
        String ip = getIntent().getStringExtra("server_ip");
        connectionManager = new ConnectionManager(ip != null ? ip : "192.168.1.5");

        repository = new ChatRepository(this);
        messages = repository.loadMessages();

        chatList = findViewById(R.id.chat_messages_list);
        chatInput = findViewById(R.id.chat_input);

        adapter = new ChatAdapter(this, messages);
        chatList.setLayoutManager(new LinearLayoutManager(this));
        chatList.setAdapter(adapter);
        if (messages.size() > 0) chatList.scrollToPosition(messages.size() - 1);

        // 1. Send Text
        findViewById(R.id.send_button).setOnClickListener(v -> {
            String text = chatInput.getText().toString().trim();
            if (!text.isEmpty()) {
                // Send UDP Command
                connectionManager.sendCommand("CHAT_MSG:" + text);

                // Update UI
                addMessage(new ChatMessage(text, "text", true));
                chatInput.setText("");
            }
        });

        // 2. Attach File (New)
        findViewById(R.id.attach_button).setOnClickListener(v -> openFilePicker());
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*"); // Allow all file types
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "Select File"), PICK_FILE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_FILE_REQUEST && resultCode == RESULT_OK && data != null) {
            Uri fileUri = data.getData();
            if (fileUri != null) {
                uploadFile(fileUri);
            }
        }
    }

    private void uploadFile(Uri uri) {
        Toast.makeText(this, "Sending file...", Toast.LENGTH_SHORT).show();

        connectionManager.sendFileToLaptop(this, uri,
                () -> {
                    // On Start
                },
                () -> {
                    // On Success: Add bubble to chat
                    runOnUiThread(() -> {
                        // Store the URI string so "Open File" can use it later
                        addMessage(new ChatMessage(uri.toString(), "file", true));
                        Toast.makeText(this, "File Sent!", Toast.LENGTH_SHORT).show();

                        // Optional: Tell PC a file was sent so it updates its chat too
                        connectionManager.sendCommand("CHAT_FILE:" + getFileName(uri));
                    });
                },
                () -> {
                    // On Error
                    runOnUiThread(() -> Toast.makeText(this, "Failed to send file", Toast.LENGTH_SHORT).show());
                }
        );
    }

    // Helper to get filename from URI
    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if(index >= 0) result = cursor.getString(index);
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) result = result.substring(cut + 1);
        }
        return result;
    }

    private void addMessage(ChatMessage msg) {
        messages.add(msg);
        adapter.notifyItemInserted(messages.size() - 1);
        chatList.scrollToPosition(messages.size() - 1);
        repository.saveMessages(messages);
    }

    // ... (onResume/onPause for Receiver from previous steps) ...
    private final BroadcastReceiver chatReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String content = intent.getStringExtra("content");
            String type = intent.getStringExtra("type");
            addMessage(new ChatMessage(content, type, false));
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(this).registerReceiver(chatReceiver, new IntentFilter("com.prajwal.myfirstapp.CHAT_EVENT"));
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(chatReceiver);
    }
}