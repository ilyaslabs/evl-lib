plugins {
    id("evl.published-library")
}

description = "EVL Maven plugin: evl:package, evl:validate and evl:index goals."

dependencies {
    implementation(project(":evl-cli"))
    compileOnly("org.apache.maven:maven-plugin-api:3.10.0")
    testImplementation("org.apache.maven:maven-plugin-api:3.10.0")
}

mavenPublishing {
    pom {
        packaging = "maven-plugin"
    }
}

// The plugin descriptor is maintained by hand (META-INF/maven/plugin.xml); only the version is filled in.
tasks.processResources {
    val v = project.version.toString()
    inputs.property("version", v)
    filesMatching("META-INF/maven/plugin.xml") {
        filter { line -> line.replace("@pluginVersion@", v) }
    }
}

tasks.test {
    systemProperty("evl.examples", rootProject.file("examples").absolutePath)
}
