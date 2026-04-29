package com.tss.controller;

import java.util.ArrayList;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tss.mongodb.model.Activity;
import com.tss.mongodb.model.UserActivity;
import com.tss.mongodb.repo.ActivityRepo;

@RestController
@RequestMapping("/api")
public class API {
    
    private final ActivityRepo activityRepo;

    public API(ActivityRepo activityRepo) {
        this.activityRepo = activityRepo;
    }

    @GetMapping("/activities")
    public List<UserActivity> getAllActivities() {
        return activityRepo.findAll();
    }

    @GetMapping("/activities/{login}")
    public UserActivity getActivityByLogin(@PathVariable String login) {
        return activityRepo.findByLogin(login).orElseThrow(() -> new RuntimeException("Nie znaleziono danych"));    
    }

    @PostMapping("/activities/{login}/add")
    public String addActivity(@PathVariable String login, @RequestBody Activity newActivity) {
        UserActivity userActivity = activityRepo.findByLogin(login).orElseGet(() -> {
            UserActivity newUA = new UserActivity();
            newUA.setLogin(login);
            newUA.setActivities(new ArrayList<>());
            return newUA;
        });

        if (newActivity.getId() == null) {
            newActivity.setId(java.util.UUID.randomUUID().toString());
        }

        userActivity.getActivities().add(newActivity);
        return activityRepo.save(userActivity).toString();
    }
}
