package holygradle.custom_gradle.plugin_apis

import org.gradle.api.credentials.PasswordCredentials

/**
 * Simple holder class for username and password.
 * TODO 2019-05-10 HughG: Rename to something like DefaultPasswordCredentials, and maybe make internal.
 */
data class Credentials(
        private var _username: String?,
        private var _password: String?
) : PasswordCredentials {
    override fun getUsername(): String? = _username

    override fun setUsername(userName: String?) {
        _username = userName
    }

    override fun getPassword(): String? = _password

    override fun setPassword(password: String?) {
        _password = password
    }

    override fun toString(): String {
        return "Credentials{" +
                "userName='" + _username + '\''.toString() +
                ", password='********'" +
                '}'.toString()
    }
}
