package holygradle.scm

import holygradle.custom_gradle.plugin_apis.CredentialSource
import holygradle.custom_gradle.plugin_apis.Credentials
import holygradle.io.FileHelper
import holygradle.util.addingDefault
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.process.ExecSpec
import java.io.File
import java.net.URL
import java.util.function.Predicate

// TODO 2017-03-28 HughG: Refactor credential methods out to a separate class.

internal object ScmHelper {
    private const val CREDENTIAL_BASIS_FILE_PATH = "holygradle/credential-bases.txt"
    private val HTTP_URL_SCHEMES = listOf("http", "https")

    fun getGitConfigValue(gitCommand: Command, workingDir: File, configKey: String): String {
        return gitCommand.execute(Action { spec: ExecSpec ->
            spec.workingDir = workingDir
            spec.args("config", "--get", configKey)
        }, Predicate { error_code ->
            // Error code 1 means the section or key is invalid, probably just no remote set, so don't throw.
            error_code != 1
        })
    }

    private fun readCredentialBasisFile(project: Project): MutableMap<String, MutableSet<String>> {
        val bases = LinkedHashMap<String, MutableSet<String>>().addingDefault { LinkedHashSet() }
        val basisFile = File(project.gradle.gradleUserHomeDir, CREDENTIAL_BASIS_FILE_PATH)
        if (!basisFile.exists()) {
            return bases
        }

        var lineIndex = 0
        var currentBasis: String? = null
        basisFile.forEachLine { line ->
            val basis = currentBasis
            ++lineIndex
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty() || line[0] == '#') {
                // Ignore blank and comment lines.
            } else if (Character.isWhitespace(line[0])) {
                if (basis == null) {
                    project.logger.warn("WARNING: Ignoring entry '${trimmedLine}' on line ${lineIndex} of " +
                            "${basisFile.absolutePath} because no basis line has been encountered yet.")
                } else {
                    bases[basis]!!.add(trimmedLine)
                }
            } else {
                // Warn the user if we haven't seen any entries under the current basis, before we set the next.
                if (basis != null && bases[basis]!!.isEmpty()) {
                    project.logger.warn("WARNING: Basis credential ${currentBasis} in ${basisFile.absolutePath} " +
                            "has no credentials listed under it.")
                }
                currentBasis = line
            }
        }
        return bases
    }

    private fun writeCredentialBasisFile(project: Project, credentialsByBasis: Map<String, Set<String>>) {
        val basisFile = File(project.gradle.gradleUserHomeDir, CREDENTIAL_BASIS_FILE_PATH)
        FileHelper.ensureMkdirs(basisFile.parentFile, "create folder for ${basisFile.name}")
        basisFile.printWriter().use { pw ->
            pw.println("# This is a machine-generated Holy Gradle Credential Basis file.")
            pw.println("#")
            pw.println("# It contains a mapping from credential basis names to credential names.")
            pw.println("# Credential bases are stored in the Windows Credential Manager as")
            pw.println("# 'Intrepid - <name>'.  Associated are credentials filled in from that name")
            pw.println("# where the credentialBasis is set on a sourceDependency; credential-store.exe")
            pw.println("# reads this file to update all associated credentials as a group.")
            pw.println("#")
            pw.println("# Lines starting with '#' are comments.  Comments and blank lines are ignored.")
            pw.println("# Lines starting with other non-whitespace characters are the names of bases.")
            pw.println("# Lines which follow a basis and start with whitespace are the names of")
            pw.println("# credentials associated with that basis.")

            credentialsByBasis.forEach { basis, credentials ->
                pw.println(basis)
                credentials.forEach { credential ->
                    pw.print("\t")
                    pw.println(credential)
                }
            }
        }
    }

    // Update credential basis file if necessary.
    private fun updateCredentialBasisFile(project: Project, credentialName: String, credentialBasis: String) {
        // Read contents of basis file.
        val credentialsByBasis = readCredentialBasisFile(project)

        // Ensure the credential name is associated with one and only one basis.
        var changed = credentialsByBasis[credentialBasis]!!.add(credentialName)
        credentialsByBasis.forEach { basis, credentials ->
            if (basis != credentialName) {
                changed = changed or credentials.remove(credentialName)
            }
        }

        // Write file, if the mapping is effectively changed.
        if (changed) {
            writeCredentialBasisFile(project, credentialsByBasis)
        }

    }

    fun storeCredential(
            project: Project,
            credentialSource: CredentialSource,
            credentialName: String,
            credentialBasis: String
    ) {
        updateCredentialBasisFile(project, credentialName, credentialBasis)
        val username = credentialSource.username(credentialBasis)
        val password = credentialSource.password(credentialBasis)
        if (username == null || password == null) {
            throw RuntimeException("Failed to get username and password for credential basis ${credentialBasis}")
        }
        credentialSource.credentialStore.writeCredential(credentialName, Credentials(username, password))
        project.logger.info("  Cached credential '${credentialName}' from basis '${credentialBasis}'.")
    }

    fun repoSupportsAuthentication(repoUrl: String): Boolean {
        return URL(repoUrl).protocol in HTTP_URL_SCHEMES
    }
}
