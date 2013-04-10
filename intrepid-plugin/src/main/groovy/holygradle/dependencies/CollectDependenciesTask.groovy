package holygradle.dependencies

import org.gradle.api.*
import org.gradle.api.artifacts.*
import org.gradle.api.tasks.*

class CollectDependenciesTask extends Copy {
    private Project project
    
    public void initialize(Project project) {
        this.project = project
        
        destinationDir = new File(project.rootProject.projectDir, "local_artifacts")
        ext.lazyConfiguration = {
            def dependencyArtifacts = []
            def collectArtifacts = { conf ->
                def resConf = conf.resolvedConfiguration
                resConf.getFirstLevelModuleDependencies().each { resolvedDependency ->
                    resolvedDependency.getAllModuleArtifacts().each { artifact ->
                        if (!dependencyArtifacts.contains(artifact)) {
                            dependencyArtifacts.add(artifact)
                        }
                    }
                }
            }
            
            project.buildscript.getConfigurations().each collectArtifacts
            project.configurations.each collectArtifacts
            
            for (ResolvedArtifact artifact in dependencyArtifacts) { 
                processArtifact(artifact)
            }
        }
        
        // Copy the custom-gradle distribution.
        if (project == project.rootProject) {
            // Will be something like: 
            // C:\Users\nmcm\.gradle\wrapper\dists\custom-gradle-1.3-1.0.1.10\j5dmdk875e2rad4dnud3sriop\custom-gradle-1.3
            def gradleHomeDir = project.gradle.gradleHomeDir.parentFile
            def split = gradleHomeDir.parentFile.name.split("-")
            def customDistVersion = split[-1]
            
            gradleHomeDir.traverse {
                if (it.name.endsWith(".zip")) {
                    from (it) {
                        into "custom-gradle/${customDistVersion}"
                    }
                }
            }
        }
        
        // Re-write the gradle-wrapper.properties file
        if (project == project.rootProject) {
            doLast {
                def propFile = null
                
                def gradleDir = new File(project.projectDir, "gradle")
                if (gradleDir.exists()) {
                    gradleDir.traverse {
                        if (it.name.endsWith(".properties")) {
                            propFile = it
                        }
                    }
                }
                
                if (propFile != null) {
                    def originalPropText = propFile.text
                    def backupPropFile = new File(propFile.path + ".original")
                    backupPropFile.write(originalPropText)
                    
                    def newPropText = originalPropText.replaceAll(
                        "distributionUrl=.*custom-gradle/([\\d\\.]+)/custom-gradle-([\\d\\.\\-]+).zip",
                        { "distributionUrl=../local_artifacts/custom-gradle/${it[1]}/custom-gradle-${it[2]}.zip" }
                    )
                    propFile.write(newPropText)
                    
                    println "NOTE: '${propFile.name}' has been rewritten to refer to the 'local_artifacts' version of custom-gradle."
                    println "A backup has been saved as '${backupPropFile.name}'."
                }
            }
        }
    }
    
    public void processArtifact(ResolvedArtifact artifact) {
        def version = artifact.getModuleVersion().getId()
        def artifactRootDir = artifact.getFile().parentFile.parentFile.parentFile
        def targetPath = null

        logger.info("Processing '${version}' for collectDependencies.")
        
        def ivyDir = new File(artifactRootDir, "ivy")
        if (ivyDir.exists()) {
            ivyDir.traverse { ivyFile ->
                if (ivyFile.name == "ivy-${version.getVersion()}.xml") {
                    targetPath = "ivy/${version.getGroup()}/${version.getName()}/${version.getVersion()}"
                    from (ivyFile) {
                        into targetPath
                    }
                }
            }
        }
            
        def pomDir = new File(artifactRootDir, "pom")
        if (pomDir.exists()) {
            pomDir.traverse { pomFile ->
                if (pomFile.name == "${version.getName()}-${version.getVersion()}.pom") {
                    def groupPath = version.getGroup().replaceAll("\\.", "/")
                    targetPath = "maven/${groupPath}/${version.getName()}/${version.getVersion()}"
                    from (pomFile) {
                        into targetPath
                    }
                    
                    processPomFileParents(pomFile)
                }
            }
        }
        
        from (artifact.getFile()) {
            into targetPath
        }
    }
    
    // Read the POM file to determine its parents. Navigate to parent POM files and copy those too.
    // Recursively call this method for grand-parents etc.
    public void processPomFileParents(File pomFile) {
        def pomArtifactRootDir = pomFile.parentFile.parentFile.parentFile
        def pomXml = null
        
        try {
            pomXml = new XmlSlurper(false, false).parseText(pomFile.text)
        } catch (e) {
            doLast {
                println "Warning: error while parsing XML in ${pomFile}."
            }
        }
        
        if (pomXml != null) {
            pomXml.parent.each { parentNode ->
                def parentPomGroup = parentNode.groupId.text()
                def parentPomModuleName = parentNode.artifactId.text()
                def parentPomVersion = parentNode.version.text()
                
                def parentPomRelativePath = parentNode.relativePath.text()
                def parentPomPath = null
                if (parentPomRelativePath != null && !parentPomRelativePath.isEmpty()) {
                    logger.info "Ignoring relative path for '${pomArtifactRootDir}' : ${parentPomRelativePath}"
                }
                def cacheRootDir = pomArtifactRootDir.parentFile.parentFile.parentFile
                parentPomPath = new File(new File(new File(cacheRootDir, parentPomGroup), parentPomModuleName), parentPomVersion)
                
                if (parentPomPath.exists()) {
                    parentPomPath.traverse { parentPomFile ->
                        if (parentPomFile.name.endsWith(".pom")) {
                            def groupPath = parentPomGroup.replaceAll("\\.", "/")
                            from (parentPomFile) {
                                into "maven/${groupPath}/${parentPomModuleName}/${parentPomVersion}"
                            }
                            
                            processPomFileParents(parentPomFile.getCanonicalFile())
                        }
                    }
                } else {
                    doLast {
                        println "Warning: ${parentPomPath} does not exist."
                    }
                }
            }
        }
    }
}