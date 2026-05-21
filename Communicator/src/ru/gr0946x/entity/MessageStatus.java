package ru.gr0946x.entity;

public enum MessageStatus {
    SENT("Отправлено"),
    DELIVERED("Доставлено"),
    READ("Прочитано");

    private final String displayName;

    MessageStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
