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

rootProject.name = "Curfew"
include(":app")

providers.environmentVariable("CURFEW_PROTOCOLS_CHECKOUT").orNull?.let { checkout ->
    includeBuild(checkout) {
        dependencySubstitution {
            substitute(module("studio.hypertext.curfew:curfew-protocols"))
                .using(project(":generated:kotlin"))
        }
    }
}
