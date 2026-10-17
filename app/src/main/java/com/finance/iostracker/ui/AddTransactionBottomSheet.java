package com.finance.iostracker.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.finance.iostracker.R;
import com.finance.iostracker.ai.AiCategorizerEngine;
import com.finance.iostracker.data.DatabaseModels.Category;
import com.finance.iostracker.data.FinanceRepository;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class AddTransactionBottomSheet extends BottomSheetDialogFragment {

    public interface OnTransactionSavedListener {
        void onTransactionSaved();
    }

    private FinanceRepository repository;
    private AiCategorizerEngine aiEngine;
    private OnTransactionSavedListener listener;

    private String lastAiSuggestedCategory = null;
    private final Handler typingDebounceHandler = new Handler(Looper.getMainLooper());
    private Runnable typingRunnable;

    public void setup(FinanceRepository repository, AiCategorizerEngine aiEngine, OnTransactionSavedListener listener) {
        this.repository = repository;
        this.aiEngine = aiEngine;
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottom_sheet_add_transaction, container, false);

        EditText etAmount = view.findViewById(R.id.etAmount);
        EditText etName = view.findViewById(R.id.etMerchantName);
        EditText etDate = view.findViewById(R.id.etDate);
        EditText etComment = view.findViewById(R.id.etComment);
        TextView tvAiBadge = view.findViewById(R.id.tvAiSparkleBadge);
        Spinner spinnerCategory = view.findViewById(R.id.spinnerCategory);
        Button btnSaveExpense = view.findViewById(R.id.btnSaveExpense);
        Button btnSaveIncome = view.findViewById(R.id.btnSaveIncome);

        etDate.setText(LocalDate.now().toString());

        List<String> categoryNames = new ArrayList<>();
        for (Category c : repository.getDb().categories) {
            categoryNames.add(c.name);
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, categoryNames);
        spinnerCategory.setAdapter(adapter);

        // Real-time AI Categorization as user types Merchant Name
        etName.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (typingRunnable != null) typingDebounceHandler.removeCallbacks(typingRunnable);
            }
            @Override public void afterTextChanged(Editable s) {
                String input = s.toString().trim();
                if (input.length() < 2) return;
                typingRunnable = () -> aiEngine.categorizeExpenseAsync(input, (categoryName, sourceBadge) -> {
                    lastAiSuggestedCategory = categoryName;
                    tvAiBadge.setText(sourceBadge + ": " + categoryName);
                    int idx = categoryNames.indexOf(categoryName);
                    if (idx >= 0) spinnerCategory.setSelection(idx);
                });
                typingDebounceHandler.postDelayed(typingRunnable, 250);
            }
        });

        btnSaveExpense.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String amountStr = etAmount.getText().toString().trim().replace(",", ".");
            String date = etDate.getText().toString().trim();
            String comment = etComment.getText().toString().trim();
            String selectedCat = spinnerCategory.getSelectedItem().toString();

            if (name.isEmpty() || amountStr.isEmpty()) {
                Toast.makeText(getContext(), "Please enter merchant name and amount", Toast.LENGTH_SHORT).show();
                return;
            }

            double amount = Double.parseDouble(amountStr);

            // Continuous Learning: If user overrode AI suggestion, save rule to local dictionary!
            if (lastAiSuggestedCategory != null && !lastAiSuggestedCategory.equalsIgnoreCase(selectedCat)) {
                repository.recordUserCategoryOverride(name, selectedCat);
                Toast.makeText(getContext(), "Learned: '" + name + "' → " + selectedCat, Toast.LENGTH_SHORT).show();
            }

            repository.addExpense(name, amount, date, selectedCat, comment);
            if (listener != null) listener.onTransactionSaved();
            dismiss();
        });

        btnSaveIncome.setOnClickListener(v -> {
            String source = etName.getText().toString().trim();
            String amountStr = etAmount.getText().toString().trim().replace(",", ".");
            String date = etDate.getText().toString().trim();
            String comment = etComment.getText().toString().trim();

            if (source.isEmpty() || amountStr.isEmpty()) {
                Toast.makeText(getContext(), "Please enter income source and amount", Toast.LENGTH_SHORT).show();
                return;
            }

            repository.addIncome(source, Double.parseDouble(amountStr), date, comment);
            if (listener != null) listener.onTransactionSaved();
            dismiss();
        });

        return view;
    }
}