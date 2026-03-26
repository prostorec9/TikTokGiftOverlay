package com.tiktok.giftoverlay;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

public class CopyActivity extends Activity {

    public static final String EXTRA_USERNAME = "username_to_copy";

    public static Intent createIntent(Context context, String username) {
        Intent intent = new Intent(context, CopyActivity.class);
        intent.putExtra(EXTRA_USERNAME, username);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String username = getIntent().getStringExtra(EXTRA_USERNAME);
        if (username != null && !username.isEmpty()) {
            try {
                ClipboardManager cm = (ClipboardManager)
                    getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("username", "@" + username));
                    Toast.makeText(this, "@" + username + " copied!", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception ignored) {}
        }
        finish();
    }
}
