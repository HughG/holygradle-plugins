package holygradle.scm

import holygradle.custom_gradle.plugin_apis.CredentialSource
import holygradle.io.FileHelper
import org.gradle.api.Project
import org.gradle.process.ExecSpec

class ScmHelper {
    public static String getGitConfigValue(Command gitCommand, File workingDir, String configKey) {
        return gitCommand.execute({ ExecSpec spec ->
            spec.workingDir = workingDir
            spec.args "config", "--get", configKey
        }, {int error_code ->
            // Error code 1 means the section or key is invalid, probably just no remote set, so don't throw.
            return (error_code != 1)
        })
    }

    private static Map<String, Set<String>> readCredentialBasisFile(Project project) {
        Map<String, Set<String>> bases = [:].withDefault { new LinkedHashSet<String>() }
        File basisFile = new File(project.gradle.gradleUserHomeDir, "holygradle/credential-basis.txt")
        if (!basisFile.exists()) {
            return bases
        }

        int lineIndex = 0
        String currentBasis = null
        basisFile.eachLine { String line ->
            ++lineIndex
            final String trimmedLine = line.trim()
            if (trimmedLine.empty || line[0] == '#') {
                // Ignore blank and comment lines.
            } else if (Character.isWhitespace(line.charAt(0))) {
                if (currentBasis == null) {
                    project.logger.warn "WARNING: Ignoring entry '${trimmedLine}' on line ${lineIndex} of " +
                                            "${basisFile.absolutePath} because no basis line has been encountered yet."
                } else {
                    bases[currentBasis] << trimmedLine
                }
            } else {
                // Warn the user if we haven't seen any entries under the current basis, before we set the next.
                if (currentBasis != null && bases[currentBasis].empty) {
                    project.logger.warn "WARNING: Basis credential ${currentBasis} in ${basisFile.absolutePath} " +
                                            "has no credentials listed under it."
                }
                currentBasis = line
            }
        }
        return bases
    }

    private static void writeCredentialBasisFile(Project project, Map<String, Set<String>> credentialsByBasis) {
        File basisFile = new File(project.gradle.gradleUserHomeDir, "holygradle/credential-basis.txt")
        FileHelper.ensureMkdirs(basisFile.parentFile, "create folder for ${basisFile.name}")
        basisFile.withPrintWriter { pw ->
            pw.println "# This is a machine-generated Holy Gradle Credential Basis file."
            pw.println "#"
            pw.println "# It contains a mapping from credential basis names to credential names."
            pw.println "# Credential bases are stored in the Windows Credential Manager as"
            pw.println "# 'Intrepid - <name>'.  Associated are credentials filled in from that name"
            pw.println "# where the credentialBasis is set on a sourceDependency; credential-store.exe"
            pw.println "# reads this file to update all associated credentials as a group."
            pw.println "#"
            pw.println "# Lines starting with '#' are comments.  Comments and blank lines are ignored."
            pw.println "# Lines starting with other non-whitespace characters are the names of bases."
            pw.println "# Lines which follow a basis and start with whitespace are the names of"
            pw.println "# credentials associated with that basis."

            credentialsByBasis.each { basis, credentials ->
                pw.println basis
                credentials.each { credential ->
                    pw.print "\t"
                    pw.println credential
                }
            }
        }
    }

    // Update credential basis file if necessary.
    private static void updateCredentialBasisFile(Project project, String credentialName, String credentialBasis) {
        // - Read contents of basis file.
        Map<String, Set<String>> credentialsByBasis = readCredentialBasisFile(project)

        // - Write file, if it was new.
        boolean hadBasis = credentialsByBasis.containsKey(credentialBasis)
        def credentialsForBasis = credentialsByBasis[credentialBasis]
        boolean hadCredential = credentialsForBasis.contains(credentialName)
        if (!hadBasis || !hadCredential) {
            writeCredentialBasisFile(project, credentialsByBasis)
        }

    }

    public static void storeCredential(
        Project project,
        Command credentialStoreCommand,
        CredentialSource credentialSource,
        String credentialName,
        String credentialBasis
    ) {
        updateCredentialBasisFile(project, credentialName, credentialBasis)
        credentialStoreCommand.execute({ ExecSpec spec ->
            final username = credentialSource.username(credentialBasis)
            final password = credentialSource.password(credentialBasis)
            spec.args credentialName, username, password
        })
        project.logger.info "  Cached credential '${credentialName}'."
    }

    public static boolean repoSupportsAuthentication(String repoUrl) {
        return new URL(repoUrl).protocol in ["http", "https"]
    }
}
