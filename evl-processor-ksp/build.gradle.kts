import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("evl.published-library")
    kotlin("jvm")
}

description = "EVL KSP processor: extracts @EVL.* declarations from Kotlin sources into META-INF/evl/index.json."

dependencies {
    implementation(project(":evl-processor"))
    compileOnly("com.google.devtools.ksp:symbol-processing-api:2.2.20-2.0.4")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
    }
}
