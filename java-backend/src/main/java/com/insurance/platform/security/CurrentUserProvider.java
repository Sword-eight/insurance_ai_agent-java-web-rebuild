package com.insurance.platform.security;

import java.util.UUID;

/** Supplies the authenticated public user id without exposing transport details to services. */
public interface CurrentUserProvider {

    UUID requireUserId();
}
