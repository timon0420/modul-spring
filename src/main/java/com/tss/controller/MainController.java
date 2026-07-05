package com.tss.controller;

import java.security.Principal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import com.tss.mongodb.model.UserActivity;
import com.tss.mongodb.repo.ActivityRepo;
import com.tss.postgres.model.User;
import com.tss.postgres.repo.UserRepo;
import org.springframework.web.bind.annotation.RequestParam;

import com.tss.components.SessionComponent;
import com.tss.mongodb.model.Activity;
import com.tss.mongodb.model.DailyLimits;
import com.tss.mongodb.model.ActivityLimit;
import com.tss.mongodb.model.Notification;

@Controller
public class MainController {

    private final PasswordEncoder passwordEncoder;
    private final UserRepo userRepo;
    private final ActivityRepo activityRepo;

    @Autowired
    SessionComponent sessionComponentQuery;

    @Autowired
    BuildProperties buildProperties;

    @Value("${myparams.jdkversion}")
    String jdkVersion;

    @Value("${myparams.springbootversion}")
    String springBootVersion;

    @Value("${application.name}")
    String applicationName;

    @Value("${build.version}")
    String buildVersion;

    @Value("${build.timestamp}")
    String buildTimestamp;

    @Value("${analysis.gateway.base-url}")
    String analysisGatewayBaseUrl;

    public MainController(UserRepo userRepo, PasswordEncoder passwordEncoder, ActivityRepo activityRepo) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
        this.activityRepo = activityRepo;
    }

    @GetMapping("/")
    public String index(Model model, Principal principal) {
        String login = principal.getName();

        UserActivity userActivity = activityRepo.findByLogin(login).orElseGet(() -> {
            UserActivity newUA = new UserActivity();
            newUA.setLogin(login);
            newUA.setActivities(new ArrayList<>());
            return newUA;
        });

        sessionComponentQuery.incrementCounter();

        model.addAttribute("data", userActivity);
        model.addAttribute("login", login);
        model.addAttribute("jdkVersion", jdkVersion);
        model.addAttribute("springBootVersion", springBootVersion);
        model.addAttribute("applicationName", applicationName);
        model.addAttribute("buildVersion", buildVersion);
        model.addAttribute("buildTimestamp", buildTimestamp);
        model.addAttribute("analysisGatewayBaseUrl", analysisGatewayBaseUrl);

        model.addAttribute("counter", sessionComponentQuery.getCounter());

        return "index";
    }

    @GetMapping("/register")
    public String showRegisterForm(Model model) {
        model.addAttribute("user", new User());

        return "register";
    }

    @PostMapping("/register")
    public String registerUser(User user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        userRepo.save(user);
        return "redirect:/";
    }

    @GetMapping("/websocket")
    public String getMethodName() {
        return "websocket";
    }

    @PostMapping("/delete/{id}")
    public String deleteActivity(@PathVariable String id, Principal principal) {
        String login = principal.getName();

        UserActivity userActivity = activityRepo.findByLogin(login)
                .orElseThrow(() -> new RuntimeException("Nie znaleziono użytkownika w MongoDB"));

        boolean removed = userActivity.getActivities().removeIf(activity -> activity.getId().equals(id));

        if (!removed) {
            throw new RuntimeException("Nie znaleziono aktywności do usuniecia");
        } else {
            activityRepo.save(userActivity);
            triggerGoAnalysisAsync(login);
        }

        return "redirect:/";
    }

    @GetMapping("/addActivity")
    public String addActivityForm() {
        return "addActivityForm";
    }

    @PostMapping("/addActivity")
    public String addActivity(@RequestParam String activity_name, @RequestParam String activity_description,
            @RequestParam String start_time, @RequestParam Number time,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date date, Principal principal) {
        String login = principal.getName();

        UserActivity userActivity = activityRepo.findByLogin(login).orElseGet(() -> {
            UserActivity newUA = new UserActivity();
            newUA.setLogin(login);
            newUA.setActivities(new ArrayList<>());
            return newUA;
        });

        Activity newActivity = new Activity();
        newActivity.setActivity_name(activity_name);
        newActivity.setActivity_description(activity_description);
        newActivity.setStart_time(start_time);
        newActivity.setTime(time);
        newActivity.setDate(date);
        userActivity.getActivities().add(newActivity);
        activityRepo.save(userActivity);
        triggerGoAnalysisAsync(login);

        return "redirect:/";
    }

    @GetMapping("/edit/{id}")
    public String editActivityForm(@PathVariable String id, Model model, Principal principal) {
        String login = principal.getName();
        UserActivity userActivity = activityRepo.findByLogin(login)
                .orElseThrow(() -> new RuntimeException("Nie znaleziono użytkownika w MongoDB"));
        Activity activity = userActivity.getActivities().stream().filter(a -> a.getId().equals(id)).findFirst()
                .orElseThrow(() -> new RuntimeException("Nie znaleziono aktywnosci"));
        model.addAttribute("activity", activity);
        return "editActivityForm";
    }

    @PutMapping("/edit/{id}")
    public String editActivity(@RequestParam String activity_name, @RequestParam String activity_description,
            @RequestParam String start_time, @RequestParam Number time,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date date, Principal principal,
            @PathVariable String id) {
        String login = principal.getName();

        UserActivity userActivity = activityRepo.findByLogin(login)
                .orElseThrow(() -> new RuntimeException("Nie znaleziono użytkownika w MongoDB"));
        Activity activity = userActivity.getActivities().stream().filter(a -> a.getId().equals(id)).findFirst()
                .orElseThrow(() -> new RuntimeException("Nie znaleziono aktywnosci"));

        activity.setActivity_name(activity_name);
        activity.setActivity_description(activity_description);
        activity.setStart_time(start_time);
        activity.setTime(time);
        activity.setDate(date);
        activityRepo.save(userActivity);
        triggerGoAnalysisAsync(login);

        return "redirect:/";
    }

    @PostMapping("/limits/set")
    public String setLimits(@RequestParam(required = false) Integer globalLimit,
            @RequestParam(required = false) String activityName,
            @RequestParam(required = false) Integer activityLimit,
            Principal principal) {
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

        if (globalLimit != null) {
            dailyLimits.setGlobalLimit(globalLimit);
        }

        if (activityName != null && !activityName.trim().isEmpty() && activityLimit != null) {
            boolean exists = false;
            for (ActivityLimit limit : dailyLimits.getActivities()) {
                if (limit.getActivityName().equalsIgnoreCase(activityName.trim())) {
                    limit.setLimit(activityLimit);
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                dailyLimits.getActivities().add(new ActivityLimit(activityName.trim(), activityLimit));
            }
        }

        userActivity.setDailyLimits(dailyLimits);
        activityRepo.save(userActivity);

        triggerGoAnalysisAsync(login);

        return "redirect:/";
    }

    @PostMapping("/notifications/read/{id}")
    public String readNotification(@PathVariable String id, Principal principal) {
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
        return "redirect:/";
    }

    private void triggerGoAnalysisAsync(String login) {
        new Thread(() -> {
            try {
                String baseUrl = analysisGatewayBaseUrl.endsWith("/")
                        ? analysisGatewayBaseUrl.substring(0, analysisGatewayBaseUrl.length() - 1)
                        : analysisGatewayBaseUrl;
                String encodedLogin = URLEncoder.encode(login, StandardCharsets.UTF_8);
                java.net.URL url = new java.net.URL(baseUrl + "/analyze?login=" + encodedLogin);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.getResponseCode();
                conn.disconnect();
            } catch (Exception e) {
                System.err.println("Failed to trigger Go analysis: " + e.getMessage());
            }
        }).start();
    }
}
