package holygradle.artifactory_manager

import holygradle.kotlin.dsl.task
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.File

class ArtifactoryManagerPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        /**************************************
         * DSL extensions
         **************************************/
            
        // Define 'artifactoryManager' DSL
        val artifactoryManagerExtension =
            project.extensions.create("artifactoryManager", ArtifactoryManagerHandler::class.java, project)
                    as ArtifactoryManagerHandler
        
        /**************************************
         * Tasks
         **************************************/
        project.gradle.projectsEvaluated {
            if (artifactoryManagerExtension.canDelete) {
                project.task<DefaultTask>("cleanupArtifactory") {
                    doLast {
                        artifactoryManagerExtension.doDelete(false)
                    }
                }
                project.task<DefaultTask>("cleanupArtifactoryDryRun") {
                    doLast {
                        artifactoryManagerExtension.doDelete(true)
                    }
                }
            }

            project.task<DefaultTask>("listArtifactoryStorageSize") {
                description = "Lists storage usage by group, module, and version for each repository, to '" +
                    File(artifactoryManagerExtension.outputDir, "[repo-name]-sizes.txt").absolutePath.toString() +
                    "' etc."
                doLast {
                    artifactoryManagerExtension.listStorage()
                }
            }
        }
    }
}

