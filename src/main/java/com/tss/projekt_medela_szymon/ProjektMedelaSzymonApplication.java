package com.tss.projekt_medela_szymon;


import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.tss.postgres.model.User;
import com.tss.postgres.repo.UserRepo;

@SpringBootApplication
@EntityScan(basePackages = "com.tss")
@ComponentScan(basePackages = "com.tss")
@EnableAutoConfiguration
public class ProjektMedelaSzymonApplication {

	public static void main(String[] args) {
		SpringApplication.run(ProjektMedelaSzymonApplication.class, args);
	}

	@Bean
	CommandLineRunner initData(UserRepo repo, PasswordEncoder encoder) {
		return args -> {
			if (repo.findByLogin("admin").isEmpty()) {
				User user = new User();
				user.setLogin("admin");
				user.setPassword(encoder.encode("admin"));
				repo.save(user);
			}
		};
	}

}
