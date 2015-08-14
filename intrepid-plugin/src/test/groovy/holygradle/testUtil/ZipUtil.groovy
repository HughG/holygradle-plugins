package holygradle.testUtil

import holygradle.io.FileHelper
import net.lingala.zip4j.core.ZipFile

/**
 * Utilities for unit tests, related to ZIP files.
 */
public class ZipUtil {
    public static File extractZip(File zipParentDir, String zipName) {
        File zipDir = new File(zipParentDir, zipName)
        FileHelper.ensureDeleteDirRecursive(zipDir)
        ZipFile zipFile = new ZipFile(new File(zipParentDir.parentFile, zipName + ".zip"))
        zipFile.extractAll(zipDir.path)
        zipDir
    }
}
