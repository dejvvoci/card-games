package com.pesekatesh.user;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** Regjistron një përdorues të ri. Hedh IllegalStateException nëse username-i është zënë. */
    public User register(String username, String rawPassword) {
        String normalized = username.trim();
        if (normalized.isEmpty() || rawPassword == null || rawPassword.isEmpty()) {
            throw new IllegalArgumentException("Emri i përdoruesit dhe fjalëkalimi janë të detyrueshëm.");
        }
        if (userRepository.existsByUsername(normalized)) {
            throw new IllegalStateException("Ky emër përdoruesi është zënë tashmë.");
        }
        User user = new User(normalized, passwordEncoder.encode(rawPassword));
        return userRepository.save(user);
    }

    /** Kthen përdoruesin nëse username/fjalëkalimi përputhen, ndryshe Optional.empty() */
    public Optional<User> authenticate(String username, String rawPassword) {
        return userRepository.findByUsername(username.trim())
                .filter(u -> passwordEncoder.matches(rawPassword, u.getPasswordHash()));
    }
}
