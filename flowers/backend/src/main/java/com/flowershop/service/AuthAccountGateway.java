package com.flowershop.service;

import java.util.Optional;

public interface AuthAccountGateway {

    void ensureSchema();

    Optional<AuthAccount> findByLoginTypeAndAccount(String loginType, String account);

    void save(AuthAccount authAccount);

    void updatePasswordDigest(String loginType, String account, String passwordDigest);
}