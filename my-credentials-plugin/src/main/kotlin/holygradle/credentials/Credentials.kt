package holygradle.credentials

/**
 * Simple holder class for username and password.
 */
data class Credentials(val userName: String, val password: String) {
    override fun toString(): String {
        return "Credentials($userName, ****)"
    }
}
