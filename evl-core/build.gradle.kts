plugins {
    id("evl.published-library")
}

description = "EVL core: canonical model, YAML and index parsing, resolution, validation and graph queries."

dependencies {
    implementation("org.snakeyaml:snakeyaml-engine:3.2")
    testImplementation("org.snakeyaml:snakeyaml-engine:3.2")
    testImplementation("com.networknt:json-schema-validator:1.5.8")
    testImplementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.19.2")
}

// Regenerates spec/schema/*.json and spec/rules.yaml from the spec tables (Kind, StepType, Rule).
tasks.register<JavaExec>("generateSpec") {
    group = "build"
    description = "Regenerates the JSON Schemas and rules.yaml in spec/."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("io.github.ilyaslabs.evl.core.spec.SpecGenerator")
    args(rootProject.layout.projectDirectory.dir("spec").asFile.absolutePath)
}

tasks.test {
    systemProperty("evl.spec", rootProject.file("spec").absolutePath)
    systemProperty("evl.examples", rootProject.file("examples").absolutePath)
}
