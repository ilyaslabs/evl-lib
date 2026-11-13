// Shared conventions live in buildSrc (evl.java-conventions / evl.published-library / evl.central-publishing).
rootProject.name = "evl-lib"

include(
    "evl-annotations",
    "evl-core",
    "evl-processor",
    "evl-processor-ksp",
    "evl-cli",
    "evl-gradle-plugin",
    "evl-maven-plugin",
    "evl-bom",
)
