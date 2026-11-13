plugins {
    id("com.vanniktech.maven.publish")
    signing
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()
    modulePom(project)
}

// CI imports the key into gpg and passes its id as signing.gnupg.keyName; gpg-agent holds the passphrase.
if (providers.gradleProperty("signing.gnupg.keyName").isPresent) {
    signing {
        useGpgCmd()
    }
}
