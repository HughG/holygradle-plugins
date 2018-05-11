package holygradle.unit_test

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import holygradle.kotlin.dsl.getByName
import holygradle.kotlin.dsl.getValue

internal class TestFlavourHandler(
        val name: String
) {
    companion object {
        @JvmStatic
        fun createContainer(project: Project): NamedDomainObjectContainer<TestFlavourHandler> {
            project.extensions.add("testFlavours", project.container(TestFlavourHandler::class.java))
            return project.extensions.getByName<NamedDomainObjectContainer<TestFlavourHandler>>("testFlavours")
        }

        @JvmStatic
        fun getAllFlavours(project: Project): List<String> {
            val testFlavours: Collection<TestFlavourHandler> by project.extensions
            return if (testFlavours.isNotEmpty()) {
                testFlavours.map { it.name }
            } else {
                ArrayList(TestHandler.DEFAULT_FLAVOURS)
            }
        }
    }

}
