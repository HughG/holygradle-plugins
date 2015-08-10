
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

import org.artifactory.repo.Repositories
import org.artifactory.repo.HttpRepositoryConfiguration
import org.artifactory.repo.VirtualRepositoryConfiguration
import org.artifactory.request.Request
import org.artifactory.resource.ResourceStreamHandle
import groovyx.net.http.RESTClient
import groovyx.net.http.HttpResponseDecorator
import groovyx.net.http.ResponseParseException
import groovy.json.JsonOutput

executions {
  
  /**
   * An execution definition.
   * The first value is a unique name for the execution.
   *
   * Context variables:
   * status (int) - a response status code. Defaults to -1 (unset). Not applicable for an async execution.
   * message (java.lang.String) - a text message to return in the response body, replacing the response content.
   *                              Defaults to null. Not applicable for an async execution.
   *
   * Plugin info annotation parameters:
   *  version (java.lang.String) - Closure version. Optional.
   *  description (java.lang.String) - Closure description. Optional.
   *  httpMethod (java.lang.String, values are GET|PUT|DELETE|POST) - HTTP method this closure is going
   *    to be invoked with. Optional (defaults to POST).
   *  params (java.util.Map<java.lang.String, java.lang.String>) - Closure default parameters. Optional.
   *  users (java.util.Set<java.lang.String>) - Users permitted to query this plugin for information or invoke it.
   *  groups (java.util.Set<java.lang.String>) - Groups permitted to query this plugin for information or invoke it.
   *
   * Closure parameters:
   *  params (java.util.Map) - An execution takes a read-only key-value map that corresponds to the REST request
   *    parameter 'params'. Each entry in the map contains an array of values. This is the default closure parameter,
   *    and so if not named it will be "it" in groovy.
   *  ResourceStreamHandle body - Enables you to access the full input stream of the request body.
   *    This will be considered only if the type ResourceStreamHandle is declared in the closure.
   */
  
  getRepositoryGraph(version: "0.1", description: "Repository Graph", httpMethod: 'GET') { params ->
    output = [:]

    for (name in repositories.localRepositories) {
        output << ["$name": [type: "local"]]
    }

    for (name in repositories.remoteRepositories) {
        output << ["$name": [type: "remote"]]
    }

    for (name in repositories.virtualRepositories) {
        output << ["$name": [type: "virtual", includes: repositories.getRepositoryConfiguration(name).repositories]]
    }

    message = JsonOutput.toJson(output)
    status = 200
  }

}
