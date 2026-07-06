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
import com.tss.mongodb.model.DailyLimits;
import com.tss.mongodb.model.ActivityLimit;
import com.tss.mongodb.model.Notification;
import com.tss.mongodb.repo.ActivityRepo;
import com.tss.grpc.GrpcAnalysisClientService;
import com.tss.service.AnalysisNotificationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api")
@Tag(name = "Activity Management", description = "Endpoints for managing user activities, limits, and notifications")
public class API {

    private final ActivityRepo activityRepo;
    private final GrpcAnalysisClientService grpcAnalysisClientService;
    private final AnalysisNotificationService analysisNotificationService;

    public API(ActivityRepo activityRepo, GrpcAnalysisClientService grpcAnalysisClientService,
            AnalysisNotificationService analysisNotificationService) {
        this.activityRepo = activityRepo;
        this.grpcAnalysisClientService = grpcAnalysisClientService;
        this.analysisNotificationService = analysisNotificationService;
    }

    @Operation(summary = "Get all user activities", description = "Returns a list of all user activities", tags = {"Activity Management"})
    @ApiResponse(responseCode = "200", description = "List of user activities")
    @ApiResponse(responseCode = "404", description = "No user activities found")
    @ApiResponse(responseCode = "500", description = "Internal server error")
    @GetMapping("/all_activities")
    public List<UserActivity> getAllActivities() {
        return activityRepo.findAll();
    }

    @Operation(summary = "Get user activity by login", description = "Returns the activity of a user by their login", tags = {"Activity Management"})
    @ApiResponse(responseCode = "200", description = "User activity found")
    @ApiResponse(responseCode = "404", description = "User activity not found")
    @GetMapping("/activities")
    public UserActivity getActivityByLogin(Principal principal) {
        return activityRepo.findByLogin(principal.getName())
                .orElseThrow(() -> new RuntimeException("Nie znaleziono danych"));
    }

    @Operation(summary = "Add user activity", description = "Adds a new activity for the logged-in user", tags = {"Activity Management"})
    @ApiResponse(responseCode = "200", description = "Activity added")
    @ApiResponse(responseCode = "400", description = "Invalid activity data")
    @ApiResponse(responseCode = "404", description = "User activity not found")
    @ApiResponse(responseCode = "500", description = "Internal server error")
    @PostMapping("/activities/add")
    public String addActivity(@RequestBody Activity newActivity, Principal principal) {
        String login = principal.getName();
        UserActivity userActivity = activityRepo.findByLogin(login).orElseGet(() -> {
            UserActivity newUA = new UserActivity();
            newUA.setLogin(login);
            newUA.setActivities(new ArrayList<>());
            return newUA;
        });

        if (userActivity.getActivities() == null) {
            userActivity.setActivities(new ArrayList<>());
        }

        if (newActivity.getId() == null) {
            newActivity.setId(java.util.UUID.randomUUID().toString());
        }

        userActivity.getActivities().add(newActivity);
        UserActivity saved = activityRepo.save(userActivity);
        analyzeLimitsAndNotify(login);
        return saved.toString();
    }

    @Operation(summary = "Delete user activity", description = "Deletes an existing activity for the logged-in user", tags = {"Activity Management"})
    @ApiResponse(responseCode = "200", description = "Activity deleted")
    @ApiResponse(responseCode = "404", description = "Activity not found")
    @ApiResponse(responseCode = "500", description = "Internal server error")
    @DeleteMapping("/activities/delete/{id}")
    public ResponseEntity<?> deleteActivity(@PathVariable String id, Principal principal) {
        String login = principal.getName();

        UserActivity userActivity = activityRepo.findByLogin(login)
                .orElseThrow(() -> new RuntimeException("Nie znaleziono użytkownika w MongoDB"));

        boolean removed = userActivity.getActivities().removeIf(activity -> activity.getId().equals(id));

        if (!removed) {
            throw new RuntimeException("Nie znaleziono aktywności do usuniecia");
        } else {
            activityRepo.save(userActivity);
            analyzeLimitsAndNotify(login);
        }

        return new ResponseEntity<>("Activity deleted", HttpStatus.OK);
    }

    @Operation(summary = "Edit user activity", description = "Edits an existing activity for the logged-in user", tags = {"Activity Management"})
    @ApiResponse(responseCode = "200", description = "Activity edited")
    @ApiResponse(responseCode = "404", description = "Activity not found")
    @ApiResponse(responseCode = "500", description = "Internal server error")
    @PutMapping("/activities/edit/{id}")
    public ResponseEntity<?> editActivity(@PathVariable String id, @RequestBody Activity editedActivity,
            Principal principal) {
        String login = principal.getName();

        UserActivity userActivity = activityRepo.findByLogin(login)
                .orElseThrow(() -> new RuntimeException("Nie znaleziono użytkownika w MongoDB"));
        Activity activity = userActivity.getActivities().stream().filter(a -> a.getId().equals(id)).findFirst()
                .orElseThrow(() -> new RuntimeException("Nie znaleziono aktywnosci"));

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
        analyzeLimitsAndNotify(login);

        return new ResponseEntity<>("Activity edited", HttpStatus.OK);
    }

    @Operation(summary = "Set user activity limits", description = "Sets activity limits for the logged-in user", tags = {"Activity Management"})
    @ApiResponse(responseCode = "200", description = "Limits set")
    @ApiResponse(responseCode = "404", description = "User activity not found")
    @ApiResponse(responseCode = "500", description = "Internal server error")
    @PostMapping("/limits/set")
    public ResponseEntity<?> setLimits(@RequestBody DailyLimits limitsDto, Principal principal) {
        String login = principal.getName();
        UserActivity userActivity = activityRepo.findByLogin(login).orElseGet(() -> {
            UserActivity newUA = new UserActivity();
            newUA.setLogin(login);
            newUA.setActivities(new ArrayList<>());
            return newUA;
        });

        DailyLimits dailyLimits = userActivity.getDailyLimits();
        if (dailyLimits == null) {
            dailyLimits = new DailyLimits();
            dailyLimits.setActivities(new ArrayList<>());
        }

        if (limitsDto.getGlobalLimit() != null) {
            dailyLimits.setGlobalLimit(limitsDto.getGlobalLimit());
        }

        if (limitsDto.getActivities() != null) {
            for (ActivityLimit inputLimit : limitsDto.getActivities()) {
                if (inputLimit.getActivityName() != null && inputLimit.getLimit() != null) {
                    boolean exists = false;
                    for (ActivityLimit existingLimit : dailyLimits.getActivities()) {
                        if (existingLimit.getActivityName().equalsIgnoreCase(inputLimit.getActivityName().trim())) {
                            existingLimit.setLimit(inputLimit.getLimit());
                            exists = true;
                            break;
                        }
                    }
                    if (!exists) {
                        dailyLimits.getActivities()
                                .add(new ActivityLimit(inputLimit.getActivityName().trim(), inputLimit.getLimit()));
                    }
                }
            }
        }

        userActivity.setDailyLimits(dailyLimits);
        activityRepo.save(userActivity);
        analyzeLimitsAndNotify(login);

        return new ResponseEntity<>("Limits set", HttpStatus.OK);
    }

    @Operation(summary = "Delete global activity limit", description = "Deletes the global activity limit for the logged-in user", tags = {"Activity Management"})
    @ApiResponse(responseCode = "200", description = "Global limit deleted")
    @ApiResponse(responseCode = "404", description = "User activity not found")
    @ApiResponse(responseCode = "500", description = "Internal server error")
    @DeleteMapping("/limits/global")
    public ResponseEntity<?> deleteGlobalLimit(Principal principal) {
        UserActivity userActivity = activityRepo.findByLogin(principal.getName())
                .orElseThrow(() -> new RuntimeException("Nie znaleziono użytkownika"));
        DailyLimits limits = userActivity.getDailyLimits();
        if (limits != null) {
            limits.setGlobalLimit(null);
            removeEmptyLimits(userActivity, limits);
            activityRepo.save(userActivity);
            analyzeLimitsAndNotify(principal.getName());
        }
        return new ResponseEntity<>("Global limit deleted", HttpStatus.OK);
    }

    @Operation(summary = "Delete activity limit", description = "Deletes an activity limit for the logged-in user", tags = {"Activity Management"})
    @ApiResponse(responseCode = "200", description = "Activity limit deleted")
    @ApiResponse(responseCode = "404", description = "User activity not found")
    @ApiResponse(responseCode = "500", description = "Internal server error")
    @DeleteMapping("/limits/activity/{activityName}")
    public ResponseEntity<?> deleteActivityLimit(@PathVariable String activityName, Principal principal) {
        UserActivity userActivity = activityRepo.findByLogin(principal.getName())
                .orElseThrow(() -> new RuntimeException("Nie znaleziono użytkownika"));
        DailyLimits limits = userActivity.getDailyLimits();
        if (limits != null && limits.getActivities() != null) {
            limits.getActivities().removeIf(limit -> limit.getActivityName().equalsIgnoreCase(activityName));
            removeEmptyLimits(userActivity, limits);
            activityRepo.save(userActivity);
            analyzeLimitsAndNotify(principal.getName());
        }
        return new ResponseEntity<>("Activity limit deleted", HttpStatus.OK);
    }

    private void removeEmptyLimits(UserActivity userActivity, DailyLimits limits) {
        if (limits.getGlobalLimit() == null
                && (limits.getActivities() == null || limits.getActivities().isEmpty())) {
            userActivity.setDailyLimits(null);
        } else {
            userActivity.setDailyLimits(limits);
        }
    }

    @Operation(summary = "Mark notification as read", description = "Marks a notification as read for the logged-in user", tags = {"Notifications"})
    @ApiResponse(responseCode = "200", description = "Notification marked as read")
    @ApiResponse(responseCode = "404", description = "User activity not found")
    @ApiResponse(responseCode = "500", description = "Internal server error")
    @PostMapping("/notifications/read/{id}")
    public ResponseEntity<?> readNotification(@PathVariable String id, Principal principal) {
        String login = principal.getName();
        UserActivity userActivity = activityRepo.findByLogin(login)
                .orElseThrow(() -> new RuntimeException("Nie znaleziono użytkownika"));
        if (userActivity.getNotifications() != null) {
            for (Notification notification : userActivity.getNotifications()) {
                if (notification.getId().equals(id)) {
                    notification.setRead(true);
                    break;
                }
            }
            activityRepo.save(userActivity);
        }
        return new ResponseEntity<>("Notification marked as read", HttpStatus.OK);
    }

    private void analyzeLimitsAndNotify(String login) {
        analysisNotificationService.analyzeAndNotify(login);
    }

}
