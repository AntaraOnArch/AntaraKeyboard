package com.example.antarakeyboard.data

/**
 * Third-party components shipped in the app, shown in About → Open-source licenses.
 * Each license text lives in assets/licenses/. Keep this list in sync with the
 * release runtime dependencies (`./gradlew :app:dependencies --configuration releaseRuntimeClasspath`).
 */
object OpenSourceLicenses {

    data class Entry(
        val name: String,
        val copyright: String,
        val licenseName: String,
        /** File in assets/licenses/. */
        val licenseAsset: String,
        val url: String
    )

    private const val APACHE = "Apache License 2.0"
    private const val APACHE_FILE = "apache-2.0.txt"

    val ENTRIES = listOf(
        Entry(
            "SymSpellKt", "© 2024 Adam Brown, © 2019 Lucky Sharma, © 2018 Wolf Garbe",
            "MIT License", "mit-symspellkt.txt", "https://github.com/Wavesonics/SymSpellKt"
        ),
        Entry(
            "kotlinx-murmurhash (in SymSpellKt)", "© 2021-2022 Gonçalo Silva",
            "MIT License", "mit-murmurhash.txt", "https://github.com/goncalossilva/kotlinx-murmurhash"
        ),
        Entry(
            "FrequencyWords (word suggestion dictionaries)", "Hermit Dave, OpenSubtitles 2018",
            "CC BY-SA 4.0", "cc-by-sa-4.0.txt", "https://github.com/hermitdave/FrequencyWords"
        ),
        Entry(
            "AndroidX (AppCompat, Core, Lifecycle, …)", "© The Android Open Source Project",
            APACHE, APACHE_FILE, "https://developer.android.com/jetpack/androidx"
        ),
        Entry(
            "Material Components for Android", "© Google LLC",
            APACHE, APACHE_FILE, "https://github.com/material-components/material-components-android"
        ),
        Entry(
            "Kotlin standard library", "© JetBrains s.r.o. and Kotlin Programming Language contributors",
            APACHE, APACHE_FILE, "https://kotlinlang.org"
        ),
        Entry(
            "kotlinx.coroutines", "© JetBrains s.r.o. and contributors",
            APACHE, APACHE_FILE, "https://github.com/Kotlin/kotlinx.coroutines"
        ),
        Entry(
            "Gson", "© Google Inc.",
            APACHE, APACHE_FILE, "https://github.com/google/gson"
        ),
        Entry(
            "Guava ListenableFuture", "© The Guava Authors",
            APACHE, APACHE_FILE, "https://github.com/google/guava"
        ),
        Entry(
            "Error Prone annotations", "© The Error Prone Authors",
            APACHE, APACHE_FILE, "https://github.com/google/error-prone"
        ),
        Entry(
            "JetBrains Java Annotations", "© JetBrains s.r.o.",
            APACHE, APACHE_FILE, "https://github.com/JetBrains/java-annotations"
        ),
        Entry(
            "JSpecify", "© The JSpecify Authors",
            APACHE, APACHE_FILE, "https://jspecify.dev"
        )
    )
}
