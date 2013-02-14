package holygradle.artifactorymanager

import org.gradle.*
import org.gradle.api.*

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
                    doLast {
                        artifactoryManagerExtension.doDelete(false)
                    }
                }
                project.task("cleanupArtifactoryDryRun", type: DefaultTask) {
                    doLast {
                        artifactoryManagerExtension.doDelete(true)
                    }
                }
            }
        }
    }
}

