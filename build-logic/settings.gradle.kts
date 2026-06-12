// Share the root build's version catalog so the convention plugin and this build resolve the same
// pins. Included builds do not inherit `gradle/libs.versions.toml` automatically.
dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "kt-fuzzer-build-logic"
