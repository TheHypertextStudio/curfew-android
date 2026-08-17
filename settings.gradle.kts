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
        maven {
            name = "CurfewProtocolPackages"
            url = uri("https://maven.pkg.github.com/TheHypertextStudio/curfew-protocols")
            credentials {
                username = providers.environmentVariable("GITHUB_ACTOR")
                    .orElse(providers.gradleProperty("gpr.user"))
                    .getOrElse("")
                password = providers.environmentVariable("GITHUB_TOKEN")
                    .orElse(providers.gradleProperty("gpr.key"))
                    .getOrElse("")
            }
            content { includeGroup("studio.hypertext.curfew") }
        }
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
