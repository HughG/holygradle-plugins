package holygradle.unittest

import org.gradle.api.Project

class TestFlavourHandler {
    public final String name

    public static def createContainer(Project project) {
        project.extensions.testFlavours = project.container(TestFlavourHandler)
        project.extensions.testFlavours
    }

    public TestFlavourHandler(String name) {
        this.name = name
    }

    public static def getAllFlavours(Project project) {
        if (project.extensions.testFlavours.size() > 0) {
            project.extensions.testFlavours.collect { it.name }
        } else {
            ["Debug", "Release"]
        }
    }
}
