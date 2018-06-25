package holygradle.testUtil

import org.gradle.api.Project
import org.gradle.process.ExecSpec

/**
 * Test helper related to Mercurial.
 */
class ScmUtil {
    public static void exec(Project project, String executable, String ... args) {
        println "exec for ${project} in ${project.projectDir}: ${executable} ${args}"
        project.exec { ExecSpec spec ->
            spec.workingDir = project.projectDir
            spec.executable = executable
            spec.args = args.toList()
        }
    }

    public static void exec(File workingDir, String executable, String ... args) {
        println "exec in ${workingDir}: ${executable} ${args}"
        int exitCode = new ProcessBuilder([executable] + args.toList())
            .directory(workingDir)
            .start()
            .waitFor()
        if (exitCode != 0) {
            throw new RuntimeException("Process '${executable}' exited with code '${exitCode}'")
        }
    }

    public static void svnExec(Project project, String ... args) {
        exec(project, "svn", args)
    }

    public static void svnExec(File workingDir, String ... args) {
        exec(workingDir, "svn", args)
    }

    public static void hgExec(Project project, String ... args) {
        exec(project, "hg", args)
    }

    public static void hgExec(File workingDir, String ... args) {
        exec(workingDir, "hg", args)
    }

    public static void gitExec(Project project, String ... args) {
        exec(project, "git", args)
    }

    public static void gitExec(File workingDir, String ... args) {
        exec(workingDir, "git", args)
    }
}
