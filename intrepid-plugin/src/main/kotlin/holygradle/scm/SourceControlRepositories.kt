package holygradle.scm

import org.gradle.api.Project
import java.io.File

object SourceControlRepositories {
    @JvmStatic
    @JvmOverloads
    fun create(
        rootProject: Project,
        location: File,
        useDummyIfNecessary: Boolean = false
    ): SourceControlRepository? {
        val svnFile = File(location, ".svn")
        val hgFile = File(location, ".hg")
        val gitFile = File(location, ".git")
        if (listOf(svnFile, hgFile, gitFile).count { it.exists() } > 1) {
            throw RuntimeException(
                    "${location} contains a combination of Subversion '.svn', Mercurial '.hg' and/or Git '.git' folders, " +
                    "which is not supported, because it is impossible to tell which to use for source version information."
            )
        }

        return when {
            svnFile.exists() -> SvnRepository(CommandLine(rootProject.logger, "svn.exe", rootProject::exec), location)
            hgFile.exists() -> HgRepository(CommandLine(rootProject.logger, "hg.exe", rootProject::exec), location)
            gitFile.exists() -> GitRepository(CommandLine(rootProject.logger, "git.exe", rootProject::exec), location)
            useDummyIfNecessary -> DummySourceControl()
            else -> null
        }
    }

    @JvmStatic
    fun createExtension(project: Project): SourceControlRepository? {
        val repo = create(project, project.projectDir, true)
        if (repo != null) {
            project.extensions.add("sourceControl", repo)
        }
        return repo
    }
}