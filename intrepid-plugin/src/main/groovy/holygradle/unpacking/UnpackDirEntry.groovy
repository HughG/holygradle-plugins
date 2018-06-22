package holygradle.unpacking

/**
 * Describes where and how to unpack the ZIP files for a given {@link UnpackModuleVersion}.
 */
class UnpackDirEntry {
    /**
     * The location to which to unpack all the ZIP files.
     */
    public final File unpackDir

    /**
     * If true, unpacking tasks should use Gradle's built-in "up-to-date checking" mechanisms to compare the files in
     * inside the ZIP files with the files in the {@link #unpackDir} before unpacking.  Otherwise, they may use
     * some quicker short-cut mechanism.
     */
    public final boolean applyUpToDateChecks

    /**
     * If true, the unpacked files should be marked as readonly; otherwise, they may be read-write.
     */
    public final boolean makeReadOnly

    public UnpackDirEntry(File unpackDir, boolean applyUpToDateChecks, boolean makeReadOnly) {
        this.unpackDir = unpackDir
        this.applyUpToDateChecks = applyUpToDateChecks
        this.makeReadOnly = makeReadOnly

        if (unpackDir == null) {
            throw new NullPointerException("unpackDir is null in UnpackEntry")
        }
    }

    @Override
    public String toString() {
        return "UnpackDirEntry{" +
            "unpackDir=" + unpackDir +
            ", applyUpToDateChecks=" + applyUpToDateChecks +
            ", makeReadOnly=" + makeReadOnly +
            '}'
    }

    @Override
    public boolean equals(o) {
        if (this.is(o)) return true
        if (getClass() != o.class) return false

        UnpackDirEntry that = (UnpackDirEntry) o

        if (applyUpToDateChecks != that.applyUpToDateChecks) return false
        if (makeReadOnly != that.makeReadOnly) return false
        if (unpackDir != that.unpackDir) return false

        return true
    }

    @Override
    public int hashCode() {
        int result
        result = 31 * result + unpackDir.hashCode()
        result = 31 * result + (applyUpToDateChecks ? 1 : 0)
        result = 31 * result + (makeReadOnly ? 1 : 0)
        return result
    }
}
