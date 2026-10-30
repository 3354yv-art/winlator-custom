package com.winlator;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.widget.Toast;

import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.core.PreloaderDialog;
import com.winlator.xenvironment.RootFS;
import com.winlator.xenvironment.RootFSInstaller;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

/**
 * Shared logic for "give me a Windows file and run it":
 * makes sure a container exists (creating a default one if needed),
 * copies the file to C:\Downloads inside it and starts it.
 */
public abstract class WindowsFileInstaller {
    public interface Done {
        void onDone(boolean launched);
    }

    public static boolean isSystemReady(Activity activity) {
        RootFS rootFS = RootFS.find(activity);
        return rootFS.isValid() && rootFS.getVersion() >= RootFSInstaller.LATEST_VERSION;
    }

    public static void installAndRun(final Activity activity, final Uri uri, final PreloaderDialog dialog, final Done done) {
        if (uri == null) {
            if (done != null) done.onDone(false);
            return;
        }

        if (!isSystemReady(activity)) {
            Toast.makeText(activity, R.string.open_app_first, Toast.LENGTH_LONG).show();
            activity.startActivity(new Intent(activity, MainActivity.class));
            if (done != null) done.onDone(false);
            return;
        }

        final ContainerManager manager = new ContainerManager(activity);
        ArrayList<Container> containers = manager.getContainers();
        if (!containers.isEmpty()) {
            copyAndLaunch(activity, uri, containers.get(0), done);
            return;
        }

        // First time: build a default container automatically
        if (dialog != null) dialog.show(R.string.preparing_environment);
        try {
            JSONObject data = new JSONObject();
            data.put("name", "Container-1");
            data.put("screenSize", Container.DEFAULT_SCREEN_SIZE);
            data.put("envVars", Container.DEFAULT_ENV_VARS);
            data.put("cpuList", Container.getFallbackCPUList());
            data.put("cpuListWoW64", Container.getFallbackCPUList());
            data.put("dxwrapper", Container.DEFAULT_DXWRAPPER);
            data.put("audioDriver", Container.DEFAULT_AUDIO_DRIVER);
            data.put("wincomponents", Container.DEFAULT_WINCOMPONENTS);
            data.put("drives", Container.DEFAULT_DRIVES);
            manager.createContainerAsync(data, (container) -> {
                if (dialog != null) dialog.close();
                if (container == null) {
                    Toast.makeText(activity, R.string.unable_to_open_file, Toast.LENGTH_LONG).show();
                    if (done != null) done.onDone(false);
                }
                else copyAndLaunch(activity, uri, container, done);
            });
        }
        catch (Exception e) {
            if (dialog != null) dialog.close();
            Toast.makeText(activity, R.string.unable_to_open_file, Toast.LENGTH_LONG).show();
            if (done != null) done.onDone(false);
        }
    }

    private static void copyAndLaunch(final Activity activity, final Uri uri, final Container container, final Done done) {
        Toast.makeText(activity, R.string.copying_file_to_container, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            final File target = copyToContainer(activity, uri, container);
            activity.runOnUiThread(() -> {
                if (target == null) {
                    Toast.makeText(activity, R.string.unable_to_open_file, Toast.LENGTH_LONG).show();
                }
                else {
                    Intent launch = new Intent(activity, XServerDisplayActivity.class);
                    launch.putExtra("container_id", container.id);
                    launch.putExtra("exec_path", target.getPath());
                    activity.startActivity(launch);
                }
                if (done != null) done.onDone(target != null);
            });
        }).start();
    }

    private static File copyToContainer(Activity activity, Uri uri, Container container) {
        try {
            File downloadDir = new File(container.getRootDir(), ".wine/drive_c/Downloads");
            if (!downloadDir.isDirectory() && !downloadDir.mkdirs()) return null;

            File target = new File(downloadDir, getSafeFileName(activity, uri));
            try (InputStream inStream = activity.getContentResolver().openInputStream(uri);
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

    private static String getSafeFileName(Activity activity, Uri uri) {
        String name = null;
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = activity.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) name = cursor.getString(0);
            }
            catch (Exception e) {}
        }
        if (name == null || name.isEmpty()) name = uri.getLastPathSegment();
        if (name == null || name.isEmpty()) name = "setup.exe";

        int slash = name.lastIndexOf('/');
        if (slash != -1) name = name.substring(slash + 1);

        name = name.replaceAll("[^A-Za-z0-9._-]", "_");
        if (!name.contains(".")) name += ".exe";
        return name;
    }
}
