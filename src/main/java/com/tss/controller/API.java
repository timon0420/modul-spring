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

@RestController
@RequestMapping("/api")
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

    @GetMapping("/all_activities")
    public List<UserActivity> getAllActivities() {
        return activityRepo.findAll();
    }

    @GetMapping("/activities")
    public UserActivity getActivityByLogin(Principal principal) {
        return activityRepo.findByLogin(principal.getName())
                .orElseThrow(() -> new RuntimeException("Nie znaleziono danych"));
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
        String res = activityRepo.save(userActivity).toString();
        triggerGoAnalysis(login);
        return res;
    }

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
            triggerGoAnalysis(login);
        }

        return new ResponseEntity<>("Activity deleted", HttpStatus.OK);
    }

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
        triggerGoAnalysis(login);

        return new ResponseEntity<>("Activity edited", HttpStatus.OK);
    }

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
        triggerGoAnalysis(login);

        return new ResponseEntity<>("Limits set", HttpStatus.OK);
    }

    @DeleteMapping("/limits/global")
    public ResponseEntity<?> deleteGlobalLimit(Principal principal) {
        UserActivity userActivity = activityRepo.findByLogin(principal.getName())
                .orElseThrow(() -> new RuntimeException("Nie znaleziono użytkownika"));
        DailyLimits limits = userActivity.getDailyLimits();
        if (limits != null) {
            limits.setGlobalLimit(null);
            removeEmptyLimits(userActivity, limits);
            activityRepo.save(userActivity);
            triggerGoAnalysis(principal.getName());
        }
        return new ResponseEntity<>("Global limit deleted", HttpStatus.OK);
    }

    @DeleteMapping("/limits/activity/{activityName}")
    public ResponseEntity<?> deleteActivityLimit(@PathVariable String activityName, Principal principal) {
        UserActivity userActivity = activityRepo.findByLogin(principal.getName())
                .orElseThrow(() -> new RuntimeException("Nie znaleziono użytkownika"));
        DailyLimits limits = userActivity.getDailyLimits();
        if (limits != null && limits.getActivities() != null) {
            limits.getActivities().removeIf(limit -> limit.getActivityName().equalsIgnoreCase(activityName));
            removeEmptyLimits(userActivity, limits);
            activityRepo.save(userActivity);
            triggerGoAnalysis(principal.getName());
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

    private void triggerGoAnalysis(String login) {
        analysisNotificationService.analyzeAndNotify(login);
    }

}
