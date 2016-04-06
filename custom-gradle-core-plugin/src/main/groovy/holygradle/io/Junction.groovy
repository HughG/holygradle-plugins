package holygradle.io

import com.sun.jna.Memory
import com.sun.jna.ptr.IntByReference
import holygradle.jna.platform.win32.Kernel32
import holygradle.jna.platform.win32.Ntifs
import holygradle.jna.platform.win32.Win32Exception
import holygradle.jna.platform.win32.WinNT

import java.nio.ByteBuffer

/**
 * Utility class for managing Directory Junctions under Windows.  Directory junctions are useful because creating a
 * symbolic link under Windows requires the process to have administrator privileges if the process user is a local
 * administrator; but creating a directory junction doesn't need an special privileges.
 *
 * From searching the Internet, this restriction seems to be because symlinks can point to network shares, and the
 * target of a network share can be changed on the server side, so that allows for a class of exploit where an admin
 * user is tricked into running something they didn't intend to.  Directory junctions can only point to folders on the
 * local machine, so aren't quite as risky.
 */
class Junction {
    private static final byte SIZEOF_ULONG = 4 // bytes, because ULONG is always 32-bit
    private static final byte SIZEOF_USHORT = 2 // bytes
    private static final byte REPARSE_DATA_HEADER_LENGTH =
        SIZEOF_ULONG + // ReparseTag
        SIZEOF_USHORT + // ReparseDataLength
        SIZEOF_USHORT // Reserved
    private static final byte MOUNT_POINT_REPARSE_BUFFER_HEADER_LENGTH =
        SIZEOF_USHORT + // SubstituteNameOffset
        SIZEOF_USHORT + // SubstituteNameLength
        SIZEOF_USHORT + // PrintNameOffset
        SIZEOF_USHORT // PrintNameLength

    public static final int NOT_A_REPARSE_POINT = 0x00001126
    public static final String IGNORE_MAX_PATH_PREFIX = '\\\\?\\'
    public static final String DO_NOT_PARSE_FILENAME_PREFIX = '\\??\\'

    /**
     * Returns true if the file is a directory junction.  This does not guarantee that it has a valid target; for
     * example, a non-existent file or a network share would not be a valid target.
     * @param file The file to check
     * @return true if the file is a directory junction; otherwise false
     */
    public static boolean isJunction(File file) {
        // For simplicity in any error reporting, use canonical path for the link.
        file = file.canonicalFile

        try {
            return isMountPoint(file)
        } catch (Win32Exception ignored) {
            return false
        }
    }

    /**
     * Throws an exception if {@code link} exists and is not a directory junction.
     * @param link The potential link to check.
     */
    public static void checkIsJunctionOrMissing(File link) {
        // For simplicity in any error reporting, use canonical path for the link.
        link = link.canonicalFile

        if (link.exists() && !isJunction(link)) {
            throw new RuntimeException(
                "Cannot not delete or create a directory junction at '${link.path}' " +
                "because a folder or file already exists there and is not a directory junction."
            )
        }
    }

    /**
     * Deletes the {@code link} if it exists and is a directory junction, then also deletes the underlying directory if
     * it is empty.  Does nothing if the {@code link} does not exist or is not a directory junction.
     * @param link The directory junction to delete
     */
    public static void delete(File link) {
        // For simplicity in any error reporting, use canonical path for the link.
        link = link.canonicalFile

        checkIsJunctionOrMissing(link)

        if (isJunction(link)) {
            removeMountPoint(link.canonicalPath)

            // Delete the mount point if the directory is empty
            if (link.isDirectory() && link.list().length == 0) {
                link.delete()
            }
        }
    }

    /**
     * Deletes the {@code link} if it exists and is a directory junction, then creates a directory junction to the
     * {@code target}, including any parent folders for the {@code link}.  Fails if the target does not exist or is not
     * a valid target for a directory junction (for example, if it is a network share path).
     * @param link The directory junction to (re-)create
     * @param target The target of the new directory junction
     */
    public static void rebuild(File link, File target) {
        // For simplicity in any error reporting, use canonical paths for the link and target.
        // Also, directory junction targets must be absolute paths.
        link = link.canonicalFile
        target = getCanonicalTarget(link, target)

        checkIsJunctionOrMissing(link)

        // Directory junctions can be created to non-existent targets but it breaks stuff so disallow it
        if (!target.exists()) {
            throw new IOException("Cannot create link to non-existent target: from '${link}' to '${target}'")
        }

        FileHelper.ensureMkdirs(link, "for directory junction to '${target}'")

        createMountPoint(link.path, target.path)

        // createMountPoint will blindly create links to stuff that won't work, like network shares or non-existent
        // targets. The only way I can see to detect this is to check if the resulting file exists. This will return
        // false if the target is invalid even if the link has been successfully created.
        if (!link.exists()) {
            // Remove the link before continuing.
            delete(link)
            // This will be immediately caught by the outer "catch" block.  Junction.rebuild might also throw for
            // other reasons, which is why we don't just factor this out to another method returning "false" or
            // something.
            throw new IOException("Failed to create directory junction from '${link}' to '${target}'.")
        }
    }

    private static File getCanonicalTarget(File canonicalLink, File target) {
        if (target.absolute) {
            target
        } else {
            // If [target] is relative, we want createSymbolicLink to create a link relative to [link] (as opposed to
            // relative to the current working directory) so we have to calculate this.
            new File(canonicalLink.parentFile, target.path).canonicalFile
        }
    }

    @Category(Integer)
    private static class JunctionHelperIntegerCategory {
        public int characterLengthAsBytes() {
            this * Character.SIZE / Byte.SIZE
        }

        public int bytesAsCharacterLength() {
            this / (Character.SIZE / Byte.SIZE)
        }

        public short asShortSafely() {
            if (this < Short.MIN_VALUE || this > Short.MAX_VALUE) {
                throw new ArithmeticException("$this is outside the range for a short")
            }
            return (short)this
        }
    }

    @Category(ByteBuffer)
    private static class JunctionHelperByteBufferCategory {
        public ByteBuffer putString(String s) {
            int size = s.size()
            for (int i = 0; i < size; ++i) {
                this.putChar(s.charAt(i))
            }
            this.putChar('\0' as char)
            return this
        }
    }

    private static <T> T withReparsePointHandle(String path, int desiredAccess, Closure closure) {
        WinNT.HANDLE reparsePointHandle = Kernel32.INSTANCE.CreateFile(
            path,
            desiredAccess,
            0,
            null,
            Kernel32.OPEN_EXISTING,
            Kernel32.FILE_FLAG_BACKUP_SEMANTICS | Kernel32.FILE_FLAG_OPEN_REPARSE_POINT,
            null
        )
        if (reparsePointHandle == Kernel32.INVALID_HANDLE_VALUE) {
            throw getLastErrorException()
        }

        try {
            return closure(reparsePointHandle)
        } finally {
            Kernel32.INSTANCE.CloseHandle(reparsePointHandle)
        }
    }

    /**
     * Returns the target path of a directory junction.
     * @param link The directory junction whose target path is to be returned
     * @return The target of the directory junction
     * @throws Exception if the target cannot be read from the directory junction (including if the {@code link} is not
     * a directory junction).
     */
    public static File getTarget(File link) {
        // For simplicity in any error reporting, use canonical path for the link.
        link = link.canonicalFile

        withReparsePointHandle(link.toString(), Kernel32.GENERIC_READ) { WinNT.HANDLE reparsePointHandle ->
            Memory reparseDataBuffer = new Memory(Ntifs.MAXIMUM_REPARSE_DATA_BUFFER_SIZE)

            boolean succeeded = Kernel32.INSTANCE.DeviceIoControl(
                reparsePointHandle,
                Ntifs.FSCTL_GET_REPARSE_POINT,
                null,
                0,
                reparseDataBuffer,
                Ntifs.MAXIMUM_REPARSE_DATA_BUFFER_SIZE,
                new IntByReference(),
                null
            )

            if (!succeeded) {
                throw getLastErrorException()
            }

            ByteBuffer reparseDataByteBuffer = reparseDataBuffer.getByteBuffer(0, Ntifs.MAXIMUM_REPARSE_DATA_BUFFER_SIZE) // just the tag
            int reparseTag = reparseDataByteBuffer.getInt()

            if (reparseTag == Ntifs.IO_REPARSE_TAG_MOUNT_POINT) {
                reparseDataByteBuffer.getShort() // Skip the data length
                reparseDataByteBuffer.getShort() // Skip the reserved data

                int nameOffset = reparseDataByteBuffer.getShort()
                int nameLength = reparseDataByteBuffer.getShort()

                reparseDataByteBuffer.getShort() // Skip the print name offset
                reparseDataByteBuffer.getShort() // Skip the print name length

                // Move to the start of name
                for (int i = 0; i < nameOffset; i++) {
                    reparseDataByteBuffer.get()
                }

                String result = "";

                use(JunctionHelperIntegerCategory) {
                    // Read the bytes
                    final int bytesAsCharacterLength = nameLength.bytesAsCharacterLength()
                    for (int i = 0; i < bytesAsCharacterLength; i++) {
                        result += reparseDataByteBuffer.getChar()
                    }
                }

                // Strip the starting "no parse" prefix if it exists.
                if (result.startsWith(DO_NOT_PARSE_FILENAME_PREFIX)) {
                    result = result.substring(DO_NOT_PARSE_FILENAME_PREFIX.length())
                }
                return new File(result)
            } else {
                throw new RuntimeException("File ${link} is not a directory junction")
            }
        }
    }

    private static boolean isMountPoint(File link) {
        // Here we use the '\\?\' prefix which sidesteps the MAX_PATH (260) limit and passes the string directly to the
        // filesystem which, in the case of NTFS, has a limit of 32767 characters; see
        // <https://msdn.microsoft.com/en-us/library/windows/desktop/aa365247(v=vs.85).aspx>.  This allows us to delete
        // links even if they've been created at a location beyond the MAX_PATH limit.  This can be necessary for the
        // Holy Gradle integration tests, depending on the checkout location, and may be useful in projects whcih use it.
        String path = IGNORE_MAX_PATH_PREFIX + link.canonicalPath
        return withReparsePointHandle(path, Kernel32.FILE_READ_ATTRIBUTES) { WinNT.HANDLE reparsePointHandle ->
            Memory reparseDataBuffer = new Memory(Ntifs.MAXIMUM_REPARSE_DATA_BUFFER_SIZE)

            boolean succeeded = Kernel32.INSTANCE.DeviceIoControl(
                reparsePointHandle,
                Ntifs.FSCTL_GET_REPARSE_POINT,
                null,
                0,
                reparseDataBuffer,
                Ntifs.MAXIMUM_REPARSE_DATA_BUFFER_SIZE,
                new IntByReference(),
                null
            )

            if (succeeded) {
                ByteBuffer reparseDataByteBuffer = reparseDataBuffer.getByteBuffer(0, SIZEOF_ULONG) // just the tag
                int reparseTag = reparseDataByteBuffer.getInt()
                return (reparseTag == Ntifs.IO_REPARSE_TAG_MOUNT_POINT)
            }

            if (Kernel32.INSTANCE.GetLastError().intValue() == NOT_A_REPARSE_POINT) {
                return false
            }

            throw getLastErrorException()
        }
    }

    private static void createMountPoint(String link, String target) {
        withReparsePointHandle(link, Kernel32.GENERIC_READ | Kernel32.GENERIC_WRITE) { WinNT.HANDLE reparsePointHandle ->
            use (JunctionHelperIntegerCategory) {
                // For the SubstituteName, we use the '\??\' prefix which disables further parsing; see
                // <http://www.flexhex.com/docs/articles/hard-links.phtml>.  Note that this is NOT the same as the
                // '\\?\' prefix, which just sidesteps the MAX_PATH (260) limit and passes the string directly to the
                // filesystem which, in the case of NTFS, has a limit of 32767 characters; see
                // <https://msdn.microsoft.com/en-us/library/windows/desktop/aa365247(v=vs.85).aspx>.
                String printName = target
                String substituteName = DO_NOT_PARSE_FILENAME_PREFIX + target + '\\'

                short pathBufferLength = (substituteName.size() + 1 + printName.size() + 1).characterLengthAsBytes().asShortSafely()
                short mountPointReparseBufferLength = (MOUNT_POINT_REPARSE_BUFFER_HEADER_LENGTH + pathBufferLength).asShortSafely()
                short reparseDataBufferLength = REPARSE_DATA_HEADER_LENGTH + mountPointReparseBufferLength
                short substituteNameOffset = 0
                short substituteNameLength = (substituteName.size().characterLengthAsBytes()).asShortSafely()
                short printNameOffset = (substituteNameLength + 1.characterLengthAsBytes()).asShortSafely()
                short printNameLength = (printName.size().characterLengthAsBytes()).asShortSafely()
                Memory reparseDataBuffer = new Memory(reparseDataBufferLength)
                ByteBuffer reparseDataByteBuffer = reparseDataBuffer.getByteBuffer(0, reparseDataBufferLength)

                use (JunctionHelperByteBufferCategory) {
                    // First the REPARSE_DATA_BUFFER ...
                    reparseDataByteBuffer.putInt(Ntifs.IO_REPARSE_TAG_MOUNT_POINT)
                        .putShort(mountPointReparseBufferLength)
                        .putShort((short)0) // Reserved
                        // ... then, within that, the MountPointReparseBuffer ...
                        .putShort(substituteNameOffset)
                        .putShort(substituteNameLength)
                        .putShort(printNameOffset)
                        .putShort(printNameLength)
                        // ... then, within that, the PathBuffer ...
                        .putString(substituteName)
                        .putString(printName)
                }

                boolean succeeded = Kernel32.INSTANCE.DeviceIoControl(
                    reparsePointHandle,
                    Ntifs.FSCTL_SET_REPARSE_POINT,
                    reparseDataBuffer,
                    reparseDataBufferLength,
                    null,
                    0,
                    new IntByReference(),
                    null
                )
                if (!succeeded) {
                    throw getLastErrorException()
                }
            }
        }
    }

    private static void removeMountPoint(String link) {
        withReparsePointHandle(link, Kernel32.GENERIC_READ | Kernel32.GENERIC_WRITE) { WinNT.HANDLE reparsePointHandle ->
            Memory reparseDataBuffer = new Memory(REPARSE_DATA_HEADER_LENGTH)
            ByteBuffer reparseDataByteBuffer = reparseDataBuffer.getByteBuffer(0, REPARSE_DATA_HEADER_LENGTH)

            use (JunctionHelperByteBufferCategory) {
                // First the REPARSE_DATA_BUFFER ...
                reparseDataByteBuffer.putInt(Ntifs.IO_REPARSE_TAG_MOUNT_POINT)
                    .putShort((short)0) // ReparseDataLength; 0 because we don't need extra data to delete
                    .putShort((short)0) // Reserved
            }

            boolean succeeded = Kernel32.INSTANCE.DeviceIoControl(
                reparsePointHandle,
                Ntifs.FSCTL_DELETE_REPARSE_POINT,
                reparseDataBuffer,
                REPARSE_DATA_HEADER_LENGTH,
                null,
                0,
                new IntByReference(),
                null
            )
            if (!succeeded) {
                throw getLastErrorException()
            }
        }
    }

    private static Win32Exception getLastErrorException() {
        return new Win32Exception(Kernel32.INSTANCE.GetLastError())
    }

}
