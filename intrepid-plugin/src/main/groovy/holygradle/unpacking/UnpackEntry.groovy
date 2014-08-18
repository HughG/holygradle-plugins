package holygradle.unpacking

class UnpackEntry {
    /**
     * The set of ZIP files to unpack.
     */
    public final Collection<File> zipFiles

    /**
     * The location to which to unpack all the {@link #zipFiles}.
     */
    public final File unpackDir

    /**
     * If true, unpacking tasks should use Gradle's built-in "up-to-date checking" mechanisms to compare the files in
     * inside the {@link #zipFiles} with the files in the {@link #unpackDir} before unpacking.  Otherwise, they may use
     * some quicker short-cut mechanism.
     */
    public final boolean applyUpToDateChecks

    /**
     * If true, the unpacked files should be marked as readonly; otherwise, they may be read-write.
     */
    public final boolean makeReadOnly

    public UnpackEntry(Collection<File> zipFiles, File unpackDir, boolean applyUpToDateChecks, boolean makeReadOnly) {
        this.zipFiles = zipFiles
        this.unpackDir = unpackDir
        this.applyUpToDateChecks = applyUpToDateChecks
        this.makeReadOnly = makeReadOnly

        if (unpackDir == null) {
            throw new NullPointerException("unpackDir is null in UnpackEntry")
        }
    }

    @Override
    public String toString() {
        return "UnpackEntry{" +
            "zipFiles=" + zipFiles +
            ", unpackDir=" + unpackDir +
            ", applyUpToDateChecks=" + applyUpToDateChecks +
            ", makeReadOnly=" + makeReadOnly +
            '}';
    }

    @Override
    public boolean equals(o) {
        if (this.is(o)) return true
        if (getClass() != o.class) return false

        UnpackEntry that = (UnpackEntry) o

        if (applyUpToDateChecks != that.applyUpToDateChecks) return false
        if (makeReadOnly != that.makeReadOnly) return false
        if (unpackDir != that.unpackDir) return false
        if (zipFiles != that.zipFiles) return false

        return true
    }

    @Override
    public int hashCode() {
        int result
        result = zipFiles.hashCode()
        result = 31 * result + unpackDir.hashCode()
        result = 31 * result + (applyUpToDateChecks ? 1 : 0)
        result = 31 * result + (makeReadOnly ? 1 : 0)
        return result
    }
}
