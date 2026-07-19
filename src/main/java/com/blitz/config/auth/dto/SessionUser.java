package com.blitz.config.auth.dto;

import com.blitz.domain.user.User;

import java.io.Serial;
import java.io.Serializable;

public record SessionUser(Long userId, String name, String email) implements Serializable {

    public static final String SESSION_ATTRIBUTE = "user";

    @Serial
    private static final long serialVersionUID = 1L;

    public SessionUser(User user) {
        this(user.getId(), user.getName(), user.getEmail());
    }
}
