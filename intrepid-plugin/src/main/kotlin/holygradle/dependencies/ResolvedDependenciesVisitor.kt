package holygradle.dependencies

import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ResolvedDependency
import java.util.*

/**
 * Helper class for traversing a graph of resolved dependencies.
 */
object ResolvedDependenciesVisitor {
    /**
     * This class combines a module version ID and configuration name into one immutable, equatable object.
     *
     * When visiting configurations in the original project's dependency graph (as opposed to in the detached
     * configurations for ivy.xml files), we need to track not just which "module versions" we've seen, but which
     * "module version plus module configuration", since dependencies differ per configuration.
     */
    class ResolvedDependencyId(versionId: ModuleVersionIdentifier, configuration: String) :
            java.util.AbstractMap.SimpleImmutableEntry<ModuleVersionIdentifier, String>(versionId, configuration)
    {
        val id: ModuleVersionIdentifier get() = key
    }

    /**
     * A class encapsulating choices about visiting nodes in a graph of {@link org.gradle.api.artifacts
     * .ResolvedDependency} instances.
     *
     * Instances of this class are used, rather than separate boolean flags, so that some of the calculation for the
     * decisions for both node and children can be made together, on the basis of the same state.
     */
    data class VisitChoice(
            /**
             * A flag indicating whether to call a {@code dependencyAction} on a {@link org.gradle.api.artifacts.ResolvedDependency} itself..
             */
            val visitDependency: Boolean,
            /**
             * A value indicating whether to call a {@code dependencyAction} on the children of a {@link org.gradle.api
             * .artifacts.ResolvedDependency}.
             */
            val visitChildren: Boolean
    )

    /**
     * Calls a closure for all {@link ResolvedDependency} instances in the transitive graph, selecting whether or not
     * to visit the children of eah using a predicate.  The same dependency may be visited more than once, if it appears
     * in the graph more than once.
     *
     * @param dependencies The initial set of dependencies.
     * @param getVisitChoice A closure returning an object with flags indicating whether to call the
     * {@code dependencyAction} on a given dependency, and whether to visit its children.
     * @param dependencyAction The closure to call for each {@link ResolvedDependency}.
     */
    private fun doTraverseResolvedDependencies(
        dependencies: Set<ResolvedDependency>,
        dependencyStack: Stack<ResolvedDependency>,
        getVisitChoice: (ResolvedDependency) -> VisitChoice,
        dependencyAction: (ResolvedDependency) -> Unit
    ) {
        // Note: This method used to have the predicates as optional arguments, where null meant "always true", but I
        // kept making mistakes with them, so clearly it was a bad idea.
        for (resolvedDependency in dependencies) {
            try {
                dependencyStack.push(resolvedDependency)
                // println("tRD: ${dependencyStack.join(' <- ')}")

                val visitChoice = getVisitChoice(resolvedDependency)
                // println("  ${visitChoice}")

                if (visitChoice.visitDependency) {
                    dependencyAction(resolvedDependency)
                }

                if (visitChoice.visitChildren) {
                    doTraverseResolvedDependencies(
                            resolvedDependency.children,
                            dependencyStack,
                            getVisitChoice,
                            dependencyAction
                    )
                }
            } finally {
                dependencyStack.pop()
            }
        }
    }

    /**
     * Calls a closure for all {@link ResolvedDependency} instances in the transitive graph, selecting whether or not
     * to visit the children of eah using a predicate.  The same dependency may be visited more than once, if it appears
     * in the graph more than once.
     *
     * @param dependencies The initial set of dependencies.
     * @param getVisitChoice A closure returning an object with flags indicating whether to call the
     * {@code dependencyAction} on a given dependency, and whether to visit its children.
     * @param dependencyAction The closure to call for each {@link ResolvedDependency}.
     */
    fun traverseResolvedDependencies(
        dependencies: Set<ResolvedDependency>,
        getVisitChoice: (ResolvedDependency) -> VisitChoice,
        dependencyAction: (ResolvedDependency) -> Unit
    ) {
        // Note: This method used to have the predicates as optional arguments, where null meant "always true", but I
        // kept making mistakes with them, so clearly it was a bad idea.
        doTraverseResolvedDependencies(
            dependencies,
            Stack<ResolvedDependency>(),
            getVisitChoice,
            dependencyAction
        )
    }
}
