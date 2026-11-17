package com.paygrid;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class PayGridApplicationTests {

    @Test
    void contextLoads() {
        // Verifies Spring context loading and Flyway database migration
    }
}

