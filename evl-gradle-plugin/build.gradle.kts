import com.vanniktech.maven.publish.GradlePlugin
import com.vanniktech.maven.publish.JavadocJar

plugins {
    id("evl.java-conventions")
    id("evl.central-publishing")
    `java-gradle-plugin`
}

description = "EVL Gradle plugin: runs the EVL processor, packages EVL data into the jar and adds evlValidate and evlIndex tasks."

dependencies {
    implementation(project(":evl-cli"))
    testImplementation(gradleTestKit())
}

gradlePlugin {
    plugins {
        create("evl") {
            id = "io.github.ilyaslabs.evl"
            implementationClass = "io.github.ilyaslabs.evl.gradle.EvlPlugin"
            displayName = "EVL"
            description = "Validate and package EVL architecture and workflow documentation."
        }
    }
}

mavenPublishing {
    configure(GradlePlugin(javadocJar = JavadocJar.Javadoc(), sourcesJar = true))
}

tasks.processResources {
    val v = project.version.toString()
    inputs.property("version", v)
    filesMatching("**/evl-gradle.properties") {
        expand("version" to v)
    }
}

tasks.test {
    systemProperty("evl.examples", rootProject.file("examples").absolutePath)
}
