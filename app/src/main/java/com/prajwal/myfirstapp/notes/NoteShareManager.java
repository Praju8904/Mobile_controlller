package com.prajwal.myfirstapp.notes;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *  NOTE SHARE MANAGER — Share notes as text, HTML, or via Android share sheet.
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *
 *  Sharing options:
 *  • Plain text (copy or share)
 *  • Markdown format
 *  • HTML (styled, dark theme)
 *  • Share intent (to any app)
 */
public class NoteShareManager {

    private final Context context;

    public NoteShareManager(Context context) {
        this.context = context;
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  PLAIN TEXT
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Convert note content blocks to plain text.
     */
    public static String toPlainText(Note note, List<ContentBlock> blocks) {
        StringBuilder sb = new StringBuilder();

        if (note.title != null && !note.title.isEmpty()) {
            sb.append(note.title).append("\n");
            sb.append("═".repeat(Math.min(40, note.title.length()))).append("\n\n");
        }

        if (blocks != null) {
            for (ContentBlock block : blocks) {
                String text = block.getText();
                if (text == null || text.trim().isEmpty()) continue;

                switch (block.blockType) {
                    case ContentBlock.TYPE_HEADING1:
                        sb.append("# ").append(text).append("\n\n");
                        break;
                    case ContentBlock.TYPE_HEADING2:
                        sb.append("## ").append(text).append("\n\n");
                        break;
                    case ContentBlock.TYPE_HEADING3:
                        sb.append("### ").append(text).append("\n\n");
                        break;
                    case ContentBlock.TYPE_BULLET:
                        sb.append("• ").append(text).append("\n");
                        break;
                    case ContentBlock.TYPE_NUMBERED:
                        sb.append("  ").append(text).append("\n");
                        break;
                    case ContentBlock.TYPE_CHECKLIST:
                        sb.append("☐ ").append(text).append("\n");
                        break;
                    case ContentBlock.TYPE_QUOTE:
                        sb.append("> ").append(text).append("\n\n");
                        break;
                    case ContentBlock.TYPE_CODE:
                        sb.append("```\n").append(text).append("\n```\n\n");
                        break;
                    case ContentBlock.TYPE_DIVIDER:
                        sb.append("────────────────\n\n");
                        break;
                    default:
                        sb.append(text).append("\n\n");
                        break;
                }
            }
        }

        // Meta info
        sb.append("\n---\n");
        if (note.category != null) sb.append("Category: ").append(note.category).append("\n");
        if (note.tags != null && !note.tags.isEmpty()) {
            sb.append("Tags: ").append(String.join(", ", note.tags)).append("\n");
        }
        sb.append("Last updated: ").append(new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US)
                .format(new Date(note.updatedAt))).append("\n");

        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  MARKDOWN
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Convert note content blocks to Markdown format.
     */
    public static String toMarkdown(Note note, List<ContentBlock> blocks) {
        StringBuilder sb = new StringBuilder();

        if (note.title != null && !note.title.isEmpty()) {
            sb.append("# ").append(note.title).append("\n\n");
        }

        if (blocks != null) {
            int listCounter = 1;
            for (ContentBlock block : blocks) {
                String text = block.getText();
                if (text == null || text.trim().isEmpty()) {
                    if (block.blockType.equals(ContentBlock.TYPE_DIVIDER)) {
                        sb.append("\n---\n\n");
                    }
                    listCounter = 1;
                    continue;
                }

                switch (block.blockType) {
                    case ContentBlock.TYPE_HEADING1:
                        sb.append("# ").append(text).append("\n\n");
                        listCounter = 1;
                        break;
                    case ContentBlock.TYPE_HEADING2:
                        sb.append("## ").append(text).append("\n\n");
                        listCounter = 1;
                        break;
                    case ContentBlock.TYPE_HEADING3:
                        sb.append("### ").append(text).append("\n\n");
                        listCounter = 1;
                        break;
                    case ContentBlock.TYPE_BULLET:
                        sb.append("- ").append(text).append("\n");
                        break;
                    case ContentBlock.TYPE_NUMBERED:
                        sb.append(listCounter++).append(". ").append(text).append("\n");
                        break;
                    case ContentBlock.TYPE_CHECKLIST:
                        sb.append("- [ ] ").append(text).append("\n");
                        break;
                    case ContentBlock.TYPE_QUOTE:
                        sb.append("> ").append(text).append("\n\n");
                        listCounter = 1;
                        break;
                    case ContentBlock.TYPE_CODE:
                        sb.append("```\n").append(text).append("\n```\n\n");
                        listCounter = 1;
                        break;
                    case ContentBlock.TYPE_CALLOUT:
                        sb.append("> **Note:** ").append(text).append("\n\n");
                        listCounter = 1;
                        break;
                    case ContentBlock.TYPE_DIVIDER:
                        sb.append("\n---\n\n");
                        listCounter = 1;
                        break;
                    default:
                        sb.append(text).append("\n\n");
                        listCounter = 1;
                        break;
                }
            }
        }

        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  SHARE INTENT
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Launch Android share intent with note content as text.
     */
    public void shareAsText(Note note, List<ContentBlock> blocks) {
        String text = toPlainText(note, blocks);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, note.title != null ? note.title : "Shared Note");
        intent.putExtra(Intent.EXTRA_TEXT, text);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(Intent.createChooser(intent, "Share Note"));
    }

    /**
     * Launch Android share intent with note as Markdown.
     */
    public void shareAsMarkdown(Note note, List<ContentBlock> blocks) {
        String md = toMarkdown(note, blocks);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, (note.title != null ? note.title : "Note") + ".md");
        intent.putExtra(Intent.EXTRA_TEXT, md);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(Intent.createChooser(intent, "Share as Markdown"));
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  COPY TO CLIPBOARD
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Copy note text to clipboard.
     */
    public void copyToClipboard(Note note, List<ContentBlock> blocks) {
        String text = toPlainText(note, blocks);
        android.content.ClipboardManager clipboard =
                (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Note", text));
        }
    }
}
