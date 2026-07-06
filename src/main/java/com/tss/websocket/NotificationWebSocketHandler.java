package com.tss.websocket;

import java.io.IOException;
import java.security.Principal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.mongodb.repo.ActivityRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class NotificationWebSocketHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(NotificationWebSocketHandler.class);
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ActivityRepo activityRepo;
    private final ObjectMapper objectMapper;

    public NotificationWebSocketHandler(ActivityRepo activityRepo, ObjectMapper objectMapper) {
        this.activityRepo = activityRepo;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String login = authenticatedLogin(session);
        WebSocketSession previous = sessions.put(login, session);
        if (previous != null && previous.isOpen()) previous.close();
        log.info("Połączono WebSocket powiadomień użytkownika {}", login);
        activityRepo.findByLogin(login).ifPresent(user -> {
            if (user.getNotifications() != null) user.getNotifications().stream()
                    .filter(notification -> !notification.isRead())
                    .forEach(notification -> send(login, notification));
        });
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Principal principal = session.getPrincipal();
        if (principal != null) sessions.remove(principal.getName(), session);
    }

    public void send(String login, Object notification) {
        WebSocketSession session = sessions.get(login);
        if (session == null || !session.isOpen()) return;
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(notification)));
            }
        } catch (IOException e) {
            log.warn("Nie udało się wysłać powiadomienia do {}", login, e);
        }
    }

    private String authenticatedLogin(WebSocketSession session) {
        Principal principal = session.getPrincipal();
        if (principal == null) throw new IllegalStateException("WebSocket wymaga uwierzytelnienia");
        return principal.getName();
    }
}
