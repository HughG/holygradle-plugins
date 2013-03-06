package holygradle.customgradleimport org.gradle.*import org.gradle.api.*class PrerequisitesChecker {    public final String name    public final Project project    private final Closure checkClosure    private boolean ok = true    public boolean checkingAllPrerequisites = false        PrerequisitesChecker(Project project, String name, Closure checkClosure) {        this.project = project        this.name = name        this.checkClosure = checkClosure                project.gradle.taskGraph.whenReady {            checkingAllPrerequisites = project.gradle.taskGraph.hasTask(project.prerequisites.getCheckAllPrerequisitesTask())        }    }    public boolean run(def params) {        // The 'ok' variable is not really stateful. Its purpose is to determine the return value for this method.        ok = true        if (checkingAllPrerequisites) {            if (params == null) {                println "Checking '${name}'..."            } else {                println "Checking '${name}' (${params.join(', ')})..."            }        }        if (params == null) {            checkClosure(this)        } else {            checkClosure(this, params)        }        ok    }        public String readRegistry(String location, String key) {        def regOutput = new ByteArrayOutputStream()        def execResult = project.exec {            commandLine "cmd", "/c", "reg", "query", "\"${location}\"", "/v", key            setStandardOutput regOutput            setErrorOutput new ByteArrayOutputStream()            setIgnoreExitValue true        }        if (execResult.getExitValue() == 0) {            String line = regOutput.toString().readLines()[2]            return line.split()[-1]        }        return null    }        public String readEnvironment(String variable) {        System.getenv(variable)    }        public String readProperty(String property) {        System.getProperty(property)    }        public String readFile(String path) {        def f = new File(path)        if (f.exists() && f.isFile()) {            return f.text        } else {            return null        }    }        public String readFileVersion(String path) {        def powershellOutput = new ByteArrayOutputStream()        def execResult = project.exec {            commandLine "powershell", "-Command", "(Get-Item '${path}').VersionInfo.FileVersion"            setStandardOutput powershellOutput            setErrorOutput new ByteArrayOutputStream()            setIgnoreExitValue true        }        if (execResult.getExitValue() == 0) {            return powershellOutput.toString().trim()        }        return null    }        public void assertOnPath(String shouldBeOnPath, String failureMessage = null) {        def execResult = project.exec {            commandLine "cmd", "/c", "where", shouldBeOnPath            setStandardOutput new ByteArrayOutputStream()            setErrorOutput new ByteArrayOutputStream()            setIgnoreExitValue true        }        if (execResult.getExitValue() != 0) {            fail "Failed to find '${shouldBeOnPath}' on the path. ", failureMessage        }    }        public void assertEnvironmentVariableExists(String envVar, String failureMessage = null) {        if (readEnvironment(envVar) == null) {            fail "The environment variable '${envVar}' has not been set. ", failureMessage        }    }         public void assertEnvironmentVariableRefersToDirectory(String envVar, String failureMessage = null) {        def env = readEnvironment(envVar)        if (env == null) {            fail "The environment variable '${envVar}' has not been set. ", failureMessage        } else {            def dir = new File(env)            if (dir.exists()) {                if (!dir.isDirectory()) {                    fail "The environment variable '${envVar}' is set, contains '{env}' but that is not a directory. ", failureMessage                }            } else {                fail "The environment variable '${envVar}' is set, contains '{env}' but that path does not exist. ", failureMessage            }        }    }        public void fail(String... textItems) {        String text = null        textItems.each {            if (it != null) {                if (text == null) {                    text = it                } else {                    text += it                }            }        }        if (text == null || text == "") {            text = "Prerequisite check '${name}' failed."        }        wrapMessage(text)        ok = false        if (!checkingAllPrerequisites) {            throw new RuntimeException("Prerequisite check '${name}' failed.")        }    }        public void wrapMessage(String text, int columns = 80) {        println "-"*columns        text.eachLine { lnText ->            def line = ""            lnText.split(" ").each { word ->                if (word.length() > columns) {                    def part = word.substring(0, columns - line.length())                    line += part                    word = word.substring(part.length())                }                if (line.length() + word.length() > columns) {                    println line.trim()                    line = ""                }                while (word.length() > columns) {                    println word.substring(0, columns)                    word = word.substring(columns)                }                line += word + " "                   }            println line.trim()        }        println "-"*columns    }}