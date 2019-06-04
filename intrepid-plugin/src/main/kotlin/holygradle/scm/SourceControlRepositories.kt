package holygradle.scm

import holygradle.source_dependencies.SourceDependencyHandler
import org.gradle.api.Project
import java.io.File

object SourceControlRepositories {
    private val ALL_REPOSITORY_TYPES = listOf(SvnRepository.TYPE, HgRepository.TYPE, GitRepository.TYPE)

    @JvmStatic
    fun create(
        project: Project
    ): SourceControlRepository {
        val location = project.projectDir
        val repoLocation = findSourceControlDir(location)
        val repoTypesAtLocation = getRepositoryTypesAtLocation(repoLocation)

        return when (repoTypesAtLocation.size) {
            0 -> DummySourceControl()
            1 -> {
                val repo = getRepositoryOfType(repoTypesAtLocation[0], makeCommandLineFactory(project), repoLocation)
                if (repoLocation == location) {
                    return repo
                }

                // The original "location" exists in a folder within a directory hierarchy which is under some kind
                // of source control working copy directory hierarchy, but it might be in an ignored folder, in
                // which case it isn't really under source control.
                //
                // If we're searching upward then we're in a Gradle project, so we must have at least one file
                // (settings and/or build script) in the location folder.  This is relevant because some source
                // control systems (Git and Mercurial) don't let you find the ignored status of folders, so we need
                // to ask about a normal file.
                return if (repo.ignoresFile(project.buildFile)) {
                    DummySourceControl()
                } else {
                    repo
                }
            }
            else -> throw RuntimeException(
                    "${repoLocation} contains a combination of folders from " +
                            ALL_REPOSITORY_TYPES.map {it.stateDirName} +
                            ", which is not supported, because it is impossible to tell which to use."
                    )
        }
    }

    @JvmStatic
    fun create(
        handler: SourceDependencyHandler
    ): SourceControlRepository  {
        // We don't use handler.getSourceDependencyProject(...).projectDir, because source dependencies don't have
        // to contain a Gradle project.  We don't search upward or allow a dummy SourceControlRepository because we
        // expect source dependencies to be in their own repos.
        val repoLocation = handler.destinationDir
        val repoTypesAtLocation = getRepositoryTypesAtLocation(repoLocation)
        return when (repoTypesAtLocation.size) {
            0 -> DummySourceControl()
            1 -> getRepositoryOfType(repoTypesAtLocation[0], makeCommandLineFactory(handler.project), repoLocation)
            else -> throw RuntimeException(
                    "${repoLocation} contains a combination of folders from " +
                            ALL_REPOSITORY_TYPES.map {it.stateDirName} +
                            ", which is not supported, because it is impossible to tell which to use."
                    )
        }
    }

    private fun makeCommandLineFactory(project: Project): (String) -> Command {
        return { executableName -> CommandLine(project.logger, executableName, project::exec) }
    }

    /**
     * Find the source control repository root folder which contains {@code location}.
     * @param location
     * @return
     */
    private fun findSourceControlDir(location: File): File {
        var d = location
        while (d.parentFile != null) {
            if (!getRepositoryTypesAtLocation(d).isEmpty()) {
                return d
            }
            d = d.parentFile
        }
        return location
    }

    private fun getRepositoryTypesAtLocation(location: File): List<SourceControlType> {
        return ALL_REPOSITORY_TYPES.filter {
            File(location, it.stateDirName).exists()
        }
    }

    private fun getRepositoryOfType(
        type: SourceControlType,
        commandFactory: (String) -> Command,
        repoLocation: File
    ): SourceControlRepository  {
        return type.repositoryClass
                .getConstructor(Command::class.java, File::class.java)
                .newInstance(commandFactory(type.executableName), repoLocation)
    }
    @JvmStatic
    fun createExtension(project: Project): SourceControlRepository {
        val repo = create(project)
        project.extensions.add("sourceControl", repo)
        return repo
    }
}