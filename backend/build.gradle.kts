import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.24"
    kotlin("plugin.serialization") version "1.9.24"
    id("io.ktor.plugin") version "2.3.13"
}

group = "com.xbertz.onsite.backend"
version = "0.1.0"

application {
    mainClass.set("com.xbertz.onsite.backend.ApplicationKt")
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}

val exposedVersion = "0.52.0"
val ktorVersion = "2.3.13"

dependencies {
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jwt-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-call-logging-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-cors-jvm:$ktorVersion")

    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-kotlin-datetime:$exposedVersion")

    implementation("org.postgresql:postgresql:42.7.4")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.flywaydb:flyway-core:10.17.3")
    implementation("org.flywaydb:flyway-database-postgresql:10.17.3")

    implementation("ch.qos.logback:logback-classic:1.5.8")

    testImplementation("io.ktor:ktor-server-test-host-jvm:$ktorVersion")
    // `useJUnitPlatform()` below runs on the JUnit 5 platform, which needs the Jupiter engine
    // on the classpath to actually discover and execute tests - kotlin-test-junit (no "5")
    // only binds kotlin.test to JUnit 4 and silently runs zero tests under the platform.
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:1.9.24")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.3")
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

// flyway-core and flyway-database-postgresql each ship their own
// META-INF/services/org.flywaydb.core.extensibility.Plugin file. Shadow's default merge
// strategy keeps only one, silently dropping the other's plugin registrations - which broke
// Flyway's migration scanning when run from the fat jar (it saw the 4 SQL migrations but
// rejected all of them as not matching the naming convention, applying none). buildFatJar
// and :run use different classpaths (one merged jar vs. exploded directories), so this only
// ever showed up in the packaged jar. Merging the service files keeps every module's
// registrations intact, matching how :run resolves them.
// flyway-core and flyway-database-postgresql each ship their own
// META-INF/services/org.flywaydb.core.extensibility.Plugin file. Shadow's default merge
// strategy keeps only one, silently dropping the other's plugin registrations - which broke
// Flyway's migration scanning when run from the fat jar (it saw the 4 SQL migrations but
// rejected all of them as not matching the naming convention, applying none). buildFatJar
// and :run use different classpaths (one merged jar vs. exploded directories), so this only
// ever showed up in the packaged jar. Merging the service files keeps every module's
// registrations intact, matching how :run resolves them.
// The Ktor Gradle plugin applies Shadow internally rather than via this script's own
// `plugins {}` block, so its task type isn't resolvable here for a typed `tasks.named<>` -
// withGroovyBuilder configures it dynamically instead.
tasks.named("shadowJar") {
    withGroovyBuilder {
        "mergeServiceFiles"()
    }
}

tasks.test {
    useJUnitPlatform()
}
