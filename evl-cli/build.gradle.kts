plugins {
    id("evl.published-library")
}

description = "EVL command-line tool: validate, inspect, list, graph, index and format EVL documentation."

dependencies {
    api(project(":evl-core"))
    implementation("info.picocli:picocli:4.7.7")
    implementation("com.github.javaparser:javaparser-core:3.27.0")
    implementation("org.ow2.asm:asm:9.8")
    implementation("org.snakeyaml:snakeyaml-engine:3.2")
    testImplementation(project(":evl-processor"))
    testImplementation(project(":evl-annotations"))
}

tasks.processResources {
    val v = project.version.toString()
    inputs.property("version", v)
    filesMatching("**/evl-version.properties") {
        expand("version" to v)
    }
}

// Runnable fat jar: evl-cli-<version>-all.jar (attached to GitHub releases).
val allJar by tasks.registering(Jar::class) {
    group = "build"
    description = "Assembles the runnable evl CLI jar with all dependencies."
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes("Main-Class" to "io.github.ilyaslabs.evl.cli.Main", "Implementation-Version" to project.version)
    }
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({ configurations.runtimeClasspath.get().filter { it.name.endsWith(".jar") }.map { zipTree(it) } }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/versions/*/module-info.class", "module-info.class")
    }
}

tasks.assemble { dependsOn(allJar) }

tasks.test {
    systemProperty("evl.examples", rootProject.file("examples").absolutePath)
}
