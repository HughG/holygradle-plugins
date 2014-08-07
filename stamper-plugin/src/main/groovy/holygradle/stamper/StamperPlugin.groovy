package holygradle.stamper

import holygradle.custom_gradle.util.ProfilingHelper
import org.gradle.api.Project
import org.gradle.api.Plugin
import org.gradle.api.Task

class StamperPlugin implements Plugin<Project> {

    void apply(Project project) {
        StampingHandler stampingHandler = project.extensions.create("stamping", StampingHandler, project)
        
        project.gradle.projectsEvaluated {
            ProfilingHelper profilingHelper = new ProfilingHelper(project.logger)
            profilingHelper.timing("StamperPlugin(${project})#projectsEvaluated for stamping task") {
                if ((stampingHandler.fileReplacers.size() + stampingHandler.patternReplacers.size()) > 0) {
                    // Add a task that stamps the files with replacement strings
                    project.task(stampingHandler.taskName) { Task task ->
                        group = "Stamping"
                        description = stampingHandler.taskDescription

                        task.doLast {
                            for (replacer in stampingHandler.fileReplacers) {
                                replacer.doPatternReplacement();
                            }
                            if (stampingHandler.patternReplacers.size() > 0) {
                                project.projectDir.traverse { file ->
                                    stampingHandler.patternReplacers.each { replacer ->
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

