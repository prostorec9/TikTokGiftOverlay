package com.tiktok.giftoverlay.network;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.tiktok.giftoverlay.model.GiftDictionary;
import com.tiktok.giftoverlay.model.GiftEvent;
import okhttp3.*;
import java.util.concurrent.TimeUnit;

public class TikTokWebSocketClient {

    private static final String TAG = "TikTokWS";

    // ← твой сервер на Render
    private static final String PROXY_SERVER = "wss://tiktok-gift-proxy.onrender.com";

    private OkHttpClient httpClient;
    private WebSocket webSocket;
    private GiftCallback callback;
    private boolean isConnected = false;
    private int reconnectAttempts = 0;
    private String lastUsername;
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
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .pingInterval(25, TimeUnit.SECONDS)
                .build();
    }

    public void connect(String tiktokUsername) {
        this.lastUsername = tiktokUsername;
        Log.i(TAG, "Connecting via proxy to @" + tiktokUsername);

        String wsUrl = PROXY_SERVER + "/ws?username=" + tiktokUsername;

        Request request = new Request.Builder()
                .url(wsUrl)
                .build();

        webSocket = httpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket ws, Response response) {
                Log.i(TAG, "Proxy connected!");
            }

            @Override
            public void onMessage(WebSocket ws, String text) {
                try {
                    JsonObject json = new Gson().fromJson(text, JsonObject.class);
                    String type = json.has("type") ? json.get("type").getAsString() : "";

                    switch (type) {
                        case "connected":
                            isConnected = true;
                            reconnectAttempts = 0;
                            String uname = json.has("username") ? json.get("username").getAsString() : tiktokUsername;
                            mainHandler.post(() -> {
                                if (callback != null) callback.onConnected(uname);
                            });
                            break;

                        case "gift":
                            String nickname = json.has("nickname") ? json.get("nickname").getAsString() : "Аноним";
                            String username = json.has("username") ? json.get("username").getAsString() : "";
                            String avatarUrl = json.has("avatarUrl") ? json.get("avatarUrl").getAsString() : "";
                            String giftName = json.has("giftName") ? json.get("giftName").getAsString() : "Gift";
                            long giftId = json.has("giftId") ? json.get("giftId").getAsLong() : 5655L;
                            int giftCount = json.has("giftCount") ? json.get("giftCount").getAsInt() : 1;
                            String giftImageUrl = GiftDictionary.getGiftImageUrl(giftId);

                            final GiftEvent gift = new GiftEvent(
                                    nickname, username, avatarUrl,
                                    giftName, giftImageUrl, giftCount
                            );
                            mainHandler.post(() -> {
                                if (callback != null) callback.onGiftReceived(gift);
                            });
                            break;

                        case "error":
                            String msg = json.has("message") ? json.get("message").getAsString() : "Ошибка";
                            mainHandler.post(() -> {
                                if (callback != null) callback.onError(msg);
                            });
                            break;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Parse error: " + e.getMessage());
                }
            }

            @Override
            public void onFailure(WebSocket ws, Throwable t, Response response) {
                isConnected = false;
                Log.e(TAG, "WS failure: " + t.getMessage());
                scheduleReconnect(tiktokUsername);
            }

            @Override
            public void onClosed(WebSocket ws, int code, String reason) {
                isConnected = false;
                scheduleReconnect(tiktokUsername);
            }
        });
    }

    private void scheduleReconnect(String username) {
        if (reconnectAttempts >= 10) {
            mainHandler.post(() -> {
                if (callback != null) callback.onError("Потеряно соединение с сервером.");
            });
            return;
        }
        reconnectAttempts++;
        long delay = Math.min(3000L * reconnectAttempts, 30000L);
        mainHandler.postDelayed(() -> connect(username), delay);
    }

    public void disconnect() {
        isConnected = false;
        if (webSocket != null) {
            webSocket.close(1000, "disconnect");
            webSocket = null;
        }
    }

    public boolean isConnected() { return isConnected; }
}
