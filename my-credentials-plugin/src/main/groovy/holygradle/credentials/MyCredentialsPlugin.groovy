package holygradle.credentials

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.file.CopySpec

class MyCredentialsPlugin implements Plugin<Project> {
    void apply(Project project) {
        /**************************************
         * Dependencies
         **************************************/
        
        ResolvedArtifact credentialStoreArtifact = null
        project.getBuildscript().getConfigurations().each { conf ->
            conf.resolvedConfiguration.getFirstLevelModuleDependencies().each { resolvedDependency ->
                resolvedDependency.getAllModuleArtifacts().each { ResolvedArtifact art ->
                    String artName = art.getName()
                    if (artName.startsWith("credential-store")) {
                        credentialStoreArtifact = art
                    }
                }
            }
        }
        String credentialStorePath = credentialStoreArtifact.getFile().path
        
        // Copy the credential-store to the root of the workspace.
        if (project == project.rootProject) {
            File credStoreFile = new File(project.projectDir, "credential-store.exe")
            if (!credStoreFile.exists() || credStoreFile.canWrite()) {
                project.copy { CopySpec spec ->
                    spec.from credentialStoreArtifact.getFile()
                    spec.into credStoreFile.parentFile
                    spec.rename { credStoreFile.name }
                }
            }
        }
        
        /**************************************
         * DSL extensions
         **************************************/
            
        // Define 'my' DSL to allow user to retrieve secure user-specific settings.
        MyHandler.defineExtension(project, credentialStorePath)
        
        /**************************************
         * Tasks
         **************************************/
        if (project == project.rootProject && !project.ext.usingLocalArtifacts) {
            String taskName = "cacheCredentials"
            Task credTask = project.tasks.findByName(taskName)
            if (credTask == null) {
                credTask = project.task(taskName, type: DefaultTask)
                credTask.group = "Credentials"
                credTask.description = "Refreshes and caches all credentials."
            }
            credTask.doLast {
                println "-" * 80
                println "Please use 'credential-store.exe' instead."
                println "-" * 80
            }
        }
    }
}

