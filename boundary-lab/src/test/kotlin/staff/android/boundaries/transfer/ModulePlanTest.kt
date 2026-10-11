package staff.android.boundaries.transfer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

abstract class ModulePlanContract {
    abstract fun plan(
        modules: List<ModuleSpec>,
        roots: Set<String>,
        target: Platform,
    ): ConsumerPlan

    @ParameterizedTest(name = "{0}")
    @MethodSource("histories")
    fun returnsTheExactPlanOrFirstWitness(
        label: String,
        modules: List<ModuleSpec>,
        roots: Set<String>,
        target: Platform,
        expected: ConsumerPlan,
    ) {
        assertEquals(expected, plan(modules, roots, target), label)
    }

    @Test fun graphDeclarationOrderDoesNotChangeThePlan() {
        val nodes = listOf(
            jvm("root", "b", "a"), jvm("b", "shared"),
            jvm("a", "shared"), jvm("shared"), jvm("unrelated"),
        )
        val reordered = nodes.reversed().map {
            it.copy(dependencies = it.dependencies.reversed().toSet())
        }
        val expected = ConsumerPlan.Ready(listOf("shared", "a", "b", "root"))
        assertEquals(expected, plan(nodes, setOf("root"), Platform.JVM))
        assertEquals(expected, plan(reordered, setOf("root"), Platform.JVM))
    }

    @Test fun preservesSuppliedListsSetsAndExactIds() {
        val dependencies = linkedSetOf(" text ")
        val roots = linkedSetOf("report")
        val nodes = mutableListOf(
            ModuleSpec("report", Platform.JVM, dependencies), jvm(" text "),
        )
        val before = nodes.map { it.copy(dependencies = it.dependencies.toSet()) }
        assertEquals(
            ConsumerPlan.Ready(listOf(" text ", "report")),
            plan(nodes, roots, Platform.JVM),
        )
        assertEquals(before, nodes)
        assertEquals(setOf(" text "), dependencies)
        assertEquals(setOf("report"), roots)
    }

    @Test fun supportsTheEntireDeclaredGraphBound() {
        val ids = (0 until MAX_MODULES).map { "m%03d".format(it) }
        val modules = ids.mapIndexed { index, id ->
            ModuleSpec(
                id, Platform.JVM,
                ids.getOrNull(index + 1)?.let { setOf(it) } ?: emptySet(),
            )
        }
        assertEquals(
            ConsumerPlan.Ready(ids.reversed()),
            plan(modules, setOf(ids.first()), Platform.JVM),
        )
    }

    @Test fun rejectsBlankAndDuplicateIdsEvenOutsideTheClosure() {
        for (nodes in listOf(
            listOf(jvm("ok"), jvm(" ")),
            listOf(jvm("ok"), jvm("unused"), android("unused")),
        )) {
            assertThrows(IllegalArgumentException::class.java) {
                plan(nodes, setOf("ok"), Platform.JVM)
            }
        }
    }

    @Test fun rejectsUnknownDependenciesAndRootsBeforeTraversal() {
        assertThrows(IllegalArgumentException::class.java) {
            plan(
                listOf(jvm("ok"), jvm("unused", "missing")),
                emptySet(), Platform.JVM,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            plan(listOf(android("a")), setOf("missing"), Platform.JVM)
        }
    }

    @Test fun rejectsLargerGraphsEvenWithNoSelectedRoots() {
        assertThrows(IllegalArgumentException::class.java) {
            plan((0..MAX_MODULES).map { jvm("m$it") }, emptySet(), Platform.JVM)
        }
    }

    companion object {
        private fun jvm(id: String, vararg dependencies: String) =
            ModuleSpec(id, Platform.JVM, dependencies.toSet())
        private fun android(id: String, vararg dependencies: String) =
            ModuleSpec(id, Platform.ANDROID, dependencies.toSet())

        @JvmStatic fun histories(): List<Arguments> = listOf(
            Arguments.of(
                "pure report chain builds providers first",
                listOf(jvm("report-core", "report-format"),
                    jvm("report-format", "report-text"), jvm("report-text")),
                setOf("report-core"), Platform.JVM,
                ConsumerPlan.Ready(listOf("report-text", "report-format", "report-core")),
            ),
            Arguments.of(
                "transitive Android dependency includes the full root path",
                listOf(jvm("report-core", "report-format"),
                    jvm("report-format", "android-paths"), android("android-paths")),
                setOf("report-core"), Platform.JVM,
                ConsumerPlan.PlatformLeak(listOf("report-core", "report-format", "android-paths")),
            ),
            Arguments.of(
                "Android consumer accepts Android and JVM providers",
                listOf(android("report-ui", "report-core"),
                    jvm("report-core", "android-paths"), android("android-paths")),
                setOf("report-ui"), Platform.ANDROID,
                ConsumerPlan.Ready(listOf("android-paths", "report-core", "report-ui")),
            ),
            Arguments.of(
                "Android root is already a JVM leak",
                listOf(android("report-ui")), setOf("report-ui"), Platform.JVM,
                ConsumerPlan.PlatformLeak(listOf("report-ui")),
            ),
            Arguments.of(
                "cycle witness excludes the acyclic prefix",
                listOf(jvm("importer", "report-core"),
                    jvm("report-core", "report-format"), jvm("report-format", "report-core")),
                setOf("importer"), Platform.JVM,
                ConsumerPlan.Cycle(listOf("report-core", "report-format", "report-core")),
            ),
            Arguments.of(
                "self dependency is a closed cycle",
                listOf(jvm("report", "report")), setOf("report"), Platform.JVM,
                ConsumerPlan.Cycle(listOf("report", "report")),
            ),
            Arguments.of(
                "unselected cycle and Android platform do not block a pure root",
                listOf(jvm("report"), android("unused", "unused")),
                setOf("report"), Platform.JVM, ConsumerPlan.Ready(listOf("report")),
            ),
            Arguments.of(
                "diamond includes shared provider once",
                listOf(jvm("report", "b", "a"), jvm("a", "shared"),
                    jvm("b", "shared"), jvm("shared")),
                setOf("report"), Platform.JVM,
                ConsumerPlan.Ready(listOf("shared", "a", "b", "report")),
            ),
            Arguments.of(
                "a root already visited as a provider is not repeated",
                listOf(jvm("a", "z"), jvm("z")), setOf("z", "a"), Platform.JVM,
                ConsumerPlan.Ready(listOf("z", "a")),
            ),
            Arguments.of(
                "independent selected roots are sorted",
                listOf(jvm("z"), jvm("a")), setOf("z", "a"), Platform.JVM,
                ConsumerPlan.Ready(listOf("a", "z")),
            ),
            Arguments.of(
                "empty roots ignore behavioral defects in a valid registry",
                listOf(android("unused", "unused")), emptySet<String>(), Platform.JVM,
                ConsumerPlan.Ready(emptyList()),
            ),
            Arguments.of(
                "empty registry and roots need no work",
                emptyList<ModuleSpec>(), emptySet<String>(), Platform.JVM,
                ConsumerPlan.Ready(emptyList()),
            ),
            Arguments.of(
                "sorted first root cycle precedes later platform leak",
                listOf(android("z"), jvm("a", "a")), setOf("z", "a"), Platform.JVM,
                ConsumerPlan.Cycle(listOf("a", "a")),
            ),
            Arguments.of(
                "sorted dependency platform leak precedes later cycle",
                listOf(jvm("root", "z", "a"), jvm("z", "z"), android("a")),
                setOf("root"), Platform.JVM,
                ConsumerPlan.PlatformLeak(listOf("root", "a")),
            ),
            Arguments.of(
                "platform check on entry precedes that node's self cycle",
                listOf(android("a", "a")), setOf("a"), Platform.JVM,
                ConsumerPlan.PlatformLeak(listOf("a")),
            ),
        )
    }
}

class ModulePlanReferenceTest : ModulePlanContract() {
    override fun plan(modules: List<ModuleSpec>, roots: Set<String>, target: Platform) =
        staff.android.boundaries.transfer.solution.planConsumer(modules, roots, target)
}

@Tag("exercise")
class ModulePlanExerciseTest : ModulePlanContract() {
    override fun plan(modules: List<ModuleSpec>, roots: Set<String>, target: Platform) =
        staff.android.boundaries.transfer.exercise.planConsumer(modules, roots, target)
}
