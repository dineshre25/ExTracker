package com.finance.iostracker.sync;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.finance.iostracker.data.FinanceRepository;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.FileContent;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GoogleDriveSyncManager {

    public interface SyncCallback {
        void onSyncResult(boolean success, String statusMessage);
    }

    private static final String BACKUP_FILENAME = "finance_vault_sync.json";
    private final Context context;
    private final FinanceRepository repository;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public GoogleDriveSyncManager(Context context, FinanceRepository repository) {
        this.context = context.getApplicationContext();
        this.repository = repository;
    }

    public void syncNow(GoogleSignInAccount account, SyncCallback callback) {
        if (account == null) {
            callback.onSyncResult(false, "Please sign in with Google first.");
            return;
        }

        executor.execute(() -> {
            try {
                GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(
                        context, Collections.singleton(DriveScopes.DRIVE_APPDATA));
                credential.setSelectedAccount(account.getAccount());

                Drive driveService = new Drive.Builder(
                        AndroidHttp.newCompatibleTransport(),
                        new GsonFactory(),
                        credential)
                        .setApplicationName("iOS Personal Finance Tracker")
                        .build();

                // Search inside appDataFolder only
                FileList files = driveService.files().list()
                        .setSpaces("appDataFolder")
                        .setQ("name = '" + BACKUP_FILENAME + "' and trashed = false")
                        .setFields("files(id, name, modifiedTime)")
                        .execute();

                repository.getDb().metadata.lastSyncedAt = Instant.now().toString();
                repository.saveDatabase();

                FileContent mediaContent = new FileContent("application/json", repository.getDbFile());

                if (files.getFiles().isEmpty()) {
                    // Create new file inside hidden appDataFolder
                    File fileMetadata = new File();
                    fileMetadata.setName(BACKUP_FILENAME);
                    fileMetadata.setParents(Collections.singletonList("appDataFolder"));
                    driveService.files().create(fileMetadata, mediaContent).setFields("id").execute();
                } else {
                    // Update existing file in appDataFolder
                    String fileId = files.getFiles().get(0).getId();
                    File fileMetadata = new File();
                    fileMetadata.setName(BACKUP_FILENAME);
                    driveService.files().update(fileId, fileMetadata, mediaContent).execute();
                }

                mainHandler.post(() -> callback.onSyncResult(true, "Synced at " + repository.getDb().metadata.lastSyncedAt));
            } catch (Exception e) {
                e.printStackTrace();
                mainHandler.post(() -> callback.onSyncResult(false, "Sync error: " + e.getMessage()));
            }
        });
    }
}
