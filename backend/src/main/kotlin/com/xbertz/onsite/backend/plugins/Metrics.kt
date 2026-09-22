package com.xbertz.onsite.backend.plugins

import com.xbertz.onsite.backend.config.AppConfig
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.request.authorization
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.core.instrument.binder.system.UptimeMetrics
import io.micrometer.core.instrument.config.MeterFilter
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.slf4j.LoggerFactory
import java.security.MessageDigest

private val logger = LoggerFactory.getLogger("com.xbertz.onsite.backend.metrics")

/**
 * The numbers a log can't give cheaply: request rate and latency per *route template*,
 * JVM/GC, and the connection pool.
 *
 * The pool is the reason this exists. Routes run blocking Exposed transactions on
 * Dispatchers.IO against a pool of 10, so a saturating pool shows up as
 * `hikaricp_connections_pending` climbing minutes before it shows up in any log - where it
 * only appears once requests have already spent 30s timing out.
 *
 * Tags stay low cardinality on purpose: method, route template and status, never a userId,
 * accountId or requestId. Those belong in the log line, which is indexed by nothing and
 * can afford them; a metric label with one value per user is how a Prometheus falls over.
 */
fun newMeterRegistry(): PrometheusMeterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

/** Ktor spells an authenticated route as `/(authenticate auth-jwt-…)/v1/clients`. */
private val AUTH_WRAPPER = Regex("""/\(authenticate [^)]*\)""")

fun Application.configureMetrics(config: AppConfig, meterRegistry: PrometheusMeterRegistry) {
    // The same three identifiers the JSON log lines carry, so a series and a line can be
    // traced back to one deployment of one build.
    meterRegistry.config()
        .commonTags(
            "service", "onsite-backend",
            "env", config.appEnv,
            "version", config.appVersion,
        )
        // Ktor's own local socket address, identical on every request and pure noise in a
        // query - it only widens every series.
        .meterFilter(MeterFilter.ignoreTags("address"))
        // Leaves `route` as the path someone would actually search for.
        .meterFilter(MeterFilter.replaceTagValues("route", { it.replace(AUTH_WRAPPER, "") }))

    install(MicrometerMetrics) {
        registry = meterRegistry
        // Without this, a request to a path that matches no route is labelled with the raw
        // path - so `GET /v1/whatever-i-typed` mints a new time series every time, which is
        // both unbounded and trivially abusable from outside. Grouped under n/a instead.
        distinctNotRegisteredRoutes = false
        meterBinders = listOf(
            JvmMemoryMetrics(),
            JvmGcMetrics(),
            JvmThreadMetrics(),
            ProcessorMetrics(),
            UptimeMetrics(),
        )
        // Percentiles computed in the process: there is one instance, so there is nothing
        // to aggregate across and no reason to ship histogram buckets.
        distributionStatisticConfig = DistributionStatisticConfig.builder()
            .percentiles(0.5, 0.95, 0.99)
            .build()
    }

    val token = config.metricsToken
    if (token == null) {
        logger.info("metrics collected but not exposed: set METRICS_TOKEN to enable /metrics")
        return
    }

    routing {
        get("/metrics") {
            if (!call.bearerTokenMatches(token)) {
                // Logged because a misconfigured scraper is otherwise just a blank
                // dashboard, and an unexpected caller here is worth seeing.
                logger.warn("rejected /metrics scrape: missing or wrong bearer token")
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "unauthorized"))
                return@get
            }
            call.respondText(meterRegistry.scrape())
        }
    }
}

/** Constant-time, so the endpoint can't be used to guess the token a byte at a time. */
private fun ApplicationCall.bearerTokenMatches(expected: String): Boolean {
    val provided = request.authorization()?.removePrefix("Bearer ")?.trim() ?: return false
    return MessageDigest.isEqual(provided.toByteArray(), expected.toByteArray())
}
