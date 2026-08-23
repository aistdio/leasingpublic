package ru.bell.autoleasing.auth.jwt.login.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class AuthenticationRequest {
    private String username;
    private String password;
}
