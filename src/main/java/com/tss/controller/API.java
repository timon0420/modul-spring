package com.tss.controller;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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

    @GetMapping("/all_activities")
    public List<UserActivity> getAllActivities() {
        return activityRepo.findAll();
    }

    @GetMapping("/activities")
    public UserActivity getActivityByLogin(Principal principal) {
        return activityRepo.findByLogin(principal.getName()).orElseThrow(() -> new RuntimeException("Nie znaleziono danych"));    
    }

    @PostMapping("/activities/add")
    public String addActivity(@RequestBody Activity newActivity, Principal principal) {
        String login = principal.getName();
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

    @DeleteMapping("/activities/delete/{id}")
    public ResponseEntity<?> deleteActivity(@PathVariable String id, Principal principal) {
        String login = principal.getName();

        UserActivity userActivity = activityRepo.findByLogin(login).orElseThrow(() -> new RuntimeException("Nie znaleziono użytkownika w MongoDB"));

        boolean removed = userActivity.getActivities().removeIf(activity -> activity.getId().equals(id));

        if (!removed) {
            throw new RuntimeException("Nie znaleziono aktywności do usuniecia");
        } else {
            activityRepo.save(userActivity);
        }
        
        return new ResponseEntity<>("Activity deleted", HttpStatus.OK);
    }

    @PutMapping("/activities/edit/{id}")
    public ResponseEntity<?> editActivity(@PathVariable String id, @RequestBody Activity editedActivity, Principal principal) {
        String login = principal.getName();

        UserActivity userActivity = activityRepo.findByLogin(login).orElseThrow(() -> new RuntimeException("Nie znaleziono użytkownika w MongoDB"));
        Activity activity = userActivity.getActivities().stream().filter(a -> a.getId().equals(id)).findFirst().orElseThrow(() -> new RuntimeException("Nie znaleziono aktywnosci"));

        activity.setActivity_name(editedActivity.getActivity_name());
        activity.setActivity_description(editedActivity.getActivity_description());
        activity.setStart_time(editedActivity.getStart_time());
        Object rawTime = editedActivity.getTime(); 
        if (rawTime instanceof Number) {
            activity.setTime((Number) rawTime);
        } else if (rawTime != null) {
            activity.setTime(Integer.parseInt(rawTime.toString()));
        }
        activity.setDate(editedActivity.getDate());
        activityRepo.save(userActivity);

        return new ResponseEntity<>("Activity edited", HttpStatus.OK);
    }
}
