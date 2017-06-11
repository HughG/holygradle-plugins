package holygradle.custom_gradle

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.process.ExecResult
import org.gradle.process.ExecSpec

class PrerequisitesChecker<T> {
    public final String name
    public final Project project
    private final Action<PrerequisitesChecker<T>> checkAction
    public final T parameter
    private boolean ok = true

    PrerequisitesChecker(Project project, String name, Action<PrerequisitesChecker<T>> checkAction, T parameter) {
        this.project = project
        this.name = name
        this.checkAction = checkAction
        this.parameter = parameter
    }

    private boolean getCheckingAllPrerequisites() {
        return PrerequisitesExtension.getPrerequisites(project).checkingAllPrerequisites
    }

    public boolean run() {
        // The 'ok' variable is not really stateful. Its purpose is to determine the return value for this method.
        // It will be set to false if the fail method is called.
        ok = true
        if (parameter == null) {
            println "Checking '${name}'..."
        } else {
            println "Checking '${name}' (${parameter})..."
        }
        checkAction.execute(this)
        ok
    }
    
    public String readRegistry(String location, String key) {
        OutputStream regOutput = new ByteArrayOutputStream()
        ExecResult execResult = project.exec { ExecSpec it ->
            it.commandLine "cmd", "/c", "reg", "query", "\"${location}\"", "/v", key
            it.setStandardOutput regOutput
            it.setErrorOutput new ByteArrayOutputStream()
            it.setIgnoreExitValue true
        }
        if (execResult.getExitValue() == 0) {
            String line = regOutput.toString().readLines().getAt(2)
            return line.split().last()
        }
        return null
    }
    
    public String readEnvironment(String variable) {
        System.getenv(variable)
    }
    
    public String readProperty(String property) {
        System.getProperty(property)
    }
    
    public String readFile(String path) {
        File f = new File(path)
        if (f.exists() && f.isFile()) {
            return f.text
        } else {
            return null
        }
    }
    
    public String readFileVersion(String path) {
        OutputStream powershellOutput = new ByteArrayOutputStream()
        ExecResult execResult = project.exec { ExecSpec it ->
            it.commandLine "powershell", "-Command", "(Get-Item '${path}').VersionInfo.FileVersion"
            it.setStandardOutput powershellOutput
            it.setErrorOutput new ByteArrayOutputStream()
            it.setIgnoreExitValue true
        }
        if (execResult.getExitValue() == 0) {
            return powershellOutput.toString().trim()
        }
        return null
    }
    
    public void assertOnPath(String shouldBeOnPath, String failureMessage = null) {
        ExecResult execResult = project.exec { ExecSpec it ->
            it.commandLine "cmd", "/c", "where", shouldBeOnPath
            it.setStandardOutput new ByteArrayOutputStream()
            it.setErrorOutput new ByteArrayOutputStream()
            it.setIgnoreExitValue true
        }
        if (execResult.getExitValue() != 0) {
            fail "Failed to find '${shouldBeOnPath}' on the path. ", failureMessage
        }
    }
    
    public void assertEnvironmentVariableExists(String envVar, String failureMessage = null) {
        if (readEnvironment(envVar) == null) {
            fail "The environment variable '${envVar}' has not been set. ", failureMessage
        }
    }
     
    public void assertEnvironmentVariableRefersToDirectory(String envVar, String failureMessage = null) {
        String env = readEnvironment(envVar)
        if (env == null) {
            fail "The environment variable '${envVar}' has not been set. ", failureMessage
        } else {
            File dir = new File(env)
            if (dir.exists()) {
                if (!dir.isDirectory()) {
                    fail "The environment variable '${envVar}' is set, contains '{env}' but that is not a directory. ", failureMessage
                }
            } else {
                fail "The environment variable '${envVar}' is set, contains '{env}' but that path does not exist. ", failureMessage
            }
        }
    }
    
    public void fail(String... textItems) {
        String text = null
        textItems.each {
            if (it != null) {
                if (text == null) {
                    text = it
                } else {
                    text += it
                }
            }
        }
        if (text == null || text == "") {
            text = "Prerequisite check '${name}' failed."
        }
        wrapMessage(text)
        ok = false
        if (!checkingAllPrerequisites) {
            throw new RuntimeException("Prerequisite check '${name}' failed.")
        }
    }
    
    public static void wrapMessage(String text, int columns = 80) {
        println "-"*columns
        text.eachLine { lnText ->
            String line = ""
            lnText.split(" ").each { word ->
                if (word.length() > columns) {
                    String part = word.substring(0, columns - line.length())
                    line += part
                    word = word.substring(part.length())
                }
                if (line.length() + word.length() > columns) {
                    println line.trim()
                    line = ""
                }
                while (word.length() > columns) {
                    println word.substring(0, columns)
                    word = word.substring(columns)
                }
                line += word + " "       
            }
            println line.trim()
        }
        println "-"*columns
    }
}