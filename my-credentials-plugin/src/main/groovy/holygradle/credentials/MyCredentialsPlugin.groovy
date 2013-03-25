package holygradle.credentials

import org.gradle.*
import org.gradle.api.*

class MyCredentialsPlugin implements Plugin<Project> {        
    void apply(Project project) {
        /**************************************
         * Dependencies
         **************************************/
        
        def credentialStoreArtifact = null
        project.getBuildscript().getConfigurations().each { conf ->
            conf.resolvedConfiguration.getFirstLevelModuleDependencies().each { resolvedDependency ->
                resolvedDependency.getAllModuleArtifacts().each { art ->
                    def artName = art.getName()
                    if (artName.startsWith("credential-store")) {
                        credentialStoreArtifact = art
                    }
                }
            }
        }
        def credentialStorePath = credentialStoreArtifact.getFile().path
        
        // Copy the credential-store to the root of the workspace.
        if (project == project.rootProject) {
            def credStoreFile = new File(project.projectDir, "credential-store.exe")
            if (!credStoreFile.exists() || credStoreFile.canWrite()) {
                project.copy {
                    from credentialStoreArtifact.getFile()
                    into credStoreFile.parentFile
                    rename { credStoreFile.name }
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
            def taskName = "cacheCredentials"
            def credTask = project.tasks.findByName(taskName)
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

