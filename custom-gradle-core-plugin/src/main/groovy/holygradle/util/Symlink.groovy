package holygradle.util

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
    
    public static void rebuild(File link, File target, def project, boolean fileLink=false) {
        File canonicalLink = link.getCanonicalFile()
        
        // Delete the symlink if it exists
        if (isJunctionOrSymlink(canonicalLink)) {
            canonicalLink.delete()
        }
        
        // Make sure the parent directory exists
        File shouldExist = canonicalLink.parentFile
        if (canonicalLink.parentFile != null) {
            if (!canonicalLink.parentFile.exists()) {
                canonicalLink.parentFile.mkdirs()
            }
        }
        
        // Run 'mklink'
        def execResult = project.exec {
            if (fileLink) {
                commandLine "cmd", "/c", "mklink", '"' + canonicalLink.path + '"', '"' + target.path + '"' 
            } else {
                commandLine "cmd", "/c", "mklink", "/D", '"' + canonicalLink.path + '"', '"' + target.path + '"' 
            }
            setStandardOutput new ByteArrayOutputStream()
            setErrorOutput new ByteArrayOutputStream()
            setIgnoreExitValue true
        }
        if (execResult.getExitValue() != 0) {
            println "Failed to create symlink at location '${canonicalLink}' pointing to '${target}'. This could be due to User Account Control, or failing to use an Administrator command prompt."
            execResult.rethrowFailure()
        }
    }
}