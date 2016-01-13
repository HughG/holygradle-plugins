package holygradle.io

import com.sun.jna.Memory
import com.sun.jna.ptr.IntByReference
import holygradle.jna.platform.win32.Kernel32
import holygradle.jna.platform.win32.Ntifs
import holygradle.jna.platform.win32.Win32Exception
import holygradle.jna.platform.win32.WinNT

import java.nio.ByteBuffer

/**
* Utility class for managing Directory Junctions under Windows.
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

    public static boolean isJunction(File file) {
        return file.isDirectory() && isMountPoint(file.path)
    }

    /**
     * Throws an exception if {@code link} exists and is not a directory junction.
     * @param link The potential link to check.
     */
    public static void checkIsJunctionOrMissing(File link) {
        if (link.exists() && !isJunction(link)) {
            throw new RuntimeException(
                "Cannot not delete or create a directory junction at '${link.path}' " +
                "because a folder or file already exists there and is not a directory junction."
            )
        }
    }

    public static void delete(File link) {
        checkIsJunctionOrMissing(link)

        if (isJunction(link)) {
            removeMountPoint(link.path)
            link.delete()
        }
    }

    public static void rebuild(File link, File target) {
        checkIsJunctionOrMissing(link)

        // Directory junctions can be created to non-existent targets but it breaks stuff so disallow it
        if (!target.exists()) {
            throw new IOException("Cannot create link to non-existent target")
        }

        // For simplicity in any error reporting, use the canonical path for the link.
        link = link.canonicalFile
        // Directory junction targets must be absolute paths
        target = target.absoluteFile
        FileHelper.ensureMkdirs(link, "for directory junction to '${target}'")
        createMountPoint(link.path, target.canonicalPath)
    }

    @Category(Integer)
    static class JunctionHelperIntegerCategory {
        public int characterLengthAsBytes() {
            this * Character.SIZE / Byte.SIZE
        }

        public short asShortSafely() {
            if (this < Short.MIN_VALUE || this > Short.MAX_VALUE) {
                throw new ArithmeticException("$this is outside the range for a short")
            }
            return (short)this
        }
    }

    @Category(ByteBuffer)
    static class JunctionHelperByteBufferCategory {
        public ByteBuffer putString(String s) {
            int size = s.size()
            for (int i = 0; i < size; ++i) {
                this.putChar(s.charAt(i))
            }
            this.putChar('\0' as char)
            return this
        }
    }

    private static withReparsePointHandle(String path, int desiredAccess, Closure closure) {
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
            throwLastError()
        }

        try {
            closure(reparsePointHandle)
        } finally {
            Kernel32.INSTANCE.CloseHandle(reparsePointHandle)
        }
    }

    private static boolean isMountPoint(String link) {
        withReparsePointHandle(link, Kernel32.GENERIC_READ) { WinNT.HANDLE reparsePointHandle ->
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
                throwLastError()
            }

            ByteBuffer reparseDataByteBuffer = reparseDataBuffer.getByteBuffer(0, SIZEOF_ULONG) // just the tag
            int reparseTag = reparseDataByteBuffer.getInt()
            return (reparseTag == Ntifs.IO_REPARSE_TAG_MOUNT_POINT)
        }
    }

    private static void createMountPoint(String link, String target) {
        withReparsePointHandle(link, Kernel32.GENERIC_READ | Kernel32.GENERIC_WRITE) { WinNT.HANDLE reparsePointHandle ->
            use (JunctionHelperIntegerCategory) {
                // For the SubstituteName, we use the '\\?\' prefix which disables further parsing; see
                // https://msdn.microsoft.com/en-us/library/windows/desktop/aa365247(v=vs.85).aspx
                String printName = target
                String substituteName = "\\??\\" + target + "\\"

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
                    throwLastError()
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
                throwLastError()
            }
        }
    }

    private static void throwLastError() {
        throw new Win32Exception(Kernel32.INSTANCE.GetLastError())
    }

}
