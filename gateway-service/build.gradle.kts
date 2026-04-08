plugins {
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(project(":shared:common-logging"))
    implementation(libs.spring.cloud.starter.gateway.server.webflux)
    implementation(libs.spring.cloud.starter.config)
    implementation(libs.spring.cloud.starter.bus.kafka)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.data.redis.reactive)
    implementation(libs.bucket4j.core)
    implementation(libs.bucket4j.redis)
    implementation(libs.springdoc.openapi.webflux.ui)
}
