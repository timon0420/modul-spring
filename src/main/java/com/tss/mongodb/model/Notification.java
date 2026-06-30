package com.tss.mongodb.model;

import java.util.Date;
import org.springframework.data.mongodb.core.mapping.Field;

public class Notification {

    private String id;

    @Field("type")
    private String type;

    @Field("message")
    private String message;

    @Field("created_at")
    private Date createdAt;

    @Field("read")
    private boolean read;

    public Notification() {
        this.id = java.util.UUID.randomUUID().toString();
        this.createdAt = new Date();
        this.read = false;
    }

    public Notification(String type, String message) {
        this();
        this.type = type;
        this.message = message;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }
}
