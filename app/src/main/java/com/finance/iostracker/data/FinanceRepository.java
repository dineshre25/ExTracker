package com.finance.iostracker.data;

import com.finance.iostracker.data.DatabaseModels.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public class FinanceRepository {
    private static FinanceRepository instance;
    private final File dbFile;
    private final Gson gson;
    private FinanceDatabase db;

    private FinanceRepository(File storageDir) {
        this.dbFile = new File(storageDir, "finance_vault.json");
        this.gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
        loadDatabase();
        purgeExpiredRecycleBin();
    }

    public static synchronized FinanceRepository getInstance(File storageDir) {
        if (instance == null) {
            instance = new FinanceRepository(storageDir);
        }
        return instance;
    }

    public synchronized void loadDatabase() {
        if (dbFile.exists()) {
            try (FileReader reader = new FileReader(dbFile)) {
                db = gson.fromJson(reader, FinanceDatabase.class);
                if (db == null) initDefaultDatabase();
            } catch (IOException e) {
                e.printStackTrace();
                initDefaultDatabase();
            }
        } else {
            initDefaultDatabase();
            saveDatabase();
        }
    }

    public synchronized void saveDatabase() {
        try (FileWriter writer = new FileWriter(dbFile)) {
            gson.toJson(db, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void initDefaultDatabase() {
        db = new FinanceDatabase();
        // Default Initial Overrides
        db.aiSettings.userOverrides.put("carrefour", "Groceries");
        db.aiSettings.userOverrides.put("monoprix", "Groceries");
        db.aiSettings.userOverrides.put("basic fit", "Gym");
        db.aiSettings.userOverrides.put("trade republic", "Investments");
        db.aiSettings.userOverrides.put("tr", "Investments");
        db.aiSettings.userOverrides.put("sfr", "Telecom / Internet");
        db.aiSettings.userOverrides.put("loyer", "Rent");
        db.aiSettings.userOverrides.put("cic", "Bank Fees");

        // Default Initial Categories (iOS Color Palette)
        db.categories.add(new Category("cat_1", "Groceries", "🛒", "#34C759"));
        db.categories.add(new Category("cat_2", "Rent", "🏠", "#007AFF"));
        db.categories.add(new Category("cat_3", "Food", "🍽️", "#FF9500"));
        db.categories.add(new Category("cat_4", "Gym", "🏋️", "#AF52DE"));
        db.categories.add(new Category("cat_5", "Telecom / Internet", "📶", "#5856D6"));
        db.categories.add(new Category("cat_6", "Bank Fees", "🏦", "#8E8E93"));
        db.categories.add(new Category("cat_7", "Investments", "📈", "#FF2D55"));
        db.categories.add(new Category("cat_8", "Miscellaneous", "📦", "#A2845E"));

        // Sample Data from Cahier des Charges
        db.incomes.add(new Income("inc_001", LocalDate.now().withDayOfMonth(1).toString(), 2500.00, "Salary", "Monthly payroll"));
        db.expenses.add(new Expense("exp_001", "Trade Republic", 300.00, LocalDate.now().withDayOfMonth(5).toString(), "cat_7", "Monthly ETF savings plan"));
        db.expenses.add(new Expense("exp_002", "Monoprix", 42.50, LocalDate.now().toString(), "cat_1", ""));
    }

    /**
     * Balance Formula: Starting Savings + Sum(Incomes) - Sum(Expenses + Investments)
     * Note: Investments (e.g., Trade Republic / cat_7) are stored in db.expenses and strictly deducted.
     */
    public double getCurrentLiquidBalance() {
        double totalIncome = 0;
        for (Income inc : db.incomes) {
            totalIncome += inc.amount;
        }
        double totalExpensesAndInvestments = 0;
        for (Expense exp : db.expenses) {
            if (!exp.isDeleted) {
                totalExpensesAndInvestments += exp.amount;
            }
        }
        return db.metadata.initialSavingsBalance + totalIncome - totalExpensesAndInvestments;
    }

    public double getTotalExpensesOnly(String yearMonthPrefix) {
        double sum = 0;
        for (Expense e : db.expenses) {
            if (!e.isDeleted && (yearMonthPrefix == null || e.date.startsWith(yearMonthPrefix))) {
                if (!"cat_7".equals(e.categoryId)) sum += e.amount;
            }
        }
        return sum;
    }

    public double getTotalInvestments(String yearMonthPrefix) {
        double sum = 0;
        for (Expense e : db.expenses) {
            if (!e.isDeleted && (yearMonthPrefix == null || e.date.startsWith(yearMonthPrefix))) {
                if ("cat_7".equals(e.categoryId)) sum += e.amount;
            }
        }
        return sum;
    }

    public double getTotalIncome(String yearMonthPrefix) {
        double sum = 0;
        for (Income i : db.incomes) {
            if (yearMonthPrefix == null || i.date.startsWith(yearMonthPrefix)) {
                sum += i.amount;
            }
        }
        return sum;
    }

    // CRUD & Soft Delete (30-Day Recycle Bin)
    public void addExpense(String name, double amount, String date, String categoryName, String comment) {
        Category cat = getCategoryByName(categoryName);
        String catId = (cat != null) ? cat.id : "cat_8";
        Expense exp = new Expense("exp_" + UUID.randomUUID().toString().substring(0, 8), name, amount, date, catId, comment);
        db.expenses.add(0, exp);
        saveDatabase();
    }

    public void addIncome(String source, double amount, String date, String comment) {
        Income inc = new Income("inc_" + UUID.randomUUID().toString().substring(0, 8), date, amount, source, comment);
        db.incomes.add(0, inc);
        saveDatabase();
    }

    public void softDeleteExpense(String expenseId) {
        Iterator<Expense> iterator = db.expenses.iterator();
        while (iterator.hasNext()) {
            Expense exp = iterator.next();
            if (exp.id.equals(expenseId)) {
                exp.isDeleted = true;
                exp.deletedAt = Instant.now().toString();
                db.recycleBin.add(0, exp);
                iterator.remove();
                saveDatabase();
                break;
            }
        }
    }

    public void restoreFromRecycleBin(String expenseId) {
        Iterator<Expense> iterator = db.recycleBin.iterator();
        while (iterator.hasNext()) {
            Expense exp = iterator.next();
            if (exp.id.equals(expenseId)) {
                exp.isDeleted = false;
                exp.deletedAt = null;
                db.expenses.add(0, exp);
                iterator.remove();
                saveDatabase();
                break;
            }
        }
    }

    public void emptyRecycleBin() {
        db.recycleBin.clear();
        saveDatabase();
    }

    public void purgeExpiredRecycleBin() {
        Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);
        boolean changed = db.recycleBin.removeIf(exp -> {
            if (exp.deletedAt == null) return false;
            try {
                Instant deletedInstant = Instant.parse(exp.deletedAt);
                return deletedInstant.isBefore(thirtyDaysAgo);
            } catch (Exception e) {
                return false;
            }
        });
        if (changed) saveDatabase();
    }

    public void recordUserCategoryOverride(String expenseName, String chosenCategoryName) {
        if (expenseName != null && !expenseName.trim().isEmpty()) {
            db.aiSettings.userOverrides.put(expenseName.trim().toLowerCase(), chosenCategoryName);
            saveDatabase();
        }
    }

    public Category getCategoryById(String id) {
        for (Category c : db.categories) {
            if (c.id.equals(id)) return c;
        }
        return db.categories.get(db.categories.size() - 1);
    }

    public Category getCategoryByName(String name) {
        for (Category c : db.categories) {
            if (c.name.equalsIgnoreCase(name.trim())) return c;
        }
        return db.categories.get(db.categories.size() - 1);
    }

    public FinanceDatabase getDb() { return db; }
    public File getDbFile() { return dbFile; }

    public String exportJsonString() {
        return gson.toJson(db);
    }

    public void importJsonString(String json) {
        FinanceDatabase imported = gson.fromJson(json, FinanceDatabase.class);
        if (imported != null && imported.categories != null) {
            this.db = imported;
            purgeExpiredRecycleBin();
            saveDatabase();
        }
    }
}