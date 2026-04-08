plugins {
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(project(":shared:common-logging"))
    implementation(libs.spring.boot.starter.webflux)
    implementation(libs.spring.boot.starter.data.r2dbc)
    implementation(libs.oracle.r2dbc)
    implementation(libs.spring.boot.starter.data.redis.reactive)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.jjwt.api)
    runtimeOnly(libs.jjwt.impl)
    runtimeOnly(libs.jjwt.jackson)
    implementation(libs.springdoc.openapi.webflux.ui)
}
