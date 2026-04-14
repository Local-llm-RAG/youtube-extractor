package com.data.shared.i18n;

/**
 * Shared language constants used across all data-ingestion pipelines.
 *
 * <p>Centralising these here prevents the same literal from drifting
 * across OAI, PMC S3, and any future source.
 */
public final class LanguageConstants {

    /** BCP-47 language tag used as the fallback when a source does not supply language metadata. */
    public static final String DEFAULT_LANGUAGE = "en";

    private LanguageConstants() {}
}
