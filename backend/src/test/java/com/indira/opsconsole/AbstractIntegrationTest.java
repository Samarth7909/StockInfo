package com.indira.opsconsole;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Base class for Spring Boot integration tests.
 *
 * Each concrete subclass that annotates itself with a unique {@code @SpringBootTest}
 * {@code properties} override gets its own in-memory SQLite database, preventing
 * schema and data leakage between test classes.
 *
 * Subclasses should NOT add {@code @DirtiesContext} — context isolation is achieved
 * via the unique datasource URL in each subclass's {@code @SpringBootTest} annotation.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {
    // Subclasses inherit the @SpringBootTest + test profile.
    // Each subclass may override the datasource URL via
    //   @SpringBootTest(properties = "spring.datasource.url=jdbc:sqlite:file:UNIQUE_NAME?mode=memory&cache=shared&uri=true")
    // to get a completely isolated in-memory database.
}
