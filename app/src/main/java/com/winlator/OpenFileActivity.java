package com.winlator;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import com.winlator.core.PreloaderDialog;

/**
 * Receives a Windows file (exe / msi / bat ...) from "Open with" or "Share" and runs it.
 */
public class OpenFileActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        Uri uri = intent.getData();
        if (uri == null) uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);

        WindowsFileInstaller.installAndRun(this, uri, new PreloaderDialog(this), (launched) -> finish());
    }
}
