package org.kosat.heuristics

import org.kosat.swap

class PriorityQueue(val activity: MutableList<Double>) {
    // stores max-heap built on variable activities (contains variables)
    val heap: MutableList<Int> = mutableListOf()

    // for each variable contains index with it position in heap
    val index: MutableList<Int> = mutableListOf()

    // maximum possible size of heap
    private var capacity = -1

    // current size
    var size = 0

    // compares variables by activity
    private fun cmp(u: Int, v: Int): Boolean {
        if (activity[u] > activity[v]) {
            return true
        }
        if (activity[u] < activity[v]) {
            return false
        }
        return u < v
    }

    private fun leftChild(u: Int): Int {
        return 2 * u + 1
    }

    private fun rightChild(u: Int): Int {
        return 2 * u + 2
    }

    private fun parent(u: Int): Int {
        return (u - 1) / 2
    }

    private fun swap(u: Int, v: Int) {
        heap.swap(u, v)
        index[heap[u]] = u
        index[heap[v]] = v
    }

    fun siftUp(u: Int) {
        val vertex = heap[u]
        var curInd = u
        var parent = parent(curInd)

        while (curInd > 0 && cmp(vertex, heap[parent])) {
            heap[curInd] = heap[parent]
            index[heap[curInd]] = curInd
            curInd = parent
            parent = parent(curInd)
        }
        heap[curInd] = vertex
        index[vertex] = curInd
    }

    // if some value of vertex decreased this function lift this vertex down to save heap structure
    private fun siftDown(u: Int) {
        val vertex = heap[u]
        var curInd = u

        var ls: Int
        var rs: Int
        var leftVertex: Int
        var rightVertex: Int

        while (leftChild(curInd) < size) {
            ls = leftChild(curInd)
            rs = rightChild(curInd)
            leftVertex = if (ls > size - 1) -1 else heap[ls]
            rightVertex = if (rs > size - 1) -1 else heap[rs]

            if (rs > size - 1) {
                if (cmp(leftVertex, vertex)) {
                    heap[curInd] = leftVertex
                    index[leftVertex] = curInd
                    curInd = ls
                } else {
                    break
                }
            } else if (cmp(leftVertex, rightVertex)) {
                if (cmp(leftVertex, vertex)) {
                    heap[curInd] = leftVertex
                    index[leftVertex] = curInd
                    curInd = ls
                } else {
                    break
                }
            } else if (cmp(rightVertex, vertex)) {
                heap[curInd] = rightVertex
                index[rightVertex] = curInd
                curInd = rs
            } else {
                break
            }
        }
        heap[curInd] = vertex
        index[vertex] = curInd
    }

    // returns element on top of heap
    fun top(): Int {
        require(size != 0)
        return heap[0]
    }

    // delete element on top of heap and returns it
    fun pop(): Int {
        require(size != 0)
        val max = top()
        swap(0, size - 1)
        index[heap[size - 1]] = -1
        size--
        if (heap.isNotEmpty()) {
            siftDown(0)
        }
        return max
    }


    fun insert(value: Int) {
        require(size != capacity)
        heap[size] = value
        index[value] = size
        size++
        siftUp(size - 1)
    }

    fun buildHeap(activity: MutableList<Double>) {
        for (ind in 0..activity.lastIndex) {
            heap.add(ind)
        }
        size = heap.size
        while (index.size < size) {
            index.add(0)
        }
        heap.forEachIndexed { ind, elem ->
            index[elem] = ind
        }
        for (ind in (heap.size / 2) downTo 0) {
            siftDown(ind)
        }
        capacity = size
    }
}
