package holygradle.packaging

import holygradle.artifacts.ConfigurationHelper
import holygradle.io.FileHelper
import holygradle.source_dependencies.RecursivelyFetchSourceTask
import holygradle.test.AbstractHolyGradleIntegrationTest
import holygradle.test.WrapperBuildLauncher
import holygradle.testUtil.HgUtil
import org.apache.commons.io.FileUtils
import org.gradle.api.Project
import org.gradle.api.file.FileTree
import org.gradle.api.file.FileVisitDetails
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert
import org.junit.Test

import java.util.zip.ZipFile


class PackageArtifactsIntegrationTest extends AbstractHolyGradleIntegrationTest {
    @Test
    public void testBasicConfiguration() {
        File projectDir = new File(getTestDir(), "projectA")

        Project project = ProjectBuilder.builder().withProjectDir(projectDir).build()
        project.ext.holyGradleInitScriptVersion = "9.10.11.12" // Required by custom-gradle-core-plugin.
        project.ext.holyGradlePluginsRepository = ""
        project.buildscript.configurations.create(ConfigurationHelper.OPTIONAL_CONFIGURATION_NAME)
        project.apply plugin: 'intrepid'

        Collection<PackageArtifactHandler> packageArtifacts =
            project.extensions.findByName("packageArtifacts") as Collection<PackageArtifactHandler>
        Assert.assertNotNull(packageArtifacts)
    }
    
    @Test
    public void testBasicPackageEverything() {
        File projectDir = new File(getTestDir(), "projectB")
        File packagesDir = new File(projectDir, "packages")
        FileHelper.ensureDeleteDirRecursive(packagesDir)

        // Create a dummy project to provide access to FileTree methods
        Project project = ProjectBuilder.builder().withProjectDir(projectDir).build()

        // Run fAD to make sure we generate the settings file.
        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("fAD")
        }

        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("packageEverything")
        }
        
        Assert.assertTrue(packagesDir.exists())
        Assert.assertTrue(new File(packagesDir, "projectB-foo.zip").exists())
        Assert.assertTrue(new File(packagesDir, "projectB-bar.zip").exists())
        File buildScriptFile = new File(packagesDir, "projectB-buildScript.zip")
        Assert.assertTrue(buildScriptFile.exists())
        ZipFile buildScriptZipFile = new ZipFile(buildScriptFile)
        Assert.assertNotNull("build file is in zip", buildScriptZipFile.getEntry("build.gradle"))

        checkBuildInfoFiles(project.zipTree(new File("packages", "projectB-buildScript.zip")))
    }

    /**
     * This is a regression test for GR #6124.  It ensures that the build_info folder in the buildScript package for
     * every project lists the source dependency information for it and the transitive set of its source dependencies.
     * Originally it didn't list source dependency information at all; then it only listed immediate dependencies; then
     * it immediate plus subprojects recursively, which was wrong because source dependencies are subprojects of the
     * root project, not of each other.
     */
    @Test
    public void testPackageSourceDependencies() {
        File projectTemplateDir = new File(getTestDir(), "projectCin")
        File projectDir = new File(getTestDir(), "projectC")
        FileHelper.ensureDeleteDirRecursive(projectDir)

        FileUtils.copyDirectory(projectTemplateDir, projectDir)

        // Create a dummy project to provide access to FileTree methods
        Project project = ProjectBuilder.builder().withProjectDir(projectDir).build()

        HgUtil.hgExec(project, "init", "srcDep1")
        HgUtil.hgExec(project, "init", "srcDep2")
        HgUtil.hgExec(project, "init", "noBuildFile")
        HgUtil.hgExec(project, "init")

        // Run fAD to make sure the settings.gradle and settings-subprojects.txt are created.
        // First for srcDep1 ...
        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("fAD")
            launcher.expectFailure(RecursivelyFetchSourceTask.NEW_SUBPROJECTS_MESSAGE)
        }
        // ... then srcDep12 ...
        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("fAD")
            launcher.expectFailure(RecursivelyFetchSourceTask.NEW_SUBPROJECTS_MESSAGE)
        }
        // ... then noBuildScript ...
        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("fAD")
            launcher.expectFailure(RecursivelyFetchSourceTask.NEW_SUBPROJECTS_MESSAGE)
        }
        // ... and lastly just to get a successful run.
        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("fAD")
        }

        // Now do the actual test.
        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("packageEverything")
        }

        checkBuildInfoZip(project, project.projectDir, ["srcDep1", "srcDep2", "noBuildFile"])
        checkBuildInfoZip(project, new File(project.projectDir, "srcDep1"), ["srcDep2", "noBuildFile"])
        checkBuildInfoZip(project, new File(project.projectDir, "srcDep2"), ["noBuildFile"])
    }

    private static void checkBuildInfoZip(Project rootProject, File projectDir, Collection<String> srcDepNames) {
        File packagesDir = new File(projectDir, "packages")
        File buildScriptFile = new File(packagesDir, "${projectDir.name}-buildScript.zip")
        Set<String> expectedFiles = new LinkedHashSet<>()
        collectSourceDependencyFiles(expectedFiles, srcDepNames)
        Assert.assertTrue(buildScriptFile.exists())
        checkBuildInfoFiles(rootProject.zipTree(buildScriptFile), expectedFiles)
    }

    private static final String[] SOURCE_DEP_INFO_FILES = [
        "source_path.txt",
        "source_revision.txt",
        "source_url.txt",
    ]

    private static void collectSourceDependencyFiles(
        Set<String> set,
        Collection<String> subprojectNames
    ) {
        SOURCE_DEP_INFO_FILES.each {
            set.add("build_info/$it")
        }
        subprojectNames.each { subprojectName ->
            SOURCE_DEP_INFO_FILES.each {
                set.add("build_info/source_dependencies/${subprojectName}/$it")
            }
        }
    }

    public static void checkBuildInfoFiles(
        FileTree directory,
        Set<String> expectedFiles = []
    ) {
        expectedFiles.addAll([
            "build_info/versions.txt"
        ])
        Set<String> actualFiles = new LinkedHashSet<>()

        directory.visit { FileVisitDetails visitor ->
            actualFiles.add(visitor.relativePath.toString())
        }

        Set<String> missingFiles = expectedFiles - actualFiles
        Assert.assertTrue("Missing files:\n${missingFiles.join('\n    ')}", missingFiles.empty)
    }
}
