plugins {
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(project(":shared:common-logging"))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.springdoc.openapi.webmvc.ui)
    runtimeOnly(libs.ojdbc11)

    testImplementation(libs.spring.boot.starter.test)
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
