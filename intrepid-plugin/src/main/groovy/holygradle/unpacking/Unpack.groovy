package holygradle.unpacking

import org.gradle.api.Task

/**
 * Interface for tasks for unpacking packed dependencies.
 *
 * Annoyingly, this can't extend {@link Task} and still be useful because some internal Gradle base classes have a
 * "covariant return type" override of {@link Task#getTaskDependencies}, and the Groovy compiler can't cope with this
 * (even though Java can).  So, clients of this interface must cast to it.
 */
public interface Unpack {
    /**
     * Returns the directory to which the relevant file should be unpacked.
     * @return The directory to which the relevant file should be unpacked.
     */
    File getUnpackDir()
}