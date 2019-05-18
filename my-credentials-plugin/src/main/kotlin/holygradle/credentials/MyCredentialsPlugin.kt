package holygradle.credentials

import holygradle.custom_gradle.util.ProfilingHelper
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ResolvedArtifact
import holygradle.kotlin.dsl.get
import java.io.File

class MyCredentialsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val profilingHelper = ProfilingHelper(project.logger)
        val timer = profilingHelper.startBlock("MyCredentialsPlugin#apply(${project})")

        // credential-store is declared as attached to the 'compile' configuration in this plugin's
        // build.gradle, but 'runtime' extends 'compile', and we're using it here at runtime.
        val classpathConfigurationName = "classpath"
        val runtimeResolvedConfiguration = project.buildscript.configurations[classpathConfigurationName].resolvedConfiguration
        /**************************************
         * Dependencies
         **************************************/

        // TODO 2017-03-28 HughG: Use BuildScriptDependencies to get this instead?
        val credentialStoreArtifact: ResolvedArtifact? = runtimeResolvedConfiguration.firstLevelModuleDependencies
                .flatMap { it.allModuleArtifacts }
                .firstOrNull { it.name.startsWith("credential-store") }
        if (credentialStoreArtifact == null) {
            project.logger.error(
                    "Cannot initialise ${this::class.simpleName}: " +
                    "failed to find credential-store.exe in buildscript ${classpathConfigurationName} configuration"
            )
            return
        }

        // Copy the credential-store to the root of the workspace.
        if (project == project.rootProject) {
            val credStoreFile = File(project.projectDir, "credential-store.exe")
            if (!credStoreFile.exists() || credStoreFile.canWrite()) {
                project.copy { spec ->
                    spec.from(credentialStoreArtifact.file)
                    spec.into(credStoreFile.parentFile)
                    spec.rename { credStoreFile.name }
                }
            }
        }
        
        /**************************************
         * DSL extensions
         **************************************/
            
        // Define 'my' DSL to allow user to retrieve secure user-specific settings.
        MyHandler.defineExtension(project)
        
        timer.endBlock()
    }
}

