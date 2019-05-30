package holygradle.scm

import java.io.File

class DummySourceControl : SourceControlRepository {
    override val localDir: File = File("__dummy__file__")
    override val protocol: String = "n/a"
    override val url: String = "dummy:url"
    override val revision: String? = null
    override val hasLocalChanges: Boolean = false
    override fun ignoresFile(file: File): Boolean = false
}
