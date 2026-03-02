package com.prajwal.myfirstapp.notes;


import com.prajwal.myfirstapp.R;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

/**
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *  FLASHCARD REVIEW ACTIVITY — SM-2 spaced-repetition review screen.
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *
 *  Shows flashcards one at a time. Tap card to flip. Rate your knowledge (Again / Hard / Good / Easy).
 *  Rating feeds into SM-2 algorithm to schedule next review.
 */
public class FlashcardReviewActivity extends AppCompatActivity {

    // ═══════════════════════════════════════════════════════════════════════════════
    //  FIELDS
    // ═══════════════════════════════════════════════════════════════════════════════

    private FlashcardManager flashcardManager;
    private List<Flashcard> dueCards = new ArrayList<>();
    private int currentIndex = 0;
    private boolean showingBack = false;

    // Views
    private TextView tvDeckTitle, tvProgress, tvDueCount;
    private ProgressBar progressBar;
    private CardView cardFlashcard;
    private TextView tvCardSide, tvCardContent, tvTapHint;
    private LinearLayout ratingButtons, emptyState;
    private MaterialButton btnAgain, btnHard, btnGood, btnEasy;

    // ═══════════════════════════════════════════════════════════════════════════════
    //  LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flashcard_review);
        getWindow().setStatusBarColor(0xFF0A0E21);
        getWindow().setNavigationBarColor(0xFF0A0E21);

        flashcardManager = new FlashcardManager(this);
        bindViews();
        setupListeners();
        loadCards();
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  SETUP
    // ═══════════════════════════════════════════════════════════════════════════════

    private void bindViews() {
        tvDeckTitle = findViewById(R.id.tvDeckTitle);
        tvProgress = findViewById(R.id.tvProgress);
        tvDueCount = findViewById(R.id.tvDueCount);
        progressBar = findViewById(R.id.progressBar);
        cardFlashcard = findViewById(R.id.cardFlashcard);
        tvCardSide = findViewById(R.id.tvCardSide);
        tvCardContent = findViewById(R.id.tvCardContent);
        tvTapHint = findViewById(R.id.tvTapHint);
        ratingButtons = findViewById(R.id.ratingButtons);
        emptyState = findViewById(R.id.emptyState);
        btnAgain = findViewById(R.id.btnAgain);
        btnHard = findViewById(R.id.btnHard);
        btnGood = findViewById(R.id.btnGood);
        btnEasy = findViewById(R.id.btnEasy);
    }

    private void setupListeners() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // Flip card on tap
        cardFlashcard.setOnClickListener(v -> flipCard());

        // Rating buttons
        btnAgain.setOnClickListener(v -> rateCard(0));
        btnHard.setOnClickListener(v -> rateCard(2));
        btnGood.setOnClickListener(v -> rateCard(3));
        btnEasy.setOnClickListener(v -> rateCard(5));
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  LOAD CARDS
    // ═══════════════════════════════════════════════════════════════════════════════

    private void loadCards() {
        String deckFilter = getIntent().getStringExtra("deck");
        String noteId = getIntent().getStringExtra("noteId");

        if (deckFilter != null) {
            dueCards = flashcardManager.getCardsForDeck(deckFilter);
            tvDeckTitle.setText(deckFilter);
        } else if (noteId != null) {
            dueCards = flashcardManager.getCardsForNote(noteId);
            tvDeckTitle.setText("Note Flashcards");
        } else {
            dueCards = flashcardManager.getDueCards();
            tvDeckTitle.setText("Due Review");
        }

        tvDueCount.setText(dueCards.size() + " cards");

        if (dueCards.isEmpty()) {
            cardFlashcard.setVisibility(View.GONE);
            emptyState.setVisibility(View.VISIBLE);
            ratingButtons.setVisibility(View.GONE);
        } else {
            currentIndex = 0;
            showCard();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  CARD DISPLAY
    // ═══════════════════════════════════════════════════════════════════════════════

    private void showCard() {
        if (currentIndex >= dueCards.size()) {
            // All done
            cardFlashcard.setVisibility(View.GONE);
            ratingButtons.setVisibility(View.GONE);
            emptyState.setVisibility(View.VISIBLE);
            return;
        }

        Flashcard card = dueCards.get(currentIndex);
        showingBack = false;

        tvCardSide.setText("FRONT");
        tvCardSide.setTextColor(0xFFF59E0B);
        tvCardContent.setText(card.getFront());
        tvTapHint.setVisibility(View.VISIBLE);
        tvTapHint.setText("Tap to flip");
        ratingButtons.setVisibility(View.GONE);

        // Progress
        tvProgress.setText((currentIndex + 1) + " / " + dueCards.size());
        progressBar.setMax(dueCards.size());
        progressBar.setProgress(currentIndex + 1);

        // Animate card in
        cardFlashcard.setAlpha(0f);
        cardFlashcard.setScaleX(0.95f);
        cardFlashcard.setScaleY(0.95f);
        cardFlashcard.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(200)
                .start();
    }

    private void flipCard() {
        if (currentIndex >= dueCards.size()) return;
        Flashcard card = dueCards.get(currentIndex);

        if (!showingBack) {
            // Flip to back
            cardFlashcard.animate()
                    .rotationYBy(90)
                    .setDuration(120)
                    .withEndAction(() -> {
                        showingBack = true;
                        tvCardSide.setText("BACK");
                        tvCardSide.setTextColor(0xFF10B981);
                        tvCardContent.setText(card.getBack());
                        tvTapHint.setVisibility(View.GONE);
                        ratingButtons.setVisibility(View.VISIBLE);

                        cardFlashcard.setRotationY(-90);
                        cardFlashcard.animate()
                                .rotationYBy(90)
                                .setDuration(120)
                                .start();
                    })
                    .start();
        } else {
            // Flip back to front
            cardFlashcard.animate()
                    .rotationYBy(90)
                    .setDuration(120)
                    .withEndAction(() -> {
                        showingBack = false;
                        tvCardSide.setText("FRONT");
                        tvCardSide.setTextColor(0xFFF59E0B);
                        tvCardContent.setText(card.getFront());
                        tvTapHint.setVisibility(View.VISIBLE);
                        ratingButtons.setVisibility(View.GONE);

                        cardFlashcard.setRotationY(-90);
                        cardFlashcard.animate()
                                .rotationYBy(90)
                                .setDuration(120)
                                .start();
                    })
                    .start();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  RATING
    // ═══════════════════════════════════════════════════════════════════════════════

    private void rateCard(int quality) {
        if (currentIndex >= dueCards.size()) return;

        Flashcard card = dueCards.get(currentIndex);
        flashcardManager.reviewCard(card.getId(), quality);

        String msg;
        switch (quality) {
            case 0: msg = "Review again soon"; break;
            case 2: msg = "Harder next time"; break;
            case 5: msg = "Easy — interval extended!"; break;
            default: msg = "Got it!"; break;
        }
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();

        currentIndex++;
        showCard();
    }
}
