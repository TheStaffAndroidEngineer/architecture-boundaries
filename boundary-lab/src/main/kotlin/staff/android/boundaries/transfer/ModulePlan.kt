package staff.android.boundaries.transfer

const val MAX_MODULES = 100

enum class Platform { JVM, ANDROID }

data class ModuleSpec(
    val id: String,
    val platform: Platform,
    val dependencies: Set<String>,
)

sealed interface ConsumerPlan {
    data class Ready(val buildOrder: List<String>) : ConsumerPlan
    data class PlatformLeak(val path: List<String>) : ConsumerPlan
    data class Cycle(val path: List<String>) : ConsumerPlan
}

fun checkedModules(
    modules: List<ModuleSpec>,
    roots: Set<String>,
): Map<String, ModuleSpec> {
    require(modules.size <= MAX_MODULES)
    require(modules.all { it.id.isNotBlank() })
    val byId = modules.associateBy { it.id }
    require(byId.size == modules.size)
    require(modules.all { module ->
        module.dependencies.all { it in byId }
    })
    require(roots.all { it in byId })
    return byId
}
