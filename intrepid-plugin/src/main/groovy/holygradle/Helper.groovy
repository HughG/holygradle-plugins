package holygradle

import holygradle.custom_gradle.PrerequisitesChecker
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

class Helper {
    // Recursively navigates down subprojects to gather the names of all sourceDependencies which
    // have not specified sourceControlRevisionForPublishedArtifacts.
    public static Collection<SourceDependencyHandler> getTransitiveSourceDependencies(Project project) {
        getTransitiveSourceDependencies(project, project.sourceDependencies as Collection<SourceDependencyHandler>)
    }
    
    // Recursively navigates down subprojects to gather the names of all sourceDependencies which
    // have not specified sourceControlRevisionForPublishedArtifacts.
    private static Collection<SourceDependencyHandler> getTransitiveSourceDependencies(
        Project project,
        Collection<SourceDependencyHandler> sourceDependencies
    ) {
        Collection<SourceDependencyHandler> transSourceDep = sourceDependencies
        sourceDependencies.each { sourceDep ->
            String projName = sourceDep.targetName
            Project proj = sourceDep.getSourceDependencyProject(project)
            if (proj == null) {
                File projDir = new File("${project.rootProject.projectDir.path}/${projName}")
                if (projDir.exists()) {
                    proj = ProjectBuilder.builder().withProjectDir(projDir).build()
                }
            }
            if (proj != null) {
                Collection<SourceDependencyHandler> subprojSourceDep =
                    proj.extensions.findByName("sourceDependencies") as Collection<SourceDependencyHandler>
                if (subprojSourceDep != null) {
                    Collection<SourceDependencyHandler> transSubprojSourceDep = getTransitiveSourceDependencies(project, subprojSourceDep)
                    transSourceDep = transSourceDep + transSubprojSourceDep
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
        File groupCache = new File(project.ext.unpackedDependenciesCache as File, moduleVersion.getGroup())
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
    
    public static void addMercurialKeyringToIniFile(File hgPath) {
        File iniFile = new File(hgPath, "Mercurial.ini")
        File keyring = new File(hgPath, "mercurial_keyring.py")
        
        // Create a default mercurial.ini file.
        String hgInitFileText = ""
        if (iniFile.exists()) {
            hgInitFileText = iniFile.text
        }
        
        boolean shouldWrite = false
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