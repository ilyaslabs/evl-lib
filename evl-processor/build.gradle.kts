plugins {
    id("evl.published-library")
}

description = "EVL annotation processor: extracts @EVL.* declarations into META-INF/evl/index.json. Java 8+, no runtime dependencies."

dependencies {
    compileOnly(project(":evl-annotations"))
    testImplementation(project(":evl-annotations"))
    testImplementation(project(":evl-core"))
}

// Tests compile fixtures with javac, which needs a newer test runtime than the Java 8 main code.
tasks.named<JavaCompile>("compileTestJava") {
    options.release.set(17)
}
