package holygradle.scm

import org.gradle.process.ExecResult
import org.gradle.process.ExecSpec

class HgCommandLine implements HgCommand {

    private final String hgPath
    private final Closure<ExecResult> exec
    private final String hgrcPath

    HgCommandLine(
        String hgPath,
        File hgrcPath,
        Closure<ExecResult> exec
    ) {
        this.hgPath = hgPath
        this.exec = exec
        this.hgrcPath = hgrcPath.exists() ? hgrcPath.path : ""
    }

    @Override
    String execute(Collection<String> args) {
        OutputStream stdout = new ByteArrayOutputStream()
        OutputStream stderr = new ByteArrayOutputStream()
        String localHgrcPath = hgrcPath
        String localHgPath = hgPath
        ExecResult execResult = exec { ExecSpec spec ->
            if (hgrcPath.length() > 0) {
                spec.environment.put("HGRCPATH", localHgrcPath)
            }
            spec.setStandardOutput stdout
            spec.setErrorOutput stderr
            spec.setIgnoreExitValue true
            spec.executable localHgPath
            spec.args args
        }

        if (execResult.getExitValue() != 0) {
            throw new RuntimeException(stderr.toString().trim())
        }
        stdout.toString().trim();
    }
}
