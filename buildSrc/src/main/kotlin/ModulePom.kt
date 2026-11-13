import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.api.Project

/** Shared POM metadata required by Maven Central. */
fun MavenPublishBaseExtension.modulePom(project: Project) {
    pom {
        name.set(project.name)
        description.set(project.provider { project.description ?: "EVL: Engineering Visualization Language" })
        inceptionYear.set("2026")
        url.set("https://github.com/ilyaslabs/evl-lib")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("ilyaslabs")
                name.set("Muhammad ilyas")
                email.set("m.ilyas@live.com")
                organizationUrl.set("https://ilyaslabs.github.io/")
            }
        }
        scm {
            url.set("https://github.com/ilyaslabs/evl-lib")
            connection.set("scm:git:https://github.com/ilyaslabs/evl-lib.git")
            developerConnection.set("scm:git:ssh://git@github.com/ilyaslabs/evl-lib.git")
        }
        issueManagement {
            system.set("GitHub")
            url.set("https://github.com/ilyaslabs/evl-lib/issues")
        }
    }
}
