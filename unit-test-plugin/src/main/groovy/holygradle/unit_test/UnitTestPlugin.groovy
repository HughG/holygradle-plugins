package holygradle.unit_test

import org.gradle.api.*

class UnitTestPlugin implements Plugin<Project> {        
    void apply(Project project) {
        /**************************************
         * DSL extensions
         **************************************/
        // Define 'testFlavours' DSL to allow tests to define the flavours for tests (e.g. Debug, Release)
        TestFlavourHandler.createContainer(project)
        
        // Define 'tests' DSL to allow tests to be configured easily
        TestHandler.createContainer(project)
        
        /**************************************
         * Tasks
         **************************************/
        //TestHandler.preDefineTasks(project)
        project.gradle.projectsEvaluated {
            TestHandler.defineTasks(project)
        }
    }
}

