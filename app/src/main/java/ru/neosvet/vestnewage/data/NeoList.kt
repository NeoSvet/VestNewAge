package ru.neosvet.vestnewage.data

class NeoList<T> : Iterator<T> {
    data class Node<T>(
        val item: T,
        var next: Node<T>? = null
    )

    private var first: Node<T>? = null
    private var current: Node<T>? = null
    private var isFirst = true

    fun reset(forIterator: Boolean) {
        isFirst = forIterator
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

    override fun hasNext(): Boolean {
        if (isFirst) return first != null
        return current?.next != null
    }

    override fun next(): T {
        if (isFirst) {
            isFirst = false
            return first()
        }
        current = current?.next
        return current()
    }

    fun clear() {
        current = null
        while (first != null)
            first = first?.next
    }

    fun isNotEmpty(): Boolean = current != null
}