package holygradle.scm

import org.gradle.process.ExecSpec

import java.util.regex.Matcher

class SvnRepository extends SourceControlRepositoryBase {
    public static SourceControlType TYPE = new Type()

    public SvnRepository(Command scmCommand, File workingCopyDir) {
        super(scmCommand, workingCopyDir)
    }
    
    public String getProtocol() {
        "svn"
    }
    
    public String getUrl() {
        String url = "unknown"
        String info = scmCommand.execute { ExecSpec spec ->
            spec.workingDir = workingCopyDir
            spec.args "info"
        }
        Matcher match = info =~ /URL: (\S+)/
        if (match.size() != 0) {
            final List<String> matches = match[0] as List<String>
            url = matches[1]
        }
        url
    }
    
    public String getRevision() {
        String revision = "unknown"
        String info = scmCommand.execute { ExecSpec spec ->
            spec.workingDir = workingCopyDir
            spec.args "info"
        }
        Matcher match = info =~ /Revision: (\d+)/
        if (match.size() != 0) {
            final List<String> matches = match[0] as List<String>
            revision = matches[1]
        }
        revision
    }
    
    public boolean hasLocalChanges() {
        String changes = scmCommand.execute { ExecSpec spec ->
            spec.workingDir = workingCopyDir
            spec.args "status", "--quiet", "--ignore-externals"
        }
        changes.trim().length() > 0
    }

    protected boolean ignoresFileInternal(final File file) {
        // If the file is explicitly ignored, "svn status" will output a line starting with "I".  However, if it's
        // somewhere under an ignored folder, then "svn status" for any file under that folder will output nothing on
        // standard output (and a warning on standard error: "svn: warning: W155010: The node 'filename' was not found"),
        // in which case we keep looking upward until we hit the working copy root (which we don't check, because a repo
        // which ignores its root folder seems like madness!).
        File maybeIgnoredFile = file.absoluteFile
        while (maybeIgnoredFile != workingCopyDir) {
            String status = scmCommand.execute { ExecSpec spec ->
                spec.workingDir = workingCopyDir
                spec.args "status", maybeIgnoredFile.toString()
            }
            if (status == "") {
                maybeIgnoredFile = maybeIgnoredFile.parentFile
            } else {
                return status.startsWith("I")
            }
        }
    }

    private static class Type implements SourceControlType {
        @Override
        String getStateDirName() {
            return ".svn"
        }

        @Override
        String getExecutableName() {
            return "svn"
        }

        @Override
        Class<SourceControlRepository> getRepositoryClass() {
            return SvnRepository.class
        }
    }

}