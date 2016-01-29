package holygradle.symlinks

import holygradle.io.Link
import org.gradle.api.DefaultTask

class SymlinkTask extends DefaultTask {
    private Map<File, File> entries = [:]

    public void initialize() {
        Map<File, File> localEntries = entries // capture private for closure
        doFirst {
            localEntries.each { File linkDir, File targetDir ->
                Link.rebuild(linkDir, targetDir, this.logger)
            }
        }
    }

    public void addLink(File linkDir, File targetDir) {
        if (entries.containsKey(linkDir)) {
            throw new RuntimeException(
                "Cannot initialize for symlink from '${linkDir.path}' to '${targetDir.path}' " +
                "because a symlink has already been added from there to '${entries[linkDir].path}'"
            )
        }
        entries[linkDir] = targetDir
    }
}
