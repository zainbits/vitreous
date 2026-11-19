pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "vitreous"
include(":vitreous")
include(":sample")

// The shared engine (shapes, springs, glass renderer), built from its checkout next to this one.
includeBuild("../liquid-core") {
    dependencySubstitution {
        substitute(module("io.github.zainbits:liquid-core")).using(project(":core"))
    }
}
