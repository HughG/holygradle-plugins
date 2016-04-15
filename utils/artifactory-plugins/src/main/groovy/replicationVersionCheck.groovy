import groovyx.net.http.HttpResponseDecorator
import groovyx.net.http.ParserRegistry
import groovyx.net.http.RESTClient
import groovyx.net.http.URIBuilder
import org.slf4j.Logger

import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import org.artifactory.exception.CancelException
import org.artifactory.repo.HttpRepositoryConfiguration
import org.artifactory.repo.Repositories
import org.artifactory.repo.RepoPath
import org.artifactory.security.Security

/**
 * This plugin is a guard against the possibility that replication will behave incorrectly if the source and destination
 * servers are running different versions of Artifactory.
 *
 * This plugin requires the following JARs to be manually copied to the ".../plugins/lib" folder of Artifactory.  They
 * can be copied from your Gradle cache under the paths given, once you have run "gw build" for this project.  (All
 * other required JARs are already part of Artifactory.)
 *
 *   - http-builder-0.6.jar (org.codehaus.groovy.modules.http-builder/http-builder/0.6)
 *   - json-lib-2.3-jdk15.jar (net.sf.json-lib/json-lib/2.3)
 *
 * In the same folder as this plugin, you should create a folder "conf", and inside that two text files:
 *
 *   - notificationEmails.txt should contain a list of users to email when the plugin fails, with one email address per
 *     line (blank lines are ignored),
 *
 *   - localAdminUserInfo.txt should contain a single line with the username and password of an admin user for the local
 *     server.  This information is needed to get the mail configuration for sending emails.  The format should be
 *     'username:password' and the password must be plaintext, NOT encrypted.  For security reasons, the folder
 *     containing this file should only be readable by admin users.
 *
 *   - localServerBaseUrl.txt should contain the base URL for the server (without the "artifactory/" part), for example,
 *     "http://localhost:8081/".
 */

/**
 *
 * Globally bound variables:
 *
 * log (org.slf4j.Logger)
 * repositories (org.artifactory.repo.Repositories)
 * security (org.artifactory.security.Security)
 * searches (org.artifactory.search.Searches) [since: 2.3.4]
 * builds (org.artifactory.build.Builds) [since 2.5.2]
 *
 * ctx (org.artifactory.spring.InternalArtifactoryContext) - NOT A PUBLIC API - FOR INTERNAL USE ONLY!
 */

class ArtifactoryAPI {
    // NOTE 2016-01-20 HughG: I want to read the admin user credentials from a file, but the plugin API gives me no way
    // to get the plugin directory or something like that.  From experimentation, the current directory when the
    // "executions" block is executed is the "bin" folder of the installation.
    private static final File ARTIFACTORY_ROOT = new File("..")
    private static final String LOCAL_CRED_FILENAME = "etc/plugins/conf/localAdminUserInfo.txt"
    private static final List<String> LOCAL_CRED =
        new File(ARTIFACTORY_ROOT, LOCAL_CRED_FILENAME).text.split(':')
    private static final String LOCAL_SERVER_URI =
        new File(ARTIFACTORY_ROOT, "etc/plugins/conf/localServerBaseUrl.txt").text

    private final Logger log
    private final RESTClient client
    private def systemConfiguration = null

    public static ArtifactoryAPI getLocalApi(Logger log, Security security, Closure asSystem) {
        if (LOCAL_CRED.size() != 2 || LOCAL_CRED.any { it == null || it.trim().empty }) {
            throw new CancelException(
                "Failed to read credentials for local access from '${LOCAL_CRED_FILENAME}'. " +
                    "That file should contain a single line with the 'username:password' of an admin user. " +
                    "The password can be plaintext or encrypted.",
                500
            )
        }
        final String localUsername = LOCAL_CRED[0]
        asSystem {
            if (!security.findUser(localUsername)?.admin) {
                throw new CancelException(
                    "User '${localUsername}' configured for local access in '${LOCAL_CRED_FILENAME}' is not an admin. " +
                        "That file should contain a single line with the 'username:password' of an admin user. " +
                        "The password can be plaintext or encrypted.",
                    500
                )
            }
        }
        return new ArtifactoryAPI(log, LOCAL_SERVER_URI, localUsername, LOCAL_CRED[1])
    }

    public ArtifactoryAPI(
        Logger log,
        String server,
        String username,
        String password,
        String proxyHost = null,
        Integer proxyPort = null
    ) {
        this.log = log
        client = new RESTClient(server)
        client.parser['application/vnd.org.jfrog.artifactory.system.Version+json'] = client.parser['application/json']

        // Would be preferable to do: client.auth.basic username, password
        // but due to http://josephscott.org/archives/2011/06/http-basic-auth-with-httplib2/
        // we have to manually include the authorization header.
        String auth = "${username}:${password}"
        String authEncoded = auth.bytes.encodeBase64().toString()
        client.setHeaders(['Authorization': 'Basic ' + authEncoded])

        // Set the proxy to use, if supplied.  (Currently this doesn't support proxies which need auth.)
        if (proxyHost && proxyPort) {
            // client.setProxy(proxyHost, proxyPort, client.uri.scheme)
            client.setProxy(proxyHost, proxyPort, 'http')
        }

        ParserRegistry.setDefaultCharset(null)
    }

    public List getVersionWithRevision() {
        String query = '/artifactory/api/system/version'
        HttpResponseDecorator resp = (HttpResponseDecorator) (client.get(path: query))
        if (resp.status != 200) {
            throw new CancelException("ERROR: problem obtaining version info: {$resp.status} from ${query}", 500)
        }
        Map data = resp.data as Map
        String version = data.version as String
        String revision = data.revision as String
        return [version.split('\\.').collect { Integer.parseInt(it) }, Integer.parseInt(revision)]
    }

    private def getSystemConfiguration() {
        if (systemConfiguration == null) {
            String query = '/artifactory/api/system/configuration'
            HttpResponseDecorator resp = (HttpResponseDecorator) (client.get(path: query))
            if (resp.status != 200) {
                throw new CancelException("ERROR: problem obtaining mail server config: {$resp.status} from ${query}", 500)
            }
            systemConfiguration = resp.data
        }
        return systemConfiguration
    }

    public def getMailServerConfiguration() {
        def config = getSystemConfiguration()
        def mailServerConfig = config.mailServer
        if (!mailServerConfig) {
            throw new CancelException(
                "ERROR: This plugin requires that your Artifactory server has the 'Mail' settings configured",
                500
            )
        }
        return mailServerConfig
    }

    public def getProxy(String proxyKey) {
        def config = getSystemConfiguration()
        def proxiesConfig = config.proxies
        if (!proxiesConfig) {
            throw new CancelException("ERROR: Failed to find any proxies configured (seeking ${proxyKey})", 500)
        }
        def proxy = proxiesConfig.proxy.find { it.key == proxyKey }
        if (!proxy) {
            throw new CancelException("ERROR: Failed to find configuration for proxy key ${proxyKey}", 500)
        }
        return [proxy.host.text(), proxy.port.text().toInteger()]
    }
}

class EmailNotifier {
    // NOTE 2016-01-20 HughG: I want to read the set of mail recipients from a file, but the plugin API gives me no way
    // to get the plugin directory or something like that.  From experimentation, the current directory when the
    // "executions" block is executed is the "bin" folder of the installation.
    private static final File ARTIFACTORY_ROOT = new File("..")
    // Read all non-empty lines for email addresses.
    private static final List<String> ALERT_EMAIL_ADDRESSES =
        new File(ARTIFACTORY_ROOT, "etc/plugins/conf/notificationEmails.txt").
            readLines().
            findAll { !it.trim().empty }

    private final Logger log
    private final Security security
    private final ArtifactoryAPI localApi

    public EmailNotifier(Logger log, Security security, Closure asSystem) {
        this.log = log
        this.security = security
        this.localApi = ArtifactoryAPI.getLocalApi(log, security, asSystem)
    }

    // From http://groovy.329449.n5.nabble.com/Sending-email-with-Groovy-td331546.html
    public void sendNotifications(
        String subject,
        String errorMessage
    ) {
        def ms = localApi.getMailServerConfiguration()
        def properties = new Properties()
        properties.put('mail.smtp.host', ms.host.text())
        properties.put("mail.smtp.port", ms.port.text());

        boolean useTls = Boolean.parseBoolean(ms.tls.text())
        boolean useSsl = Boolean.parseBoolean(ms.ssl.text())
        if (useTls) {
            properties.put("mail.smtp.auth", "true");
            properties.put("mail.smtp.starttls.enable", "true");
        }
        if (useSsl) {
            properties.put("mail.smtp.socketFactory.port", "465");
            properties.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
            properties.put("mail.smtp.auth", "true");
            properties.put("mail.smtp.port", "465");
        }

        def session = Session.getInstance(
            properties,
            new javax.mail.Authenticator() {
                // This method is used by the base class, so suppress unused warning
                @SuppressWarnings("GroovyUnusedDeclaration")
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(ms.username.text(), ms.password.text());
                }
            }
        )

        String currentUserEmail = security.currentUser().email
        // log.debug("sendNotifications: security.currentUsername = ${security.currentUsername}")
        // log.debug("sendNotifications: currentUserEmail = ${currentUserEmail}")
        boolean isValidCurrentUserEmail = (currentUserEmail != null && !currentUserEmail.trim().empty)
        List<String> emailAddresses = ALERT_EMAIL_ADDRESSES
        if (isValidCurrentUserEmail) {
            emailAddresses = emailAddresses + currentUserEmail
        }
        log.debug("sendNotifications: emailAddresses = ${emailAddresses}")
        for (email in emailAddresses) {
            def message = new MimeMessage(session)
            message.from = new InternetAddress(ms.from.text())
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(email))
            message.subject = "${ms.subjectPrefix.text()} ${subject}"
            message.sentDate = new Date()
            message.text = errorMessage
            Transport.send(message)
        }
    }
}

class VersionMatchChecker {
    private final Repositories repositories
    private final Logger log
    private final Security security

    VersionMatchChecker(Logger log, Security security, Repositories repositories) {
        this.log = log
        this.security = security
        this.repositories = repositories
    }

    private boolean versionIsGreaterThan(List left, List right) {
        def (List<Integer> leftVersion, Integer leftRevision) = left
        def (List<Integer> rightVersion, Integer rightRevision) = right

        // This method returns a list pairing each item in leftVersion with the corresponding item in rightVersion.  If
        // the lists are not the same size, then the extra items from one list are dropped, so we need to check that as
        // a later step.
        List<List<Integer>> leftWithRightVersionComponents = [leftVersion, rightVersion].transpose()
        // log.debug("leftWithRightVersionComponents = ${leftWithRightVersionComponents}")
        // log.debug("leftRevision = ${leftRevision}, rightRevision = ${rightRevision}")
        int comparePairedComponents = 0
        for (pair in leftWithRightVersionComponents) {
            def (leftComponent, rightComponent) = pair
            // We don't use a switch here because we may want to break out of the for.
            def comp = leftComponent <=> rightComponent
            // log.info("left ${leftComponent} <=> right ${rightComponent} is ${comp}")
            if (comp == -1) { // less than
                comparePairedComponents = -1
                break
            } else if (comp == 0) { // equal
                // We will just continue
            } else /* comp == 1 */ { // greater than
                comparePairedComponents = 1
                break
            }
        }

        // If the paired version parts are equal, but the left version has more parts, we assume it is a greater (newer)
        // version.
        int compareVersionLength = leftVersion.size() <=> rightVersion.size()

        // If the paired version parts are equal and the length is the same, lastly check the "rev".
        int compareRevision = leftRevision <=> rightRevision

        int result = comparePairedComponents ?:
            compareVersionLength ?:
            compareRevision
        // log.debug("${comparePairedComponents} ?: ${compareVersionLength} ?: ${compareRevision} = ${result}")
        return result == 1
    }

    public void checkServerVersionsMatch(RepoPath localRepoPath, String description, Closure asSystem) {
        final ArtifactoryAPI localApi = ArtifactoryAPI.getLocalApi(log, security, asSystem)
        def localVersion = localApi.getVersionWithRevision()
        def remoteRepoName = localRepoPath.repoKey
        final String CACHE_REPO_NAME_SUFFIX = '-cache'
        if (remoteRepoName.endsWith(CACHE_REPO_NAME_SUFFIX)) {
            remoteRepoName = remoteRepoName.substring(0, remoteRepoName.size() - CACHE_REPO_NAME_SUFFIX.size())
            // log.debug("Trimmed name to ${remoteRepoName}")
        }

        HttpRepositoryConfiguration remoteRepoConf =
            repositories.getRepositoryConfiguration(remoteRepoName) as HttpRepositoryConfiguration
        String remoteServerUri = new URIBuilder(remoteRepoConf.url).setPath("/").toString()
        def (proxyHost, proxyPort) = localApi.getProxy(remoteRepoConf.proxy)
        final ArtifactoryAPI remoteApi = new ArtifactoryAPI(
            log, remoteServerUri, remoteRepoConf.username, remoteRepoConf.password, proxyHost, proxyPort
        )
        def remoteVersion = remoteApi.getVersionWithRevision()
        // log.debug("Remote version is ${remoteVersion}, local version is ${localVersion}, ${description}")
        if (versionIsGreaterThan(remoteVersion, localVersion)) {
            final String errorMessage = "Server version mismatch during replication ${description}: " +
                "remote = ${remoteVersion} is greater than local = ${localVersion}"

            new EmailNotifier(log, security, asSystem).sendNotifications(
                "Server version mismatch during replication",
                errorMessage
            )

            throw new CancelException(errorMessage, 500)
        }
    }
}

replication {
    /**
     * Handle before file replication events.
     *
     * Context variables:
     * skip (boolean) - whether to skip replication for the current item. Defaults to false. Set to false to skip replication.
     *
     * Closure parameters:
     * localRepoPath (org.artifactory.repo.RepoPath) - the repoPath of the item on the local Artifactory server.
     */
    beforeFileReplication { RepoPath localRepoPath ->
        final VersionMatchChecker checker = new VersionMatchChecker(log, security, repositories)
        checker.checkServerVersionsMatch(localRepoPath, "(before file ${localRepoPath.toPath()})", { c -> asSystem(c) })
    }
    /**
     * Handle before directory replication events.
     *
     * Context variables:
     * replicate (int) - whether to replicate the current item. Defaults to true. Set to false to skip replication.
     *
     * Closure parameters:
     * localRepoPath (org.artifactory.repo.RepoPath) - the repoPath of the item on the local Artifactory server.
     */
    beforeDirectoryReplication { RepoPath localRepoPath ->
        final VersionMatchChecker checker = new VersionMatchChecker(log, security, repositories)
        checker.checkServerVersionsMatch(localRepoPath, "(before dir ${localRepoPath.toPath()})", { c -> asSystem(c) })
    }
    /**
     * Handle before delete replication events.
     *
     * Context variables:
     * replicate (int) - whether to replicate the current item. Defaults to true. Set to false to skip replication.
     *
     * Closure parameters:
     * localRepoPath (org.artifactory.repo.RepoPath) - the repoPath of the item on the local Artifactory server.
     */
    beforeDeleteReplication { RepoPath localRepoPath ->
        final VersionMatchChecker checker = new VersionMatchChecker(log, security, repositories)
        checker.checkServerVersionsMatch(localRepoPath, "(before delete ${localRepoPath.toPath()})", { c -> asSystem(c) })
    }
    /**
     * Handle before property replication events.
     *
     * Context variables:
     * replicate (int) - whether to replicate the current item. Defaults to true. Set to false to skip replication.
     *
     * Closure parameters:
     * localRepoPath (org.artifactory.repo.RepoPath) - the repoPath of the item on the local Artifactory server.
     */
    beforePropertyReplication { RepoPath localRepoPath ->
        final VersionMatchChecker checker = new VersionMatchChecker(log, security, repositories)
        checker.checkServerVersionsMatch(localRepoPath, "(before properties of ${localRepoPath.toPath()})", { c -> asSystem(c) })
    }
}