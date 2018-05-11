package holygradle.artifactory_manager

import groovy.lang.Closure
import groovy.text.SimpleTemplateEngine
import groovy.text.Template
import groovyx.net.http.HttpResponseDecorator
import groovyx.net.http.ParserRegistry
import groovyx.net.http.RESTClient
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

private val ARTIFACTORY_REST_API_DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX")

sealed class ItemInfo(protected val map: Map<String, Any>) {
    val created: Date by lazy {
        val createdString = map["created"] as String
        try {
            ARTIFACTORY_REST_API_DATE_FORMAT.parse(createdString)
        } catch (e: ParseException) {
            println("Failed to parse date '${createdString}': ${e}")
            Date()
        }
    }
}

class FileInfo(map: Map<String, Any>) : ItemInfo(map) {
    val size: Long by map
}

class FolderInfo(map: Map<String, Any>) : ItemInfo(map) {
    @Suppress("UNCHECKED_CAST")
    val children: List<FolderChildInfo>
        get() = (map["children"] as List<Map<String, Any>>?)?.map(::FolderChildInfo) ?: listOf()
}

class FolderChildInfo(map: Map<String, Any>) {
    val uri: String by map
    val folder: Boolean by map
}

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
class DefaultArtifactoryAPI(
        server: String,
        override val repository: String,
        username: String,
        password: String,
        private val dryRun: Boolean
) : ArtifactoryAPI {
    private val client: RESTClient = RESTClient(server)
    private val engine = SimpleTemplateEngine()
    private val repoPathTemplate: Template
    private val repoPathInfoTemplate: Template

    init {
        operator fun ParserRegistry.get(contentType: String): Closure<Any> = getAt(contentType)
        operator fun ParserRegistry.set(contentType: String, value: Closure<Any>) { putAt(contentType, value) }

        client.parser["application/vnd.org.jfrog.artifactory.storage.FolderInfo+json"] = client.parser["application/json"]
        client.parser["application/vnd.org.jfrog.artifactory.storage.FileInfo+json"] = client.parser["application/json"]
        client.parser["application/vnd.org.jfrog.artifactory.repositories.RepositoryDetailsList+json"] = client.parser["application/json"]
        
        // Would be preferable to do: client.auth.basic username, password
        // but due to http://josephscott.org/archives/2011/06/http-basic-auth-with-httplib2/
        // we have to manually include the authorization header.
        val auth = "${username}:${password}"
        val authEncoded = Base64.getEncoder().encodeToString(auth.toByteArray())
        client.headers = mapOf("Authorization" to "Basic " + authEncoded)

        ParserRegistry.setDefaultCharset(null)

        // Create these only once, and bind them multiple times.  It creates and loads a new class for each template
        // creation, so doing it once per call gets you "java.lang.OutOfMemoryError: PermGen space" after a while.
        // (See http://prystash.blogspot.co.uk/2011/06/experimenting-with-groovy-template.html)
        repoPathTemplate = engine.createTemplate("/artifactory/\$repository/\$path")
        repoPathInfoTemplate = engine.createTemplate("/artifactory/api/storage/\$repository/\$path")
    }

    override fun getNow(): Date = Date()

    override fun getFileInfoJson(path: String): Map<String, Any> {
        return getJsonAsMap(path, repoPathInfoTemplate)
    }

    override fun getFolderInfoJson(path: String): Map<String, Any> {
        return getJsonAsMap(path, repoPathInfoTemplate)
    }

    override fun getFileInfo(path: String): FileInfo {
        return FileInfo(getFileInfoJson(path))
    }

    override fun getFolderInfo(path: String): FolderInfo {
        return FolderInfo(getFolderInfoJson(path))
    }

    override fun removeItem(path: String) {
        val binding = mapOf("repository" to repository, "path" to path)
        val template = repoPathTemplate.make(binding)
        val query = template.toString()
        if (!dryRun) {
            client.delete(mapOf("path" to query))
        }
    }

    private fun getJsonAsMap(path: String, requestTemplate: Template): Map<String, Any> {
        val binding = mapOf("repository" to repository, "path" to path)
        val template = requestTemplate.make(binding)
        val query = template.toString()
        val resp = client.get(mapOf("path" to query)) as HttpResponseDecorator
        if (resp.status != 200) {
            throw RuntimeException("ERROR: problem obtaining folder info: {$resp.status} from ${query}")
        }
        @Suppress("UNCHECKED_CAST")
        return resp.data as Map<String, Any>
    }
}