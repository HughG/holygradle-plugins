package holygradle.scm

import org.gradle.api.Project

public class SourceControlRepositories {
    private static final List<SourceControlType> ALL_REPOSITORY_TYPES =
        [SvnRepository.TYPE, HgRepository.TYPE, GitRepository.TYPE]

    public static SourceControlRepository create(
        Project project,
        File location,
        boolean useDummyIfNecessary = false
    ) {
        File repoLocation = findSourceControlDir(location)
        List<SourceControlType> repoTypesAtLocation = getRepositoryTypesAtLocation(repoLocation)

        switch (repoTypesAtLocation.size()) {
            case 0:
                return useDummyIfNecessary ? new DummySourceControl() : null
            case 1:
                return getRepositoryOfType(repoTypesAtLocation[0], project, repoLocation)
            default:
                throw new RuntimeException(
                    "${repoLocation} contains a combination of folders from " +
                    ALL_REPOSITORY_TYPES.collect {it.stateDirName} +
                    ", which is not supported, because it is impossible to tell which to use."
                )
        }
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
        Project project,
        File repoLocation
    ) {
        return type.repositoryClass
            .getConstructor(Command, File)
            .newInstance(new CommandLine(project.logger, type.executableName, project.&exec), repoLocation)
    }

    public static SourceControlRepository createExtension(Project project) {
        project.extensions.add("sourceControl", create(project, project.projectDir, true)) as SourceControlRepository
    }
}