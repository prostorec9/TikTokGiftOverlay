package com.tiktok.giftoverlay.ui;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.widget.Toast;

public class CopyActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String textToCopy = getIntent().getStringExtra("text_to_copy");

        if (textToCopy != null && !textToCopy.isEmpty()) {
            ClipboardManager clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("TikTok Username", textToCopy);
            clipboardManager.setPrimaryClip(clip);
            
            Toast.makeText(this, "Скопировано: " + textToCopy, Toast.LENGTH_SHORT).show();
        }

        finish();
        overridePendingTransition(0, 0);
    }
}
