package holygradle.scm

import holygradle.source_dependencies.SourceDependencyHandler
import org.gradle.api.Project

import java.util.function.Function

public class SourceControlRepositories {
    private static final List<SourceControlType> ALL_REPOSITORY_TYPES =
        [SvnRepository.TYPE, HgRepository.TYPE, GitRepository.TYPE]

    public static SourceControlRepository create(
        Project project
    ) {
        File location = project.projectDir
        File repoLocation = findSourceControlDir(location)
        List<SourceControlType> repoTypesAtLocation = getRepositoryTypesAtLocation(repoLocation)

        switch (repoTypesAtLocation.size()) {
            case 0:
                return new DummySourceControl()
            case 1:
                SourceControlRepository repo = getRepositoryOfType(repoTypesAtLocation[0], makeCommandLineFactory(project), repoLocation)
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
                if (repo.ignoresFile(project.gradle.startParameter.buildFile)) {
                    return new DummySourceControl()
                } else {
                    return repo
                }

            default:
                throw new RuntimeException(
                        "${repoLocation} contains a combination of folders from " +
                                ALL_REPOSITORY_TYPES.collect {it.stateDirName} +
                                ", which is not supported, because it is impossible to tell which to use."
                )
        }
    }

    public static SourceControlRepository create(
        SourceDependencyHandler handler
    ) {
        // We don't use handler..getSourceDependencyProject(...).projectDir, because source dependencies don't have
        // to contain a Gradle project.  We don't search upward or allow a dummy SourceControlRepository because we
        // expect source dependencies to be in their own repos.
        File repoLocation = handler.destinationDir
        List<SourceControlType> repoTypesAtLocation = getRepositoryTypesAtLocation(repoLocation)
        switch (repoTypesAtLocation.size()) {
            case 0:
                return null
            case 1:
                return getRepositoryOfType(repoTypesAtLocation[0], makeCommandLineFactory(handler.project), repoLocation)
            default:
                throw new RuntimeException(
                    "${repoLocation} contains a combination of folders from " +
                    ALL_REPOSITORY_TYPES.collect {it.stateDirName} +
                    ", which is not supported, because it is impossible to tell which to use."
                )
        }
    }

    private static Function<String, Command> makeCommandLineFactory(Project project) {
        return { String executableName -> new CommandLine(project.logger, executableName, project.&exec) }
    }

    /**
     * Find the source control repository root folder which contains {@code location}.
     * @param location
     * @return
     */
    private static File findSourceControlDir(File location) {
        for (File d = location; d.parentFile != null; d = d.parentFile) {
            if (!getRepositoryTypesAtLocation(d).isEmpty()) {
                return d
            }
        }
        return location
    }

    private static List<SourceControlType> getRepositoryTypesAtLocation(File location) {
        return ALL_REPOSITORY_TYPES.findAll {
            new File(location, it.stateDirName).exists()
        }
    }

    private static SourceControlRepository getRepositoryOfType(
        SourceControlType type,
        Function<String, Command> commandFactory,
        File repoLocation
    ) {
        return type.repositoryClass
            .getConstructor(Command, File)
            .newInstance(commandFactory.apply(type.executableName), repoLocation)
    }

    public static SourceControlRepository createExtension(Project project) {
        project.extensions.add("sourceControl", create(project)) as SourceControlRepository
    }
}