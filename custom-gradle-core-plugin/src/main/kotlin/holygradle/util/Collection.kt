package holygradle.util

/**
 * Copyright (c) 2018 Hugh Greene (githugh@tameter.org).
 */
fun <E> Collection<E>.toLinkedSet(): MutableCollection<E> = LinkedHashSet<E>().apply { addAll(this@toLinkedSet) }
fun <E> Collection<E>.unique(): Collection<E> = if (this is Set<*>) this else toLinkedSet()
fun <E> MutableCollection<E>.mutableUnique(): MutableCollection<E> = if (this is Set<*>) this else toLinkedSet()
