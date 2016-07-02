package holygradle.unpacking

import org.gradle.api.Project
import org.gradle.api.file.CopySpec

class GradleZipHelper implements Unzipper {
    private final Project project

    public GradleZipHelper(Project project) {
        this.project = project
    }

    @Override
    Object getDependencies() {
        return []
    }

    @Override
    void unzip(File zipFile, File targetDirectory) {
        project.copy { CopySpec it ->
            it.from(project.zipTree(zipFile))
            it.into(targetDirectory)
        }
    }
}
