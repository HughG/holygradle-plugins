package holygradle.unpacking

/**
 * Extends {@link UnpackDirEntry} with a specific list of ZIP files.  The mulitple {@link UnpackModuleVersion} objects
 * for the same module version (but from different configurations) should have equal {@link UnpackDirEntry} objects but
 * may have different sets of ZIP files.  Thee contents of these ZIP files will be merged in the unpack dir.
 */
class UnpackEntry extends UnpackDirEntry {
    /**
     * The set of ZIP files to unpack.
     */
    public final Collection<File> zipFiles

    public UnpackEntry(Collection<File> zipFiles, File unpackDir, boolean applyUpToDateChecks, boolean makeReadOnly) {
        super(unpackDir, applyUpToDateChecks, makeReadOnly)
        this.zipFiles = zipFiles

        if (zipFiles == null) {
            throw new NullPointerException("zipFiles is null in UnpackEntry")
        }
    }

    @Override
    public String toString() {
        return "UnpackEntry{" +
            "zipFiles=" + zipFiles +
            ", unpackDir=" + unpackDir +
            ", applyUpToDateChecks=" + applyUpToDateChecks +
            ", makeReadOnly=" + makeReadOnly +
            '}'
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
        result = 31 * result + super.hashCode()
        return result
    }
}
