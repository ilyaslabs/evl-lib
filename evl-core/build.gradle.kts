plugins {
    id("evl.published-library")
}

description = "EVL core: canonical model, YAML and index parsing, resolution, validation and graph queries."

dependencies {
    implementation("org.snakeyaml:snakeyaml-engine:2.10")
}
