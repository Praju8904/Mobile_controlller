package com.prajwal.myfirstapp.tasks;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.PopupMenu;

import com.prajwal.myfirstapp.R;

/**
 * Helper that shows the ⋯ context menu for a task card.
 *
 * Items: Edit, Duplicate, Move to Category, Share, Delete,
 *        Add to Focus, Start/Stop Timer.
 */
public class TaskContextMenu {

    /**
     * Callback interface — the Activity/Fragment implements this.
     */
    public interface OnMenuItemListener {
        void onEdit(Task task);
        void onDuplicate(Task task);
        void onMoveCategory(Task task);
        void onShare(Task task);
        void onDelete(Task task);
        void onAddToFocus(Task task);
        void onToggleTimer(Task task);
        default void onCopyLink(Task task) {}
    }

    /**
     * Show the popup anchored to the ⋯ button.
     *
     * @param context  Activity context
     * @param anchor   The ⋯ ImageView
     * @param task     The task this menu belongs to
     * @param listener Callback receiver
     */
    public static void show(Context context, View anchor, Task task, OnMenuItemListener listener) {
        PopupMenu popup = new PopupMenu(context, anchor, Gravity.END);

        // Build menu items programmatically (no menu XML dependency)
        popup.getMenu().add(0, 1, 0, "✏️  Edit");
        popup.getMenu().add(0, 2, 1, "📋  Duplicate");
        popup.getMenu().add(0, 3, 2, "📂  Move to Category");
        popup.getMenu().add(0, 4, 3, "🔗  Share");
        popup.getMenu().add(0, 5, 4, "🎯  Add to Focus");

        // Timer toggle — label reflects current state
        boolean timerActive = task.timerRunning;
        String timerLabel = timerActive ? "⏹  Stop Timer" : "▶️  Start Timer";
        popup.getMenu().add(0, 6, 5, timerLabel);

        popup.getMenu().add(0, 8, 6, "🔗  Copy Link");
        popup.getMenu().add(0, 7, 7, "🗑️  Delete");

        popup.setOnMenuItemClickListener(item -> {
            if (listener == null) return false;
            switch (item.getItemId()) {
                case 1: listener.onEdit(task);          return true;
                case 2: listener.onDuplicate(task);     return true;
                case 3: listener.onMoveCategory(task);  return true;
                case 4: listener.onShare(task);         return true;
                case 5: listener.onAddToFocus(task);    return true;
                case 6: listener.onToggleTimer(task);   return true;
                case 7: listener.onDelete(task);        return true;
                case 8: listener.onCopyLink(task);      return true;
                default: return false;
            }
        });

        popup.show();
    }
}
