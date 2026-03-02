package com.prajwal.myfirstapp.notes;


import com.prajwal.myfirstapp.R;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *  FLASHCARD LIST ACTIVITY — Browse all flashcard decks and individual cards.
 *  Launch flashcard review sessions from here.
 * ═══════════════════════════════════════════════════════════════════════════════════════
 */
public class FlashcardListActivity extends AppCompatActivity {

    // ═══════════════════════════════════════════════════════════════════════════════
    //  FIELDS
    // ═══════════════════════════════════════════════════════════════════════════════

    private FlashcardManager flashcardManager;
    private RecyclerView rvDecks;
    private LinearLayout emptyState;
    private DeckAdapter deckAdapter;

    // ═══════════════════════════════════════════════════════════════════════════════
    //  LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flashcard_review); // reuse layout — we override content
        getWindow().setStatusBarColor(0xFF0A0E21);
        getWindow().setNavigationBarColor(0xFF0A0E21);

        flashcardManager = new FlashcardManager(this);

        // Reuse layout elements
        TextView tvTitle = findViewById(R.id.tvDeckTitle);
        tvTitle.setText("Flashcard Decks");

        rvDecks = findViewById(R.id.rvCards);
        if (rvDecks == null) {
            // Fallback — if layout doesn't have rvCards, we use a simpler approach
            finish();
            return;
        }
        rvDecks.setLayoutManager(new LinearLayoutManager(this));
        deckAdapter = new DeckAdapter();
        rvDecks.setAdapter(deckAdapter);

        // Hide card-specific views
        View flipCard = findViewById(R.id.cardFlashcard);
        if (flipCard != null) flipCard.setVisibility(View.GONE);
        View ratingContainer = findViewById(R.id.ratingButtons);
        if (ratingContainer != null) ratingContainer.setVisibility(View.GONE);
        View progressBar = findViewById(R.id.progressBar);
        if (progressBar != null) progressBar.setVisibility(View.GONE);

        // Make recycler visible
        rvDecks.setVisibility(View.VISIBLE);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        loadDecks();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadDecks();
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  DATA
    // ═══════════════════════════════════════════════════════════════════════════════

    private void loadDecks() {
        List<Flashcard> allCards = flashcardManager.getAllCards();

        // Group by deck
        Map<String, List<Flashcard>> decks = new LinkedHashMap<>();
        for (Flashcard card : allCards) {
            String deck = card.getDeckName() != null && !card.getDeckName().isEmpty() ? card.getDeckName() : "General";
            if (!decks.containsKey(deck)) {
                decks.put(deck, new ArrayList<>());
            }
            decks.get(deck).add(card);
        }

        List<DeckInfo> deckInfos = new ArrayList<>();
        for (Map.Entry<String, List<Flashcard>> entry : decks.entrySet()) {
            DeckInfo info = new DeckInfo();
            info.name = entry.getKey();
            info.cards = entry.getValue();
            info.totalCards = entry.getValue().size();

            // Count due cards
            int due = 0;
            int mastered = 0;
            for (Flashcard c : entry.getValue()) {
                if (c.isDue()) due++;
                if (c.getRepetitions() >= 5) mastered++;
            }
            info.dueCards = due;
            info.masteredCards = mastered;
            deckInfos.add(info);
        }

        deckAdapter.setDecks(deckInfos);

        // Empty state
        LinearLayout emptyView = findViewById(R.id.emptyState);
        if (emptyView != null) {
            emptyView.setVisibility(deckInfos.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  MODELS
    // ═══════════════════════════════════════════════════════════════════════════════

    private static class DeckInfo {
        String name;
        List<Flashcard> cards;
        int totalCards;
        int dueCards;
        int masteredCards;
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  ADAPTER
    // ═══════════════════════════════════════════════════════════════════════════════

    private class DeckAdapter extends RecyclerView.Adapter<DeckAdapter.VH> {
        private List<DeckInfo> decks = new ArrayList<>();

        void setDecks(List<DeckInfo> list) {
            this.decks = list;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_flashcard, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            DeckInfo deck = decks.get(position);

            holder.tvDeckName.setText(deck.name);
            holder.tvFront.setText(deck.totalCards + " cards");

            // Use retention/review fields for deck info
            holder.tvRetention.setText(deck.masteredCards + " mastered");
            holder.tvReviewCount.setText(deck.dueCards + " due");
            holder.tvNextReview.setText("Tap to review");

            // Difficulty badge as deck status
            if (deck.dueCards > 0) {
                holder.tvDifficulty.setText("📚 " + deck.dueCards + " Due");
                holder.tvDifficulty.setTextColor(0xFFF59E0B);
            } else {
                holder.tvDifficulty.setText("✅ All done");
                holder.tvDifficulty.setTextColor(0xFF10B981);
            }

            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(FlashcardListActivity.this, FlashcardReviewActivity.class);
                intent.putExtra("deck", deck.name);
                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() { return decks.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tvDeckName, tvFront, tvDifficulty, tvRetention, tvNextReview, tvReviewCount;
            VH(View v) {
                super(v);
                tvDeckName = v.findViewById(R.id.tvDeckName);
                tvFront = v.findViewById(R.id.tvFront);
                tvDifficulty = v.findViewById(R.id.tvDifficulty);
                tvRetention = v.findViewById(R.id.tvRetention);
                tvNextReview = v.findViewById(R.id.tvNextReview);
                tvReviewCount = v.findViewById(R.id.tvReviewCount);
            }
        }
    }
}
