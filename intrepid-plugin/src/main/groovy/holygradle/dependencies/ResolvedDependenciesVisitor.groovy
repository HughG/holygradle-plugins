package holygradle.dependencies

import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ResolvedDependency

/**
 * Helper class for traversing a graph of resolved dependencies.
 */
class ResolvedDependenciesVisitor {
    /**
     * This class combines a module version ID and configuration name into one immutable, equatable object.
     *
     * When visiting configurations in the original project's dependency graph (as opposed to in the detached
     * configurations for ivy.xml files), we need to track not just which "module versions" we've seen, but which
     * "module version plus module configuration", since dependencies differ per configuration.
     */
    public static class ResolvedDependencyId
        extends AbstractMap.SimpleImmutableEntry<ModuleVersionIdentifier, String>
    {
        /**
         *  Creates an entry representing a resolved module version, visited under a particular configuration.
         *
         * @param key the key represented by this entry
         * @param value the value represented by this entry
         */
        ResolvedDependencyId(ModuleVersionIdentifier versionId, String configuration) {
            super(versionId, configuration)
        }

        public ModuleVersionIdentifier getId() { super.getKey() }
        //public String getConfiguration() { super.getValue() }
    }

    /**
     * A class encapsulating choices about visiting nodes in a graph of {@link org.gradle.api.artifacts
     * .ResolvedDependency} instances.
     *
     * Instances of this class are used, rather than separate boolean flags, so that some of the calculation for the
     * decisions for both node and children can be made together, on the basis of the same state.
     */
    public static class VisitChoice {
        /**
         * A flag indicating whether to call a {@code dependencyAction} on a {@link org.gradle.api.artifacts.ResolvedDependency} itself..
         */
        public final boolean visitDependency
        /**
         * A value indicating whether to call a {@code dependencyAction} on the children of a {@link org.gradle.api
         * .artifacts.ResolvedDependency}.
         */
        public final boolean visitChildren

        /**
         * Creates an instance of {@link holygradle.dependencies.ResolvedDependenciesVisitor.VisitChoice}.
         * @param visitDependency A flag indicating whether to call a {@code dependencyAction} on a
         * {@link org.gradle.api.artifacts.ResolvedDependency} itself.
         * @param visitChildren A value indicating whether to call a {@code dependencyAction} on the children of a
         * {@link org.gradle.api.artifacts.ResolvedDependency}.
         */
        VisitChoice(boolean visitDependency, boolean visitChildren) {
            this.visitDependency = visitDependency
            this.visitChildren = visitChildren
        }
    }

    /**
     * Calls a closure for all {@link ResolvedDependency} instances in the transitive graph, selecting whether or not
     * to visit the children of eah using a predicate.  The same dependency may be visited more than once, if it appears
     * in the graph more than once.
     *
     * @param dependencies The initial set of dependencies.
     * @param dependencyAction The closure to call for each {@link ResolvedDependency}.
     * @param visitDependencyPredicate A predicate closure to call for {@link ResolvedDependency} to decide whether to
     * call the dependencyAction for the dependency.
     * @param visitChildrenPredicate A predicate closure to call for {@link ResolvedDependency} to decide whether to
     * visit its children.  The children may be visited even if the visitDependencyPredicate returned false.
     */
    private static void traverseResolvedDependencies(
        Set<ResolvedDependency> dependencies,
        Stack<ResolvedDependency> dependencyStack,
        Closure dependencyAction,
        Closure getVisitChoice
    ) {
        // Note: This method used to have the predicates as optional arguments, where null meant "always true", but I
        // kept making mistakes with them, so clearly it was a bad idea.
        dependencies.each { resolvedDependency ->
            try {
                dependencyStack.push(resolvedDependency)
                //println("tRD: ${dependencyStack.join(' <- ')}")

                VisitChoice visitChoice = getVisitChoice(resolvedDependency)

                if (visitChoice.visitDependency) {
                    dependencyAction(resolvedDependency)
                }

                if (visitChoice.visitChildren) {
                    traverseResolvedDependencies(
                        resolvedDependency.children,
                        dependencyStack,
                        dependencyAction,
                        getVisitChoice
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
     * @param dependencyAction The closure to call for each {@link ResolvedDependency}.
     * @param visitDependencyPredicate A predicate closure to call for {@link ResolvedDependency} to decide whether to
     * call the dependencyAction for the dependency.
     * @param visitChildrenPredicate A predicate closure to call for {@link ResolvedDependency} to decide whether to
     * visit its children.  The children may be visited even if the visitDependencyPredicate returned false.
     */
    public static void traverseResolvedDependencies(
        Set<ResolvedDependency> dependencies,
        Closure dependencyAction,
        Closure getVisitChoice
    ) {
        // Note: This method used to have the predicates as optional arguments, where null meant "always true", but I
        // kept making mistakes with them, so clearly it was a bad idea.
        traverseResolvedDependencies(
            dependencies,
            new Stack<ResolvedDependency>(),
            dependencyAction,
            getVisitChoice
        )
    }
}
