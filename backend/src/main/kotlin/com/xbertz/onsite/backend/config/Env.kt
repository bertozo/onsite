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
            )
        }
    }
}
