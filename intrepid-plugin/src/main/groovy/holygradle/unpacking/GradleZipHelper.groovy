package holygradle.unpacking

import org.gradle.api.Project
import org.gradle.api.tasks.Copy

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
        project.copy { Copy it ->
            it.from(project.fileTree(zipFile))
            it.into(targetDirectory)
        }
    }
}
