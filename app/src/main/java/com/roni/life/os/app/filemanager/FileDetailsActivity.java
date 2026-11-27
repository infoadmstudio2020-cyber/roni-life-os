package com.roni.life.os.app.filemanager;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.roni.library.contracts.filemanager.FileEntry;
import com.roni.library.platform.PlatformEngine;
import com.roni.library.platform.filemanager.FileManagerException;
import com.roni.library.platform.filemanager.FileManagerService;
import com.roni.life.os.app.R;

import java.io.File;
import java.text.DateFormat;
import java.util.Date;

/** File details with open, share and delete actions. */
public final class FileDetailsActivity extends AppCompatActivity {

    public static final String EXTRA_PATH = "extra_file_path";

    private FileManagerService service;
    private FileEntry entry;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_details);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Details");
        }
        toolbar.setNavigationOnClickListener(view -> finish());

        service = PlatformEngine.getInstance().getFileManagerService();
        String path = getIntent().getStringExtra(EXTRA_PATH);
        TextView icon = findViewById(R.id.detail_icon);
        TextView name = findViewById(R.id.detail_name);
        TextView meta = findViewById(R.id.detail_meta);
        TextView pathView = findViewById(R.id.detail_path);

        if (service == null || path == null) {
            name.setText("File unavailable");
            return;
        }
        try {
            entry = service.getFileInfo(path);
        } catch (FileManagerException error) {
            name.setText(error.getMessage());
            return;
        }
        icon.setText(FileEntryAdapter.iconFor(entry));
        name.setText(entry.getName());
        String date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(new Date(entry.getModifiedAt()));
        meta.setText("Type: " + entry.getMimeType()
                + "\nSize: " + FileEntryAdapter.formatSize(entry.getSizeBytes())
                + "\nModified: " + date
                + "\nCategory: " + entry.getCategory());
        pathView.setText("Path: /" + entry.getRelativePath());

        findViewById(R.id.detail_open).setOnClickListener(view -> open());
        findViewById(R.id.detail_share).setOnClickListener(view -> share());
        findViewById(R.id.detail_delete).setOnClickListener(view -> {
            try {
                service.deleteOne(entry.getRelativePath());
                finish();
            } catch (FileManagerException error) {
                Toast.makeText(this, error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void open() {
        try {
            File file = service.resolveForSharing(entry.getRelativePath());
            if (file == null || !file.exists()) {
                Toast.makeText(this, "File is not available", Toast.LENGTH_SHORT).show();
                return;
            }
            Uri uri = androidx.core.content.FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, entry.getMimeType());
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Open with"));
        } catch (FileManagerException error) {
            Toast.makeText(this, error.getMessage(), Toast.LENGTH_SHORT).show();
        } catch (android.content.ActivityNotFoundException error) {
            Toast.makeText(this, "No app can open this file", Toast.LENGTH_SHORT).show();
        }
    }

    private void share() {
        try {
            File file = service.resolveForSharing(entry.getRelativePath());
            if (file == null || !file.exists()) {
                Toast.makeText(this, "File is not available", Toast.LENGTH_SHORT).show();
                return;
            }
            Uri uri = androidx.core.content.FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType(entry.getMimeType());
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Share"));
        } catch (FileManagerException error) {
            Toast.makeText(this, error.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
