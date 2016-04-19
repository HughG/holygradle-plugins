package holygradle

import groovyx.net.http.HTTPBuilder
import groovyx.net.http.Method
import java.util.regex.Matcher

class ArtifactoryHelper {
    private final String url
    private final String repository
    private final Map<String, String> headers

    private static List<String> parseRepositoryUrl(String repositoryUrl) {
        Matcher urlMatch = repositoryUrl =~ /(http\w*[:\/]+[\w\.]+)\/.*\/([\w\-]+)/
        if (urlMatch.size() == 0) {
            throw new RuntimeException("Failed to parse URL for server and repository")
        }

        return urlMatch[0] as List<String>
    }

    public ArtifactoryHelper(String repositoryUrl, Map<String, String> headers = null) {
        List<String> parts = parseRepositoryUrl(repositoryUrl)
        url = parts[1] + "/"
        repository = parts[2]
        this.headers = headers
    }

    public ArtifactoryHelper(String repositoryUrl, String username, String password) {
        this(repositoryUrl, buildAuthHeaders(username, password))
    }

    private static Map<String, String> buildAuthHeaders(String username, String password) {
        // Would be preferable to do: client.auth.basic username, password
        // but due to http://josephscott.org/archives/2011/06/http-basic-auth-with-httplib2/
        // we have to manually include the authorization header.
        String auth = "${username}:${password}".toString()
        String authEncoded = auth.bytes.encodeBase64().toString()
        return ['Authorization': 'Basic ' + authEncoded]
    }

    public boolean artifactExists(String artifactPath) {
        HTTPBuilder http = new HTTPBuilder(url)
        if (headers != null) {
            http.setHeaders(headers)
        }
        boolean exists = false
        http.request( Method.HEAD ) { req ->
            uri.path = "artifactory/${repository}/${artifactPath}"
            response.success = { resp, reader ->
                exists = true
            }
            response.failure = { }
        }
        exists
    }
}