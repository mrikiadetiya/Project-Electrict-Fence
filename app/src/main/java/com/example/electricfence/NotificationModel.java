package com.example.electricfence;

public class NotificationModel {
    private String title;
    private String message;
    private String timestamp;
    private String type; // ERROR, WARNING, INFO

    public NotificationModel() {}

    public NotificationModel(String title, String message, String timestamp, String type) {
        this.title = title;
        this.message = message;
        this.timestamp = timestamp;
        this.type = type;
    }

    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public String getTimestamp() { return timestamp; }
    public String getType() { return type; }
}