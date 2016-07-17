package holygradle.unpacking

import org.gradle.api.Project
import org.gradle.api.file.CopySpec
import org.gradle.api.file.FileCopyDetails

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
        long zipFileLastModified = zipFile.lastModified()
        project.copy { CopySpec it ->
            it.from(project.zipTree(zipFile))
            it.into(targetDirectory)
            it.eachFile { FileCopyDetails details ->
                File output = new File(targetDirectory, details.path)
                // If the output file already exists:
                //   - if it's newer, don't bother unzipping;
                //   - if not, make sure it's writable so we can replace it.
                if (output.exists()) {
                    if (output.lastModified() >= zipFileLastModified) {
                        details.exclude()
                    } else {
                        output.writable = true
                    }
                }
            }
        }
    }
}
