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
    
    public static void rebuild(File link, File target, Project project, boolean fileLink=false) {
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
        
        // Run 'mklink'
        ExecResult execResult = project.exec { ExecSpec it ->
            if (fileLink) {
                it.commandLine "cmd", "/c", "mklink", '"' + canonicalLink.path + '"', '"' + target.path + '"'
            } else {
                it.commandLine "cmd", "/c", "mklink", "/D", '"' + canonicalLink.path + '"', '"' + target.path + '"'
            }
            it.setStandardOutput new ByteArrayOutputStream()
            it.setErrorOutput new ByteArrayOutputStream()
            it.setIgnoreExitValue true
        }
        if (execResult.getExitValue() != 0) {
            println "Failed to create symlink at location '${canonicalLink}' pointing to '${target}'. " +
                "This could be due to User Account Control, or failing to use an Administrator command prompt."
            execResult.rethrowFailure()
        }
    }
}