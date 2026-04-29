package com.tss.mongodb.repo;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import com.tss.mongodb.model.UserActivity;

public interface ActivityRepo extends MongoRepository<UserActivity, String> {
    Optional<UserActivity> findByLogin(String login);
}
