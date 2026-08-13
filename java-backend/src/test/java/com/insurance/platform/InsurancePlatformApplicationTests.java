package com.insurance.platform;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class InsurancePlatformApplicationTests {

    @Test
    void contextLoadsWithIsolatedTestDatabaseAndWithoutRedisOrPython() {
        // Spring context startup plus the test Flyway migration is the assertion.
    }
}
