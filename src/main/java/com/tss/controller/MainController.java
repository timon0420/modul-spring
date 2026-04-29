package com.tss.controller;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Date;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import com.tss.mongodb.model.UserActivity;
import com.tss.mongodb.repo.ActivityRepo;
import com.tss.postgres.model.User;
import com.tss.postgres.repo.UserRepo;
import org.springframework.web.bind.annotation.RequestParam;

import com.tss.mongodb.model.Activity;


@Controller
public class MainController {
    
    private final PasswordEncoder passwordEncoder;
    private final UserRepo userRepo;
    private final ActivityRepo activityRepo;

    public MainController(UserRepo userRepo, PasswordEncoder passwordEncoder, ActivityRepo activityRepo) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
        this.activityRepo = activityRepo;
    }

    @GetMapping("/")
    public String index(Model model, Principal principal) {
        String login = principal.getName();

        UserActivity userActivity = activityRepo.findByLogin(login)
            .orElseThrow(() -> new RuntimeException("Nie znaleziono użytkownika w MongoDB"));

        model.addAttribute("data", userActivity);
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

        UserActivity userActivity = activityRepo.findByLogin(login).orElseThrow(() -> new RuntimeException("Nie znaleziono użytkownika w MongoDB"));

        boolean removed = userActivity.getActivities().removeIf(activity -> activity.getId().equals(id));

        if (!removed) {
            throw new RuntimeException("Nie znaleziono aktywności do usuniecia");
        } else {
            activityRepo.save(userActivity);
        }
        
        return "redirect:/";
    }

    @GetMapping("/addActivity")
    public String addActivityForm() {
        return "addActivityForm";
    }
    

    @PostMapping("/addActivity")
    public String addActivity(@RequestParam String activity_name, @RequestParam String activity_description, @RequestParam String time, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date date, Principal principal) {
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
        newActivity.setTime(time);
        newActivity.setDate(date);
        userActivity.getActivities().add(newActivity);
        activityRepo.save(userActivity);

        return "redirect:/";
    }
    
}
