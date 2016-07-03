package holygradle.testUtil

import org.gradle.api.Project
import org.gradle.process.ExecSpec

/**
 * Test helper related to Git.
 */
class GitUtil {
    public static void gitExec(Project project, Object ... args) {
        project.exec { ExecSpec spec ->
            spec.workingDir = project.projectDir
            spec.executable = "git.exe"
            spec.args = args.toList()
        }
    }
}
