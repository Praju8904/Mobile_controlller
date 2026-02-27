package com.prajwal.myfirstapp;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.util.ArrayList;

/**
 * Handles all expense-related notifications:
 * - Subscription renewal reminders
 * - Auto-logged expense confirmations
 * - Budget threshold alerts
 * - Budget exceeded alerts
 *
 * Fires local push notifications that deep link into the relevant screens.
 */
public class ExpenseNotificationHelper extends BroadcastReceiver {

    private static final String TAG = "ExpenseNotif";
    private static final String CHANNEL_SUBSCRIPTION = "subscription_reminders";
    private static final String CHANNEL_BUDGET = "budget_alerts";

    // Actions
    public static final String ACTION_SUBSCRIPTION_REMINDER = "com.prajwal.myfirstapp.SUBSCRIPTION_REMINDER";
    public static final String ACTION_EXPENSE_LOGGED = "com.prajwal.myfirstapp.EXPENSE_LOGGED";
    public static final String ACTION_BUDGET_THRESHOLD = "com.prajwal.myfirstapp.BUDGET_THRESHOLD";
    public static final String ACTION_BUDGET_EXCEEDED = "com.prajwal.myfirstapp.BUDGET_EXCEEDED";
    public static final String ACTION_CHECK_RECURRING = "com.prajwal.myfirstapp.CHECK_RECURRING_EXPENSES";

    // Extras
    public static final String EXTRA_SUBSCRIPTION_ID = "subscription_id";
    public static final String EXTRA_SUBSCRIPTION_NAME = "subscription_name";
    public static final String EXTRA_AMOUNT = "amount";
    public static final String EXTRA_CURRENCY = "currency";
    public static final String EXTRA_DAYS_UNTIL = "days_until";
    public static final String EXTRA_CATEGORY_ID = "category_id";
    public static final String EXTRA_BUDGET_AMOUNT = "budget_amount";
    public static final String EXTRA_SPENT_AMOUNT = "spent_amount";
    public static final String EXTRA_PERCENT = "percent";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) {
            action = ACTION_CHECK_RECURRING;
        }

        createNotificationChannels(context);

        switch (action) {
            case ACTION_SUBSCRIPTION_REMINDER:
                handleSubscriptionReminder(context, intent);
                break;
            case ACTION_BUDGET_THRESHOLD:
                handleBudgetThreshold(context, intent);
                break;
            case ACTION_BUDGET_EXCEEDED:
                handleBudgetExceeded(context, intent);
                break;
            case ACTION_CHECK_RECURRING:
                processRecurringExpenses(context);
                break;
            default:
                processRecurringExpenses(context);
                break;
        }
    }

    /**
     * Process all recurring expenses — auto-log overdue ones, check reminders & budgets.
     * Called on app open, from alarm, and on boot.
     */
    public static void processRecurringExpenses(Context context) {
        try {
            ExpenseRepository expenseRepo = new ExpenseRepository(context);
            RecurringExpenseRepository recurringRepo = new RecurringExpenseRepository(context);
            CategoryBudgetRepository budgetRepo = new CategoryBudgetRepository(context);
            WalletRepository walletRepo = new WalletRepository(context);

            // 1. Process expired budget periods
            budgetRepo.processExpiredPeriods(expenseRepo);

            // 2. Auto-log overdue recurring expenses (wallet-aware)
            int logged = recurringRepo.processOverdueExpensesWithBalance(expenseRepo, walletRepo);
            if (logged > 0) {
                Log.i(TAG, "Auto-logged " + logged + " recurring expenses (wallet-aware)");
            }

            // 3. Send auto-log confirmations for recently processed
            if (logged > 0) {
                ArrayList<RecurringExpense> all = recurringRepo.loadAll();
                for (RecurringExpense re : all) {
                    if (re.isActive) {
                        // Send confirmation for recently logged ones
                        fireExpenseLoggedNotification(context, re);
                    }
                }
            }

            // 4. Check subscription reminders
            ArrayList<RecurringExpense> reminders = recurringRepo.getDueForReminder();
            for (RecurringExpense re : reminders) {
                fireSubscriptionReminderNotification(context, re);
            }

            // 5. Check budget alerts
            ArrayList<CategoryBudget> alerts = budgetRepo.checkBudgetAlerts(expenseRepo);
            for (CategoryBudget cb : alerts) {
                double spent = budgetRepo.getCategorySpending(
                    cb.categoryId, cb.startDate, cb.endDate, expenseRepo);
                if (cb.id.endsWith("_exceeded")) {
                    fireBudgetExceededNotification(context, cb.categoryId, cb.budgetAmount, spent);
                } else {
                    fireBudgetThresholdNotification(context, cb, spent);
                }
            }

            // 6. Schedule next check alarm
            schedulePeriodicCheck(context);

        } catch (Exception e) {
            Log.e(TAG, "Error processing recurring expenses: " + e.getMessage());
        }
    }

    // ─── Notification Builders ───────────────────────────────

    private static void fireSubscriptionReminderNotification(Context context, RecurringExpense re) {
        int daysUntil = re.getDaysUntilDue();
        String title = re.name + " renews in " + daysUntil + " day" + (daysUntil != 1 ? "s" : "");
        String body = re.currency + String.format("%.0f", re.amount) + " — " + re.categoryId;

        Intent tapIntent = new Intent(context, SubscriptionsActivity.class);
        tapIntent.putExtra(EXTRA_SUBSCRIPTION_ID, re.id);
        tapIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(context,
            ("sub_remind_" + re.id).hashCode(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_SUBSCRIPTION)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setColor(re.color);

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(("sub_" + re.id).hashCode() & 0x7FFFFFFF, builder.build());
        }
    }

    private static void fireExpenseLoggedNotification(Context context, RecurringExpense re) {
        String title = re.name + " " + re.currency + String.format("%.0f", re.amount) + " has been recorded";
        String body = "Auto-logged " + re.getRecurrenceLabel().toLowerCase() + " subscription";

        Intent tapIntent = new Intent(context, ExpenseTrackerActivity.class);
        tapIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(context,
            ("exp_logged_" + re.id).hashCode(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_SUBSCRIPTION)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setColor(0xFF22C55E);

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(("exp_" + re.id).hashCode() & 0x7FFFFFFF, builder.build());
        }
    }

    private static void fireBudgetThresholdNotification(Context context, CategoryBudget cb,
                                                         double spent) {
        String icon = Expense.getCategoryIcon(cb.categoryId);
        String title = "You've used " + cb.alertThresholdPercent + "% of your " + cb.categoryId + " budget";
        String body = icon + " " + cb.currency + String.format("%.0f", spent) +
                      " of " + cb.currency + String.format("%.0f", cb.budgetAmount) + " spent";

        Intent tapIntent = new Intent(context, BudgetGoalsActivity.class);
        tapIntent.putExtra(EXTRA_CATEGORY_ID, cb.categoryId);
        tapIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(context,
            ("budget_warn_" + cb.categoryId).hashCode(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_BUDGET)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setColor(0xFFF59E0B);

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(("bw_" + cb.categoryId).hashCode() & 0x7FFFFFFF, builder.build());
        }
    }

    private static void fireBudgetExceededNotification(Context context, String categoryId,
                                                        double budget, double spent) {
        String icon = Expense.getCategoryIcon(categoryId);
        String title = categoryId + " budget exceeded!";
        String body = icon + " You've spent ₹" + String.format("%.0f", spent) +
                      " of your ₹" + String.format("%.0f", budget) + " budget";

        Intent tapIntent = new Intent(context, BudgetGoalsActivity.class);
        tapIntent.putExtra(EXTRA_CATEGORY_ID, categoryId);
        tapIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(context,
            ("budget_exceed_" + categoryId).hashCode(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_BUDGET)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setColor(0xFFEF4444);

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(("be_" + categoryId).hashCode() & 0x7FFFFFFF, builder.build());
        }
    }

    // ─── Notification Channels ───────────────────────────────

    public static void createNotificationChannels(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;

            NotificationChannel subChannel = new NotificationChannel(
                CHANNEL_SUBSCRIPTION,
                "Subscription Reminders",
                NotificationManager.IMPORTANCE_HIGH
            );
            subChannel.setDescription("Notifications for upcoming subscription renewals");
            subChannel.enableVibration(true);
            nm.createNotificationChannel(subChannel);

            NotificationChannel budgetChannel = new NotificationChannel(
                CHANNEL_BUDGET,
                "Budget Alerts",
                NotificationManager.IMPORTANCE_HIGH
            );
            budgetChannel.setDescription("Alerts when category budgets are nearing limits or exceeded");
            budgetChannel.enableVibration(true);
            nm.createNotificationChannel(budgetChannel);
        }
    }

    // ─── Alarm Scheduling ────────────────────────────────────

    /**
     * Schedule a periodic check every 6 hours for recurring expense processing.
     */
    public static void schedulePeriodicCheck(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(context, ExpenseNotificationHelper.class);
        intent.setAction(ACTION_CHECK_RECURRING);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(context,
            9999,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        long interval = 6 * 60 * 60 * 1000L; // 6 hours
        long triggerAt = System.currentTimeMillis() + interval;

        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            interval,
            pendingIntent
        );
    }

    /**
     * Schedule an exact alarm for a specific subscription reminder.
     */
    public static void scheduleSubscriptionReminder(Context context, RecurringExpense re) {
        if (re.reminderDaysBefore <= 0 || !re.isActive) return;

        long reminderTime = re.nextDueDate - (long) re.reminderDaysBefore * 24 * 60 * 60 * 1000L;
        if (reminderTime <= System.currentTimeMillis()) return;

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(context, ExpenseNotificationHelper.class);
        intent.setAction(ACTION_SUBSCRIPTION_REMINDER);
        intent.putExtra(EXTRA_SUBSCRIPTION_ID, re.id);
        intent.putExtra(EXTRA_SUBSCRIPTION_NAME, re.name);
        intent.putExtra(EXTRA_AMOUNT, re.amount);
        intent.putExtra(EXTRA_CURRENCY, re.currency);
        intent.putExtra(EXTRA_DAYS_UNTIL, re.reminderDaysBefore);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(context,
            ("sub_alarm_" + re.id).hashCode() & 0x7FFFFFFF,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminderTime, pendingIntent);
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, reminderTime, pendingIntent);
        }
    }

    /**
     * Reschedule all subscription reminders — called on boot and when subscriptions change.
     */
    public static void rescheduleAllSubscriptionReminders(Context context) {
        RecurringExpenseRepository repo = new RecurringExpenseRepository(context);
        for (RecurringExpense re : repo.getActive()) {
            scheduleSubscriptionReminder(context, re);
        }
        schedulePeriodicCheck(context);
    }

    private void handleSubscriptionReminder(Context context, Intent intent) {
        String id = intent.getStringExtra(EXTRA_SUBSCRIPTION_ID);
        if (id == null) return;

        RecurringExpenseRepository repo = new RecurringExpenseRepository(context);
        RecurringExpense re = repo.getById(id);
        if (re != null && re.isActive) {
            fireSubscriptionReminderNotification(context, re);
        }
    }

    private void handleBudgetThreshold(Context context, Intent intent) {
        String categoryId = intent.getStringExtra(EXTRA_CATEGORY_ID);
        if (categoryId == null) return;

        CategoryBudgetRepository budgetRepo = new CategoryBudgetRepository(context);
        CategoryBudget cb = budgetRepo.getByCategory(categoryId);
        if (cb != null) {
            ExpenseRepository expenseRepo = new ExpenseRepository(context);
            double spent = budgetRepo.getCategorySpending(cb.categoryId, cb.startDate, cb.endDate, expenseRepo);
            fireBudgetThresholdNotification(context, cb, spent);
        }
    }

    private void handleBudgetExceeded(Context context, Intent intent) {
        String categoryId = intent.getStringExtra(EXTRA_CATEGORY_ID);
        double budget = intent.getDoubleExtra(EXTRA_BUDGET_AMOUNT, 0);
        double spent = intent.getDoubleExtra(EXTRA_SPENT_AMOUNT, 0);
        if (categoryId != null) {
            fireBudgetExceededNotification(context, categoryId, budget, spent);
        }
    }
}
