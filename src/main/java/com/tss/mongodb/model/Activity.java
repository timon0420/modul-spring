package com.tss.mongodb.model;

import java.util.Date;

import org.springframework.data.mongodb.core.mapping.Field;

public class Activity {

    private String id;

    @Field("activity_name")
    private String activity_name;

    @Field("activity_description")
    private String activity_description;

    @Field("start_time")
    private Object start_time;

    @Field("time")
    private Number time;

    @Field("date")
    private Date date;

    public Activity() {
        this.id = java.util.UUID.randomUUID().toString();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getActivity_name() {
        return activity_name;
    }

    public void setActivity_name(String activity_name) {
        this.activity_name = activity_name;
    }

    public String getActivity_description() {
        return activity_description;
    }

    public void setActivity_description(String activity_description) {
        this.activity_description = activity_description;
    }

    public Object getStart_time() {
        return start_time == null ? "" : start_time.toString();
    }

    public void setStart_time(Object starTime) {
        this.start_time = starTime;
    }

    public Object getTime() {
        return time;
    }

    public void setTime(Number time) {
        this.time = time;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }
}
