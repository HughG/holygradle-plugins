package holygradle.custom_gradle

import holygradle.kotlin.dsl.apply
import holygradle.kotlin.dsl.task
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.Task

class PrerequisitesExtension(private val project: Project) {
    private val checkers: MutableMap<String, PrerequisitesCheckerFactory> = linkedMapOf()
    private val statedPrerequisites: MutableList<StatedPrerequisite> = mutableListOf()
    internal var checkingAllPrerequisites: Boolean = false

    companion object {
        fun defineExtension(project: Project): PrerequisitesExtension {
            if (project == project.rootProject) {
                val extension = project.extensions.create("prerequisites", PrerequisitesExtension::class.java, project)

                // Task to check all specified prerequisites.
                val checkPrerequisitesTask = project.task<DefaultTask>("checkPrerequisites") {
                    group = "Custom Gradle"
                    description = "Runs all prerequisite checks."
                }
                checkPrerequisitesTask.doLast {
                    extension.checkAll()
                    println("All prerequisites satisfied.")
                }

                extension.register("Java", Action { checker: PrerequisitesChecker ->
                    val minVersion = checker.getParametersAs<String>().first()
                    val javaVersion = System.getProperty("java.version")!!
                    val javaVerComponents = javaVersion.split("\\.")
                    val minVerComponents = minVersion.split("\\.")
                    minVerComponents.forEachIndexed { index, item ->
                        val minVerInt = item.toInt()
                        val curVerInt = javaVerComponents[index].toInt()
                        if (minVerInt > curVerInt) {
                            checker.fail("""Java version prerequisite not met.
Minimum version required is '${minVersion}', but found '${javaVersion}'. Please
ensure that you have installed at least version '${minVersion}' of the Java
run-time. (You can go to www.java.com/getjava/ where it might be referred to
as Version ${minVerComponents[1]} Update X; or use a Java SDK of your
choice). After the appropriate version of the Java runtime has been installed
please set the JAVA_HOME environment variable to the location of your new
installation of Java (for example C:\\Program Files\\Java\\jdk1.7.0_07).
Afterwards, please start a new command prompt and re-run the same command.""")
                        }
                    }
                })

                extension.register("Windows", Action { checker: PrerequisitesChecker ->
                    val versions = checker.getParametersAs<String>()
                    val os = System.getProperty("os.name")
                    if (!versions.contains(os)) {
                        checker.fail("The operating system is '${os}', which is not supported. " +
                            "The supported operating systems are: ${versions}.")
                    }
                })
            } else {
                // Make sure that the root project has the core plugin applied, and then grab a reference to the single
                // instance of PrerequisitesExtension.  (Note calling "apply" twice with the same plugin will only apply it
                // once, so it's okay if the user has already applied the core plugin, or one which depends on it, at the
                // root.  This is to cover the case where they haven't.)
                project.rootProject.apply<CustomGradleCorePlugin>()
                project.extensions.add("prerequisites", project.rootProject.extensions.getByName("prerequisites"))
            }

            project.gradle.taskGraph.whenReady {
                val prerequisites = PrerequisitesExtension.getPrerequisites(project)
                if (prerequisites != null) {
                    prerequisites.checkingAllPrerequisites =
                            project.gradle.taskGraph.hasTask(prerequisites.getCheckAllPrerequisitesTask())
                }
            }

            return getPrerequisites(project)!!
        }

        fun getPrerequisites(project: Project): PrerequisitesExtension? =
            project.rootProject.extensions.findByName("prerequisites") as? PrerequisitesExtension
    }

    // Register a new type of prerequisite checker. The checker closure will be supplied a parameter, which may be a
    // collection.
    fun register(prerequisite: String, checkerAction: Action<PrerequisitesChecker>) {
        checkers[prerequisite] = PrerequisitesCheckerFactory(project, prerequisite, checkerAction)
    }

    fun getCheckAllPrerequisitesTask(): Task = project.tasks.getByName("checkPrerequisites")

    private fun addStatedPrerequisite(prerequisite: StatedPrerequisite) {
        // Add this one if there isn't already any equivalent prerequisite.
        val add = statedPrerequisites.none {
            fun Array<out Any>?.paramEquals(other: Array<out Any>?) =
                    if (this == null) { other == null } else { other != null && this.contentEquals(other) }
            it.name == prerequisite.name && it.param.paramEquals(prerequisite.param)
        }
        if (add) {
            statedPrerequisites.add(prerequisite)
        }
    }
    
    // Specify the details for a previously registered type of prerequisite. Calling this method does not 
    // immediately perform the prerequisite check, but simply specifies that the prerequisite exists and defines
    // the parameters. Later on check() can be called on PrerequisitesExtension or on the StatedPrerequisite 
    // returned by this method. Another way the prerequisite can be checked is by calling checkAll, which is
    // called by the checkPrerequisites task.
    fun specify(prerequisiteName: String, vararg params: Any): StatedPrerequisite {
        if (checkers.containsKey(prerequisiteName)) {
            val checker = checkers[prerequisiteName]
                    ?: throw IllegalArgumentException("No prerequisite is registered with name ${prerequisiteName}")
            val prerequisite = StatedPrerequisite(checker, params.clone())
            addStatedPrerequisite(prerequisite)
            return prerequisite
        } else {
            throw RuntimeException("Unknown prerequisite '${prerequisiteName}'.")
        }
    }
    
    // Specifies a new type of prerequisite by supplying 
    fun specify(prerequisiteName: String, checkerAction: Action<PrerequisitesChecker>): StatedPrerequisite {
        if (statedPrerequisites.any { it.name == prerequisiteName }) {
            throw RuntimeException("The prerequisite '${prerequisiteName}' has already been specified.")
        }
        val prerequisite = StatedPrerequisite(PrerequisitesCheckerFactory(project, prerequisiteName, checkerAction))
        addStatedPrerequisite(prerequisite)
        return prerequisite
    }

    // Runs the check for previously specified prerequisites of the given type. For any given type of
    // prerequisite there may be multiple StatedPrerequisite instances.
    fun check(prerequisite: String) {
        val prerequisites = statedPrerequisites.filter { it.name == prerequisite }
        if (prerequisites.isEmpty()) {
            throw RuntimeException("Unknown prerequisite '${prerequisite}'.")
        } else {
            prerequisites.forEach { it.check() }
        }
    }
    
    // Check all stated prerequisites, printing out failure messages and counting failures. An exception will
    // be thrown at the end if there were any failures.
    fun checkAll() {
        if (statedPrerequisites.any { !it.check() }) {
            println("All prerequisites satisfied.")
        } else {
            throw RuntimeException("Some prerequisites were not met.")
        }
    }
}
