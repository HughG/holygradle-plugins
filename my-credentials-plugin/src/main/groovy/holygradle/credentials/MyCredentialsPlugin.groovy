package holygradle.credentials

import holygradle.custom_gradle.util.ProfilingHelper
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.ResolvedConfiguration
import org.gradle.api.file.CopySpec

class MyCredentialsPlugin implements Plugin<Project> {
    void apply(Project project) {
        ProfilingHelper profilingHelper = new ProfilingHelper(project.logger)
        def timer = profilingHelper.startBlock("MyCredentialsPlugin#apply(${project})")

        /**************************************
         * Dependencies
         **************************************/

        // TODO 2017-03-28 HughG: Use BuildScriptDependencies to get this instead.
        ResolvedArtifact credentialStoreArtifact = null
        // credential-store is declared as attached to the 'compile' configuration in this plugin's
        // build.gradle, but 'runtime' extends 'compile', and we're using it here at runtime.
        ResolvedConfiguration runtimeResolvedConfiguration =
            project.buildscript.configurations['classpath'].resolvedConfiguration
        runtimeResolvedConfiguration.firstLevelModuleDependencies.each { resolvedDependency ->
            resolvedDependency.allModuleArtifacts.each { ResolvedArtifact art ->
                if (art.name.startsWith("credential-store")) {
                    credentialStoreArtifact = art
                }
            }
        }
        
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
        MyHandler.defineExtension(project)
        
        /**************************************
         * Tasks
         **************************************/
        if (project == project.rootProject && !project.usingLocalArtifacts) {
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

        timer.endBlock()
    }
}

