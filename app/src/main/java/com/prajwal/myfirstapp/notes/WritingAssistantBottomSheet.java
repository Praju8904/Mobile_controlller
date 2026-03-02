package com.prajwal.myfirstapp.notes;


import com.prajwal.myfirstapp.R;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

/**
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *  WRITING ASSISTANT BOTTOM SHEET — AI-powered writing analysis and suggestions.
 *  Displays writing stats, tone detection, suggestions, and action buttons.
 * ═══════════════════════════════════════════════════════════════════════════════════════
 */
public class WritingAssistantBottomSheet extends BottomSheetDialogFragment {

    // ═══════════════════════════════════════════════════════════════════════════════
    //  CONSTANTS & FIELDS
    // ═══════════════════════════════════════════════════════════════════════════════

    private static final String ARG_CONTENT = "content";
    private static final String ARG_TITLE = "title";
    private static final String ARG_NOTE_ID = "noteId";

    private WritingAssistantListener listener;

    private String content;
    private String title;
    private String noteId;

    // ═══════════════════════════════════════════════════════════════════════════════
    //  FACTORY
    // ═══════════════════════════════════════════════════════════════════════════════

    public static WritingAssistantBottomSheet newInstance(String noteId, String title, String content) {
        WritingAssistantBottomSheet sheet = new WritingAssistantBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_NOTE_ID, noteId);
        args.putString(ARG_TITLE, title);
        args.putString(ARG_CONTENT, content);
        sheet.setArguments(args);
        return sheet;
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  LISTENER INTERFACE
    // ═══════════════════════════════════════════════════════════════════════════════

    public interface WritingAssistantListener {
        void onFocusModeRequested();
        void onExportHtmlRequested();
        void onSuggestionApplied(String suggestion);
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════════════════

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof WritingAssistantListener) {
            listener = (WritingAssistantListener) context;
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            content = getArguments().getString(ARG_CONTENT, "");
            title = getArguments().getString(ARG_TITLE, "");
            noteId = getArguments().getString(ARG_NOTE_ID, "");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_writing_assistant, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // ── Writing Stats ──
        SmartWritingAssistant.WritingStats stats = SmartWritingAssistant.computeStats(content);

        TextView tvWordCount = view.findViewById(R.id.tvWordCount);
        TextView tvSentences = view.findViewById(R.id.tvSentenceCount);
        TextView tvReadTime = view.findViewById(R.id.tvReadTime);
        TextView tvReadability = view.findViewById(R.id.tvReadability);

        tvWordCount.setText(String.valueOf(stats.wordCount));
        tvSentences.setText(String.valueOf(stats.sentenceCount));
        tvReadTime.setText(String.format("%.0f min", stats.readingTimeMinutes));
        tvReadability.setText(getReadabilityLabel(stats.fleschScore));

        // ── Tone ──
        TextView tvTone = view.findViewById(R.id.tvTone);
        java.util.Map<String, Object> toneResult = SmartWritingAssistant.detectTone(content);
        String tone = toneResult != null && toneResult.containsKey("tone") ? String.valueOf(toneResult.get("tone")) : "Neutral";
        tvTone.setText(getToneEmoji(tone) + " " + tone);

        // ── Suggestions ──
        RecyclerView rvSuggestions = view.findViewById(R.id.rvSuggestions);
        rvSuggestions.setLayoutManager(new LinearLayoutManager(getContext()));
        List<SmartWritingAssistant.StyleSuggestion> suggestions = SmartWritingAssistant.analyzeStyle(content);
        rvSuggestions.setAdapter(new SuggestionAdapter(suggestions));

        // ── Summary ──
        TextView tvSummary = view.findViewById(R.id.tvSummary);
        String summary = SmartWritingAssistant.generateSummary(content, 3);
        tvSummary.setText(summary);

        // ── Action Buttons ──
        MaterialButton btnFocusMode = view.findViewById(R.id.btnFocusMode);
        MaterialButton btnExportHtml = view.findViewById(R.id.btnExportHtml);

        btnFocusMode.setOnClickListener(v -> {
            if (listener != null) listener.onFocusModeRequested();
            dismiss();
        });

        btnExportHtml.setOnClickListener(v -> {
            if (listener != null) listener.onExportHtmlRequested();
            dismiss();
        });
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════════════════════════════════════════════

    private String getReadabilityLabel(double score) {
        if (score >= 80) return "Easy";
        if (score >= 60) return "Standard";
        if (score >= 40) return "Moderate";
        if (score >= 20) return "Difficult";
        return "Very Hard";
    }

    private String getToneEmoji(String tone) {
        if (tone == null) return "📝";
        switch (tone.toLowerCase()) {
            case "formal": return "🎩";
            case "casual": return "😊";
            case "academic": return "🎓";
            case "technical": return "⚙️";
            case "creative": return "🎨";
            case "persuasive": return "💪";
            default: return "📝";
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  SUGGESTION ADAPTER
    // ═══════════════════════════════════════════════════════════════════════════════

    private class SuggestionAdapter extends RecyclerView.Adapter<SuggestionAdapter.VH> {
        private final List<SmartWritingAssistant.StyleSuggestion> items;

        SuggestionAdapter(List<SmartWritingAssistant.StyleSuggestion> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_writing_suggestion, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            SmartWritingAssistant.StyleSuggestion s = items.get(position);
            holder.tvSuggestion.setText(s.suggestion);
            holder.tvType.setText(s.type);

            int typeColor;
            switch (s.type.toLowerCase()) {
                case "warning": typeColor = 0xFFF59E0B; break;
                case "error": typeColor = 0xFFEF4444; break;
                case "info": typeColor = 0xFF3B82F6; break;
                default: typeColor = 0xFF94A3B8; break;
            }
            holder.tvType.setTextColor(typeColor);

            holder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onSuggestionApplied(s.suggestion);
                }
            });
        }

        @Override
        public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tvSuggestion, tvType;
            VH(View v) {
                super(v);
                tvSuggestion = v.findViewById(R.id.tvSuggestionText);
                tvType = v.findViewById(R.id.tvSuggestionType);
            }
        }
    }
}
