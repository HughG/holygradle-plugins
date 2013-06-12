package holygradle.custom_gradle

import holygradle.custom_gradle.util.CamelCase
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.Task

class PrerequisitesExtension {
    private final Project project
    private final Map<String, PrerequisitesChecker> checkers = [:]
    private final List<StatedPrerequisite> statedPrerequisites = []
    
    public static PrerequisitesExtension defineExtension(Project project) {
        if (project == project.rootProject) {
            project.extensions.create("prerequisites", PrerequisitesExtension, project)
             
            // Task to check all specified prerequisites.
            Task checkPrerequisitesTask = project.task("checkPrerequisites", type: DefaultTask) {
                group = "Custom Gradle"
                description = "Runs all prerequisite checks."
            }
            checkPrerequisitesTask.doLast {
                // We won't get this far to print this reassuring message, if any dependent tasks failed.
                println "All prerequisites satisfied."
            }

            getPrerequisites(project).register("Java", { PrerequisitesChecker checker, Object... params ->
                String minVersion = params[0] as String
                String javaVersion = checker.readProperty("java.version")
                String[] javaVerComponents = javaVersion.split("\\.")
                String[] minVerComponents = minVersion.split("\\.")
                minVerComponents.eachWithIndex { item, index ->
                    int minVerInt = (int)item
                    int curVerInt = (int)javaVerComponents[index]
                    if (minVerInt > curVerInt) {
                        checker.fail """Java version prerequisite not met.
Minimum version required is '${minVersion}', but found '${javaVersion}'. Please
ensure that you have installed at least version '${minVersion}' of the Java
run-time. (You can go to www.java.com/getjava/ where it might be referred to
as Version ${minVerComponents[1]} Update X; or use a Java SDK of your
choice). After the appropriate version of the Java runtime has been installed
please set the JAVA_HOME environment variable to the location of your new
installation of Java (for example C:\\Program Files\\Java\\jdk1.7.0_07).
Afterwards, please start a new command prompt and re-run the same command."""
                    }
                }
            })

            getPrerequisites(project).register("Windows", { checker, versions ->
                String os = checker.readProperty("os.name")
                if (!versions.contains(os)) {
                    checker.fail "The operating system is '${os}', which is not supported. " +
                            "The supported operating systems are: ${versions}."
                }
            })
        } else {
            project.extensions.add("prerequisites", project.rootProject.extensions.findByName("prerequisites"))
        }
        
        getPrerequisites(project)
    }
    
    public PrerequisitesExtension(Project project) {
        this.project = project
    }

    public static PrerequisitesExtension getPrerequisites(Project project) {
        project.rootProject.extensions.findByName("prerequisites") as PrerequisitesExtension
    }

    // Register a new type of prerequisite checker. The checker closure will be supplied parameters.
    public void register(String prerequisite, Closure checkerClosure) {
        checkers[prerequisite] = new PrerequisitesChecker(project, prerequisite, checkerClosure)
    }
    
    public Task getCheckAllPrerequisitesTask() {
        project.tasks.findByName("checkPrerequisites")
    }
    
    private void addStatedPrerequisite(StatedPrerequisite prerequisite) {
        boolean add = true
        statedPrerequisites.each { 
            if (it.name == prerequisite.name && it.params == prerequisite.params) {
                add = false
            }
        }
        if (add) {
            statedPrerequisites.add(prerequisite)
            getCheckAllPrerequisitesTask().dependsOn prerequisite.getTask()
        }
    }
    
    // Specify the details for a previously registered type of prerequisite. Calling this method does not 
    // immediately perform the prerequisite check, but simply specifies that the prerequisite exists and defines
    // the parameters. Later on check() can be called on PrerequisitesExtension or on the StatedPrerequisite 
    // returned by this method. Another way the prequisite can be checked is by calling checkAll, which is 
    // called by the checkPrerequisites task.
    public StatedPrerequisite specify(String prerequisiteName, Object... params) {
        if (checkers.containsKey(prerequisiteName)) {
            PrerequisitesChecker checker = checkers[prerequisiteName]
            StatedPrerequisite prerequisite = new StatedPrerequisite(checker, params.clone())
            addStatedPrerequisite(prerequisite)
            return prerequisite
        } else {
            throw new RuntimeException("Unknown prerequisite '${prerequisiteName}'.")
        }
    }
    
    // Specifies a new type of prerequisite by supplying 
    public StatedPrerequisite specify(String prerequisiteName, Closure checkerClosure) {
        if (statedPrerequisites.contains(prerequisiteName)) {
            throw new RuntimeException("The prerequisite '${prerequisiteName}' has already been specified.")
        }
        StatedPrerequisite prerequisite = new StatedPrerequisite(
            new PrerequisitesChecker(project, prerequisiteName, checkerClosure)
        )
        addStatedPrerequisite(prerequisite)
        return prerequisite
    }
    
    // Return a task that checks all prerequisites of this type
    public Task getTask(String prerequisiteName) {
        Collection<StatedPrerequisite> prerequisites = statedPrerequisites.findAll { it.name == prerequisiteName }
        if (prerequisites.size() == 0) {
            throw new RuntimeException("Unknown prerequisite '${prerequisiteName}'.")
        } else if (prerequisites.size() == 1) {
            return prerequisites[0].getTask()
        } else {
            Task uberTask = project.task(CamelCase.build("checkPrerequisite", prerequisiteName), type: DefaultTask)
            prerequisites.each { prerequisite ->
                uberTask.dependsOn prerequisite.getTask()
            }
            return uberTask
        }
    }
    
    // Runs the check for previously specified prerequisites of the given type. For any given type of
    // prerequisite there may be multiple StatedPrerequisite instances.
    public void check(String prerequisite) {
        List<StatedPrerequisite> prerequisites = statedPrerequisites.findAll { it.name == prerequisite }
        if (prerequisites.size() == 0) {
            throw new RuntimeException("Unknown prerequisite '${prerequisite}'.")
        } else {
            prerequisites.each { it.check() }
        }
    }
    
    // Check all stated prerequisites, printing out failure messages and counting failures. An exception will
    // be thrown at the end if there were any failures.
    public boolean checkAll() {
        if (statedPrerequisites.count({ !it.check() })) {
            println "All prerequisites satisfied."
        } else {
            throw new RuntimeException("Some prerequisites were not met.")
        }
    }
}
