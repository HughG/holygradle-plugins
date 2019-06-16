package holygradle.apache.ivy.groovy

import groovy.util.Node
import groovy.util.NodeList
import groovy.xml.Namespace
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Copyright (c) 2018 Hugh Greene (githugh@tameter.org).
 */

private fun getChildNodes(node: Node, name: String): NodeList? {
    return node[name] as? NodeList
}

private fun getChildNode(node: Node, name: String): Node? {
    val childNodes = getChildNodes(node, name) ?: return null
    val childCount = childNodes.size
    return when (childCount) {
        0 -> null
        1 -> childNodes[0] as Node
        else -> throw RuntimeException("Expected 0 or 1 child node '${name}' but found ${childCount} in ${node}")
    }
}

sealed class IvyNode(protected val node: Node) {
    class List<T : IvyNode>(
            private val parent: Node,
            private val childrenName: String,
            private val makeIvyNode: (Node) -> T
    ): AbstractMutableList<T>() {
        private val maybeNodes = getChildNodes(parent, childrenName)
        private val nodes get() = maybeNodes
                ?: throw RuntimeException("No child node(s) '${childrenName}' found in ${parent}")
        override val size: Int get() = maybeNodes?.size ?: 0
        override fun get(index: Int): T = makeIvyNode(nodes[index] as Node)
        override fun add(element: T): Boolean = parent.append(element.node)
        override fun add(index: Int, element: T) = throw NotImplementedError("add at index is not supported")
        override fun remove(element: T): Boolean = parent.remove(element.node)
        override fun removeAt(index: Int): T = throw NotImplementedError("remove at index is not supported")
                // makeIvyNode(nodes.removeAt(index) as Node)
        override fun set(index: Int, element: T): T = throw NotImplementedError("set at index is not supported")
                // makeIvyNode(nodes.set(index, element.node) as Node)
        override fun clear() = parent.children().clear()

        fun appendNode(name: String, attributes: Map<String, String>) =
                parent.appendNode(name, attributes)!!

        init {
            println("New List around ${parent} (${parent.name()})")
            println("  children (${childrenName}): ${maybeNodes?.joinToString()}")
        }
    }

    protected class ChildNode<out T : IvyNode>(
            private val makeIvyNode: (Node) -> T
    ) : ReadOnlyProperty<IvyNode, T> {
        override operator fun getValue(thisRef: IvyNode, property: KProperty<*>): T {
            val childNode = getChildNode(thisRef.node, property.name)
                    ?: throw RuntimeException("No child node(s) '${property.name}' found in ${thisRef.node}")
            return makeIvyNode(childNode)
        }
    }

    protected fun <T : IvyNode> childNode(makeIvyNode: (Node) -> T) = ChildNode(makeIvyNode)

    protected class ListChildNode<T : IvyNode>(
            private val childrenName: String,
            private val makeIvyNode: (Node) -> T
    ) : ReadOnlyProperty<IvyNode, MutableList<T>?> {
        override operator fun getValue(thisRef: IvyNode, property: KProperty<*>): IvyNode.List<T>? {
            val childNode = getChildNode(thisRef.node, property.name)
            return if (childNode == null) null else List(childNode, childrenName, makeIvyNode)
        }
    }

    protected fun <T : IvyNode> listChildNode(name: String, makeIvyNode: (Node) -> T) =
            ListChildNode(name, makeIvyNode)

    protected class NodeAttribute<T> : ReadWriteProperty<IvyNode, T> {
        override operator fun getValue(thisRef: IvyNode, property: KProperty<*>): T {
            @Suppress("UNCHECKED_CAST")
            return thisRef.node.attributes()[property.name] as T
        }

        override operator fun setValue(thisRef: IvyNode, property: KProperty<*>, value: T) {
            thisRef.node.attributes()[property.name] = value
        }
    }

    protected fun <T> nodeAttribute() = NodeAttribute<T>()

    class NodeNamespacedAttribute<T>(private val namespace: Namespace) : ReadWriteProperty<IvyNode, T> {
        override operator fun getValue(thisRef: IvyNode, property: KProperty<*>): T {
            @Suppress("UNCHECKED_CAST")
            return thisRef.node.attributes()[namespace[property.name]] as T
        }

        override operator fun setValue(thisRef: IvyNode, property: KProperty<*>, value: T) {
            thisRef.node.attributes()[namespace[property.name]] = value
        }
    }
}

class IvyModuleNode(node: Node): IvyNode(node) {
    val info: IvyInfoNode by childNode(::IvyInfoNode)
    val configurations: IvyNode.List<IvyConfigurationNode>? by listChildNode("conf", ::IvyConfigurationNode)
    val publications: IvyNode.List<IvyArtifactNode>? by listChildNode("artifact", ::IvyArtifactNode)
    val dependencies: IvyNode.List<IvyDependencyNode>? by listChildNode("dependency", ::IvyDependencyNode)
}

class IvyInfoNode(node: Node) : IvyNode(node) {
    var organisation: String by nodeAttribute()
    var module: String by nodeAttribute()
    var revision: String by nodeAttribute()
}

class IvyConfigurationNode(node: Node) : IvyNode(node) {
    val name: String by nodeAttribute()
    var description: String by nodeAttribute()

    override fun toString(): String = "IvyConfigurationNode{${name}; node=${node}; parent=${node.parent()}}"
}

class IvyArtifactNode(node: Node) : IvyNode(node)

class IvyDependencyNode(node: Node) : IvyNode(node) {
    val org: String by nodeAttribute()
    val name: String by nodeAttribute()
    var rev: String by nodeAttribute()
    val conf: String by nodeAttribute()
}

fun Node.asIvyModule(): IvyModuleNode = IvyModuleNode(this)
