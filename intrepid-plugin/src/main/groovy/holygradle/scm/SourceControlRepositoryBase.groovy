package holygradle.scm

import java.nio.file.Files

abstract class SourceControlRepositoryBase implements SourceControlRepository {
    protected final File workingCopyDir
    protected final Command scmCommand

    public SourceControlRepositoryBase(Command scmCommand, File workingCopyDir) {
        this.scmCommand = scmCommand
        this.workingCopyDir = workingCopyDir
    }

    public final File getLocalDir() {
        workingCopyDir.absoluteFile
    }

    @Override
    public final boolean ignoresFile(File file) {
        if (file == null) {
            throw new NullPointerException("file must not be null")
        }
        if (!file.exists()) {
            throw new IllegalArgumentException(
                "Cannot check whether repo ignores file ${file} because it does not exist"
            )
        }
        if (!Files.isRegularFile(file.toPath())) {
            throw new IllegalArgumentException(
                "Cannot check whether repo ignores file ${file} because it is not a regular file"
            )
        }

        return ignoresFileInternal(file)
    }

    protected abstract boolean ignoresFileInternal(File file)
}
