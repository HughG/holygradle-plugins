package holygradle

import holygradle.custom_gradle.PrerequisitesChecker
import holygradle.dependencies.PackedDependenciesSettingsHandler
import holygradle.source_dependencies.SourceDependencyHandler
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency
import org.gradle.testfixtures.ProjectBuilder

import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest
import java.util.regex.Matcher
import java.util.regex.Pattern

class Helper {
    // Recursively navigates down subprojects to gather the names of all sourceDependencies which
    // have not specified sourceControlRevisionForPublishedArtifacts.
    public static final int MAX_VERSION_STRING_LENGTH = 50

    public static Collection<SourceDependencyHandler> getTransitiveSourceDependencies(Project project) {
        doGetTransitiveSourceDependencies(project.sourceDependencies as Collection<SourceDependencyHandler>)
    }
    
    // Recursively navigates down subprojects to gather the names of all sourceDependencies which
    // have not specified sourceControlRevisionForPublishedArtifacts.
    private static Collection<SourceDependencyHandler> doGetTransitiveSourceDependencies(
        Collection<SourceDependencyHandler> sourceDependencies
    ) {
        // Note: Make transSourceDep be a fresh collection; previous code got ConcurrentModificationException sometimes.
        Collection<SourceDependencyHandler> transSourceDep = new ArrayList<SourceDependencyHandler>(sourceDependencies)
        sourceDependencies.each { sourceDep ->
            String projName = sourceDep.targetName
            Project proj = sourceDep.getSourceDependencyProject()
            if (proj == null) {
                File projDir = new File("${sourceDep.project.rootProject.projectDir.path}/${projName}")
                if (projDir.exists()) {
                    proj = ProjectBuilder.builder().withProjectDir(projDir).build()
                }
            }
            if (proj != null) {
                Collection<SourceDependencyHandler> subprojSourceDep =
                    proj.extensions.findByName("sourceDependencies") as Collection<SourceDependencyHandler>
                if (subprojSourceDep != null) {
                    Collection<SourceDependencyHandler> transSubprojSourceDep = doGetTransitiveSourceDependencies(subprojSourceDep)
                    transSourceDep.addAll(transSubprojSourceDep)
                }
            }
        }
        return transSourceDep.unique()
    }
    
    public static String relativizePath(File targetPath, File basePath) {
        Path target = Paths.get(targetPath.getCanonicalPath())
        Path base = Paths.get(basePath.getCanonicalPath())
        return base.relativize(target).toString()
    }
    
    public static File getGlobalUnpackCacheLocation(Project project, ModuleVersionIdentifier moduleVersion) {
        File unpackCache = PackedDependenciesSettingsHandler.findPackedDependenciesSettings(project).unpackedDependenciesCacheDir
        File groupCache = new File(unpackCache, moduleVersion.getGroup())
        return new File(groupCache, moduleVersion.getName() + "-" + moduleVersion.getVersion())
    }
    
    public static String incrementVersionNumber(File versionFile) {
        String versionStr = versionFile.text
        int lastDot = versionStr.lastIndexOf('.')
        int nextVersion = versionStr[lastDot+1..-1].toInteger() + 1
        String firstChunk = versionStr[0..lastDot]
        String newVersionStr = firstChunk + nextVersion.toString()
        versionFile.write(newVersionStr)
        newVersionStr
    }
    
    public static Map<String, Task> getProjectBuildTasks(Project project) {
        Collection<Task> buildTasks = []
        project.getAllTasks(false).each { Project proj, Set<Task> tasks ->
            buildTasks = buildTasks + tasks.findAll{it.name ==~ /(build|clean)(Debug|Release)/}
        }
        return buildTasks.inject([:]) { Map<String, Task> map, Task task -> map[task.name] = task; map }
    }

    public static boolean configurationExists(Project project, String group, String moduleName, String version, String config) {
        Dependency externalDependency = new DefaultExternalModuleDependency(group, moduleName, version, config)
        Configuration dependencyConf = project.configurations.detachedConfiguration(externalDependency)
        try {
            dependencyConf.getResolvedConfiguration().getFirstLevelModuleDependencies().each { ResolvedDependency dep ->
                dep.getModuleVersion()
            }
        } catch (ignored) {
            return false
        }

        return true
    }

    // TODO 2013-06-13 HughG: This should maybe manage a Map<String, Collection<String>> instead.
    public static void parseConfigurationMapping(
        String config,
        Collection<AbstractMap.SimpleEntry<String, String>> configurations,
        String formattingErrorMessage
    ) {
        String[] split = config.split("->")
        if (split.size() == 1) {
            String[] configSet = config.split(",")
            configSet.each { conf ->
                configurations.add(new AbstractMap.SimpleEntry(conf, conf))
            }
        } else if (split.size() == 2) {
            String[] fromConfigSet = split[0].split(",")
            String[] toConfigSet = split[1].split(",")
            fromConfigSet.each { from ->
                toConfigSet.each { to ->
                    if (to == "*") {
                        throw new RuntimeException(
                            formattingErrorMessage + " Due to Gradle bug http://issues.gradle.org/browse/GRADLE-2352 " +
                            "(fixed in 1.5-cr-1), using '*' as the target in a configuration string will have the " +
                            "wrong effect.  Please list configurations explicitly, use '@' if appropriate, or use " +
                            "some other workaround."
                        )
                    }
                    configurations.add(new AbstractMap.SimpleEntry(from, to))
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
    
    public static void setReadOnlyRecursively(File root) {
        root.listFiles().each { File f ->
            f.setReadOnly()
            if (f.isDirectory()) {
                setReadOnlyRecursively(f)
            }
        }
    }

    public static void checkHgAuth(PrerequisitesChecker checker) {
        File mercurialIniFile = new File(System.getenv("USERPROFILE"), "mercurial.ini")
        if (mercurialIniFile.exists()) {
            String iniText = mercurialIniFile.text
            boolean ok = true
            if (!iniText.contains("[auth]") || !iniText.contains("default.schemes") || !iniText.contains("default.prefix") || !iniText.contains("default.username")) {
                ok = false
            }
            if (!ok) {
                checker.fail """Your mercurial.ini file (in ${mercurialIniFile.parent}) is not properly
configured with an [auth] section. This is necessary for storage of
credentials using the 'mercurial_keyring' extension. The following properties
must exist in the ini file:

[auth]
default.schemes = https
default.prefix = *
default.username = YOUR_USERNAME

Run the task 'fixMercurialIni' to have this applied automatically."""
            }
        } else {
            checker.fail """No mercurial.ini file could be found in ${mercurialIniFile.parent}.
Please run the task 'fixMercurialIni'."""
        }
    }
    
    public static void fixMercurialIni() {
        // Create a default mercurial.ini file.
        File mercurialIniFile = new File(System.getenv("USERPROFILE"), "mercurial.ini")
        String iniText = ""
        if (mercurialIniFile.exists()) {
            iniText = mercurialIniFile.text
        }
        
        boolean shouldWrite = false
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

    /**
     * Converts a file path to a version string, by hashing the path and prefixing "SOURCE".  A short, 4 character,
     * hash is used because it may itself be used as part of a file path and some parts of Windows are limited to 260
     * characters for the entire file path.
     * @param path A file path
     * @return A valid version string, of length at most {@link Helper#MAX_VERSION_STRING_LENGTH}.
     */
    public static String convertPathToVersion(String path) {
        String canonicalPath = new File(path).canonicalPath

        MessageDigest sha1 = MessageDigest.getInstance("SHA1")
        byte[] digest = sha1.digest(canonicalPath.getBytes())
        return "SOURCE_${digest[0..3].collect { String.format('%02x', it) }.join()}"
    }
}