description = "Annotations for Exposed KSP"

plugins {
    id("com.github.darkxanter.library-convention")
}

tasks.jar {
    archiveBaseName.set("exposed-ksp-${archiveBaseName.get()}")
}