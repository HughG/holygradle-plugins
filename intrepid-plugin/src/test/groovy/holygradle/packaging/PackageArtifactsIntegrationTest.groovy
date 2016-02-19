package holygradle.packaging

import holygradle.io.FileHelper
import holygradle.source_dependencies.RecursivelyFetchSourceTask
import holygradle.test.AbstractHolyGradleIntegrationTest
import holygradle.test.WrapperBuildLauncher
import holygradle.testUtil.HgUtil
import org.apache.commons.io.FileUtils
import org.gradle.api.Project
import org.gradle.api.file.FileTree
import org.gradle.api.file.FileVisitDetails
import org.gradle.process.ExecSpec
import org.gradle.testfixtures.ProjectBuilder
import org.hamcrest.core.IsEqual
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ErrorCollector

import java.util.zip.ZipFile

import static org.junit.Assert.assertNotNull
import static org.junit.Assert.assertTrue

class PackageArtifactsIntegrationTest extends AbstractHolyGradleIntegrationTest {
    @Rule
    public ErrorCollector collector = new ErrorCollector()

    @Test
    public void testBasicConfiguration() {
        File projectDir = new File(getTestDir(), "projectA")

        Project project = ProjectBuilder.builder().withProjectDir(projectDir).build()
        project.ext.holyGradleInitScriptVersion = "1.2.3.4" // Required by custom-gradle-core-plugin.
        project.ext.holyGradlePluginsRepository = ""
        project.apply plugin: 'intrepid'

        Collection<PackageArtifactHandler> packageArtifacts =
            project.extensions.findByName("packageArtifacts") as Collection<PackageArtifactHandler>
        assertNotNull(packageArtifacts)
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
        
        assertTrue(packagesDir.exists())
        assertTrue(new File(packagesDir, "projectB-foo.zip").exists())
        assertTrue(new File(packagesDir, "projectB-bar.zip").exists())
        File buildScriptFile = new File(packagesDir, "projectB-buildScript.zip")
        assertTrue(buildScriptFile.exists())
        ZipFile buildScriptZipFile = new ZipFile(buildScriptFile)
        assertNotNull("build file is in zip", buildScriptZipFile.getEntry("build.gradle"))

        checkBuildInfo(project.zipTree(new File("packages", "projectB-buildScript.zip")), collector)
    }

    @Test
    public void testPackageSourceDependencies() {
        File projectTemplateDir = new File(getTestDir(), "projectCin")
        File projectDir = new File(getTestDir(), "projectC")
        FileHelper.ensureDeleteDirRecursive(projectDir)

        FileUtils.copyDirectory(projectTemplateDir, projectDir)

        File packagesDir = new File(projectDir, "packages")

        // Create a dummy project to provide access to FileTree methods
        Project project = ProjectBuilder.builder().withProjectDir(projectDir).build()

        HgUtil.hgExec(project, "init", "noBuildFile")
        HgUtil.hgExec(project, "init", "subProj")
        HgUtil.hgExec(project, "init")

        // Run fAD to make sure the settings.gradle and settings-subprojects.txt are created.
        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("fAD")
            launcher.expectFailure(RecursivelyFetchSourceTask.NEW_SUBPROJECTS_MESSAGE)
        }
        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("fAD")
        }

        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("packageEverything")
        }

        File buildScriptFile = new File(packagesDir, "projectC-buildScript.zip")
        assertTrue(buildScriptFile.exists())

        checkBuildInfo(
            project.zipTree(new File("packages", "projectC-buildScript.zip")),
            collector,
            [
                "build_info/source_path.txt",
                "build_info/source_revision.txt",
                "build_info/source_url.txt",

                "build_info/source_dependencies",

                "build_info/source_dependencies/noBuildFile",
                "build_info/source_dependencies/noBuildFile/source_path.txt",
                "build_info/source_dependencies/noBuildFile/source_revision.txt",
                "build_info/source_dependencies/noBuildFile/source_url.txt",

                "build_info/source_dependencies/subProj",
                "build_info/source_dependencies/subProj/source_path.txt",
                "build_info/source_dependencies/subProj/source_revision.txt",
                "build_info/source_dependencies/subProj/source_url.txt"
            ]
        )
    }

    public static void checkBuildInfo(FileTree directory, ErrorCollector collector, Collection<String> checkFiles = []) {
        checkFiles.addAll([
            "build_info",
            "build_info/versions.txt"
        ])

        Map<String, Boolean> fileMap = checkFiles.collectEntries { [it, false] }

        directory.visit { FileVisitDetails visitor ->
            String fileRelativePath = visitor.relativePath.toString()

            if (fileMap.containsKey(fileRelativePath)) {
                fileMap[fileRelativePath] = true
            }
        }

        fileMap.each { file ->
            collector.checkThat("File '$file.key' was not found", file.value, IsEqual.equalTo(true))
        }
    }
}
