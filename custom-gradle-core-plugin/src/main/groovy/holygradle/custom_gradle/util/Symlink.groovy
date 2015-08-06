package holygradle.custom_gradle.util

import holygradle.io.FileHelper

import java.nio.file.Files
import java.nio.file.Paths

public class Symlink {
    public static boolean isJunctionOrSymlink(File file) throws IOException {
        Files.isSymbolicLink(Paths.get(file.path))
    }

    public static void delete(File link) {
        if (isJunctionOrSymlink(link)) {
            FileHelper.ensureDeleteFile(link)
        }
    }
    
    public static void rebuild(File link, File target) {
        File canonicalLink = link.getCanonicalFile()
        
        // Delete the symlink if it exists
        if (isJunctionOrSymlink(canonicalLink)) {
            FileHelper.ensureDeleteFile(canonicalLink)
        }
        
        // Make sure the parent directory exists
        final File linkParentDir = canonicalLink.parentFile
        if (linkParentDir != null) {
            FileHelper.ensureMkdirs(linkParentDir, "as parent for symlnk ${canonicalLink.name}")
        }

        if (!target.exists()) {
            throw new IOException("Cannot create link to non-existent target; from '${canonicalLink}' to '${target}'")
        }
        Files.createSymbolicLink(canonicalLink.toPath(), target.toPath())
    }
}