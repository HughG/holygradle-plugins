package holygradle.io

import com.sun.jna.Memory
import com.sun.jna.ptr.IntByReference
import holygradle.jna.platform.win32.Kernel32
import holygradle.jna.platform.win32.Ntifs
import holygradle.jna.platform.win32.Win32Exception
import holygradle.jna.platform.win32.WinNT
import java.io.File
import java.io.IOException

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
object Junction {
    private const val SIZEOF_ULONG: Byte = 4 // bytes, because ULONG is always 32-bit
    private const val SIZEOF_USHORT: Byte = 2 // bytes
    private const val REPARSE_DATA_HEADER_LENGTH: Byte = (
            SIZEOF_ULONG + // ReparseTag
            SIZEOF_USHORT + // ReparseDataLength
            SIZEOF_USHORT // Reserved
            ).toByte()
    private const val MOUNT_POINT_REPARSE_BUFFER_HEADER_LENGTH: Byte = (
            SIZEOF_USHORT + // SubstituteNameOffset
            SIZEOF_USHORT + // SubstituteNameLength
            SIZEOF_USHORT + // PrintNameOffset
            SIZEOF_USHORT // PrintNameLength
            ).toByte()

    private const val NOT_A_REPARSE_POINT: Int = 0x00001126
    private const val IGNORE_MAX_PATH_PREFIX = "\\\\?\\"
    private const val DO_NOT_PARSE_FILENAME_PREFIX = "\\??\\"

    /**
     * Returns true if the file is a directory junction.  This does not guarantee that it has a valid target; for
     * example, a non-existent file or a network share would not be a valid target.
     * @param file The file to check
     * @return true if the file is a directory junction; otherwise false
     */
    @JvmStatic
    fun isJunction(file: File): Boolean {
        // For simplicity in any error reporting, use canonical path for the link.
        val canonicalFile = file.canonicalFile

        return try {
            isMountPoint(canonicalFile)
        } catch (_: Win32Exception) {
            false
        }
    }

    /**
     * Deletes the {@code link} if it exists and is a directory junction, then also deletes the underlying directory if
     * it is empty.  Does nothing if the {@code link} does not exist or is not a directory junction.
     * @param link The directory junction to delete
     */
    @JvmStatic
    fun delete(link: File) {
        // For simplicity in any error reporting, use canonical path for the link.
        val canonicalLink = link.canonicalFile

        fun ensureDeleteJunction() {
            try {
                removeMountPoint(canonicalLink.path)
            } finally {
                // Delete the mount point if the directory is empty.  We do this even if the mount point removal throws
                // an exception, in case it sort-of worked.
                if (FileHelper.isEmptyDirectory(canonicalLink)) {
                    canonicalLink.delete()
                }
            }
        }

        when {
            FileHelper.isEmptyDirectory(canonicalLink) ->
                // Delete the existing "link" if it's actually an empty directory, because it may be a left-over from a
                // previous failed attempt to delete a directory junction.  This has been observed occasionally and we
                // suspect it's due to some background process temporarily having a lock on the folder -- for example, a
                // virus scanner, or the Windows Indexing Service.
                canonicalLink.delete()
            isJunction(canonicalLink) -> ensureDeleteJunction()
            canonicalLink.exists() -> throw RuntimeException(
                "Cannot not delete or create a directory junction at '${canonicalLink.path}' " +
                "because a folder or file already exists there and is not a directory junction or an empty folder."
            )
        }
    }

    /**
     * Deletes the {@code link} if it exists and is a directory junction, then creates a directory junction to the
     * {@code target}, including any parent folders for the {@code link}.  Fails if the target does not exist or is not
     * a valid target for a directory junction (for example, if it is a network share path).
     * @param link The directory junction to (re-)create
     * @param target The target of the new directory junction
     */
    @JvmStatic
    fun rebuild(link: File, target: File) {
        // For simplicity in any error reporting, use canonical paths for the link and target.
        // Also, directory junction targets must be absolute paths.
        val canonicalLink = link.canonicalFile

        delete(canonicalLink)

        val canonicalTarget = getCanonicalTarget(canonicalLink, target)
        // Directory junctions can be created to non-existent targets but it breaks stuff so disallow it
        if (!canonicalTarget.exists()) {
            throw IOException("Cannot create link to non-existent target: from '${canonicalLink}' to '${canonicalTarget}'")
        }

        FileHelper.ensureMkdirs(canonicalLink, "for directory junction to '${canonicalTarget}'")

        try {
            createMountPoint(canonicalLink.path, canonicalTarget.path)
        } catch (e: Win32Exception) {
            // If we failed to create the mount point, we should delete the folder we just created, so that we can fall
            // back to symlink creation.  If we leave the folder, we'll fail to create a symlink from that location.
            delete(canonicalLink)
            throw e
        }

        // Even if createMountPoint doesn't throw, it will blindly create links to stuff that won't work, like network
        // shares or non-existent targets. The only way I can see to detect this is to check if the resulting file
        // exists. This will return false if the target is invalid even if the link has been successfully created.
        if (!canonicalLink.exists()) {
            // Remove the link before continuing.
            delete(canonicalLink)
            // This will be immediately caught by the outer "catch" block.  Junction.rebuild might also throw for
            // other reasons, which is why we don't just factor this out to another method returning "false" or
            // something.
            throw IOException("Failed to create directory junction from '${canonicalLink}' to '${canonicalTarget}'.")
        }
    }

    private fun getCanonicalTarget(canonicalLink: File, target: File): File {
        // If [target] is relative, we want to create a link relative to [link] (as opposed to relative to the current
        // working directory) so we have to calculate this.
        val absoluteTarget = if (target.isAbsolute) target else File(canonicalLink.parentFile, target.path)
        // We also have to return the canonical filename to make sure that there are no relative path parts ('..') in
        // the path, because the low-level junction-creation code prevents Windows from resolving this itself.
        return absoluteTarget.canonicalFile
    }

    private fun Int.characterLengthAsBytes(): Int = this * Character.SIZE / Byte.SIZE
    private fun Int.bytesAsCharacterLength(): Int = this / (Character.SIZE / Byte.SIZE)
    private fun Int.asShortSafely(): Short {
        if (this < Short.MIN_VALUE || this > Short.MAX_VALUE) {
            throw ArithmeticException("$this is outside the range for a short")
        }
        return this.toShort()
    }

    @Suppress("unused") // unused receiver
    private inline val Byte.Companion.SIZE: Int get() = java.lang.Byte.SIZE

    private fun ByteBuffer.putString(s: String): ByteBuffer = also {
        val size = s.length
        for (i in 0..(size - 1)) {
            this.putChar(s[i])
        }
        this.putChar('\u0000')
    }

    private inline fun <T> withReparsePointHandle(path: String, desiredAccess: Int, closure: (WinNT.HANDLE) -> T): T {
        val reparsePointHandle = Kernel32.INSTANCE.CreateFile(
            path,
            desiredAccess,
            0,
            null,
            Kernel32.OPEN_EXISTING,
            Kernel32.FILE_FLAG_BACKUP_SEMANTICS or Kernel32.FILE_FLAG_OPEN_REPARSE_POINT,
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
    @JvmStatic
    fun getTarget(link: File): File {
        // For simplicity in any error reporting, use canonical path for the link.
        val canonicalLink = link.canonicalFile

        withReparsePointHandle(canonicalLink.toString(), Kernel32.GENERIC_READ) { reparsePointHandle ->
            val reparseDataBuffer = Memory(Ntifs.MAXIMUM_REPARSE_DATA_BUFFER_SIZE.toLong())

            val succeeded = Kernel32.INSTANCE.DeviceIoControl(
                reparsePointHandle,
                Ntifs.FSCTL_GET_REPARSE_POINT,
                null,
                0,
                reparseDataBuffer,
                Ntifs.MAXIMUM_REPARSE_DATA_BUFFER_SIZE,
                IntByReference(),
                null
            )

            if (!succeeded) {
                throw getLastErrorException()
            }

            val reparseDataByteBuffer = reparseDataBuffer.getByteBuffer(0, Ntifs.MAXIMUM_REPARSE_DATA_BUFFER_SIZE.toLong()) // just the tag
            val reparseTag = reparseDataByteBuffer.getInt()

            if (reparseTag == Ntifs.IO_REPARSE_TAG_MOUNT_POINT) {
                reparseDataByteBuffer.getShort() // Skip the data length
                reparseDataByteBuffer.getShort() // Skip the reserved data

                val nameOffset = reparseDataByteBuffer.getShort().toInt()
                val nameLength = reparseDataByteBuffer.getShort().toInt()

                reparseDataByteBuffer.getShort() // Skip the print name offset
                reparseDataByteBuffer.getShort() // Skip the print name length

                // Move to the start of name
                for (i in 0..(nameOffset - 1)) {
                    reparseDataByteBuffer.get()
                }

                val result = StringBuilder()

                // Read the bytes
                val bytesAsCharacterLength = nameLength.bytesAsCharacterLength()
                for (i in 0..(bytesAsCharacterLength - 1)) {
                    result.append(reparseDataByteBuffer.getChar())
                }

                // Strip the starting "no parse" prefix if it exists.
                val resultString = if (result.startsWith(DO_NOT_PARSE_FILENAME_PREFIX)) {
                    result.substring(DO_NOT_PARSE_FILENAME_PREFIX.length)
                } else {
                    result.toString()
                }
                return File(resultString)
            } else {
                throw RuntimeException("File ${canonicalLink} is not a directory junction")
            }
        }
    }

    private fun isMountPoint(link: File): Boolean {
        // Here we use the '\\?\' prefix which sidesteps the MAX_PATH (260) limit and passes the string directly to the
        // filesystem which, in the case of NTFS, has a limit of 32767 characters; see
        // <https://msdn.microsoft.com/en-us/library/windows/desktop/aa365247(v=vs.85).aspx>.  This allows us to delete
        // links even if they've been created at a location beyond the MAX_PATH limit.  This can be necessary for the
        // Holy Gradle integration tests, depending on the checkout location, and may be useful in projects which use it.
        val path = IGNORE_MAX_PATH_PREFIX + link.canonicalPath
        withReparsePointHandle(path, Kernel32.FILE_READ_ATTRIBUTES) { reparsePointHandle ->
            val reparseDataBuffer = Memory(Ntifs.MAXIMUM_REPARSE_DATA_BUFFER_SIZE.toLong())

            val succeeded = Kernel32.INSTANCE.DeviceIoControl(
                reparsePointHandle,
                Ntifs.FSCTL_GET_REPARSE_POINT,
                null,
                0,
                reparseDataBuffer,
                Ntifs.MAXIMUM_REPARSE_DATA_BUFFER_SIZE,
                IntByReference(),
                null
            )

            if (succeeded) {
                val reparseDataByteBuffer = reparseDataBuffer.getByteBuffer(0, SIZEOF_ULONG.toLong()) // just the tag
                val reparseTag = reparseDataByteBuffer.getInt()
                return (reparseTag == Ntifs.IO_REPARSE_TAG_MOUNT_POINT)
            }

            if (Kernel32.INSTANCE.GetLastError() == NOT_A_REPARSE_POINT) {
                return false
            }

            throw getLastErrorException()
        }
    }

    private fun createMountPoint(link: String, target: String) {
        withReparsePointHandle(link, Kernel32.GENERIC_READ or Kernel32.GENERIC_WRITE) { reparsePointHandle ->
            // For the SubstituteName, we use the '\??\' prefix which disables further parsing; see
            // <http://www.flexhex.com/docs/articles/hard-links.phtml>.  Note that this is NOT the same as the
            // '\\?\' prefix, which just sidesteps the MAX_PATH (260) limit and passes the string directly to the
            // filesystem which, in the case of NTFS, has a limit of 32767 characters; see
            // <https://msdn.microsoft.com/en-us/library/windows/desktop/aa365247(v=vs.85).aspx>.
            val printName = target
            val substituteName = DO_NOT_PARSE_FILENAME_PREFIX + target + '\\'

            val pathBufferLength = (substituteName.length + 1 + printName.length + 1).characterLengthAsBytes().asShortSafely()
            val mountPointReparseBufferLength = (MOUNT_POINT_REPARSE_BUFFER_HEADER_LENGTH + pathBufferLength).asShortSafely()
            val reparseDataBufferLength = REPARSE_DATA_HEADER_LENGTH + mountPointReparseBufferLength
            val substituteNameOffset = 0.toShort()
            val substituteNameLength = (substituteName.length.characterLengthAsBytes()).asShortSafely()
            val printNameOffset = (substituteNameLength + 1.characterLengthAsBytes()).asShortSafely()
            val printNameLength = (printName.length.characterLengthAsBytes()).asShortSafely()
            val reparseDataBuffer = Memory(reparseDataBufferLength.toLong())
            val reparseDataByteBuffer = reparseDataBuffer.getByteBuffer(0, reparseDataBufferLength.toLong())

            // First the REPARSE_DATA_BUFFER ...
            reparseDataByteBuffer.putInt(Ntifs.IO_REPARSE_TAG_MOUNT_POINT)
                .putShort(mountPointReparseBufferLength)
                .putShort(0) // Reserved
                // ... then, within that, the MountPointReparseBuffer ...
                .putShort(substituteNameOffset)
                .putShort(substituteNameLength)
                .putShort(printNameOffset)
                .putShort(printNameLength)
                // ... then, within that, the PathBuffer ...
                .putString(substituteName)
                .putString(printName)

            val succeeded = Kernel32.INSTANCE.DeviceIoControl(
                reparsePointHandle,
                Ntifs.FSCTL_SET_REPARSE_POINT,
                reparseDataBuffer,
                reparseDataBufferLength,
                null,
                0,
                IntByReference(),
                null
            )
            if (!succeeded) {
                throw getLastErrorException()
            }
        }
    }

    private fun removeMountPoint(link: String) {
        withReparsePointHandle(link, Kernel32.GENERIC_READ or Kernel32.GENERIC_WRITE) { reparsePointHandle ->
            val reparseDataBuffer = Memory(REPARSE_DATA_HEADER_LENGTH.toLong())
            val reparseDataByteBuffer = reparseDataBuffer.getByteBuffer(0, REPARSE_DATA_HEADER_LENGTH.toLong())

            // First the REPARSE_DATA_BUFFER ...
            reparseDataByteBuffer.putInt(Ntifs.IO_REPARSE_TAG_MOUNT_POINT)
                .putShort(0) // ReparseDataLength; 0 because we don't need extra data to delete
                .putShort(0) // Reserved

            val succeeded = Kernel32.INSTANCE.DeviceIoControl(
                reparsePointHandle,
                Ntifs.FSCTL_DELETE_REPARSE_POINT,
                reparseDataBuffer,
                REPARSE_DATA_HEADER_LENGTH.toInt(),
                null,
                0,
                IntByReference(),
                null
            )
            if (!succeeded) {
                throw getLastErrorException()
            }
        }
    }

    private fun getLastErrorException(): Win32Exception {
        return Win32Exception(Kernel32.INSTANCE.GetLastError())
    }
}
