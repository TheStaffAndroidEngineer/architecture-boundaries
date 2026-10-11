import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile

buildscript { dependencies { classpath(libs.kotlinGradlePlugin) } }

plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinCompose) apply false
}

abstract class BoundaryFitness : DefaultTask() {
    @get:Input abstract val edges: ListProperty<String>
    @get:OutputFile abstract val report: RegularFileProperty

    @TaskAction
    fun verify() {
        val actual = edges.get().sorted()
        report.get().asFile.apply {
            parentFile.mkdirs()
            writeText(actual.joinToString("\n") + "\n")
        }
        val expected =
            setOf(
                ":app|implementation|:cart-impl",
                ":app|implementation|:feature-cart",
                ":app|implementation|:feature-saved",
                ":app|implementation|:cart-api",
                ":cart-impl|api|:cart-api",
                ":feature-cart|api|:cart-api",
                ":feature-saved|api|:cart-api",
            )
        check(actual.toSet() == expected) {
            "Boundary violation. " +
                "Extra: ${actual.toSet() - expected}; " +
                "missing: ${expected - actual.toSet()}"
        }
        logger.lifecycle(
            "BOUNDARIES PASS: ${actual.size} declared production edges"
        )
    }
}

tasks.register<BoundaryFitness>("verifyBoundaries") {
    val production =
        setOf(
            ":app",
            ":cart-api",
            ":cart-impl",
            ":feature-cart",
            ":feature-saved",
        )
    edges.set(
        provider {
            subprojects
                .filter { it.path in production }
                .flatMap { owner ->
                    owner.configurations
                        .filter { configuration ->
                            val name = configuration.name.lowercase()
                            !name.contains("test") &&
                                listOf(
                                        "api",
                                        "implementation",
                                        "compileonly",
                                        "runtimeonly",
                                    )
                                    .any { name.endsWith(it) }
                        }
                        .flatMap { configuration ->
                            configuration.dependencies
                                .withType(ProjectDependency::class.java)
                                .map {
                                    "${owner.path}|${configuration.name}|${it.path}"
                                }
                        }
                }
        }
            .get()
    )
    report.set(layout.buildDirectory.file("reports/module-edges.tsv"))
}
