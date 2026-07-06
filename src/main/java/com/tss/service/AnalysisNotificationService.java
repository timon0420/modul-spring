package com.tss.service;

import com.tss.mongodb.model.Activity;
import com.tss.mongodb.model.ActivityLimit;
import com.tss.mongodb.model.DailyLimits;
import com.tss.mongodb.model.Notification;
import com.tss.mongodb.model.UserActivity;
import com.tss.mongodb.repo.ActivityRepo;
import com.tss.websocket.NotificationWebSocketHandler;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AnalysisNotificationService {
    private static final Logger log = LoggerFactory.getLogger(AnalysisNotificationService.class);
    private static final String TYPE = "LIMIT_WARNING";

    private final NotificationWebSocketHandler webSocketHandler;
    private final ActivityRepo activityRepo;
    private final ZoneId zoneId;

    public AnalysisNotificationService(NotificationWebSocketHandler webSocketHandler, ActivityRepo activityRepo,
            @Value("${notifications.time-zone:Europe/Warsaw}") String timeZone) {
        this.webSocketHandler = webSocketHandler;
        this.activityRepo = activityRepo;
        this.zoneId = ZoneId.of(timeZone);
    }

    /** Detects exceeded limits, persists new notifications and sends them via Spring WebSocket. */
    public void analyzeAndNotify(String login) {
        activityRepo.findByLogin(login).ifPresent(user -> {
            List<Notification> created = createNotifications(user);
            if (created.isEmpty()) return;

            if (user.getNotifications() == null) user.setNotifications(new ArrayList<>());
            user.getNotifications().addAll(created);
            activityRepo.save(user);
            created.forEach(notification -> webSocketHandler.send(login, notification));
            log.info("Zapisano i wysłano {} powiadomień o limitach użytkownika {}", created.size(), login);
        });
    }

    private List<Notification> createNotifications(UserActivity user) {
        DailyLimits limits = user.getDailyLimits();
        if (limits == null) return List.of();

        LocalDate today = LocalDate.now(zoneId);
        Map<String, Integer> minutesByActivity = new HashMap<>();
        int totalMinutes = 0;
        if (user.getActivities() != null) {
            for (Activity activity : user.getActivities()) {
                if (activity.getDate() == null
                        || !activity.getDate().toInstant().atZone(zoneId).toLocalDate().equals(today)) continue;
                int minutes = parseMinutes(activity.getTime());
                totalMinutes += minutes;
                if (activity.getActivity_name() != null) {
                    minutesByActivity.merge(normalize(activity.getActivity_name()), minutes, Integer::sum);
                }
            }
        }

        List<Notification> created = new ArrayList<>();
        if (limits.getGlobalLimit() != null && totalMinutes > limits.getGlobalLimit()) {
            addIfMissing(user, created, "GLOBAL", today, String.format(
                    "Przekroczono dobowy limit globalny aktywności! Czas: %d min, limit: %d min.",
                    totalMinutes, limits.getGlobalLimit()));
        }
        if (limits.getActivities() != null) {
            for (ActivityLimit limit : limits.getActivities()) {
                if (limit.getActivityName() == null || limit.getLimit() == null) continue;
                int spent = minutesByActivity.getOrDefault(normalize(limit.getActivityName()), 0);
                if (spent > limit.getLimit()) {
                    addIfMissing(user, created, "ACTIVITY:" + normalize(limit.getActivityName()), today,
                            String.format("Przekroczono dobowy limit dla aktywności '%s'! Czas: %d min, limit: %d min.",
                                    limit.getActivityName(), spent, limit.getLimit()));
                }
            }
        }
        return created;
    }

    private void addIfMissing(UserActivity user, List<Notification> created, String limitKey,
            LocalDate today, String message) {
        boolean exists = user.getNotifications() != null && user.getNotifications().stream()
                .anyMatch(notification -> TYPE.equals(notification.getType())
                        && limitKey.equals(notification.getLimitKey())
                        && notification.getCreatedAt() != null
                        && notification.getCreatedAt().toInstant().atZone(zoneId).toLocalDate().equals(today));
        if (!exists) {
            Notification notification = new Notification(TYPE, message);
            notification.setLimitKey(limitKey);
            created.add(notification);
        }
    }

    private int parseMinutes(Object value) {
        if (value == null) return 0;
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ignored) {
            log.warn("Pominięto nieprawidłowy czas aktywności: {}", value);
            return 0;
        }
    }

    private String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
