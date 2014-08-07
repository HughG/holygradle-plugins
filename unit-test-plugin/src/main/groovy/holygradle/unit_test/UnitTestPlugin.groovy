package holygradle.unit_test

import holygradle.custom_gradle.util.ProfilingHelper
import org.gradle.api.*

class UnitTestPlugin implements Plugin<Project> {        
    void apply(Project project) {
        ProfilingHelper profilingHelper = new ProfilingHelper(project.logger)
        def timer = profilingHelper.startBlock("UnitTestPlugin#apply(${project})")

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
            profilingHelper.timing("UnitTestPlugin(${project})#projectsEvaluated for defining tasks") {
                TestHandler.defineTasks(project)
            }
        }

        timer.endBlock()
    }
}

