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
                    if (art.getName().startsWith("credential-store")) {
                        credentialStoreArtifact = art
                    }
                }
            }
        }
        def credentialStorePath = credentialStoreArtifact.getFile().path
        
        /**************************************
         * DSL extensions
         **************************************/
            
        // Define 'my' DSL to allow user to retrieve secure user-specific settings.
        def myExtension = MyHandler.defineExtension(project, credentialStorePath)
        
        /**************************************
         * Tasks
         **************************************/
        if (project == project.rootProject) {
            def taskName = "cacheCredentials"
            def credTask = project.tasks.findByName(taskName)
            if (credTask == null) {
                credTask = project.task(taskName, type: DefaultTask)
                credTask.group = "Credentials"
                credTask.description = "Refreshes and caches all credentials."
            }
            credTask.doLast {
                myExtension.refreshAllCredentials()
            }
        }
    }
}

