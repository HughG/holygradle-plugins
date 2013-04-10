package holygradle

import java.nio.file.Files
import java.nio.file.Paths
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency
import org.gradle.testfixtures.*

class Helper {
    public static String MakeCamelCase(String... components) {
        def camelCase = new StringBuilder()
        boolean firstCharacterLowerCase = true
        components.each { component ->
            component.split("[_\\-\\s\\/]+").each { chunk ->
                if (chunk != null && chunk.length() > 0) {
                    if (firstCharacterLowerCase) {
                        camelCase.append(chunk[0].toLowerCase())
                    } else {
                        camelCase.append(chunk[0].toUpperCase())
                    }
                    if (chunk.size() > 1) {
                        camelCase.append(chunk[1..-1])
                    }
                    firstCharacterLowerCase = false
                }
            }
        }
        return camelCase
    }
    
    // Recursively navigates down subprojects to gather the names of all sourceDependencies which
    // have not specified sourceControlRevisionForPublishedArtifacts.
    public static def getTransitiveSourceDependencies(Project project) {
        getTransitiveSourceDependencies(project, project.sourceDependencies)
    }
    
    // Recursively navigates down subprojects to gather the names of all sourceDependencies which
    // have not specified sourceControlRevisionForPublishedArtifacts.
    private static def getTransitiveSourceDependencies(Project project, def sourceDependencies) {
        def transSourceDep = sourceDependencies
        sourceDependencies.each { sourceDep ->
            def projName = sourceDep.getTargetName()
            def proj = sourceDep.getSourceDependencyProject(project)
            if (proj == null) {
                def projDir = new File("${project.rootProject.projectDir.path}/${projName}")
                if (projDir.exists()) {
                    proj = ProjectBuilder.builder().withProjectDir(projDir).build()
                }
            }
            if (proj != null) {
                def subprojSourceDep = proj.extensions.findByName("sourceDependencies")
                if (subprojSourceDep != null) {
                    def transSubprojSourceDep = getTransitiveSourceDependencies(project, subprojSourceDep)
                    transSourceDep = transSourceDep + transSubprojSourceDep
                }
            }
        }
        return transSourceDep.unique()
    }
    
    public static String relativizePath(File targetPath, File basePath) {
        def target = Paths.get(targetPath.getCanonicalPath())
        def base = Paths.get(basePath.getCanonicalPath())
        return base.relativize(target).toString()
    }
    
    public static File getGlobalUnpackCacheLocation(Project project, ModuleVersionIdentifier moduleVersion) {
        def groupCache = new File(project.ext.unpackedDependenciesCache, moduleVersion.getGroup())
        return new File(groupCache, moduleVersion.getName() + "-" + moduleVersion.getVersion())
    }
    
    public static String incrementVersionNumber(File versionFile) {
        def versionStr = versionFile.text
        int lastDot = versionStr.lastIndexOf('.')
        int nextVersion = versionStr[lastDot+1..-1].toInteger() + 1
        def firstChunk = versionStr[0..lastDot]
        def newVersionStr = firstChunk + nextVersion.toString()
        versionFile.write(newVersionStr)
        newVersionStr
    }
    
    public static def getProjectBuildTasks(def project) {
        def buildTasks = []
        project.getAllTasks(false).each { proj, tasks ->
            buildTasks = buildTasks + tasks.findAll{it.name ==~ /(build|clean)(Debug|Release)/}
        }
        return buildTasks.inject([:]) { map, task -> map[task.name] = task; map }
    }
    
    public static boolean isJunctionOrSymlink(File file) throws IOException {
        Files.isSymbolicLink(Paths.get(file.path))
    }

    public static void deleteSymlink(File link) {
        if (isJunctionOrSymlink(link)) {
            link.delete()
        }
    }
    
    public static void rebuildSymlink(File link, File target, def project) {
        File canonicalLink = link.getCanonicalFile()
        
        // Delete the symlink if it exists
        if (isJunctionOrSymlink(canonicalLink)) {
            canonicalLink.delete()
        }
        
        // Make sure the parent directory exists
        File shouldExist = canonicalLink.parentFile
        if (canonicalLink.parentFile != null) {
            if (!canonicalLink.parentFile.exists()) {
                canonicalLink.parentFile.mkdirs()
            }
        }
        
        // Run 'mklink'
        def execResult = project.exec {
            commandLine "cmd", "/c", "mklink", "/D", '"' + canonicalLink.path + '"', '"' + target.path + '"' 
            setStandardOutput new ByteArrayOutputStream()
            setErrorOutput new ByteArrayOutputStream()
            setIgnoreExitValue true
        }
        if (execResult.getExitValue() != 0) {
            println "Failed to create symlink at location '${canonicalLink}' pointing to '${target}'. This could be due to User Account Control, or failing to use an Administrator command prompt."
            execResult.rethrowFailure()
        }
    }
    
    public static boolean configurationExists(Project project, String group, String moduleName, String version, String config) {
        def externalDependency = new DefaultExternalModuleDependency(group, moduleName, version, config)
        def dependencyConf = project.configurations.detachedConfiguration(externalDependency)
        try {
            dependencyConf.getResolvedConfiguration().getFirstLevelModuleDependencies().each {
                latestVersion = it.getModuleVersion()
            }
        } catch (Exception e) {
            return false
        }
        
        return true
    }
    
    public static void processConfiguration(String config, def configurations, String formattingErrorMessage) {
        if (config instanceof Configuration) {
            Configuration c = config
            configurations.add([c.name, c.name])
        } else {
            def split = config.split("->")
            if (split.size() == 1) {
                def configSet = config.split(",")
                configSet.each { conf ->
                    configurations.add([conf, conf])
                }
            } else if (split.size() == 2) {
                def fromConfigSet = split[0].split(",")
                def toConfigSet = split[1].split(",")
                fromConfigSet.each { from ->
                    toConfigSet.each { to ->
                        configurations.add([from, to])
                    }
                }
            } else {
                throw new RuntimeException(
                    formattingErrorMessage + " The configuration '$config' should be in the " +
                    "form 'aa,bb->cc,dd', where aa and bb are configurations defined in *this* build script, and cc and " +
                    "dd are configurations defined by the dependency. This will generate all pair-wise combinations of " +
                    "configuration mappings. However, if the configuration names are the same in the source and destination " +
                    "then you can simply specify comma-separated configuration names without any '->' in between e.g. 'aa,bb'. " +
                    "This will produce one configuration mapping per entry i.e. 'aa->aa' and 'bb->bb'."
                )
            }
        }
    }
    
    public static void setReadOnlyRecursively(File root) {
        root.listFiles().each { f ->
            f.setReadOnly()
            if (f.isDirectory()) {
                setReadOnlyRecursively(f)
            }
        }
    }
    
    public static boolean wildcardMatch(String pattern, String input) {
        for (chunk in pattern.split("\\*")) {
            int index = input.indexOf(chunk)
            if (index < 0) {
                return false
            }
            input = input.substring(index + chunk.length())
        }
        
        return true
    }
    
    public static boolean anyWildcardMatch(def patterns, String input) {
        for (pattern in patterns) {
            if (wildcardMatch(pattern, input)) {
                return true
            }
        }
        return false
    }
    
    public static void checkHgAuth(def checker) {
        def mercurialIniFile = new File(System.getenv("USERPROFILE"), "mercurial.ini")
        if (mercurialIniFile.exists()) {
            def iniText = mercurialIniFile.text
            def ok = true
            if (!iniText.contains("[auth]") || !iniText.contains("default.schemes") || !iniText.contains("default.prefix") || !iniText.contains("default.username")) {
                ok = false
            }
            if (!ok) {
                checker.fail "Your mercurial.ini file (in ${mercurialIniFile.parent}) is not properly configured with an [auth] section. This is necessary for storage of credentials using the 'mercurial_keyring' extension. The following properties must exist in the ini file:\n[auth]\ndefault.schemes = https\ndefault.prefix = *\ndefault.username = YOUR_USERNAME\nRun the task 'fixMercurialIni' to have this applied automatically."
            }
        } else {
            checker.fail "No mercurial.ini file could be found in ${mercurialIniFile.parent}. Please run the task 'fixMercurialIni'."
        }
    }
    
    public static void fixMercurialIni() {
        // Create a default mercurial.ini file.
        def mercurialIniFile = new File(System.getenv("USERPROFILE"), "mercurial.ini")
        def iniText = ""
        if (mercurialIniFile.exists()) {
            iniText = mercurialIniFile.text
        }
        
        def shouldWrite = false
        if (!iniText.contains("[auth]") || !iniText.contains("default.schemes") || !iniText.contains("default.prefix") || !iniText.contains("default.username")) {
            shouldWrite = true
            iniText += "[auth]\r\n" +
                "default.schemes = https\r\n" +
                "default.prefix = *\r\n" +
                "default.username = " + System.getProperty("user.name").toLowerCase() + "\r\n"
        }
        if (shouldWrite) {
            mercurialIniFile.write(iniText)
            println "Your mercurial.ini (in ${mercurialIniFile.parent}) has been modified."
        }
    } 
    
    public static void addMercurialKeyringToIniFile(File hgPath) {
        File iniFile = new File(hgPath, "Mercurial.ini")
        File keyring = new File(hgPath, "mercurial_keyring.py")
        
        // Create a default mercurial.ini file.
        def hgInitFileText = ""
        if (iniFile.exists()) {
            hgInitFileText = iniFile.text
        }
        
        def shouldWrite = false
        if (!hgInitFileText.contains("hgext.mercurial_keyring")) {
            shouldWrite = true
            hgInitFileText += "\r\n\r\n" +
                "[extensions]\r\n" +
                "hgext.mercurial_keyring = ${keyring.path}\r\n\r\n"
        }
        if (shouldWrite) {
            iniFile.write(hgInitFileText)
        }
    } 
}