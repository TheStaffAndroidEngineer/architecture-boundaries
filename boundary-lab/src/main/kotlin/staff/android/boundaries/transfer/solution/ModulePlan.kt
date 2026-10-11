package staff.android.boundaries.transfer.solution

import staff.android.boundaries.transfer.*

fun planConsumer(
    modules: List<ModuleSpec>,
    roots: Set<String>,
    target: Platform,
): ConsumerPlan {
    val byId = checkedModules(modules, roots)
    val active = mutableListOf<String>()
    val activeIndex = mutableMapOf<String, Int>()
    val completed = mutableSetOf<String>()
    val order = mutableListOf<String>()

    fun visit(id: String): ConsumerPlan? {
        val module = byId.getValue(id)
        if (target == Platform.JVM &&
            module.platform == Platform.ANDROID)
            return ConsumerPlan.PlatformLeak(active + id)
        val cycleStart = activeIndex[id]
        if (cycleStart != null)
            return ConsumerPlan.Cycle(active.drop(cycleStart) + id)
        if (id in completed) return null

        activeIndex[id] = active.size
        active.add(id)
        for (dependency in module.dependencies.sorted()) {
            val failure = visit(dependency)
            if (failure != null) return failure
        }
        active.removeAt(active.lastIndex)
        activeIndex.remove(id)
        completed.add(id)
        order.add(id)
        return null
    }

    for (root in roots.sorted()) {
        val failure = visit(root)
        if (failure != null) return failure
    }
    return ConsumerPlan.Ready(order.toList())
}
