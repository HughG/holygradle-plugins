package holygradle

import groovyx.net.http.HttpResponseDecorator
import groovyx.net.http.ParserRegistry
import groovyx.net.http.RESTClient
import org.artifactory.exception.CancelException
import org.artifactory.fs.ItemInfo
import org.artifactory.repo.RepoPath
import org.artifactory.security.Security
import org.slf4j.Logger

import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

/**
 * This plugin is a workaround for defect <https://www.jfrog.com/jira/browse/RTFACT-6377>.  It avoids getting a repo
 * into a state in which some files won't be backed up, by rejecting additions of files which would cause a name clash.
 * It won't detect whether you already have folders which clash, though.
 *
 * I tried quite hard to get this plugin to provide a useful error message in all cases, but it will just give a 500
 * with a semi-informative error in some cases.  This happens if you deploy a file for which one of its parent folders
 * has a clash.  This is because recursive creation of parents happens automatically, outside the scope of the handler
 * methods below, and if one of those fails, the 409 thrown by this plugin is caught and re-thrown as a 500.  An admin
 * can still see an informative message in the logs, though.
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

/*
 * NOTE 2016-03-08 HughG: For now I'm duplicating this class and EmailNotifier from the replicationVersionCheck.groovy
 * plugin.  I could pull them out into a JAR but that would be harder to understand for server admins.  I can't use
 * Script#evaluate as suggested at http://stackoverflow.com/a/9154553 (I get class load errors) and I can't use
 * @BaseScript as suggested at http://stackoverflow.com/a/20017892 because Artifactory 3.x has Groovy 1.8, and that
 * needs Groovy 2.2 -- but it will be possible for Artifactory 4.x.
 */
class ArtifactoryAPI2 {
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

    public static ArtifactoryAPI2 getLocalApi(Logger log, Security security, Closure asSystem) {
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
        return new ArtifactoryAPI2(log, LOCAL_SERVER_URI, localUsername, LOCAL_CRED[1])
    }

    public ArtifactoryAPI2(Logger log, String server, String username, String password) {
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

class EmailNotifier2 {
    // NOTE 2016-01-20 HughG: I want to read the set of mail recipients from a file, but the plugin API gives me no way
    // to get the plugin directory or something like that.  From experimentation, the current directory when the
    // "executions" block is executed is the "bin" folder of the installation.
    private static final File ARTIFACTORY_ROOT = new File("..")
    // Read all non-empty lines for email addresses.
    private static final Set<String> ALERT_EMAIL_ADDRESSES =
        new File(ARTIFACTORY_ROOT, "etc/plugins/conf/notificationEmails.txt")
            .readLines()
            .findAll { !it.trim().empty }
            .toSet()

    private final Logger log
    private final Security security
    private final ArtifactoryAPI2 localApi

    public EmailNotifier2(Logger log, Security security, Closure asSystem) {
        this.log = log
        this.security = security
        this.localApi = ArtifactoryAPI2.getLocalApi(log, security, asSystem)
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
        log.debug("sendNotifications: security.currentUsername = ${security.currentUsername}")
        log.debug("sendNotifications: currentUserEmail = ${currentUserEmail}")
        boolean isValidCurrentUserEmail = (currentUserEmail != null && !currentUserEmail.trim().empty)
        Set<String> emailAddresses = new HashSet<String>(ALERT_EMAIL_ADDRESSES)
        if (isValidCurrentUserEmail) {
            emailAddresses += currentUserEmail
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

def List<ItemInfo> checkSiblings(RepoPath p) {
    List<ItemInfo> siblings = repositories.getChildren(p.parent)
    List<ItemInfo> clashes = siblings.findAll {
        log.debug("    sibling " + it)
        final boolean clash = (p.path != it.repoPath.path && p.path.equalsIgnoreCase(it.repoPath.path))
        if (clash) {
            log.debug("        CLASH!")
        }
        return clash
    }
    return clashes
}

def void checkPathForCaseClashes(RepoPath repoPath, Closure asSystem) {
    List<ItemInfo> clashes = []
    for (RepoPath path = repoPath; !path.root; path = path.parent) {
        log.debug("Checking " + path)
        clashes += checkSiblings(path)
    }

    if (!clashes.empty) {
        final String errorMessage = "Path ${repoPath} is partly case-insensitive-equal to the following existing items, " +
            "which would interfere with backups due to <https://www.jfrog.com/jira/browse/RTFACT-6377>: " +
            clashes*.repoPath*.toString().join(", ")

        // Send email as well as giving an error.  This is helpful because the user won't see a useful error message
        // unless the case clash is in the filename, rather than in any parent folder.
        new EmailNotifier2(log, security, asSystem).sendNotifications(
            "Blocked deploy by ${security.currentUsername} (${security.currentUser().email}) to case-insensitive-equal path",
            errorMessage
        )

        throw new CancelException(errorMessage, HttpURLConnection.HTTP_CONFLICT)
    }
}

storage {

    /**
     * Handle before create events.
     *
     * Closure parameters:
     * item (org.artifactory.fs.ItemInfo) - the original item being created.
     */
    beforeCreate { ItemInfo item ->
        checkPathForCaseClashes(item.repoPath, { c -> asSystem(c) })
    }

    /**
     * Handle before move events.
     *
     * Closure parameters:

     * item (org.artifactory.fs.ItemInfo) - the source item being moved.
     * targetRepoPath (org.artifactory.repo.RepoPath) - the target repoPath for the move.
     * properties (org.artifactory.md.Properties) - user specified properties to add to the item being moved.
     */
    beforeMove { ItemInfo item, RepoPath targetRepoPath, org.artifactory.md.Properties properties ->
        checkPathForCaseClashes(targetRepoPath, { c -> asSystem(c) })
    }

    /**
     * Handle before copy events.
     *
     * Closure parameters:
     * item (org.artifactory.fs.ItemInfo) - the source item being copied.
     * targetRepoPath (org.artifactory.repo.RepoPath) - the target repoPath for the copy.
     * properties (org.artifactory.md.Properties) - user specified properties to add to the item being moved.
     */
    beforeCopy { ItemInfo item, RepoPath targetRepoPath, org.artifactory.md.Properties properties ->
        checkPathForCaseClashes(targetRepoPath, { c -> asSystem(c) })
    }
}