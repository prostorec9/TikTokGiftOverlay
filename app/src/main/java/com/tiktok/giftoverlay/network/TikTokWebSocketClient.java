package com.tiktok.giftoverlay.network;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.tiktok.giftoverlay.model.GiftDictionary;
import com.tiktok.giftoverlay.model.GiftEvent;
import okhttp3.*;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class TikTokWebSocketClient {

    private static final String TAG = "TikTokWS";
    private OkHttpClient httpClient;
    private WebSocket webSocket;
    private GiftCallback callback;
    private boolean isConnected = false;
    private int reconnectAttempts = 0;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface GiftCallback {
        void onGiftReceived(GiftEvent gift);
        void onConnected(String username);
        void onDisconnected();
        void onError(String message);
    }

    public TikTokWebSocketClient(GiftCallback callback) {
        this.callback = callback;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .pingInterval(25, TimeUnit.SECONDS)
                .followRedirects(true)
                .build();
    }

    public void connect(String tiktokUsername) {
        Log.i(TAG, "Connecting to @" + tiktokUsername);
        fetchRoomIdFromPage(tiktokUsername);
    }

    private void fetchRoomIdFromPage(String username) {
        String url = "https://www.tiktok.com/@" + username + "/live";
        Request request = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 Mobile/15E148 Safari/604.1")
                .addHeader("Accept-Language", "en-US,en;q=0.5")
                .addHeader("Referer", "https://www.tiktok.com/")
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                tryApiMethod(username);
            }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String html = response.body().string();
                String roomId = extractRoomIdFromHtml(html);
                if (roomId != null) {
                    connectToWebSocket(username, roomId);
                } else {
                    tryApiMethod(username);
                }
            }
        });
    }

    private void tryApiMethod(String username) {
        String url = "https://webcast.tiktok.com/webcast/room/info/?aid=1988&app_name=tiktok_web&unique_id=" + username;
        Request request = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36")
                .addHeader("Referer", "https://www.tiktok.com/")
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                notifyError("Не удалось подключиться. Проверьте username и интернет.");
            }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    String body = response.body().string();
                    JsonObject json = new Gson().fromJson(body, JsonObject.class);
                    if (json.has("data")) {
                        JsonObject data = json.getAsJsonObject("data");
                        String roomId = null;
                        if (data.has("room_id")) roomId = data.get("room_id").getAsString();
                        else if (data.has("roomId")) roomId = data.get("roomId").getAsString();
                        if (roomId != null) { connectToWebSocket(username, roomId); return; }
                    }
                    notifyError("Стример @" + username + " сейчас не в эфире");
                } catch (Exception e) {
                    notifyError("Ошибка подключения: " + e.getMessage());
                }
            }
        });
    }

    private String extractRoomIdFromHtml(String html) {
        String[] patterns = {"\"roomId\":\"", "\"room_id\":\"", "\"liveRoomId\":\""};
        for (String pattern : patterns) {
            int idx = html.indexOf(pattern);
            if (idx != -1) {
                int start = idx + pattern.length();
                int end = html.indexOf("\"", start);
                if (end > start && end - start < 30) {
                    String roomId = html.substring(start, end);
                    if (roomId.matches("\\d+")) return roomId;
                }
            }
        }
        return null;
    }

    private void connectToWebSocket(String username, String roomId) {
        String wsUrl = "wss://webcast.tiktok.com/webcast/im/push/v2/?" +
                "app_name=tiktok_web&version_code=180800&webcast_sdk_version=1.3.0" +
                "&compress=gzip&device_platform=web&cookie_enabled=1" +
                "&screen_width=1080&screen_height=1920&browser_language=ru-RU" +
                "&aid=1988&live_id=12&did_rule=3&fetch_rule=1&identity=audience" +
                "&room_id=" + roomId + "&host=https://www.tiktok.com";

        Request request = new Request.Builder()
                .url(wsUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36")
                .addHeader("Origin", "https://www.tiktok.com")
                .addHeader("Referer", "https://www.tiktok.com/@" + username + "/live")
                .build();

        webSocket = httpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket ws, Response response) {
                isConnected = true;
                reconnectAttempts = 0;
                mainHandler.post(() -> { if (callback != null) callback.onConnected(username); });
            }
            @Override
            public void onMessage(WebSocket ws, String text) { parseTextMessage(text); }
            @Override
            public void onMessage(WebSocket ws, okio.ByteString bytes) {
                try {
                    String text = new String(bytes.toByteArray(), "UTF-8");
                    if (text.contains("giftId") || text.contains("gift_id")) parseTextMessage(text);
                } catch (Exception ignored) {}
            }
            @Override
            public void onFailure(WebSocket ws, Throwable t, Response response) {
                isConnected = false;
                scheduleReconnect(username);
            }
            @Override
            public void onClosed(WebSocket ws, int code, String reason) {
                isConnected = false;
                scheduleReconnect(username);
            }
        });
    }

    private void parseTextMessage(String text) {
        try {
            JsonObject json = new Gson().fromJson(text, JsonObject.class);
            if (json.has("data")) {
                JsonElement dataEl = json.get("data");
                if (dataEl.isJsonArray()) {
                    for (JsonElement el : dataEl.getAsJsonArray()) processEvent(el.getAsJsonObject());
                } else if (dataEl.isJsonObject()) processEvent(dataEl.getAsJsonObject());
            }
            if (json.has("giftId") || json.has("gift_id")) processEvent(json);
        } catch (Exception ignored) {}
    }

    private void processEvent(JsonObject event) {
        try {
            boolean isGift = event.has("giftId") || event.has("gift_id") ||
                    (event.has("type") && event.get("type").getAsString().contains("gift"));
            if (!isGift) return;

            String nickname = "Аноним", username = "", avatarUrl = "";
            long giftId = 5655L;
            int count = 1;

            JsonObject user = event.has("user") ? event.getAsJsonObject("user") :
                              event.has("sender") ? event.getAsJsonObject("sender") : null;
            if (user != null) {
                if (user.has("nickname")) nickname = user.get("nickname").getAsString();
                if (user.has("uniqueId")) username = user.get("uniqueId").getAsString();
                if (user.has("avatarThumb") && user.get("avatarThumb").isJsonObject()) {
                    JsonObject av = user.getAsJsonObject("avatarThumb");
                    if (av.has("urlList") && av.getAsJsonArray("urlList").size() > 0)
                        avatarUrl = av.getAsJsonArray("urlList").get(0).getAsString();
                }
            }
            if (event.has("giftId")) giftId = event.get("giftId").getAsLong();
            else if (event.has("gift_id")) giftId = event.get("gift_id").getAsLong();
            if (event.has("repeatCount")) count = event.get("repeatCount").getAsInt();

            GiftDictionary.GiftInfo giftInfo = GiftDictionary.getGift(giftId);
            final GiftEvent gift = new GiftEvent(nickname, username, avatarUrl,
                    giftInfo.name, GiftDictionary.getGiftImageUrl(giftId), count);
            mainHandler.post(() -> { if (callback != null) callback.onGiftReceived(gift); });
        } catch (Exception ignored) {}
    }

    private void notifyError(String msg) {
        mainHandler.post(() -> { if (callback != null) callback.onError(msg); });
    }

    private void scheduleReconnect(String username) {
        if (reconnectAttempts >= 10) { notifyError("Потеряно соединение."); return; }
        reconnectAttempts++;
        mainHandler.postDelayed(() -> connect(username), Math.min(3000L * reconnectAttempts, 30000L));
    }

    public void disconnect() {
        isConnected = false;
        if (webSocket != null) { webSocket.close(1000, "disconnect"); webSocket = null; }
    }

    public boolean isConnected() { return isConnected; }
}
