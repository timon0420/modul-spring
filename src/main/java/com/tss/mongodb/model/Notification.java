package com.tss.mongodb.model;

import java.util.Date;
import org.springframework.data.mongodb.core.mapping.Field;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Notification {

    private String id;

    @Field("type")
    private String type;

    @Field("message")
    private String message;

    @Field("created_at")
    @JsonProperty("created_at")
    private Date createdAt;

    @Field("read")
    private boolean read;

    @Field("limit_key")
    @JsonProperty("limit_key")
    private String limitKey;

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

    public String getLimitKey() {
        return limitKey;
    }

    public void setLimitKey(String limitKey) {
        this.limitKey = limitKey;
    }
}
