package com.insurance.platform.security;

import com.insurance.platform.common.error.ErrorCode;
import com.insurance.platform.common.exception.BusinessException;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Phase 7 fail-closed placeholder; Phase 9 will replace it with JWT-backed identity. */
@Component
public class UnauthenticatedCurrentUserProvider implements CurrentUserProvider {

    @Override
    public UUID requireUserId() {
        throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
    }
}
