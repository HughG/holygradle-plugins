package holygradle.util

/**
 * Kotlin's withDefault extension functions, unlike Groovy's, return maps which never add the default value to the
 * underlying map, and don't return the default from the "[]" operator or get method.  This map instead acts like
 * Groovy's.
 */
interface MapAddingDefault<K, V> : MutableMap<K, V> {
    override fun get(key: K): V
}

class MapAddingDefaultImpl<K, V>(
        private val map: MutableMap<K, V>,
        private val makeDefault: () -> V
) :
        MutableMap<K, V> by map,
        MapAddingDefault<K, V>
{
    override fun get(key: K): V {
        var v = map[key]
        if (v == null) {
            v = makeDefault()
            map[key] = v
        }
        return v!!
    }
}

/**
 * Returns a wrapper of the original map which acts like Groovy's "map with default".
 */
fun <K, V> MutableMap<K, V>.addingDefault(makeDefault: () -> V): MutableMap<K, V> =
        MapAddingDefaultImpl(this, makeDefault)
