package holygradle.custom_gradle.plugin_apis

/**
 * An interface for plugins which can supply a username and password for authentication.
 */
public interface CredentialSource {
    public static final String DEFAULT_CREDENTIAL_TYPE = "Domain Credentials"

    CredentialStore getCredentialStore()

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

    /**
     * Returns the username stored in a particular "Intrepid - <credentialType>" credential.
     * @return The username stored in a particular "Intrepid - <credentialType>" credential.
     */
    String username(String credentialType)

    /**
     * Returns the password stored in a particular "Intrepid - <credentialType>" credential.
     * @return The password stored in a particular "Intrepid - <credentialType>" credential.
     */
    String password(String credentialType)
}