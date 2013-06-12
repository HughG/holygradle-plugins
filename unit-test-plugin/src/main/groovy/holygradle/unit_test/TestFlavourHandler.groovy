package holygradle.unit_test

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project

class TestFlavourHandler {
    public final String name

    public static TestFlavourHandler createContainer(Project project) {
        project.extensions.testFlavours = project.container(TestFlavourHandler)
        project.extensions.testFlavours
    }

    public TestFlavourHandler(String name) {
        this.name = name
    }

    public static Collection<String> getAllFlavours(Project project) {
        Collection<TestFlavourHandler> flavours =
            project.extensions.testFlavours as NamedDomainObjectContainer<TestFlavourHandler>
        if (flavours.size() > 0) {
            flavours.collect { it.name }
        } else {
            new ArrayList<String>(TestHandler.DEFAULT_FLAVOURS)
        }
    }
}
