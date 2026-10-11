package staff.android.boundaries.transfer.exercise

import staff.android.boundaries.transfer.*

// Deliberately incomplete: names the selected roots, not their providers.
fun planConsumer(
    modules: List<ModuleSpec>,
    roots: Set<String>,
    target: Platform,
): ConsumerPlan {
    checkedModules(modules, roots)
    return ConsumerPlan.Ready(roots.sorted())
}
