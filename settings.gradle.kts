@file:Suppress("UnstableApiUsage")

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // enable jitpack
        maven { setUrl("https://jitpack.io") }
    }
}
buildCache {
    local {
        isEnabled = true
    }
}
rootProject.name = "MagiskModuleManager"
include(":app")