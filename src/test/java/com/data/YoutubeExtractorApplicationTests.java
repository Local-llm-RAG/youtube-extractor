package com.data;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Placeholder smoke-test for the Spring context.
 *
 * <p>Intentionally disabled: bringing up the full {@code @SpringBootTest} context
 * here requires an entire set of environment-specific dependencies (a live
 * PostgreSQL instance on {@code localhost:5432}, GROBID on its Docker port, the
 * {@code YOUTUBE_API_KEY}/{@code QDRANT_API_KEY}/{@code GPT_API_KEY} environment
 * variables, etc.) that are not present in CI or in a clean developer checkout.
 *
 * <p>Focused unit tests live under the respective feature packages
 * (e.g. {@code com.data.shared.license}, {@code com.data.pmcs3.jats}).
 * Add a dedicated slice test here only if we can make it self-contained.
 */
@Disabled("Full Spring context requires live Postgres / GROBID / env vars; see class javadoc.")
class YoutubeExtractorApplicationTests {

    @Test
    void contextLoads() {
        // intentionally empty — see @Disabled reason on the class
    }
}
