package holygradle.unit_test

import holygradle.IntrepidPlugin
import holygradle.custom_gradle.util.ProfilingHelper
import org.gradle.api.Plugin
import org.gradle.api.Project
import holygradle.kotlin.dsl.apply

class UnitTestPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val profilingHelper = ProfilingHelper(project.logger)
        profilingHelper.timing("UnitTestPlugin#apply(${project})") {
            /**************************************
             * Apply other plugins
             **************************************/
            project.apply<IntrepidPlugin>()

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
        }
    }
}
