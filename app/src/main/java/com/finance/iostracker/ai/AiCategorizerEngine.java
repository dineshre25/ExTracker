package com.finance.iostracker.ai;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.finance.iostracker.data.DatabaseModels.Category;
import com.finance.iostracker.data.FinanceRepository;
import com.google.mediapipe.tasks.genai.llminference.LlmInference;
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class AiCategorizerEngine {

    public interface CategorizationCallback {
        void onCategorySuggested(String categoryName, String sourceBadge);
    }

    private static final String ASSET_MODEL_NAME = "model.bin";
    private final Context context;
    private final FinanceRepository repository;
    private final ExecutorService executorService;
    private final Handler mainHandler;
    private final OkHttpClient httpClient;
    private LlmInference llmInference;

    public AiCategorizerEngine(Context context, FinanceRepository repository) {
        this.context = context.getApplicationContext();
        this.repository = repository;
        this.executorService = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.httpClient = new OkHttpClient();
        initMediaPipeModel();
    }

    private void initMediaPipeModel() {
        executorService.execute(() -> {
            try {
                File modelFile = copyAssetModelToInternalStorage(context, ASSET_MODEL_NAME);
                if (modelFile != null && modelFile.exists()) {
                    LlmInferenceOptions options = LlmInferenceOptions.builder()
                            .setModelPath(modelFile.getAbsolutePath())
                            .setMaxTokens(1000)
                            .build();
                    llmInference = LlmInference.createFromOptions(context, options);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private File copyAssetModelToInternalStorage(Context context, String assetFileName) {
        File outFile = new File(context.getFilesDir(), assetFileName);
        // Only copy on the very first app launch (or if missing)
        if (!outFile.exists()) {
            try (java.io.InputStream is = context.getAssets().open(assetFileName);
                 java.io.FileOutputStream fos = new java.io.FileOutputStream(outFile)) {
                byte[] buffer = new byte[1024 * 1024]; // 1MB buffer for fast copying
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
                fos.flush();
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
        return outFile;
    }

    public void categorizeExpenseAsync(String expenseName, CategorizationCallback callback) {
        if (expenseName == null || expenseName.trim().isEmpty()) return;

        String lowerCaseExpense = expenseName.trim().toLowerCase();
        Map<String, String> userOverridesMap = repository.getDb().aiSettings.userOverrides;

        // 1. Check Local Override Learning Dictionary first (Instant 0ms lookup)
        if (userOverridesMap.containsKey(lowerCaseExpense)) {
            String savedCategory = userOverridesMap.get(lowerCaseExpense);
            mainHandler.post(() -> callback.onCategorySuggested(savedCategory, "⚡ Learned Rule"));
            return;
        }

        // Also check partial keyword match in overrides
        for (Map.Entry<String, String> entry : userOverridesMap.entrySet()) {
            if (lowerCaseExpense.contains(entry.getKey())) {
                mainHandler.post(() -> callback.onCategorySuggested(entry.getValue(), "⚡ Learned Rule"));
                return;
            }
        }

        // 2. Run AI Categorization on Background Thread
        executorService.execute(() -> {
            String suggestedCategory = "Miscellaneous";
            String badge = "✨ On-Device AI";

            String apiKey = repository.getDb().aiSettings.cloudApiKey;
            boolean useCloud = "cloud".equalsIgnoreCase(repository.getDb().aiSettings.provider)
                    && apiKey != null && !apiKey.trim().isEmpty();

            if (useCloud) {
                suggestedCategory = callGeminiCloudFallback(expenseName, apiKey);
                badge = "☁️ Cloud AI";
            } else if (llmInference != null) {
                suggestedCategory = runMediaPipeCategorization(expenseName);
            } else {
                // Heuristic fallback if model.task hasn't been pushed to /data/local/tmp/llm/ yet
                suggestedCategory = heuristicFallback(lowerCaseExpense);
            }

            final String finalCategory = sanitizeCategoryResponse(suggestedCategory);
            final String finalBadge = badge;

            mainHandler.post(() -> callback.onCategorySuggested(finalCategory, finalBadge));
        });
    }

    private String runMediaPipeCategorization(String expenseName) {
        try {
            String inputPrompt = "You are a financial categorizer. Read the expense name and reply with ONLY ONE of the following categories: Groceries, Rent, Food, Gym, Telecom / Internet, Bank Fees, Investments, Miscellaneous. Expense Name: " + expenseName + ". Category:";
            return llmInference.generateResponse(inputPrompt);
        } catch (Exception e) {
            e.printStackTrace();
            return "Miscellaneous";
        }
    }

    private String callGeminiCloudFallback(String expenseName, String apiKey) {
        try {
            String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + apiKey;
            String prompt = "You are a financial categorizer. Read the expense name and reply with ONLY ONE of the following categories: Groceries, Rent, Food, Gym, Telecom / Internet, Bank Fees, Investments, Miscellaneous. Expense Name: " + expenseName + ". Category:";

            JSONObject textPart = new JSONObject().put("text", prompt);
            JSONObject parts = new JSONObject().put("parts", new JSONArray().put(textPart));
            JSONObject payload = new JSONObject().put("contents", new JSONArray().put(parts));

            RequestBody body = RequestBody.create(payload.toString(), MediaType.parse("application/json"));
            Request request = new Request.Builder().url(url).post(body).build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    JSONObject jsonRes = new JSONObject(response.body().string());
                    return jsonRes.getJSONArray("candidates")
                            .getJSONObject(0)
                            .getJSONObject("content")
                            .getJSONArray("parts")
                            .getJSONObject(0)
                            .getString("text").trim();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "Miscellaneous";
    }

    private String sanitizeCategoryResponse(String rawResponse) {
        if (rawResponse == null) return "Miscellaneous";
        String cleaned = rawResponse.trim();
        for (Category cat : repository.getDb().categories) {
            if (cleaned.toLowerCase().contains(cat.name.toLowerCase())) {
                return cat.name;
            }
        }
        return "Miscellaneous";
    }

    private String heuristicFallback(String lowerName) {
        if (lowerName.contains("leclerc") || lowerName.contains("auchan") || lowerName.contains("lidl")) return "Groceries";
        if (lowerName.contains("uber") || lowerName.contains("mcdo") || lowerName.contains("resto") || lowerName.contains("boulangerie")) return "Food";
        if (lowerName.contains("etf") || lowerName.contains("pea") || lowerName.contains("crypto") || lowerName.contains("boursorama")) return "Investments";
        if (lowerName.contains("orange") || lowerName.contains("bouygues") || lowerName.contains("free")) return "Telecom / Internet";
        return "Miscellaneous";
    }
}