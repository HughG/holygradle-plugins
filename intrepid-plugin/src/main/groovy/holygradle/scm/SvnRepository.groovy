package holygradle.scm

import org.gradle.process.ExecSpec

import java.util.regex.Matcher

class SvnRepository implements SourceControlRepository {
    public static SourceControlType TYPE = new Type()

    private final File workingCopyDir
    private final Command svnCommand

    public SvnRepository(Command hgCommand, File localPath) {
        this.svnCommand = hgCommand
        workingCopyDir = localPath
    }

    public File getLocalDir() {
        workingCopyDir
    }
    
    public String getProtocol() {
        "svn"
    }
    
    public String getUrl() {
        String url = "unknown"
        String info = svnCommand.execute { ExecSpec spec ->
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
        String info = svnCommand.execute { ExecSpec spec ->
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
        String changes = svnCommand.execute { ExecSpec spec ->
            spec.workingDir = workingCopyDir
            spec.args "status", "--quiet", "--ignore-externals"
        }
        changes.trim().length() > 0
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