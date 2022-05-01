package ru.neosvet.utils

class NeoList<T> {
    data class Node<T>(
        val item: T,
        var next: Node<T>? = null
    )

    private var first: Node<T>? = null
    private var current: Node<T>? = null

    fun reset() {
        current = first
    }

    fun add(elem: T) {
        if (first == null) {
            first = Node(elem)
            current = first
        } else {
            val node = Node(elem)
            current!!.next = node
            current = node
        }
    }

    fun first(): T = first!!.item

    fun current(): T = current!!.item

    fun next(): Boolean {
        if (current?.next == null)
            return false
        current = current?.next
        return true
    }

    fun clear() {
        current = null
        while (first != null)
            first = first?.next
    }

    fun isNotEmpty(): Boolean = current != null
}