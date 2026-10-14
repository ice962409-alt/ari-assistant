package com.ari.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AriBubbleEdgeTest {

    private val screen = 1080
    private val size = 150

    @Test
    fun peekLeavesPartOfTheBubbleVisible() {
        val peek = AriBubble.edgePeek(size)
        assertTrue(peek in 1 until size)
    }

    @Test
    fun snapsToTheNearestEdge() {
        val peek = AriBubble.edgePeek(size)
        // Cerca de la izquierda → se esconde por la izquierda (x negativa, solo asoma el peek).
        val (leftX, left) = AriBubble.collapsedX(100, screen, size, peek)
        assertTrue(left)
        assertEquals(-(size - peek), leftX)
        // Cerca de la derecha → se esconde por la derecha.
        val (rightX, right) = AriBubble.collapsedX(1000, screen, size, peek)
        assertFalse(right)
        assertEquals(screen - peek, rightX)
    }

    @Test
    fun expandingBringsItFullyOnScreen() {
        assertEquals(0, AriBubble.expandedX(true, screen, size))
        assertEquals(screen - size, AriBubble.expandedX(false, screen, size))
    }

    @Test
    fun collapsedBubbleIsMostlyOffScreen() {
        val peek = AriBubble.edgePeek(size)
        val (leftX, _) = AriBubble.collapsedX(10, screen, size, peek)
        // Solo asoma el peek por la izquierda.
        assertEquals(peek, leftX + size)
    }
}
