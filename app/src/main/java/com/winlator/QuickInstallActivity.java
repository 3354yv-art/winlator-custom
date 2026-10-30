package com.winlator;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.winlator.core.PreloaderDialog;

/**
 * Simple home screen: drop a Windows file (or tap to choose one) and it gets installed and run.
 */
public class QuickInstallActivity extends Activity {
    private static final int PICK_FILE_REQUEST_CODE = 100;
    private PreloaderDialog preloaderDialog;
    private LinearLayout dropZone;
    private boolean busy = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!WindowsFileInstaller.isSystemReady(this)) {
            // First run: the original screen installs the system files
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        preloaderDialog = new PreloaderDialog(this);
        setContentView(buildContent());
    }

    private View buildContent() {
        final float density = getResources().getDisplayMetrics().density;
        int pad = (int)(24 * density);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.parseColor("#0F2A4A"));
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextColor(Color.WHITE);
        title.setTextSize(28);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        dropZone = new LinearLayout(this);
        dropZone.setOrientation(LinearLayout.VERTICAL);
        dropZone.setGravity(Gravity.CENTER);
        dropZone.setPadding(pad, pad * 2, pad, pad * 2);
        setDropZoneBackground(false);

        TextView plus = new TextView(this);
        plus.setText("+");
        plus.setTextColor(Color.parseColor("#2ECC71"));
        plus.setTextSize(64);
        plus.setGravity(Gravity.CENTER);
        dropZone.addView(plus);

        TextView hint = new TextView(this);
        hint.setText(R.string.drop_file_here);
        hint.setTextColor(Color.WHITE);
        hint.setTextSize(20);
        hint.setGravity(Gravity.CENTER);
        dropZone.addView(hint);

        TextView sub = new TextView(this);
        sub.setText(R.string.supported_file_types);
        sub.setTextColor(Color.parseColor("#9FB4CC"));
        sub.setTextSize(14);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, (int)(8 * density), 0, 0);
        dropZone.addView(sub);

        LinearLayout.LayoutParams zoneParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        zoneParams.topMargin = pad;
        zoneParams.bottomMargin = pad;
        root.addView(dropZone, zoneParams);

        dropZone.setOnClickListener((v) -> pickFile());
        root.setOnDragListener(this::onDrag);
        dropZone.setOnDragListener(this::onDrag);

        Button choose = new Button(this);
        choose.setText(R.string.choose_file);
        choose.setTextSize(18);
        choose.setOnClickListener((v) -> pickFile());
        root.addView(choose, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int)(60 * density)));

        Button advanced = new Button(this);
        advanced.setText(R.string.advanced_mode);
        advanced.setAllCaps(false);
        advanced.setBackgroundColor(Color.TRANSPARENT);
        advanced.setTextColor(Color.parseColor("#9FB4CC"));
        advanced.setOnClickListener((v) -> startActivity(new Intent(this, MainActivity.class)));
        LinearLayout.LayoutParams advParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        advParams.topMargin = (int)(8 * density);
        root.addView(advanced, advParams);

        return root;
    }

    private void setDropZoneBackground(boolean highlighted) {
        float density = getResources().getDisplayMetrics().density;
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(20 * density);
        bg.setColor(Color.parseColor(highlighted ? "#1D4A7A" : "#14385F"));
        bg.setStroke((int)(3 * density), Color.parseColor(highlighted ? "#2ECC71" : "#4A78A8"), 18 * density, 10 * density);
        dropZone.setBackground(bg);
    }

    private boolean onDrag(View view, DragEvent event) {
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                return !busy && event.getClipDescription() != null;
            case DragEvent.ACTION_DRAG_ENTERED:
                setDropZoneBackground(true);
                return true;
            case DragEvent.ACTION_DRAG_EXITED:
            case DragEvent.ACTION_DRAG_ENDED:
                setDropZoneBackground(false);
                return true;
            case DragEvent.ACTION_DROP:
                setDropZoneBackground(false);
                ClipData clipData = event.getClipData();
                if (clipData == null || clipData.getItemCount() == 0) return false;
                Uri uri = clipData.getItemAt(0).getUri();
                if (uri == null) {
                    Toast.makeText(this, R.string.drop_not_supported, Toast.LENGTH_LONG).show();
                    return false;
                }
                try {
                    requestDragAndDropPermissions(event);
                }
                catch (Exception e) {}
                runFile(uri);
                return true;
        }
        return true;
    }

    private void pickFile() {
        if (busy) return;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, PICK_FILE_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_FILE_REQUEST_CODE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            runFile(data.getData());
        }
    }

    private void runFile(Uri uri) {
        if (busy) return;
        busy = true;
        WindowsFileInstaller.installAndRun(this, uri, preloaderDialog, (launched) -> busy = false);
    }
}
