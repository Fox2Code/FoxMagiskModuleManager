// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven {
            setUrl("https://jitpack.io")
        }
    }
    extra.apply {
        set("sentryConfigFile", rootProject.file("sentry.properties"))
        set("hasSentryConfig", false)
        set("sentryVersion", "6.17.0")
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.1.0-beta01")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.8.20")
        classpath("com.mikepenz.aboutlibraries.plugin:aboutlibraries-plugin:10.6.2")

        // NOTE: Do not place your application dependencies here; they belong
        // in the individual module build.gradle files
        //noinspection GradleDependency
        classpath("io.realm:realm-gradle-plugin:10.14.0-transformer-api")
        classpath("io.sentry:sentry-android-gradle-plugin:3.5.0")
    }
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}
afterEvaluate {
    tasks.withType<JavaCompile>().configureEach {
        sourceCompatibility = JavaVersion.VERSION_17.toString()
        targetCompatibility = JavaVersion.VERSION_17.toString()
    }
}
val service = project.extensions.getByType<JavaToolchainService>()
val customLauncher = service.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(17))
}
project.tasks.withType<UsesKotlinJavaToolchain>().configureEach {
    kotlinJavaToolchain.toolchain.use(customLauncher)
}
