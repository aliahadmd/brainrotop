package com.aliahad.brainrotop.detector

data class NodeSnapshot(
    val packageName: String? = null,
    val className: String? = null,
    val text: String? = null,
    val contentDescription: String? = null,
    val viewIdResourceName: String? = null,
    val children: List<NodeSnapshot> = emptyList(),
) {
    fun flatten(): Sequence<NodeSnapshot> = sequence {
        yield(this@NodeSnapshot)
        children.forEach { yieldAll(it.flatten()) }
    }
}

