package holygradle.custom_gradle

import groovy.transform.PackageScope
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.Task

class PrerequisitesExtension {
    private final Project project
    private final Map<String, PrerequisitesCheckerFactory> checkers = [:]
    private final List<StatedPrerequisite> statedPrerequisites = []
    @PackageScope boolean checkingAllPrerequisites = false
    
    public static PrerequisitesExtension defineExtension(Project project) {
        if (project == project.rootProject) {
            project.extensions.create("prerequisites", PrerequisitesExtension, project)

            // Task to check all specified prerequisites.
            Task checkPrerequisitesTask = project.task("checkPrerequisites", type: DefaultTask) {
                it.group = "Custom Gradle"
                it.description = "Runs all prerequisite checks."
            }
            checkPrerequisitesTask.doLast {
                checkAll()
                println "All prerequisites satisfied."
            }

            getPrerequisites(project).register("Java") { PrerequisitesChecker<String> checker ->
                String minVersion = checker.parameter
                String javaVersion = checker.readProperty("java.version")
                String[] javaVerComponents = javaVersion.split("\\.")
                String[] minVerComponents = minVersion.split("\\.")
                minVerComponents.eachWithIndex { item, index ->
                    int minVerInt = item.toInteger()
                    int curVerInt = javaVerComponents[index].toInteger()
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
            }

            getPrerequisites(project).register("Windows", { PrerequisitesChecker<List<String>> checker ->
                def versions = checker.parameter
                String os = checker.readProperty("os.name")
                if (!versions.contains(os)) {
                    checker.fail "The operating system is '${os}', which is not supported. " +
                            "The supported operating systems are: ${versions}."
                }
            })
        } else {
            // Make sure that the root project has the core plugin applied, and then grab a reference to the single
            // instance of PrerequisitesExtension.  (Note calling "apply" twice with the same plugin will only apply it
            // once, so it's okay if the user has already applied the core plugin, or one which depends on it, at the
            // root.  This is to cover the case where they haven't.)
            project.rootProject.apply plugin: CustomGradleCorePlugin
            project.extensions.add("prerequisites", project.rootProject.extensions.getByName("prerequisites"))
        }

        project.gradle.taskGraph.whenReady {
            final PrerequisitesExtension prerequisites = PrerequisitesExtension.getPrerequisites(project)
            checkingAllPrerequisites = project.gradle.taskGraph.hasTask(prerequisites.getCheckAllPrerequisitesTask())
        }

        getPrerequisites(project)
    }
    
    public PrerequisitesExtension(Project project) {
        this.project = project
    }

    public static PrerequisitesExtension getPrerequisites(Project project) {
        project.rootProject.extensions.findByName("prerequisites") as PrerequisitesExtension
    }

    // Register a new type of prerequisite checker. The checker closure will be supplied a parameter, which may be a
    // collection.
    public <T> void register(String prerequisite, Closure checkerClosure) {
        checkers[prerequisite] = new PrerequisitesCheckerFactory(project, prerequisite, new ClosureAction(checkerClosure))
    }

    public <T> void register(String prerequisite, Action<PrerequisitesChecker<T>> checkerAction) {
        checkers[prerequisite] = new PrerequisitesCheckerFactory(project, prerequisite, checkerAction)
    }

    public Task getCheckAllPrerequisitesTask() {
        project.tasks.findByName("checkPrerequisites")
    }
    
    private void addStatedPrerequisite(StatedPrerequisite prerequisite) {
        boolean add = true
        statedPrerequisites.each {
            if (it.name == prerequisite.name && Arrays.equals((Object[])it.param, (Object[])prerequisite.param)) {
                add = false
            }
        }
        if (add) {
            statedPrerequisites.add(prerequisite)
        }
    }
    
    // Specify the details for a previously registered type of prerequisite. Calling this method does not 
    // immediately perform the prerequisite check, but simply specifies that the prerequisite exists and defines
    // the parameters. Later on check() can be called on PrerequisitesExtension or on the StatedPrerequisite 
    // returned by this method. Another way the prequisite can be checked is by calling checkAll, which is 
    // called by the checkPrerequisites task.
    public StatedPrerequisite specify(String prerequisiteName, Object... params) {
        if (checkers.containsKey(prerequisiteName)) {
            PrerequisitesCheckerFactory checker = checkers[prerequisiteName]
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
            new PrerequisitesCheckerFactory(project, prerequisiteName, new ClosureAction<PrerequisitesChecker>(checkerClosure))
        )
        addStatedPrerequisite(prerequisite)
        return prerequisite
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
    public void checkAll() {
        if (statedPrerequisites.count({ !it.check() })) {
            println "All prerequisites satisfied."
        } else {
            throw new RuntimeException("Some prerequisites were not met.")
        }
    }

    private static class ClosureAction<T> implements Action<T> {
        final Closure<T> closure

        ClosureAction(Closure<T> closure) {
            this.closure = closure
        }

        @Override
        void execute(T t) {
            closure.call(t)
        }
    }
}
