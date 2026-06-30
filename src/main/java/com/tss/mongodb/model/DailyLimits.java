package com.tss.mongodb.model;

import java.util.List;
import org.springframework.data.mongodb.core.mapping.Field;

public class DailyLimits {

    @Field("global_limit")
    private Integer globalLimit;

    @Field("activities")
    private List<ActivityLimit> activities;

    public DailyLimits() {
    }

    public DailyLimits(Integer globalLimit, List<ActivityLimit> activities) {
        this.globalLimit = globalLimit;
        this.activities = activities;
    }

    public Integer getGlobalLimit() {
        return globalLimit;
    }

    public void setGlobalLimit(Integer globalLimit) {
        this.globalLimit = globalLimit;
    }

    public List<ActivityLimit> getActivities() {
        return activities;
    }

    public void setActivities(List<ActivityLimit> activities) {
        this.activities = activities;
    }
}
