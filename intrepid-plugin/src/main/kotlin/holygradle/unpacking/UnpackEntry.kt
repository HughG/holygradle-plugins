package holygradle.unpacking

import java.io.File

data class UnpackEntry(
        /**
         * The set of ZIP files to unpack.
         */
        val zipFiles: Collection<File>,

        /**
         * The location to which to unpack all the {@link #zipFiles}.
         */
        val unpackDir: File,

        /**
         * If true, unpacking tasks should use Gradle's built-in "up-to-date checking" mechanisms to compare the files in
         * inside the {@link #zipFiles} with the files in the {@link #unpackDir} before unpacking.  Otherwise, they may use
         * some quicker short-cut mechanism.
         */
        val applyUpToDateChecks: Boolean,

        /**
         * If true, the unpacked files should be marked as readonly; otherwise, they may be read-write.
         */
        val makeReadOnly: Boolean
)
