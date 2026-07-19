package com.blitz.web;

import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;


@RequiredArgsConstructor
@RestController
public class ProfileController {
    private static final List<String> DEPLOYMENT_PROFILES = List.of("real", "real1", "real2");

    private final Environment env;

    @GetMapping("/profile")
    public String profile() {
        List<String> profiles = effectiveProfiles();

        return DEPLOYMENT_PROFILES.stream()
                .filter(profiles::contains)
                .findFirst()
                .orElseGet(() -> profiles.getFirst());
    }

    private List<String> effectiveProfiles() {
        String[] activeProfiles = env.getActiveProfiles();
        if (activeProfiles.length > 0) {
            return Arrays.asList(activeProfiles);
        }

        return Arrays.asList(env.getDefaultProfiles());
    }
}
