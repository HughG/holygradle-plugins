package holygradle.apache.ivy.groovy

import groovy.util.Node
import groovy.util.NodeList
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Copyright (c) 2018 Hugh Greene (githugh@tameter.org).
 */

private fun getChildNodes(node: Node, name: String): NodeList {
    return node[name] as? NodeList
            ?: throw RuntimeException("No child node(s) '${name}' found in ${node}")
}

private fun getChildNode(node: Node, name: String): Node {
    val childNodes = getChildNodes(node, name)
    val childCount = childNodes.size
    if (childCount != 1) {
        throw RuntimeException("Expected exactly 1 child node '${name}' but found ${childCount} in ${node}")
    }
    return childNodes[0] as Node
}

sealed class IvyNode(protected val node: Node) {
    class List<T : IvyNode>(
            private val parent: Node,
            childrenName: String,
            private val makeIvyNode: (Node) -> T
    ): AbstractMutableList<T>() {
        private val nodes = getChildNodes(parent, childrenName)
        override val size: Int get() = nodes.size
        override fun get(index: Int): T = makeIvyNode(nodes[index] as Node)
        override fun add(index: Int, element: T) = nodes.add(index, element.node)
        override fun removeAt(index: Int): T = makeIvyNode(nodes.removeAt(index) as Node)
        override fun set(index: Int, element: T): T = makeIvyNode(nodes.set(index, element.node) as Node)

        fun appendNode(name: String, attrbiutes: Map<String, String>) =
                parent.appendNode(name, attrbiutes)
    }

    protected class ChildNodes<T : IvyNode>(
            private val makeIvyNode: (Node) -> T
    ) : ReadOnlyProperty<IvyNode, MutableList<T>> {
        override operator fun getValue(thisRef: IvyNode, property: KProperty<*>): MutableList<T> {
            return List(thisRef.node, property.name, makeIvyNode)
        }

//    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
//        println("$value has been assigned to '${property.name}' in $thisRef.")
//    }
    }

    protected fun <T : IvyNode> childNodes(makeIvyNode: (Node) -> T) = ChildNodes(makeIvyNode)
    //protected inline fun <reified T : IvyNode> childNodes() = ChildNodes<T>({n -> T(n)})

    protected class ChildNode<out T : IvyNode>(
            private val makeIvyNode: (Node) -> T
    ) : ReadOnlyProperty<IvyNode, T> {
        override operator fun getValue(thisRef: IvyNode, property: KProperty<*>): T {
            return makeIvyNode(getChildNode(thisRef.node, property.name))
        }

//    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
//        println("$value has been assigned to '${property.name}' in $thisRef.")
//    }
    }

    protected fun <T : IvyNode> childNode(makeIvyNode: (Node) -> T) = ChildNode(makeIvyNode)

    protected class ListChildNode<T : IvyNode>(
            private val childrenName: String,
            private val makeIvyNode: (Node) -> T
    ) : ReadOnlyProperty<IvyNode, MutableList<T>> {
        override operator fun getValue(thisRef: IvyNode, property: KProperty<*>): IvyNode.List<T> {
            val childNode = getChildNode(thisRef.node, property.name)
            return List(childNode, childrenName, makeIvyNode)
        }

//    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
//        println("$value has been assigned to '${property.name}' in $thisRef.")
//    }
    }

    protected fun <T : IvyNode> listChildNode(name: String, makeIvyNode: (Node) -> T) =
            ListChildNode<T>(name, makeIvyNode)

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
}

class IvyModuleNode(node: Node): IvyNode(node) {
    val configurations: IvyNode.List<IvyConfigurationNode> by listChildNode("conf", ::IvyConfigurationNode)
    val dependencies: IvyNode.List<IvyDependencyNode> by listChildNode("dependency", ::IvyDependencyNode)
}

class IvyConfigurationNode(node: Node) : IvyNode(node) {
    val name: String by nodeAttribute()
}

class IvyDependencyNode(node: Node) : IvyNode(node) {
    val org: String by nodeAttribute()
    val name: String by nodeAttribute()
    var rev: String by nodeAttribute()
    val conf: String by nodeAttribute()
}

fun Node.asIvyModule(): IvyModuleNode = IvyModuleNode(this)