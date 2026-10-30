package com.winlator;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.widget.Toast;

import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.xenvironment.RootFS;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

/**
 * Receives a Windows file (exe / msi / bat ...) from "Open with" or "Share",
 * copies it into the first container (C:\Downloads) and runs it there.
 */
public class OpenFileActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        Uri uri = intent.getData();
        if (uri == null) uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (uri == null) {
            finish();
            return;
        }

        if (!RootFS.find(this).isValid()) {
            Toast.makeText(this, R.string.open_app_first, Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        ContainerManager containerManager = new ContainerManager(this);
        ArrayList<Container> containers = containerManager.getContainers();
        if (containers.isEmpty()) {
            Toast.makeText(this, R.string.create_container_first, Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        final Container container = containers.get(0);
        final Uri sourceUri = uri;
        Toast.makeText(this, R.string.copying_file_to_container, Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            File target = copyToContainer(sourceUri, container);
            runOnUiThread(() -> {
                if (target == null) {
                    Toast.makeText(this, R.string.unable_to_open_file, Toast.LENGTH_LONG).show();
                }
                else {
                    Intent launch = new Intent(this, XServerDisplayActivity.class);
                    launch.putExtra("container_id", container.id);
                    launch.putExtra("exec_path", target.getPath());
                    startActivity(launch);
                }
                finish();
            });
        }).start();
    }

    private File copyToContainer(Uri uri, Container container) {
        try {
            File downloadDir = new File(container.getRootDir(), ".wine/drive_c/Downloads");
            if (!downloadDir.isDirectory() && !downloadDir.mkdirs()) return null;

            File target = new File(downloadDir, getSafeFileName(uri));
            ContentResolver resolver = getContentResolver();
            try (InputStream inStream = resolver.openInputStream(uri);
                 OutputStream outStream = new FileOutputStream(target)) {
                if (inStream == null) return null;
                byte[] buffer = new byte[256 * 1024];
                int length;
                while ((length = inStream.read(buffer)) > 0) outStream.write(buffer, 0, length);
            }
            target.setReadable(true, false);
            target.setExecutable(true, false);
            return target;
        }
        catch (Exception e) {
            return null;
        }
    }

    private String getSafeFileName(Uri uri) {
        String name = null;
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) name = cursor.getString(0);
            }
            catch (Exception e) {}
        }
        if (name == null || name.isEmpty()) name = uri.getLastPathSegment();
        if (name == null || name.isEmpty()) name = "setup.exe";

        int slash = name.lastIndexOf('/');
        if (slash != -1) name = name.substring(slash + 1);

        // Keep only characters that are safe for Windows paths and for the launcher
        name = name.replaceAll("[^A-Za-z0-9._-]", "_");
        if (!name.contains(".")) name += ".exe";
        return name;
    }
}
