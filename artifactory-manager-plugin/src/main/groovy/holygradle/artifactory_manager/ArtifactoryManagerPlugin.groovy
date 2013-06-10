package holygradle.artifactory_manager

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project

class ArtifactoryManagerPlugin implements Plugin<Project> {        
    void apply(Project project) {        
        /**************************************
         * DSL extensions
         **************************************/
            
        // Define 'artifactoryManager' DSL
        def artifactoryManagerExtension = project.extensions.create("artifactoryManager", ArtifactoryManagerHandler, project)
        
        /**************************************
         * Tasks
         **************************************/
        project.gradle.projectsEvaluated {
            if (artifactoryManagerExtension.canDelete()) {
                project.task("cleanupArtifactory", type: DefaultTask) {
                    it.doLast {
                        artifactoryManagerExtension.doDelete(false)
                    }
                }
                project.task("cleanupArtifactoryDryRun", type: DefaultTask) {
                    it.doLast {
                        artifactoryManagerExtension.doDelete(true)
                    }
                }
            }
        }
    }
}

