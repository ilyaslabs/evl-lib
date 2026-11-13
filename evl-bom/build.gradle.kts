import com.vanniktech.maven.publish.JavaPlatform

plugins {
    `java-platform`
    id("evl.central-publishing")
}

description = "EVL bill of materials: aligns the versions of all EVL artifacts."

dependencies {
    constraints {
        listOf("evl-annotations", "evl-processor", "evl-processor-ksp", "evl-core", "evl-cli", "evl-maven-plugin")
            .forEach { api(project(":$it")) }
    }
}

mavenPublishing {
    configure(JavaPlatform())
}
