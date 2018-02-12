package holygradle

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.*

class ArtifactoryHelper(
        repositoryUrl: String,
        private val headers: Map<String, String>? = null
) {
    companion object {
        private fun parseRepositoryUrl(repositoryUrl: String): List<String> {
            val urlMatch = "(http\\w*[:/]+[\\w.]+)/.*/([\\w\\-]+)".toRegex().find(repositoryUrl)
            return urlMatch?.groupValues ?:
                throw RuntimeException("Failed to parse URL for server and repository")
        }

        private fun buildAuthHeaders(username: String, password: String): Map<String, String> {
            // Would be preferable to do: client.auth.basic username, password
            // but due to http://josephscott.org/archives/2011/06/http-basic-auth-with-httplib2/
            // we have to manually include the authorization header.
            val auth = "${username}:${password}"
            val authEncoded = Base64.getEncoder().encodeToString(auth.toByteArray())
            return mapOf("Authorization" to "Basic " + authEncoded)
        }
    }

    private val url: String
    private val repository: String

    init {
        val parts = parseRepositoryUrl(repositoryUrl)
        url = parts[1] + "/"
        repository = parts[2]
    }

    constructor(repositoryUrl: String, username: String, password: String) :
        this(repositoryUrl, buildAuthHeaders(username, password))

    fun artifactExists(artifactPath: String): Boolean {
        val connection = URL(url + "artifactory/${repository}/${artifactPath}").openConnection() as HttpURLConnection
        connection.requestMethod = "HEAD"
        if (headers != null) {
            for ((key, value) in headers) {
                connection.setRequestProperty(key, value)
            }
        }
        return try {
            connection.connect()
            true
        } catch (_: IOException) {
            false
        }
    }
}