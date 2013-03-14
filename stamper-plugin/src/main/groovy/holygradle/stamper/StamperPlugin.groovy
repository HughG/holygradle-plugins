package holygradle.stamper

import org.gradle.api.Project
import org.gradle.api.Plugin
import org.gradle.util.ConfigureUtil
import java.util.regex.Matcher
import java.util.regex.Pattern

class StamperPlugin implements Plugin<Project> {

    void apply(Project project) {
        def stampingHandler = project.extensions.create("stamping", StampingHandler, project)
        
        project.gradle.projectsEvaluated {
            if ((stampingHandler.m_fileReplacers.size() + stampingHandler.m_patternReplacers.size()) > 0) {
                // Add a task that stamps the files with replacement strings
                project.task(stampingHandler.taskName) {
                    group = "Stamping"
                    description = stampingHandler.taskDescription
                    
                    doLast {
                        for(replacer in stampingHandler.m_fileReplacers) {
                            replacer.doPatternReplacement();
                        }
                        if (stampingHandler.m_patternReplacers.size() > 0) {
                            project.projectDir.traverse { file ->
                                stampingHandler.m_patternReplacers.each { replacer ->
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

