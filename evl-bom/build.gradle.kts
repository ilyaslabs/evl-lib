import com.vanniktech.maven.publish.JavaPlatform

plugins {
    `java-platform`
    id("evl.central-publishing")
}

description = "EVL bill of materials: aligns the versions of all EVL artifacts."

dependencies {
    constraints {
        listOf("evl-annotations", "evl-core", "evl-cli")
            .forEach { api(project(":$it")) }
    }
}

mavenPublishing {
    configure(JavaPlatform())
}
