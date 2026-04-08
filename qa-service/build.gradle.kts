plugins {
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(project(":shared:common-logging"))
    implementation(libs.spring.boot.starter.webflux)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.springdoc.openapi.webflux.ui)
}
