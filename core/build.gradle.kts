import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `kord-module`
    `kord-sampled-module`
    `kord-publishing`
}

dependencies {
    api(common)
    api(rest)
    api(gateway)
    api(voice)

    implementation(libs.bundles.common)

    api(libs.kord.cache.api)
    api(libs.kord.cache.map)

    @Suppress("UnstableApiUsage")
    samplesImplementation(libs.slf4j.simple)
    samplesImplementation(libs.lavaplayer)
    testImplementation(libs.mockk)
    testImplementation(libs.bundles.test.implementation)
    testRuntimeOnly(libs.bundles.test.runtime)
}

// Move this into the build script plugins when implemented in all modules
kotlin {
    explicitApi()
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = freeCompilerArgs + CompilerArguments.stdLib
    }
}
