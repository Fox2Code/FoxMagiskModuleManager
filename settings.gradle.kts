@file:Suppress("UnstableApiUsage")
plugins {
    id("com.gradle.enterprise") version("3.13")
}

gradleEnterprise {
    if (System.getenv("CI") != null) {
        buildScan {
            publishAlways()
            termsOfServiceUrl = "https://gradle.com/terms-of-service"
            termsOfServiceAgree = "yes"
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // enable jitpack
        maven { setUrl("https://jitpack.io") }
    }
}

// val isCiServer = System.getenv().containsKey("CI")
// Cache build artifacts, so expensive operations do not need to be re-computed
buildCache {
   local {
       isEnabled = true
   }
}

rootProject.name = "MagiskModuleManager"
include(":app")
