package com.xbertz.onsite.backend.config

/**
 * Every setting comes from the environment (loaded from .env by the run scripts, or set
 * directly in whatever host runs the container) - no secrets live in source control.
 */
data class AppConfig(
    val dbUrl: String,
    val dbUser: String,
    val dbPassword: String,
    val supabaseJwtSecret: String,
    val supabaseJwtIssuer: String,
    val supabaseProjectUrl: String,
    val port: Int,
    val devAuthEnabled: Boolean,
    // Consumed by logback.xml itself (it is parsed before this class exists), mirrored
    // here only so the startup line can state the level and format actually in effect.
    // LOG_FORMAT is lowercase on purpose: logback resolves it into the name of the include
    // it loads (logback-console.xml / logback-json.xml).
    val logLevel: String = "INFO",
    val logFormat: String = "console",
    // Named on every log line (see logback-json.xml, which reads the same two variables)
    // and on every metric as a common tag, so one series or line is traceable to one
    // deployment of one build.
    val appEnv: String = "local",
    val appVersion: String = "dev",
    /**
     * Null (the default) means /metrics is not registered at all: a scrape endpoint hands
     * out every route name and its traffic volume, so it is opt-in rather than
     * accidentally public. Set it to whatever bearer token the scraper will send.
     */
    val metricsToken: String? = null,
) {
    companion object {
        fun fromEnv(): AppConfig {
            fun required(name: String): String =
                System.getenv(name) ?: error("Missing required environment variable: $name")

            return AppConfig(
                dbUrl = required("DB_URL"),
                dbUser = required("DB_USER"),
                dbPassword = required("DB_PASSWORD"),
                supabaseJwtSecret = required("SUPABASE_JWT_SECRET"),
                supabaseJwtIssuer = System.getenv("SUPABASE_JWT_ISSUER") ?: "supabase",
                supabaseProjectUrl = required("SUPABASE_PROJECT_URL").trimEnd('/'),
                port = System.getenv("PORT")?.toIntOrNull() ?: 8080,
                devAuthEnabled = System.getenv("DEV_AUTH_ENABLED")?.toBoolean() ?: false,
                logLevel = System.getenv("LOG_LEVEL") ?: "INFO",
                logFormat = System.getenv("LOG_FORMAT") ?: "console",
                appEnv = System.getenv("APP_ENV") ?: "local",
                appVersion = System.getenv("APP_VERSION") ?: "dev",
                metricsToken = System.getenv("METRICS_TOKEN")?.takeIf { it.isNotBlank() },
            )
        }
    }
}
