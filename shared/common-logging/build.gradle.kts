plugins {
    `java-library`
}

dependencies {
    api("org.springframework:spring-context")
    api("org.springframework.boot:spring-boot-starter-log4j2")
    api("org.apache.logging.log4j:log4j-layout-template-json")
    api(libs.opentelemetry.sdk)
    api(libs.opentelemetry.exporter.otlp)
    api(libs.opentelemetry.log4j.appender)
}
