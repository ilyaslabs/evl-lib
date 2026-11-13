plugins {
    id("evl.published-library")
}

description = "EVL core: scans @Flow paths, // @flow markers and @EVL symbols from Java and Kotlin sources into a flow tree."

tasks.test {
    systemProperty("evl.examples", rootProject.file("examples").absolutePath)
}
