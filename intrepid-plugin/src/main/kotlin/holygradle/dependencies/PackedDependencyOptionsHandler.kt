package holygradle.dependencies

interface PackedDependencyOptions {
    var applyUpToDateChecks: Boolean?
    var readonly: Boolean?
    var unpackToCache: Boolean?
    val createLinkToCache: Boolean?
    var createSettingsFile: Boolean?
    fun noCreateLinkToCache()

    val shouldUnpackToCache: Boolean get() {
        return unpackToCache ?: true
    }

    val shouldCreateSettingsFile: Boolean get() {
        return createSettingsFile ?: false
    }

    val shouldMakeReadonly: Boolean get() {
        return readonly ?: !shouldApplyUpToDateChecks
    }

    val shouldApplyUpToDateChecks: Boolean get() {
        return applyUpToDateChecks ?: false
    }
}

open class PackedDependencyOptionsHandler : PackedDependencyOptions {
    override var applyUpToDateChecks: Boolean? = null
    override var readonly: Boolean? = null
    override var unpackToCache: Boolean? = null
    var _createLinkToCache: Boolean? = null
        private set
    override val createLinkToCache: Boolean? get() = _createLinkToCache
    override var createSettingsFile: Boolean? = null

    override fun noCreateLinkToCache() {
        _createLinkToCache = false
    }
}
