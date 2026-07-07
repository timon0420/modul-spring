package com.tss.controller;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;

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
@Tag(name = "Activity API", description = "Endpoints for managing activities, limits, reports, notifications, and WebSocket documentation")
public class API {

    private final ActivityRepo activityRepo;
    private final GrpcAnalysisClientService grpcAnalysisClientService;
    private final AnalysisNotificationService analysisNotificationService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final String gatewayBaseUrl;

    public API(ActivityRepo activityRepo, GrpcAnalysisClientService grpcAnalysisClientService,
            AnalysisNotificationService analysisNotificationService,
            @Value("${analysis.gateway.base-url}") String gatewayBaseUrl) {
        this.activityRepo = activityRepo;
        this.grpcAnalysisClientService = grpcAnalysisClientService;
        this.analysisNotificationService = analysisNotificationService;
        this.gatewayBaseUrl = gatewayBaseUrl;
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

    @Operation(summary = "Add user activity", description = "Adds a new activity for the logged-in user. After saving to MongoDB the API runs limit analysis; newly detected limit exceedances are saved as notifications and sent through /ws/notifications.", tags = {"Activity Management"})
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

    @Operation(summary = "Delete user activity", description = "Deletes an existing activity for the logged-in user. After saving to MongoDB the API runs limit analysis and sends new notifications through the notification WebSocket.", tags = {"Activity Management"})
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

    @Operation(summary = "Edit user activity", description = "Edits an existing activity for the logged-in user. After saving to MongoDB the API runs limit analysis and sends new notifications through the notification WebSocket.", tags = {"Activity Management"})
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

    @Operation(summary = "Set user activity limits", description = "Sets activity limits for the logged-in user. If current daily activity already exceeds a saved limit, a LIMIT_WARNING notification is persisted in MongoDB and sent over /ws/notifications.", tags = {"Activity Management"})
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

    @Operation(summary = "Get user notifications", description = "Returns all persisted notifications for the logged-in user, including LIMIT_WARNING notifications created by API-triggered limit analysis.", tags = {"Notifications"})
    @ApiResponse(responseCode = "200", description = "Notifications returned")
    @ApiResponse(responseCode = "404", description = "User activity not found")
    @GetMapping("/notifications")
    public List<Notification> getNotifications(Principal principal) {
        UserActivity userActivity = activityRepo.findByLogin(principal.getName())
                .orElseThrow(() -> new RuntimeException("Nie znaleziono uzytkownika"));
        return userActivity.getNotifications() == null ? List.of() : userActivity.getNotifications();
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

    @Operation(summary = "Download activity report", description = "Admin endpoint mirroring /admin/reports/activity.json. Downloads the activity report JSON from the analysis gateway.", tags = {"Admin Reports"})
    @ApiResponse(responseCode = "200", description = "Report downloaded")
    @ApiResponse(responseCode = "403", description = "Admin role required")
    @ApiResponse(responseCode = "500", description = "Analysis gateway request failed")
    @GetMapping("/admin/reports/activity.json")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> downloadReport() {
        byte[] report = restTemplate.getForObject(gatewayUri("/report"), byte[].class);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=activity-report.json")
                .body(report);
    }

    @Operation(summary = "Import activity report", description = "Admin endpoint mirroring /admin/reports/import. Uploads a JSON file and forwards it to the analysis gateway import endpoint.", tags = {"Admin Reports"})
    @ApiResponse(responseCode = "200", description = "Report imported")
    @ApiResponse(responseCode = "400", description = "No file selected")
    @ApiResponse(responseCode = "403", description = "Admin role required")
    @ApiResponse(responseCode = "502", description = "Analysis gateway request failed")
    @PostMapping(value = "/admin/reports/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> importData(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return new ResponseEntity<>("Wybierz plik JSON do importu.", HttpStatus.BAD_REQUEST);
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<byte[]> request = new HttpEntity<>(file.getBytes(), headers);
            ResponseEntity<String> response = restTemplate.postForEntity(gatewayUri("/import"), request, String.class);
            return new ResponseEntity<>(response.getBody(), response.getStatusCode());
        } catch (IOException ex) {
            return new ResponseEntity<>("Nie udalo sie odczytac pliku: " + ex.getMessage(), HttpStatus.BAD_REQUEST);
        } catch (RestClientException ex) {
            return new ResponseEntity<>("Nie udalo sie zaimportowac danych: " + ex.getMessage(),
                    HttpStatus.BAD_GATEWAY);
        }
    }

    @Operation(summary = "Clear MongoDB activity data", description = "Admin endpoint mirroring /admin/reports/database/clear. Deletes all user activity documents through the analysis gateway.", tags = {"Admin Reports"})
    @ApiResponse(responseCode = "200", description = "Database cleared")
    @ApiResponse(responseCode = "403", description = "Admin role required")
    @ApiResponse(responseCode = "502", description = "Analysis gateway request failed")
    @DeleteMapping("/admin/reports/database")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> clearDatabase() {
        try {
            ResponseEntity<String> response = restTemplate.exchange(gatewayUri("/users"), HttpMethod.DELETE,
                    HttpEntity.EMPTY, String.class);
            return new ResponseEntity<>(response.getBody(), response.getStatusCode());
        } catch (RestClientException ex) {
            return new ResponseEntity<>("Nie udalo sie wyczyscic bazy: " + ex.getMessage(), HttpStatus.BAD_GATEWAY);
        }
    }

    @Operation(summary = "Delete user activity data", description = "Admin endpoint mirroring /admin/reports/users/delete. Deletes MongoDB activity data for the selected login through the analysis gateway.", tags = {"Admin Reports"})
    @ApiResponse(responseCode = "200", description = "User data delete request completed")
    @ApiResponse(responseCode = "400", description = "Login is blank")
    @ApiResponse(responseCode = "403", description = "Admin role required")
    @ApiResponse(responseCode = "502", description = "Analysis gateway request failed")
    @DeleteMapping("/admin/reports/users/{login}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteUserActivityData(@PathVariable String login) {
        if (login == null || login.trim().isEmpty()) {
            return new ResponseEntity<>("Podaj login uzytkownika do usuniecia.", HttpStatus.BAD_REQUEST);
        }

        try {
            String encodedLogin = UriUtils.encodePathSegment(login.trim(), StandardCharsets.UTF_8);
            ResponseEntity<Map<String, Integer>> response = restTemplate.exchange(
                    gatewayUri("/users/" + encodedLogin),
                    HttpMethod.DELETE,
                    HttpEntity.EMPTY,
                    new ParameterizedTypeReference<Map<String, Integer>>() {});
            return new ResponseEntity<>(response.getBody(), response.getStatusCode());
        } catch (RestClientException ex) {
            return new ResponseEntity<>("Nie udalo sie usunac uzytkownika: " + ex.getMessage(),
                    HttpStatus.BAD_GATEWAY);
        }
    }

    @Operation(summary = "Document notification WebSocket", description = "Returns the runtime contract for /ws/notifications. OpenAPI documents HTTP endpoints only, so this endpoint exposes the WebSocket URL, authentication rule, payload fields, and delivery behavior.", tags = {"WebSocket"})
    @ApiResponse(responseCode = "200", description = "WebSocket documentation returned")
    @GetMapping("/websocket/notifications")
    public Map<String, Object> websocketDocumentation() {
        Map<String, Object> docs = new LinkedHashMap<>();
        docs.put("url", "/ws/notifications");
        docs.put("protocols", List.of("ws", "wss"));
        docs.put("authentication", "Requires the same authenticated Spring Security session as the HTTP API.");
        docs.put("serverMessages", "Unread notifications are sent after connection. New LIMIT_WARNING notifications are sent immediately after API-triggered limit analysis.");
        docs.put("clientMessages", "No client message format is required; marking a notification as read is done with POST /api/notifications/read/{id}.");
        docs.put("payloadExample", Map.of(
                "id", "uuid",
                "type", "LIMIT_WARNING",
                "message", "Przekroczono dobowy limit globalny aktywnosci! Czas: 250 min, limit: 240 min.",
                "created_at", "2026-07-07T12:00:00.000+00:00",
                "read", false,
                "limit_key", "GLOBAL"));
        return docs;
    }

    private void analyzeLimitsAndNotify(String login) {
        analysisNotificationService.analyzeAndNotify(login);
    }

    private URI gatewayUri(String path) {
        String baseUrl = gatewayBaseUrl.endsWith("/")
                ? gatewayBaseUrl.substring(0, gatewayBaseUrl.length() - 1) : gatewayBaseUrl;
        return URI.create(baseUrl + path);
    }

}
