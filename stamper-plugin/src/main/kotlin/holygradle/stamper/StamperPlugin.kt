package holygradle.stamper

import holygradle.custom_gradle.util.ProfilingHelper
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.Plugin
import holygradle.kotlin.dsl.task

class StamperPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val stampingHandler = project.extensions.create("stamping", StampingHandler::class.java, project)
        
        project.gradle.projectsEvaluated {
            val profilingHelper = ProfilingHelper(project.logger)
            profilingHelper.timing("StamperPlugin(${project})#projectsEvaluated for stamping task") {
                if ((stampingHandler.fileReplacers.size + stampingHandler.patternReplacers.size) > 0) {
                    // Add a task that stamps the files with replacement strings
                    project.task<DefaultTask>(stampingHandler.taskName) {
                        group = "Stamping"
                        description = stampingHandler.taskDescription

                        doLast {
                            for (replacer in stampingHandler.fileReplacers) {
                                replacer.doPatternReplacement()
                            }
                            if (stampingHandler.patternReplacers.isNotEmpty()) {
                                for (file in project.projectDir.walk()) {
                                    for (replacer in stampingHandler.patternReplacers) {
                                        replacer.doPatternReplacement(file)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
}
