plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
}

// whisper.cpp is compiled into the app. It is fetched at build time so the git
// repo stays small. Pin the release tag; do not float to master.
tasks.register("fetchWhisperCpp") {
    val dest = layout.projectDirectory.dir("third_party/whisper.cpp").asFile
    outputs.dir(dest)
    onlyIf { !File(dest, "include/whisper.h").exists() }
    doLast {
        dest.parentFile.mkdirs()
        exec {
            commandLine(
                "git",
                "clone",
                "--depth",
                "1",
                "--branch",
                "v1.9.5",
                "https://github.com/ggml-org/whisper.cpp.git",
                dest.absolutePath,
            )
        }
    }
}
