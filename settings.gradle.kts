pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "architecture-boundaries"
include(":app", ":monolith", ":repaired", ":cart-api", ":cart-impl", ":feature-cart", ":feature-saved", ":boundary-lab")
