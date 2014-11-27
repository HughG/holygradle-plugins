package holygradle.buildHelpers

import org.gradle.api.Project
import org.gradle.api.logging.Logger

/**
 * Helper class for logging and tracking failure state.
 */
class BuildContext {
    private final Logger logger
    private boolean failed = false

    public BuildContext(Project project) {
        this.logger = project.logger
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
