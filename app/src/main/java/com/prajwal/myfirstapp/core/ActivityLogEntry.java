package com.prajwal.myfirstapp.core;

public class ActivityLogEntry {
    public static final String FEATURE_TASKS = "Tasks";
    public static final String FEATURE_NOTES = "Notes";
    public static final String FEATURE_CALENDAR = "Calendar";
    public static final String FEATURE_VAULT = "Vault";
    public static final String FEATURE_HUB = "Smart File Hub";
    public static final String FEATURE_LAPTOP = "Laptop";
    public static final String FEATURE_EXPENSES = "Expenses";
    public static final String FEATURE_PASSWORD = "Password Vault";
    public static final String FEATURE_PERSONAL_VAULT = "Personal Vault";

    public long id;
    public String feature;
    public String description;
    public long timestamp;
    public String icon;

    public ActivityLogEntry(String feature, String description, String icon) {
        this.feature = feature;
        this.description = description;
        this.timestamp = System.currentTimeMillis();
        this.icon = icon;
    }
}
