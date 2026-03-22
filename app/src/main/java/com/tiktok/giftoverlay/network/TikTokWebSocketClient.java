package com.tiktok.giftoverlay.network;

import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.tiktok.giftoverlay.model.GiftDictionary;
import com.tiktok.giftoverlay.model.GiftEvent;
import okhttp3.*;
import java.util.concurrent.TimeUnit;

/**
 * Подключается к TikTok LIVE через WebSocket
 * Получает события подарков в реальном времени
 */
public class TikTokWebSocketClient {

    private static final String TAG = "TikTokWS";

    // TikTok LIVE WebSocket endpoint
    private static final String TIKTOK_WS_URL =
            "wss://webcast.tiktok.com/webcast/im/push/v2/";

    private OkHttpClient httpClient;
    private WebSocket webSocket;
    private String roomId;
    private GiftCallback callback;
    private boolean isConnected = false;
    private int reconnectAttempts = 0;

    public interface GiftCallback {
        void onGiftReceived(GiftEvent gift);
        void onConnected(String username);
        void onDisconnected();
        void onError(String message);
    }

    public TikTokWebSocketClient(GiftCallback callback) {
        this.callback = callback;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .pingInterval(20, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Подключиться к комнате TikTok LIVE по юзернейму стримера
     */
    public void connect(String tiktokUsername) {
        // Сначала получаем roomId через HTTP запрос
        fetchRoomId(tiktokUsername);
    }

    private void fetchRoomId(String username) {
        String url = "https://www.tiktok.com/api/live/detail/?aid=1988&roomID=&uniqueId=" + username;

        Request request = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36")
                .addHeader("Referer", "https://www.tiktok.com/")
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, java.io.IOException e) {
                Log.e(TAG, "Failed to fetch room ID", e);
                // Пробуем альтернативный метод
                fetchRoomIdAlternative(username);
            }

            @Override
            public void onResponse(Call call, Response response) throws java.io.IOException {
                if (!response.isSuccessful()) {
                    fetchRoomIdAlternative(username);
                    return;
                }
                try {
                    String body = response.body().string();
                    JsonObject json = new Gson().fromJson(body, JsonObject.class);
                    if (json.has("LiveRoomInfo")) {
                        JsonObject roomInfo = json.getAsJsonObject("LiveRoomInfo");
                        roomId = roomInfo.get("roomId").getAsString();
                        connectWebSocket(username);
                    } else {
                        fetchRoomIdAlternative(username);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Parse error", e);
                    fetchRoomIdAlternative(username);
                }
            }
        });
    }

    private void fetchRoomIdAlternative(String username) {
        // Альтернативный endpoint
        String url = "https://www.tiktok.com/@" + username + "/live";

        Request request = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36")
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, java.io.IOException e) {
                if (callback != null) callback.onError("Не удалось найти стрим @" + username);
            }

            @Override
            public void onResponse(Call call, Response response) throws java.io.IOException {
                try {
                    String html = response.body().string();
                    // Ищем roomId в HTML странице
                    String marker = "\"roomId\":\"";
                    int idx = html.indexOf(marker);
                    if (idx != -1) {
                        int start = idx + marker.length();
                        int end = html.indexOf("\"", start);
                        roomId = html.substring(start, end);
                        connectWebSocket(username);
                    } else {
                        if (callback != null) callback.onError("Стример @" + username + " сейчас не в эфире");
                    }
                } catch (Exception e) {
                    if (callback != null) callback.onError("Ошибка подключения");
                }
            }
        });
    }

    private void connectWebSocket(String username) {
        if (roomId == null || roomId.isEmpty()) {
            if (callback != null) callback.onError("Room ID не найден");
            return;
        }

        String wsUrl = TIKTOK_WS_URL +
                "?aid=1988&app_name=tiktok_web&browser_language=ru&browser_platform=Android" +
                "&browser_version=Chrome%2F120&compress=gzip" +
                "&did_rule=3&fetch_rule=1&host=https%3A%2F%2Fwww.tiktok.com" +
                "&identity=audience&internal_ext=internal_src%3Awebcast" +
                "&live_id=12&room_id=" + roomId +
                "&screen_height=1920&screen_width=1080&tz_name=Europe%2FKiev" +
                "&update_version_code=1.3.0";

        Request request = new Request.Builder()
                .url(wsUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 12)")
                .addHeader("Origin", "https://www.tiktok.com")
                .addHeader("Referer", "https://www.tiktok.com/@" + username + "/live")
                .build();

        webSocket = httpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket ws, Response response) {
                isConnected = true;
                reconnectAttempts = 0;
                Log.i(TAG, "WebSocket connected!");
                if (callback != null) callback.onConnected(username);
            }

            @Override
            public void onMessage(WebSocket ws, String text) {
                parseMessage(text);
            }

            @Override
            public void onMessage(WebSocket ws, okio.ByteString bytes) {
                // TikTok присылает бинарные сообщения (protobuf)
                parseBinaryMessage(bytes.toByteArray());
            }

            @Override
            public void onFailure(WebSocket ws, Throwable t, Response response) {
                isConnected = false;
                Log.e(TAG, "WebSocket failure: " + t.getMessage());
                scheduleReconnect(username);
            }

            @Override
            public void onClosed(WebSocket ws, int code, String reason) {
                isConnected = false;
                if (callback != null) callback.onDisconnected();
                scheduleReconnect(username);
            }
        });
    }

    /**
     * Парсим JSON сообщение от TikTok
     */
    private void parseMessage(String text) {
        try {
            JsonObject json = new Gson().fromJson(text, JsonObject.class);
            if (json.has("data")) {
                JsonArray events = json.getAsJsonArray("data");
                for (JsonElement el : events) {
                    processEvent(el.getAsJsonObject());
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Parse error: " + e.getMessage());
        }
    }

    /**
     * Парсим бинарное сообщение (упрощённый вариант без protobuf)
     */
    private void parseBinaryMessage(byte[] bytes) {
        // Ищем JSON-подобные структуры внутри бинарных данных
        try {
            String text = new String(bytes, "UTF-8");
            // Ищем паттерн подарка
            if (text.contains("gift") || text.contains("Gift")) {
                parseMessage(text);
            }
        } catch (Exception e) {
            // ignore
        }
    }

    private void processEvent(JsonObject event) {
        try {
            String type = event.has("type") ? event.get("type").getAsString() : "";

            // Событие подарка
            if (type.equals("gift") || event.has("giftId")) {
                String nickname = "Аноним";
                String username2 = "";
                String avatarUrl = "";
                long giftId = 5655L; // Rose по умолчанию
                int count = 1;

                if (event.has("user")) {
                    JsonObject user = event.getAsJsonObject("user");
                    if (user.has("nickname")) nickname = user.get("nickname").getAsString();
                    if (user.has("uniqueId")) username2 = user.get("uniqueId").getAsString();
                    if (user.has("avatarThumb")) {
                        JsonObject avatar = user.getAsJsonObject("avatarThumb");
                        if (avatar.has("urlList")) {
                            JsonArray urls = avatar.getAsJsonArray("urlList");
                            if (urls.size() > 0) avatarUrl = urls.get(0).getAsString();
                        }
                    }
                }

                if (event.has("giftId")) giftId = event.get("giftId").getAsLong();
                if (event.has("repeatCount")) count = event.get("repeatCount").getAsInt();

                GiftDictionary.GiftInfo giftInfo = GiftDictionary.getGift(giftId);
                String giftImageUrl = GiftDictionary.getGiftImageUrl(giftId);

                GiftEvent gift = new GiftEvent(nickname, username2, avatarUrl,
                        giftInfo.name, giftImageUrl, count);

                if (callback != null) callback.onGiftReceived(gift);
            }
        } catch (Exception e) {
            Log.w(TAG, "Event parse error: " + e.getMessage());
        }
    }

    private void scheduleReconnect(String username) {
        if (reconnectAttempts >= 5) {
            if (callback != null) callback.onError("Потеряно соединение после 5 попыток");
            return;
        }
        reconnectAttempts++;
        long delay = Math.min(5000L * reconnectAttempts, 30000L);
        Log.i(TAG, "Reconnecting in " + delay + "ms (attempt " + reconnectAttempts + ")");

        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                () -> connect(username), delay);
    }

    public void disconnect() {
        isConnected = false;
        if (webSocket != null) {
            webSocket.close(1000, "User disconnected");
            webSocket = null;
        }
    }

    public boolean isConnected() {
        return isConnected;
    }
}
