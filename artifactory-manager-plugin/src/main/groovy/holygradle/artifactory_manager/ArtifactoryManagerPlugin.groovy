package holygradle.artifactory_manager

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task

class ArtifactoryManagerPlugin implements Plugin<Project> {        
    void apply(Project project) {        
        /**************************************
         * DSL extensions
         **************************************/
            
        // Define 'artifactoryManager' DSL
        ArtifactoryManagerHandler artifactoryManagerExtension =
            project.extensions.create("artifactoryManager", ArtifactoryManagerHandler, project) as ArtifactoryManagerHandler
        
        /**************************************
         * Tasks
         **************************************/
        project.gradle.projectsEvaluated {
            if (artifactoryManagerExtension.canDelete()) {
                project.task("cleanupArtifactory", type: DefaultTask) { Task task ->
                    task.doLast {
                        artifactoryManagerExtension.doDelete(false)
                    }
                }
                project.task("cleanupArtifactoryDryRun", type: DefaultTask) { Task task ->
                    task.doLast {
                        artifactoryManagerExtension.doDelete(true)
                    }
                }
            }

            project.task("listArtifactoryStorageSize", type: DefaultTask) { Task task ->
                task.description = "Lists storage usage by group, module, and version for each repository, to '" +
                    new File(artifactoryManagerExtension.outputDir, "[repo-name]-sizes.txt").absolutePath.toString() +
                    "' etc."
                task.doLast {
                    artifactoryManagerExtension.listStorage()
                }
            }
        }
    }
}

