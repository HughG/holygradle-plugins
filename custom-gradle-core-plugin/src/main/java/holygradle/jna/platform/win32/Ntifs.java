package holygradle.jna.platform.win32;

import com.sun.jna.Native;
import com.sun.jna.win32.W32APIOptions;

/**
 * JNA interface for necessary parts of Ntifs.h.
 */
public interface Ntifs {
    int FSCTL_SET_REPARSE_POINT = 0x000900A4;
    int FSCTL_GET_REPARSE_POINT = 0x000900A8;
    int FSCTL_DELETE_REPARSE_POINT = 0x000900AC;

    // Should be in JNA's WinNT class, but isn't, at JNA 3.4.0
    int IO_REPARSE_TAG_MOUNT_POINT = 0xA0000003;

    // Should probably be in some JNA class, but I couldn't find it.
    int MAXIMUM_REPARSE_DATA_BUFFER_SIZE = 16 * 1024;
}
