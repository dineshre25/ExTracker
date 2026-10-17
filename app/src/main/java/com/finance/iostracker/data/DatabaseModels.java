package com.finance.iostracker.data;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DatabaseModels {

    public static class FinanceDatabase {
        @SerializedName("version")
        public String version = "1.0";

        @SerializedName("metadata")
        public Metadata metadata = new Metadata();

        @SerializedName("ai_settings")
        public AiSettings aiSettings = new AiSettings();

        @SerializedName("categories")
        public List<Category> categories = new ArrayList<>();

        @SerializedName("incomes")
        public List<Income> incomes = new ArrayList<>();

        @SerializedName("expenses")
        public List<Expense> expenses = new ArrayList<>();

        @SerializedName("recycle_bin")
        public List<Expense> recycleBin = new ArrayList<>();
    }

    public static class Metadata {
        @SerializedName("last_synced_at")
        public String lastSyncedAt = "";

        @SerializedName("currency")
        public String currency = "EUR";

        @SerializedName("initial_savings_balance")
        public double initialSavingsBalance = 5000.00;
    }

    public static class AiSettings {
        @SerializedName("provider")
        public String provider = "local"; // "local" or "cloud"

        @SerializedName("cloud_api_key")
        public String cloudApiKey = null;

        @SerializedName("user_overrides")
        public Map<String, String> userOverrides = new HashMap<>();
    }

    public static class Category {
        @SerializedName("id")
        public String id;
        @SerializedName("name")
        public String name;
        @SerializedName("icon")
        public String icon;
        @SerializedName("color")
        public String color;

        public Category(String id, String name, String icon, String color) {
            this.id = id;
            this.name = name;
            this.icon = icon;
            this.color = color;
        }
    }

    public static class Income {
        @SerializedName("id")
        public String id;
        @SerializedName("date")
        public String date; // YYYY-MM-DD
        @SerializedName("amount")
        public double amount;
        @SerializedName("source")
        public String source;
        @SerializedName("comment")
        public String comment;

        public Income(String id, String date, double amount, String source, String comment) {
            this.id = id;
            this.date = date;
            this.amount = amount;
            this.source = source;
            this.comment = comment != null ? comment : "";
        }
    }

    public static class Expense {
        @SerializedName("id")
        public String id;
        @SerializedName("name")
        public String name;
        @SerializedName("amount")
        public double amount;
        @SerializedName("date")
        public String date; // YYYY-MM-DD
        @SerializedName("category_id")
        public String categoryId;
        @SerializedName("comment")
        public String comment;
        @SerializedName("is_deleted")
        public boolean isDeleted;
        @SerializedName("deleted_at")
        public String deletedAt; // ISO-8601 UTC timestamp

        public Expense(String id, String name, double amount, String date, String categoryId, String comment) {
            this.id = id;
            this.name = name;
            this.amount = amount;
            this.date = date;
            this.categoryId = categoryId;
            this.comment = comment != null ? comment : "";
            this.isDeleted = false;
            this.deletedAt = null;
        }
    }
}