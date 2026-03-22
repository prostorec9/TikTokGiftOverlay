package com.tiktok.giftoverlay;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.tiktok.giftoverlay.service.GiftOverlayService;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_OVERLAY_PERMISSION = 1001;

    private EditText usernameInput;
    private Button startButton;
    private Button stopButton;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        usernameInput = findViewById(R.id.username_input);
        startButton = findViewById(R.id.start_button);
        stopButton = findViewById(R.id.stop_button);
        statusText = findViewById(R.id.status_text);

        // Загружаем сохранённый юзернейм
        SharedPreferences prefs = getSharedPreferences("gift_overlay", MODE_PRIVATE);
        String savedUsername = prefs.getString("username", "");
        usernameInput.setText(savedUsername);

        startButton.setOnClickListener(v -> handleStart());
        stopButton.setOnClickListener(v -> handleStop());

        updateUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUI();
    }

    private void handleStart() {
        String username = usernameInput.getText().toString().trim();
        // Убираем @ если пользователь добавил
        if (username.startsWith("@")) username = username.substring(1);

        if (TextUtils.isEmpty(username)) {
            Toast.makeText(this, "Введите @username TikTok стримера", Toast.LENGTH_SHORT).show();
            return;
        }

        // Проверяем разрешение на overlay
        if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermission();
            return;
        }

        // Запускаем сервис
        Intent intent = new Intent(this, GiftOverlayService.class);
        intent.setAction(GiftOverlayService.ACTION_START);
        intent.putExtra(GiftOverlayService.EXTRA_USERNAME, username);

        // Сохраняем что сервис был активен
        getSharedPreferences("gift_overlay", MODE_PRIVATE)
                .edit()
                .putString("username", username)
                .putBoolean("was_active", true)
                .apply();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }

        Toast.makeText(this, "Подключение к @" + username + "...", Toast.LENGTH_SHORT).show();
        updateUI();
    }

    private void handleStop() {
        Intent intent = new Intent(this, GiftOverlayService.class);
        intent.setAction(GiftOverlayService.ACTION_STOP);
        startService(intent);

        getSharedPreferences("gift_overlay", MODE_PRIVATE)
                .edit()
                .putBoolean("was_active", false)
                .apply();

        updateUI();
        Toast.makeText(this, "Overlay остановлен", Toast.LENGTH_SHORT).show();
    }

    private void requestOverlayPermission() {
        Toast.makeText(this,
                "Нужно разрешение «Поверх других приложений»",
                Toast.LENGTH_LONG).show();

        Intent intent = new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName())
        );
        startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_OVERLAY_PERMISSION) {
            if (Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Разрешение получено! Нажмите СТАРТ", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Разрешение не дано — overlay не будет работать", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void updateUI() {
        boolean running = GiftOverlayService.isRunning;
        startButton.setEnabled(!running);
        stopButton.setEnabled(running);

        if (running) {
            statusText.setText("🟢 Работает — подарки отображаются");
            statusText.setTextColor(getColor(R.color.green));
        } else {
            statusText.setText("⚫ Остановлен");
            statusText.setTextColor(getColor(R.color.gray));
        }

        // Проверяем разрешение
        if (!Settings.canDrawOverlays(this)) {
            statusText.setText("⚠️ Нужно разрешение «Поверх других приложений»");
            statusText.setTextColor(getColor(R.color.orange));
        }
    }
}
