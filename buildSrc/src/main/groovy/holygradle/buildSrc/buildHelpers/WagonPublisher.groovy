package holygradle.buildSrc.buildHelpers

import org.apache.maven.wagon.Wagon
import org.apache.maven.wagon.authentication.AuthenticationInfo
import org.apache.maven.wagon.repository.Repository
import org.codehaus.plexus.DefaultPlexusContainer
import org.codehaus.plexus.PlexusContainer
import org.gradle.api.file.FileCollection
import org.gradle.api.logging.Logger

/**
 * Wraps some other classes to support publishing the website using WebDAV.
 *
 * Written using https://bitbucket.org/davidmc24/gradle-site-plugin as a guide.
 */
public class WagonPublisher {
    private final Logger logger
    private final String publishTargetUrl
    private final AuthenticationInfo authenticationInfo

    private PlexusContainer plexusContainer

    public WagonPublisher(Logger logger, String publishTargetUrl, String username, String password) {
        this.logger = logger
        this.publishTargetUrl = publishTargetUrl
        authenticationInfo = new AuthenticationInfo()
        authenticationInfo.with {
            it.userName = username
            it.password = password
        }
    }

    public void publish(FileCollection filesToPublish, File baseDir) {
        Repository repository = new Repository("website", publishTargetUrl)
        PlexusContainer plexusContainer = new DefaultPlexusContainer()
        try {
            Wagon wagon = (Wagon)plexusContainer.lookup(Wagon.ROLE, repository.protocol)
            wagon.connect(repository, authenticationInfo)
            try {
                def basePath = baseDir.toPath()
                for (File file in filesToPublish) {
                    // Note: we replace '\' with '/' because we've got Windows paths but want valid URLs.
                    String relativePath = basePath.relativize(file.toPath()).toString().replace('\\', '/')
                    logger.info("Uploading ${file} to ${relativePath}")
                    wagon.put(file, relativePath)
                }
                logger.info("Upload done")
            } finally {
                wagon.disconnect()
            }
        } finally {
            plexusContainer.dispose()
        }
    }
}