package holygradle.dependencies

import groovy.util.slurpersupport.GPathResult
import groovy.util.slurpersupport.NodeChild
import org.gradle.api.*
import org.gradle.api.artifacts.*
import org.gradle.api.tasks.*

class CollectDependenciesTask extends Copy {
    private Project project
    
    public void initialize(Project project) {
        this.project = project
        
        destinationDir = new File(project.rootProject.projectDir, "local_artifacts")
        this.ext.lazyConfiguration = {

            // TODO 2013-06-13 HughG: Duplication with BuildScriptDependency constructor.

            Collection<ResolvedArtifact> dependencyArtifacts = []
            Closure<Set<ResolvedDependency>> collectArtifacts = { Configuration conf ->
                ResolvedConfiguration resConf = conf.resolvedConfiguration
                resConf.firstLevelModuleDependencies.each { ResolvedDependency resolvedDependency ->
                    resolvedDependency.allModuleArtifacts.each { ResolvedArtifact artifact ->
                        if (!dependencyArtifacts.contains(artifact)) {
                            dependencyArtifacts.add(artifact)
                        }
                    }
                }
            }

            project.buildscript.configurations.each collectArtifacts
            project.configurations.each collectArtifacts
            
            for (ResolvedArtifact artifact in dependencyArtifacts) { 
                processArtifact(artifact)
            }
        }
        
        // Copy the custom-gradle distribution.
        if (project == project.rootProject) {
            // Will be something like: 
            // C:\Users\nmcm\.gradle\wrapper\dists\custom-gradle-1.3-1.0.1.10\j5dmdk875e2rad4dnud3sriop\custom-gradle-1.3
            File gradleHomeDir = project.gradle.gradleHomeDir.parentFile
            String[] split = gradleHomeDir.parentFile.name.split("-")
            String customDistVersion = split[-1]
            
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
                File propFile = null
                
                File gradleDir = new File(project.projectDir, "gradle")
                if (gradleDir.exists()) {
                    gradleDir.traverse {
                        if (it.name.endsWith(".properties")) {
                            propFile = it
                        }
                    }
                }
                
                if (propFile != null) {
                    String originalPropText = propFile.text
                    File backupPropFile = new File(propFile.path + ".original")
                    backupPropFile.write(originalPropText)
                    
                    String newPropText = originalPropText.replaceAll(
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
        ModuleVersionIdentifier version = artifact.getModuleVersion().getId()
        File artifactRootDir = artifact.getFile().parentFile.parentFile.parentFile
        String targetPath = null

        logger.info("Processing '${version}' for collectDependencies.")
        
        File ivyDir = new File(artifactRootDir, "ivy")
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
            
        File pomDir = new File(artifactRootDir, "pom")
        if (pomDir.exists()) {
            pomDir.traverse { pomFile ->
                if (pomFile.name == "${version.name}-${version.version}.pom") {
                    String groupPath = version.group.replaceAll("\\.", "/")
                    targetPath = "maven/${groupPath}/${version.name}/${version.version}"
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
        File pomArtifactRootDir = pomFile.parentFile.parentFile.parentFile
        // NOTE 2013-07-12 HughG: No point declaring the type for a GPathResult, because almost all access to such
        // objects uses magic dynamic properties.
        def /* GPathResult */ pomXml = null
        
        try {
            pomXml = new XmlSlurper(false, false).parseText(pomFile.text)
        } catch (Exception e) {
            doLast {
                println "Warning: error while parsing XML in ${pomFile}: " + e.toString()
            }
        }
        
        if (pomXml != null) {
            // NOTE 2013-07-12 HughG: Suppressing an IntelliJ IDEA warning here, because "pomXml.parent" isn't supposed
            // to be accessing the protected "parent" property of the GPathResult class, but the GPath object for the
            // "<parent />" note in the "pom.xml".  IntelliJ figures out that this is a GPathResult even if we declare
            // it with "def", so we still need to suppress this.
            //noinspection GroovyAccessibility
            pomXml.parent.each { /* GPathResult */ parentNode ->
                String parentPomGroup = parentNode.groupId.text()
                String parentPomModuleName = parentNode.artifactId.text()
                String parentPomVersion = parentNode.version.text()
                
                String parentPomRelativePath = parentNode.relativePath.text()
                if (parentPomRelativePath != null && !parentPomRelativePath.isEmpty()) {
                    logger.info "Ignoring relative path for '${pomArtifactRootDir}' : ${parentPomRelativePath}"
                }
                File cacheRootDir = pomArtifactRootDir.parentFile.parentFile.parentFile
                File parentPomPath = new File(
                    new File(
                        new File(cacheRootDir, parentPomGroup),
                        parentPomModuleName
                    ),
                    parentPomVersion
                )
                
                if (parentPomPath.exists()) {
                    parentPomPath.traverse { parentPomFile ->
                        if (parentPomFile.name.endsWith(".pom")) {
                            String groupPath = parentPomGroup.replaceAll("\\.", "/")
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