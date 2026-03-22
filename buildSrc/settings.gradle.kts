dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
        create("aniyomilibs") {
            from(files("../gradle/aniyomi.versions.toml"))
        }
    }
}

rootProject.name = "Animetail"
