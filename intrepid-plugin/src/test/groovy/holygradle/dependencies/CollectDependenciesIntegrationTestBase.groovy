package holygradle.dependencies

import holygradle.io.FileHelper
import holygradle.test.AbstractHolyGradleIntegrationTest
import holygradle.test.WrapperBuildLauncher
import org.apache.commons.io.FileUtils
import org.gradle.api.Project
import org.gradle.api.file.FileTree
import org.gradle.api.file.FileVisitDetails
import org.gradle.testfixtures.ProjectBuilder
import org.hamcrest.core.IsEqual
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ErrorCollector

abstract class CollectDependenciesIntegrationTestBase extends AbstractHolyGradleIntegrationTest {
    @Rule
    public ErrorCollector collector = new ErrorCollector()

    /**
     * Test that, if you depend on a module only via configurations which have no artifacts, and those lead to other
     * module configurations which do have artifacts, then all those modules are correctly collected.  The one with
     * empty configurations will just have an ivy.xml file.
     *
     * This is a (regression) test for #4088.
     */
    @Test
    public void useOnlyViaEmptyConfigs() {
        final File projectDir = new File(getTestDir(), "useOnlyViaEmptyConfigs")
        final File emptyConfigLibDir = new File(projectDir, "empty-config-lib")
        final Collection<String> allCollectedDepDirNames = [
            "empty-config-lib",
            "external-lib",
            "another-lib"
        ]

        doCollectDependenciesTest(projectDir, allCollectedDepDirNames, emptyConfigLibDir)
    }

    /**
     * Test that, if you depend on a module only via configurations which have no artifacts, then that module is
     * correctly collected, even if no other modules are involved.  It will just have an ivy.xml file.
     *
     * This is a (regression) test for #4088.
     */
    @Test
    public void useOnlyEmptyConfigs() {
        final File projectDir = new File(getTestDir(), "useOnlyEmptyConfigs")
        final File emptyConfigLibDir = new File(projectDir, "empty-config-lib")
        final Collection<String> allCollectedDepDirNames = ["empty-config-lib"]

        doCollectDependenciesTest(projectDir, allCollectedDepDirNames, emptyConfigLibDir)
    }

    /**
     * Test that, if you depend on a module only via a private configuration in a non-root project (and the root project
     * has no dependency on that config) then all those modules are correctly collected.  The one with empty
     * configurations will just have an ivy.xml file.
     *
     * This is a (regression) test for a bug introduced in c2f77e448810c9d42bc0a1a260db22c1c6ea1d9d (released as 7.4.1).
     */
    @Test
    public void useInPrivateSubprojConfig() {
        final File projectDir = new File(getTestDir(), "useInPrivateSubprojConfig")
        final Collection<String> allCollectedDepDirNames = [
            "external-lib",
            "another-lib"
        ]

        doCollectDependenciesTest(projectDir, allCollectedDepDirNames, null)
    }

    protected void doCollectDependenciesTest(
        File projectDir,
        Collection<String> allCollectedDepDirNames,
        File emptyConfigLibDir
    ) {
        FileHelper.ensureDeleteDirRecursive(projectDir)
        String projectName = projectDir.name
        File testBaseClassDir = new File(
            projectDir.parentFile.parentFile,
            CollectDependenciesIntegrationTestBase.class.name.tokenize('.').last()
        )
        FileUtils.copyDirectory(new File(testBaseClassDir, projectName), projectDir)

        // Create a dummy project to provide access to FileTree methods
        Project project = ProjectBuilder.builder().withProjectDir(projectDir).build()

        ensureCollectedDependenciesDeleted(project)

        invokeGradle(projectDir) { WrapperBuildLauncher launcher ->
            launcher.addArguments("--info")
            launcher.forTasks(getCollectTaskName())
        }

        checkCollectedDependenciesExist(project)

        checkBuildInfo(
            getCollectedArtifactsFileTree(project),
            getCollectedFilesPathPrefix(),
            emptyConfigLibDir?.name,
            allCollectedDepDirNames,
            collector
        )
    }

    protected abstract void ensureCollectedDependenciesDeleted(Project project)

    protected abstract void checkCollectedDependenciesExist(Project project)

    protected abstract FileTree getCollectedArtifactsFileTree(Project project)

    protected abstract String getCollectTaskName()

    protected abstract String getCollectedFilesPathPrefix()

    public static void checkBuildInfo(
        FileTree directory,
        String collectedFilesPathPrefix,
        String emptyConfigModuleName,
        Collection<String> allCollectedModuleNames,
        ErrorCollector collector
    ) {
        // NOTE 2015-08-18 HughG: We have to call #toString() here otherwise we get instance of GStringImpl (and the
        // collection type doesn't save us, "thanks" to Java's runtime type erasure); then when we later try to find
        // a given relativePath String as a key in fileMap, the #equals() method always returns false.
        Collection<String> checkFiles = allCollectedModuleNames.collect {
            "${collectedFilesPathPrefix}ivy/holygradle.test/$it/1.0/ivy-1.0.xml".toString()
        }
        Map<String, Boolean> fileMap = checkFiles.collectEntries { [it, false] }
        Collection<String> modulesWithArtifactsFound = []

        directory.visit { FileVisitDetails details ->
            String fileRelativePath = details.relativePath.toString()

            if (fileMap.containsKey(fileRelativePath)) {
                fileMap[fileRelativePath] = true
            }

            // If we are visiting a file, inside a version directory, and it's not the Ivy file, then check that we're
            // expecting artifacts for this dependency.  (One of them should have no artifacts.)
            if (!details.isDirectory() &&
                details.file.parentFile.name == "1.0" &&
                details.file.name != "ivy-1.0.xml"
            ) {
                final String moduleName = details.file.parentFile.parentFile.name
                modulesWithArtifactsFound << moduleName
                System.out.println("Found artifact '${fileRelativePath}' for ${moduleName}")
            }
        }

        // Check that we did or did not fine artifacts for each module, as expected.
        allCollectedModuleNames.each { String moduleName ->
            boolean expectArtifacts = (moduleName != emptyConfigModuleName)
            collector.checkThat(
                "${moduleName} should have ${expectArtifacts ? 'some' : 'no'} artifacts",
                modulesWithArtifactsFound.contains(moduleName),
                IsEqual.equalTo(expectArtifacts)
            )
        }

        // Check that we found the Ivy files we expected.
        fileMap.each { file, wasFound ->
            collector.checkThat("File '$file' was found", wasFound, IsEqual.equalTo(true))
        }
    }
}
