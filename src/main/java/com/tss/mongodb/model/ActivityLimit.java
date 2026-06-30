package com.tss.mongodb.model;

import org.springframework.data.mongodb.core.mapping.Field;

public class ActivityLimit {

    @Field("activity_name")
    private String activityName;

    @Field("limit")
    private Integer limit;

    public ActivityLimit() {
    }

    public ActivityLimit(String activityName, Integer limit) {
        this.activityName = activityName;
        this.limit = limit;
    }

    public String getActivityName() {
        return activityName;
    }

    public void setActivityName(String activityName) {
        this.activityName = activityName;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }
}
