package com.tiktok.giftoverlay.model;

public class GiftEvent {
    public String nickname;      // Отображаемое имя: "shun"
    public String username;      // @username: "shun_official"
    public String avatarUrl;     // URL аватарки пользователя
    public String giftName;      // Название подарка: "Rose", "Coral"
    public String giftImageUrl;  // URL картинки подарка
    public int giftCount;        // Количество подарков
    public long timestamp;

    public GiftEvent(String nickname, String username, String avatarUrl,
                     String giftName, String giftImageUrl, int giftCount) {
        this.nickname = nickname;
        this.username = username;
        this.avatarUrl = avatarUrl;
        this.giftName = giftName;
        this.giftImageUrl = giftImageUrl;
        this.giftCount = giftCount;
        this.timestamp = System.currentTimeMillis();
    }
}
