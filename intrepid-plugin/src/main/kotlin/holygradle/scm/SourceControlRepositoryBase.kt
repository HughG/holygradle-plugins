package holygradle.scm

import java.io.File
import java.nio.file.Files

internal abstract class SourceControlRepositoryBase(
        protected val scmCommand: Command,
        protected val workingCopyDir: File
) : SourceControlRepository {
    override val localDir: File get() = workingCopyDir.absoluteFile

    override fun ignoresFile(file: File): Boolean {
        if (!file.exists()) {
            throw IllegalArgumentException(
                    "Cannot check whether repo ignores file ${file} because it does not exist"
                    )
        }
        if (!Files.isRegularFile(file.toPath())) {
            throw IllegalArgumentException(
                    "Cannot check whether repo ignores file ${file} because it is not a regular file"
                    )
        }

        return ignoresFileInternal(file)
    }

    protected abstract fun ignoresFileInternal(file: File): Boolean
}
