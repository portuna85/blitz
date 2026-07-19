package com.blitz.domain.user;

public enum Role {

    GUEST,
    USER;

    public String getKey() {
        return "ROLE_" + name();
    }

}
