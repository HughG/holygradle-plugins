package holygradle.testUtil

import org.gradle.api.Project
import org.gradle.process.ExecSpec

/**
 * Test helper related to Mercurial.
 */
class HgUtil {
    public static void hgExec(Project project, Object ... args) {
        project.exec { ExecSpec spec ->
            spec.workingDir = project.projectDir
            spec.executable = "hg.exe"
            spec.args = args.toList()
        }
    }
}
