plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

// Each module sets its own bytecode level through the `evl.javaRelease` extra property
// (8 for annotations, 17 for core and the CLI). The build itself runs on
// any JDK >= 21; CI uses JDK 25 via ilyaslabs/cicd.
val javaRelease: Int = (findProperty("evl.javaRelease") as String?)?.toInt() ?: 17

java {
    sourceCompatibility = JavaVersion.toVersion(javaRelease)
    targetCompatibility = JavaVersion.toVersion(javaRelease)
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    testImplementation("org.assertj:assertj-core:3.27.6")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = javaRelease
    options.compilerArgs.addAll(listOf("-parameters", "-Xlint:all,-processing,-serial,-try,-options"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
