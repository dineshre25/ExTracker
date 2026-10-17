package com.finance.iostracker.ai;

import com.finance.iostracker.data.DatabaseModels.*;
import com.finance.iostracker.data.FinanceRepository;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AiFinancialCoach {

    public static class CoachInsight {
        public final String title;
        public final String message;
        public final String accentColor;

        public CoachInsight(String title, String message, String accentColor) {
            this.title = title;
            this.message = message;
            this.accentColor = accentColor;
        }
    }

    public static List<CoachInsight> generateInsights(FinanceRepository repo) {
        List<CoachInsight> insights = new ArrayList<>();
        YearMonth currentMonth = YearMonth.now();
        YearMonth prevMonth = currentMonth.minusMonths(1);

        String currPrefix = currentMonth.toString(); // YYYY-MM
        String prevPrefix = prevMonth.toString();

        Map<String, Double> currMap = new HashMap<>();
        Map<String, Double> prevMap = new HashMap<>();

        for (Expense e : repo.getDb().expenses) {
            if (e.isDeleted) continue;
            if (e.date.startsWith(currPrefix)) {
                currMap.put(e.categoryId, currMap.getOrDefault(e.categoryId, 0.0) + e.amount);
            } else if (e.date.startsWith(prevPrefix)) {
                prevMap.put(e.categoryId, prevMap.getOrDefault(e.categoryId, 0.0) + e.amount);
            }
        }

        // 1. Check Month-over-Month Category Surges (> 25%)
        for (Category cat : repo.getDb().categories) {
            if ("cat_7".equals(cat.id)) continue; // Skip Investments for surge warning
            double currSpent = currMap.getOrDefault(cat.id, 0.0);
            double prevSpent = prevMap.getOrDefault(cat.id, 0.0);

            if (prevSpent > 0 && currSpent > prevSpent * 1.25) {
                double pctIncrease = ((currSpent - prevSpent) / prevSpent) * 100.0;
                insights.add(new CoachInsight(
                        "⚠️ " + cat.name + " Surge (+ " + String.format(Locale.FRANCE, "%.0f%%", pctIncrease) + ")",
                        String.format(Locale.FRANCE, "You spent %.2f € on %s in %s vs %.2f € in %s. Consider reviewing recent purchases.",
                                currSpent, cat.name, currentMonth.getMonth().name(), prevSpent, prevMonth.getMonth().name()),
                        "#FF9500"
                ));
            }
        }

        // 2. Check Investment Discipline (Trade Republic / cat_7)
        double currInvested = currMap.getOrDefault("cat_7", 0.0);
        if (currInvested > 0) {
            insights.add(new CoachInsight(
                    "📈 Strong Investment Discipline",
                    String.format(Locale.FRANCE, "You locked %.2f € into Investments (Trade Republic) this month. Since it is deducted directly from your liquid balance, your remaining budget stays conservative!", currInvested),
                    "#34C759"
            ));
        } else {
            insights.add(new CoachInsight(
                    "💡 Investment Reminder",
                    "No transfers to Trade Republic logged yet for " + currentMonth.getMonth().name() + ". Consistency builds long-term wealth.",
                    "#007AFF"
            ));
        }

        // 3. Burn Rate & Liquid Savings Check
        double currentLiquid = repo.getCurrentLiquidBalance();
        insights.add(new CoachInsight(
                "🏦 Liquid Net Worth Status",
                String.format(Locale.FRANCE, "Your available liquid balance across all time (including your %.0f € starting savings and deducting all investments) stands at %.2f €.",
                        repo.getDb().metadata.initialSavingsBalance, currentLiquid),
                "#5856D6"
        ));

        return insights;
    }
}