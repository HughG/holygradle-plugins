package holygradle.buildHelpers

import org.gradle.api.Project
import org.gradle.api.logging.Logger

/**
 * Helper class for logging and tracking failure state.
 */
class BuildContext {
    private final Logger logger
    public final File baseDir
    private boolean failed = false

    public BuildContext(Project project, File baseDir) {
        this.logger = project.logger
        this.baseDir = baseDir
    }

    public void debug(String s) {
        logger.debug(s)
    }

    public void info(String s) {
        logger.info(s)
    }

    public void warn(String s) {
        logger.warn(s)
    }

    public void error(String s) {
        failed = true
        logger.error(s)
    }

    public boolean isFailed() {
        return failed
    }
}
