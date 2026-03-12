package com.flowershop.service;

import java.util.Optional;

public interface AuthSessionGateway {

    void ensureSchema();

    void save(AuthSession authSession);

    Optional<AuthSession> findByToken(String token);

    void deleteByLoginTypeAndAccount(String loginType, String account);

    void deleteByToken(String token);
}