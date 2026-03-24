package com.auth.service.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

import java.util.Set;

@Entity
@Table(name = "users")
@Getter
@Setter
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String username;

    private String email;

    private String password;

    private boolean enabled;

    private boolean accountLocked;

    private int failedAttempts;

    private boolean isVerified;

    @ManyToMany(fetch = FetchType.EAGER)
    private Set<Role> roles;
}
