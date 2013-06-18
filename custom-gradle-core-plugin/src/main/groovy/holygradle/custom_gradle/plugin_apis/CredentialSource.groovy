package holygradle.custom_gradle.plugin_apis

/**
 * An interface for plugins which can supply a username and password for authentication.
 */
public interface CredentialSource {
    /**
     * Returns a username for authentication.
     * @return A username for authentication.
     */
    String getUsername()

    /**
     * Returns a password for authentication.
     * @return A password for authentication.
     */
    String getPassword()
}