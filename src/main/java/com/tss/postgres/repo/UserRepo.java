package com.tss.postgres.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tss.postgres.model.User;

public interface UserRepo extends JpaRepository<User, Long>  {
    
    Optional<User> findByLogin(String login);
    boolean existsByLogin(String login);
}
