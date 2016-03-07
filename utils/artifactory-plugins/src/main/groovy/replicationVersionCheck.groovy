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
 * In the same folder as this plugin, you should create two text files:
 *
 *   - replicationVersionCheck.emails.txt should contain a list of users to email when the plugin fails, with one email
 *     address per line (blank lines are ignored),
 *
 *   - replicationVersionCheck.localUserInfo.txt should contain a single line with the username and password of an admin
 *     user for the local server.  This information is needed to get the mail configuration for sending emails.  The
 *     format should be 'username:password' and the password can be plain or encrypted, depending on how your server is
 *     configured.  For security reasons, the folder containing this file should only be readable by admin users.
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
    private final Logger log
    private final RESTClient client

    public ArtifactoryAPI(Logger log, String server, String username, String password) {
        this.log = log
        client = new RESTClient(server)
        client.parser['application/vnd.org.jfrog.artifactory.system.Version+json'] = client.parser['application/json']

        // Would be preferable to do: client.auth.basic username, password
        // but due to http://josephscott.org/archives/2011/06/http-basic-auth-with-httplib2/
        // we have to manually include the authorization header.
        String auth = "${username}:${password}"
        String authEncoded = auth.bytes.encodeBase64().toString()
        client.setHeaders(['Authorization': 'Basic ' + authEncoded])

        ParserRegistry.setDefaultCharset(null)
    }

    public String getVersionWithRevision() {
        String query = '/artifactory/api/system/version'
        HttpResponseDecorator resp = (HttpResponseDecorator) (client.get(path: query))
        if (resp.status != 200) {
            throw new CancelException("ERROR: problem obtaining version info: {$resp.status} from ${query}", 500)
        }
        Map data = resp.data as Map
        return "${data.version}_rev_${data.revision}"
    }

    public def getMailServerConfiguration() {
        String query = '/artifactory/api/system/configuration'
        HttpResponseDecorator resp = (HttpResponseDecorator) (client.get(path: query))
        if (resp.status != 200) {
            throw new CancelException("ERROR: problem obtaining mail server config: {$resp.status} from ${query}", 500)
        }
        def config = resp.data
        def mailServerConfig = config.mailServer
        if (!mailServerConfig) {
            throw new CancelException(
                "ERROR: This plugin requires that your Artifactory server has the 'Mail' settings configured",
                500
            )
        }
        return mailServerConfig
    }
}

class VersionMatchChecker {
    // NOTE 2016-01-20 HughG: I want to read the set of mail recipients from a file, but the plugin API gives me no way
    // to get the plugin directory or something like that.  From experimentation, the current directory when the
    // "executions" block is executed is the "bin" folder of the installation.
    private static final File ARTIFACTORY_ROOT = new File("..")
    // Read all non-empty lines for email addresses.
    private static final List<String> ALERT_EMAIL_ADDRESSES =
        new File(ARTIFACTORY_ROOT, "etc/plugins/replicationVersionCheck.emails.txt").
            readLines().
            findAll { !it.trim().empty }
    private static final String LOCAL_CRED_FILENAME = "etc/plugins/replicationVersionCheck.localUserInfo.txt"
    private static final List<String> LOCAL_CRED =
        new File(ARTIFACTORY_ROOT, LOCAL_CRED_FILENAME).text.split(':')
    private static final String LOCAL_SERVER_URI = 'http://localhost:8081/'

    private final Repositories repositories
    private final Logger log
    private final Security security

    VersionMatchChecker(Logger log, Security security, Repositories repositories) {
        this.log = log
        this.security = security
        this.repositories = repositories
    }

    // From http://groovy.329449.n5.nabble.com/Sending-email-with-Groovy-td331546.html
    private static void sendMismatchNotifications(ArtifactoryAPI localApi, String errorMessage) {
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

        for (email in ALERT_EMAIL_ADDRESSES) {
            def message = new MimeMessage(session)
            message.from = new InternetAddress(ms.from.text())
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(email))
            message.subject = "${ms.subjectPrefix.text()} Server version mismatch during replication"
            message.sentDate = new Date()
            message.text = errorMessage
            Transport.send(message)
        }
    }

    public void checkServerVersionsMatch(RepoPath localRepoPath, String description) {
        if (LOCAL_CRED.size() != 2 || LOCAL_CRED.any { it == null || it.trim().empty }) {
            throw new CancelException(
                "Failed to read credentials for local access from '${LOCAL_CRED_FILENAME}'. " +
                "That file should contain a single line with the 'username:password' of an admin user. " +
                "The password can be plaintext or encrypted.",
                500
            )
        }
        final String localUsername = LOCAL_CRED[0]
        if (!security.findUser(localUsername)?.admin) {
            throw new CancelException(
                "User '${localUsername}' configured for local access in '${LOCAL_CRED_FILENAME}' is not an admin. " +
                "That file should contain a single line with the 'username:password' of an admin user. " +
                "The password can be plaintext or encrypted.",
                500
            )
        }
        final ArtifactoryAPI localApi = new ArtifactoryAPI(log, LOCAL_SERVER_URI, localUsername, LOCAL_CRED[1])
        String localVersion = localApi.getVersionWithRevision()
        HttpRepositoryConfiguration remoteRepoConf =
            repositories.getRepositoryConfiguration(localRepoPath.repoKey) as HttpRepositoryConfiguration
        String remoteServerUri = new URIBuilder(remoteRepoConf.url).setPath("/").toString()
        final ArtifactoryAPI remoteApi = new ArtifactoryAPI(log, remoteServerUri, remoteRepoConf.username, remoteRepoConf.password)
        String remoteVersion = remoteApi.getVersionWithRevision()
        log.info "Remote version is ${remoteVersion}, local version is ${localVersion} ${description}"
        if (remoteVersion != localVersion) {
            final String errorMessage = "Server version mismatch during replication ${description}: " +
                "remote = ${remoteVersion}, local = ${localVersion}"

            sendMismatchNotifications(localApi, errorMessage)

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
        checker.checkServerVersionsMatch(localRepoPath, "(before file ${localRepoPath.toPath()})")
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
        checker.checkServerVersionsMatch(localRepoPath, "(before dir ${localRepoPath.toPath()})")
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
        checker.checkServerVersionsMatch(localRepoPath, "(before delete ${localRepoPath.toPath()})")
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
        checker.checkServerVersionsMatch(localRepoPath, "(before properties of ${localRepoPath.toPath()})")
    }
}