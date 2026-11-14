plugins {
    id("evl.published-library")
}

description = "EVL command-line tool: prints the flow tree of a project, or its JSON model."

dependencies {
    api(project(":evl-core"))
}

tasks.processResources {
    val v = project.version.toString()
    inputs.property("version", v)
    filesMatching("**/evl-version.properties") {
        expand("version" to v)
    }
}

// Runnable jar: evl-cli-<version>-all.jar.
val allJar = tasks.register<Jar>("allJar") {
    group = "build"
    description = "Assembles the runnable evl CLI jar with all dependencies."
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes("Main-Class" to "io.github.ilyaslabs.evl.cli.Main", "Implementation-Version" to project.version)
    }
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({ configurations.runtimeClasspath.get().filter { it.name.endsWith(".jar") }.map { zipTree(it) } })
}

tasks.assemble { dependsOn(allJar) }

tasks.test {
    systemProperty("evl.examples", rootProject.file("examples").absolutePath)
}
