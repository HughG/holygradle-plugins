package holygradle.unittest

import org.gradle.*
import org.gradle.api.*

class UnitTestPlugin implements Plugin<Project> {        
    void apply(Project project) {
        /**************************************
         * DSL extensions
         **************************************/
        // Define 'testFlavours' DSL to allow tests to define the flavours for tests (e.g. Debug, Release)
        def testFlavoursExtension = TestFlavourHandler.createContainer(project)
        
        // Define 'tests' DSL to allow tests to be configured easily
        def testsExtension = TestHandler.createContainer(project)
        
        /**************************************
         * Tasks
         **************************************/
        //TestHandler.preDefineTasks(project)
        project.gradle.projectsEvaluated {
            TestHandler.defineTasks(project)
        }
    }
}

