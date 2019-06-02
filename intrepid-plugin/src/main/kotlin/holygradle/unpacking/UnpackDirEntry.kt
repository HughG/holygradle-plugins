package holygradle.unpacking

import java.io.File

/**
 * Describes where and how to unpack the ZIP files for a given {@link UnpackModuleVersion}.
 */
data class UnpackDirEntry (
        /**
         * The location to which to unpack all the ZIP files.
         */
        val unpackDir: File,

        /**
         * If true, unpacking tasks should use Gradle's built-in "up-to-date checking" mechanisms to compare the files in
         * inside the ZIP files with the files in the {@link #unpackDir} before unpacking.  Otherwise, they may use
         * some quicker short-cut mechanism.
         */
        val applyUpToDateChecks: Boolean,

        /**
         * If true, the unpacked files should be marked as readonly; otherwise, they may be read-write.
         */
        val makeReadOnly: Boolean
)