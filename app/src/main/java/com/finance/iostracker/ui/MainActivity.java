package com.finance.iostracker.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.finance.iostracker.R;
import com.finance.iostracker.ai.AiCategorizerEngine;
import com.finance.iostracker.ai.AiFinancialCoach;
import com.finance.iostracker.ai.AiFinancialCoach.CoachInsight;
import com.finance.iostracker.data.DatabaseModels.Category;
import com.finance.iostracker.data.DatabaseModels.Expense;
import com.finance.iostracker.data.FinanceRepository;
import com.finance.iostracker.sync.GoogleDriveSyncManager;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.Scope;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.api.services.drive.DriveScopes;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final int RC_SIGN_IN = 9001;

    private FinanceRepository repository;
    private AiCategorizerEngine aiEngine;
    private GoogleDriveSyncManager driveSyncManager;
    private GoogleSignInClient googleSignInClient;

    // Containers for the 4 tabs
    private View layoutHome, layoutAnalytics, layoutCoach, layoutSettings;

    // Home UI elements
    private TextView tvTotalBalance, tvQuickIncome, tvQuickExpense, tvQuickInvested;
    private RecyclerView rvTransactions;
    private ExpenseAdapter expenseAdapter;
    private boolean filterCurrentMonthOnly = true;

    // Analytics UI elements
    private IosDonutChartView donutChart;
    private IosHistogramView histogramView;
    private LinearLayout layoutCategoryBreakdown;

    // AI Coach UI elements
    private LinearLayout layoutCoachCards;

    // Settings UI elements
    private EditText etInitialSavings, etCloudApiKey;
    private TextView tvRecycleBinCount, tvSyncStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        repository = FinanceRepository.getInstance(getFilesDir());
        aiEngine = new AiCategorizerEngine(this, repository);
        driveSyncManager = new GoogleDriveSyncManager(this, repository);

        // Configure Google Sign-In with restricted drive.appdata scope
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(new Scope(DriveScopes.DRIVE_APPDATA))
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

        bindViews();
        setupNavigation();
        setupTransactionsRecycler();
        refreshAllTabs();
    }

    private void bindViews() {
        layoutHome = findViewById(R.id.sectionHome);
        layoutAnalytics = findViewById(R.id.sectionAnalytics);
        layoutCoach = findViewById(R.id.sectionCoach);
        layoutSettings = findViewById(R.id.sectionSettings);

        tvTotalBalance = findViewById(R.id.tvTotalLiquidBalance);
        tvQuickIncome = findViewById(R.id.tvQuickIncome);
        tvQuickExpense = findViewById(R.id.tvQuickExpenses);
        tvQuickInvested = findViewById(R.id.tvQuickInvested);
        rvTransactions = findViewById(R.id.rvTransactions);

        Button btnThisMonth = findViewById(R.id.btnSegmentThisMonth);
        Button btnAllTime = findViewById(R.id.btnSegmentAllTime);
        btnThisMonth.setOnClickListener(v -> { filterCurrentMonthOnly = true; refreshAllTabs(); });
        btnAllTime.setOnClickListener(v -> { filterCurrentMonthOnly = false; refreshAllTabs(); });

        donutChart = findViewById(R.id.iosDonutChart);
        histogramView = findViewById(R.id.iosHistogram);
        layoutCategoryBreakdown = findViewById(R.id.layoutCategoryBreakdown);

        layoutCoachCards = findViewById(R.id.layoutCoachCards);

        etInitialSavings = findViewById(R.id.etStartingSavings);
        etCloudApiKey = findViewById(R.id.etCloudApiKey);
        tvRecycleBinCount = findViewById(R.id.tvRecycleBinCount);
        tvSyncStatus = findViewById(R.id.tvSyncStatus);

        FloatingActionButton fabAdd = findViewById(R.id.fabAddTransaction);
        fabAdd.setOnClickListener(v -> {
            AddTransactionBottomSheet sheet = new AddTransactionBottomSheet();
            sheet.setup(repository, aiEngine, this::refreshAllTabs);
            sheet.show(getSupportFragmentManager(), "AddTransaction");
        });

        setupSettingsActions();
    }

    private void setupNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            layoutHome.setVisibility(id == R.id.nav_home ? View.VISIBLE : View.GONE);
            layoutAnalytics.setVisibility(id == R.id.nav_analytics ? View.VISIBLE : View.GONE);
            layoutCoach.setVisibility(id == R.id.nav_coach ? View.VISIBLE : View.GONE);
            layoutSettings.setVisibility(id == R.id.nav_settings ? View.VISIBLE : View.GONE);
            refreshAllTabs();
            return true;
        });
    }

    private void setupTransactionsRecycler() {
        rvTransactions.setLayoutManager(new LinearLayoutManager(this));
        expenseAdapter = new ExpenseAdapter();
        rvTransactions.setAdapter(expenseAdapter);

        // iOS Swipe-Left to Soft Delete (moves to 30-day Recycling Bin)
        ItemTouchHelper.SimpleCallback swipeCallback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            @Override
            public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int pos = viewHolder.getAdapterPosition();
                Expense swiped = expenseAdapter.getItem(pos);
                repository.softDeleteExpense(swiped.id);
                Toast.makeText(MainActivity.this, "Moved '" + swiped.name + "' to 30-Day Recycle Bin", Toast.LENGTH_SHORT).show();
                refreshAllTabs();
            }
        };
        new ItemTouchHelper(swipeCallback).attachToRecyclerView(rvTransactions);
    }

    private void setupSettingsActions() {
        findViewById(R.id.btnSaveSettings).setOnClickListener(v -> {
            try {
                double savings = Double.parseDouble(etInitialSavings.getText().toString().trim().replace(",", "."));
                repository.getDb().metadata.initialSavingsBalance = savings;
            } catch (Exception ignored) {}

            String key = etCloudApiKey.getText().toString().trim();
            repository.getDb().aiSettings.cloudApiKey = key.isEmpty() ? null : key;
            repository.getDb().aiSettings.provider = key.isEmpty() ? "local" : "cloud";
            repository.saveDatabase();
            refreshAllTabs();
            Toast.makeText(this, "Settings Saved", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btnOpenRecycleBin).setOnClickListener(v -> showRecycleBinDialog());

        findViewById(R.id.btnGoogleSignInSync).setOnClickListener(v -> {
            GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
            if (account == null) {
                startActivityForResult(googleSignInClient.getSignInIntent(), RC_SIGN_IN);
            } else {
                driveSyncManager.syncNow(account, (success, msg) -> tvSyncStatus.setText(msg));
            }
        });

        findViewById(R.id.btnExportJson).setOnClickListener(v -> {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/json");
            shareIntent.putExtra(Intent.EXTRA_TEXT, repository.exportJsonString());
            startActivity(Intent.createChooser(shareIntent, "Export Finance JSON Backup"));
        });
    }

    private void showRecycleBinDialog() {
        List<Expense> bin = repository.getDb().recycleBin;
        if (bin.isEmpty()) {
            Toast.makeText(this, "Recycling Bin is empty", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] items = new String[bin.size()];
        for (int i = 0; i < bin.size(); i++) {
            Expense e = bin.get(i);
            items[i] = e.name + " (" + String.format(Locale.FRANCE, "%.2f €", e.amount) + ") - Tap to Restore";
        }
        new AlertDialog.Builder(this)
                .setTitle("Recycling Bin (Auto-purges after 30 days)")
                .setItems(items, (dialog, which) -> {
                    repository.restoreFromRecycleBin(bin.get(which).id);
                    refreshAllTabs();
                    Toast.makeText(this, "Restored transaction", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Empty Bin Permanently", (d, w) -> {
                    repository.emptyRecycleBin();
                    refreshAllTabs();
                })
                .setNeutralButton("Close", null)
                .show();
    }

    private void refreshAllTabs() {
        String monthPrefix = filterCurrentMonthOnly ? YearMonth.now().toString() : null;

        // 1. Refresh Home Dashboard
        tvTotalBalance.setText(String.format(Locale.FRANCE, "%.2f €", repository.getCurrentLiquidBalance()));
        tvQuickIncome.setText(String.format(Locale.FRANCE, "+%.0f €", repository.getTotalIncome(monthPrefix)));
        tvQuickExpense.setText(String.format(Locale.FRANCE, "-%.0f €", repository.getTotalExpensesOnly(monthPrefix)));
        tvQuickInvested.setText(String.format(Locale.FRANCE, "-%.0f €", repository.getTotalInvestments(monthPrefix)));

        List<Expense> filtered = new ArrayList<>();
        for (Expense e : repository.getDb().expenses) {
            if (!e.isDeleted && (monthPrefix == null || e.date.startsWith(monthPrefix))) {
                filtered.add(e);
            }
        }
        expenseAdapter.setItems(filtered);

        // 2. Refresh Analytics (Donut + Monthly Histogram)
        Map<String, Float> catTotals = new HashMap<>();
        for (Expense e : filtered) {
            catTotals.put(e.categoryId, catTotals.getOrDefault(e.categoryId, 0f) + (float) e.amount);
        }
        List<IosDonutChartView.Slice> slices = new ArrayList<>();
        layoutCategoryBreakdown.removeAllViews();
        for (Category c : repository.getDb().categories) {
            float amt = catTotals.getOrDefault(c.id, 0f);
            if (amt > 0) {
                slices.add(new IosDonutChartView.Slice(c.name, amt, c.color));
                TextView row = new TextView(this);
                row.setText(String.format(Locale.FRANCE, "%s  %s: %.2f €", c.icon, c.name, amt));
                row.setTextSize(15f);
                row.setPadding(8, 12, 8, 12);
                layoutCategoryBreakdown.addView(row);
            }
        }
        donutChart.setSlices(slices);

        List<IosHistogramView.BarItem> bars = new ArrayList<>();
        YearMonth now = YearMonth.now();
        for (int i = 5; i >= 0; i--) {
            YearMonth ym = now.minusMonths(i);
            float totalMonth = (float) (repository.getTotalExpensesOnly(ym.toString()) + repository.getTotalInvestments(ym.toString()));
            bars.add(new IosHistogramView.BarItem(ym.getMonth().name().substring(0, 3), totalMonth));
        }
        histogramView.setBars(bars);

        // 3. Refresh AI Coach Insights
        layoutCoachCards.removeAllViews();
        for (CoachInsight insight : AiFinancialCoach.generateInsights(repository)) {
            View card = LayoutInflater.from(this).inflate(R.layout.item_coach_card, layoutCoachCards, false);
            ((TextView) card.findViewById(R.id.tvInsightTitle)).setText(insight.title);
            ((TextView) card.findViewById(R.id.tvInsightMessage)).setText(insight.message);
            layoutCoachCards.addView(card);
        }

        // 4. Refresh Settings
        etInitialSavings.setText(String.format(Locale.US, "%.2f", repository.getDb().metadata.initialSavingsBalance));
        if (repository.getDb().aiSettings.cloudApiKey != null) {
            etCloudApiKey.setText(repository.getDb().aiSettings.cloudApiKey);
        }
        tvRecycleBinCount.setText(repository.getDb().recycleBin.size() + " items pending 30-day purge");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_SIGN_IN) {
            GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
            if (account != null) {
                driveSyncManager.syncNow(account, (success, msg) -> tvSyncStatus.setText(msg));
            }
        }
    }

    // Inner RecyclerView Adapter for iOS Grouped Transaction Rows
    private class ExpenseAdapter extends RecyclerView.Adapter<ExpenseAdapter.VH> {
        private List<Expense> items = new ArrayList<>();

        public void setItems(List<Expense> newItems) {
            this.items = newItems;
            notifyDataSetChanged();
        }

        public Expense getItem(int pos) { return items.get(pos); }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_transaction_ios, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Expense exp = items.get(position);
            Category cat = repository.getCategoryById(exp.categoryId);
            holder.tvIcon.setText(cat.icon);
            holder.tvMerchant.setText(exp.name);
            holder.tvSubtitle.setText(cat.name + " • " + exp.date + (exp.comment.isEmpty() ? "" : " • " + exp.comment));
            holder.tvAmount.setText(String.format(Locale.FRANCE, "-%.2f €", exp.amount));
        }

        @Override
        public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tvIcon, tvMerchant, tvSubtitle, tvAmount;
            VH(View v) {
                super(v);
                tvIcon = v.findViewById(R.id.tvCategoryEmoji);
                tvMerchant = v.findViewById(R.id.tvMerchantTitle);
                tvSubtitle = v.findViewById(R.id.tvCategoryAndDate);
                tvAmount = v.findViewById(R.id.tvTransactionAmount);
            }
        }
    }
}