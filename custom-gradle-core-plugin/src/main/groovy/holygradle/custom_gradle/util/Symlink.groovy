package holygradle.custom_gradle.util

import org.gradle.api.Project
import org.gradle.process.ExecResult
import org.gradle.process.ExecSpec

import java.nio.file.Files
import java.nio.file.Paths

public class Symlink {
    public static boolean isJunctionOrSymlink(File file) throws IOException {
        Files.isSymbolicLink(Paths.get(file.path))
    }

    public static void delete(File link) {
        if (isJunctionOrSymlink(link)) {
            link.delete()
        }
    }
    
    public static void rebuild(File link, File target) {
        File canonicalLink = link.getCanonicalFile()
        
        // Delete the symlink if it exists
        if (isJunctionOrSymlink(canonicalLink)) {
            canonicalLink.delete()
        }
        
        // Make sure the parent directory exists
        final File linkParentDir = canonicalLink.parentFile
        if (linkParentDir != null) {
            if (!linkParentDir.exists()) {
                linkParentDir.mkdirs()
            }
        }

        if (!target.exists()) {
            throw new RuntimeException("Cannot create link to non-existent target; from '${canonicalLink}' to '${target}'")
        }
        Files.createSymbolicLink(canonicalLink.toPath(), target.toPath())
    }
}