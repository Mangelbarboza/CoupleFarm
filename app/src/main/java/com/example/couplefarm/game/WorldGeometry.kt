package com.example.couplefarm.game

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** A position or direction in world units. This type deliberately has no Android dependency. */
data class Vector2(val x: Float, val y: Float) {
    operator fun plus(other: Vector2) = Vector2(x + other.x, y + other.y)
    operator fun minus(other: Vector2) = Vector2(x - other.x, y - other.y)
    operator fun times(scale: Float) = Vector2(x * scale, y * scale)
    operator fun div(scale: Float) = if (scale == 0f) ZERO else Vector2(x / scale, y / scale)

    fun lengthSquared(): Float = x * x + y * y
    fun length(): Float = sqrt(lengthSquared())
    fun normalized(): Vector2 = length().let { if (it <= 0.0001f) ZERO else this / it }
    fun distanceSquaredTo(other: Vector2): Float = (this - other).lengthSquared()
    fun distanceTo(other: Vector2): Float = sqrt(distanceSquaredTo(other))
    fun dot(other: Vector2): Float = x * other.x + y * other.y
    fun perpendicular() = Vector2(-y, x)
    fun coerceIn(bounds: CollisionRect, margin: Float = 0f) = Vector2(
        x.coerceIn(bounds.left + margin, bounds.right - margin),
        y.coerceIn(bounds.top + margin, bounds.bottom - margin),
    )

    companion object {
        val ZERO = Vector2(0f, 0f)
        val UP = Vector2(0f, -1f)
        val DOWN = Vector2(0f, 1f)
        val LEFT = Vector2(-1f, 0f)
        val RIGHT = Vector2(1f, 0f)
    }
}

/** Collision geometry used for ponds, buildings, trees and actors. */
sealed interface CollisionShape {
    fun contains(point: Vector2): Boolean
    fun intersects(circle: CollisionCircle): Boolean
    fun distanceTo(point: Vector2): Float
    fun nearestPointTo(point: Vector2): Vector2
}

data class CollisionCircle(val center: Vector2, val radius: Float) : CollisionShape {
    override fun contains(point: Vector2): Boolean = center.distanceSquaredTo(point) <= radius * radius

    override fun intersects(circle: CollisionCircle): Boolean {
        val radii = radius + circle.radius
        return center.distanceSquaredTo(circle.center) < radii * radii
    }

    override fun distanceTo(point: Vector2): Float = max(0f, center.distanceTo(point) - radius)

    override fun nearestPointTo(point: Vector2): Vector2 {
        val direction = (point - center).normalized()
        return center + (if (direction == Vector2.ZERO) Vector2.RIGHT else direction) * radius
    }
}

data class CollisionRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) : CollisionShape {
    init {
        require(right >= left) { "right must be greater than or equal to left" }
        require(bottom >= top) { "bottom must be greater than or equal to top" }
    }

    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val center: Vector2 get() = Vector2((left + right) * 0.5f, (top + bottom) * 0.5f)

    override fun contains(point: Vector2): Boolean =
        point.x in left..right && point.y in top..bottom

    override fun intersects(circle: CollisionCircle): Boolean {
        val closestX = circle.center.x.coerceIn(left, right)
        val closestY = circle.center.y.coerceIn(top, bottom)
        val dx = circle.center.x - closestX
        val dy = circle.center.y - closestY
        return dx * dx + dy * dy < circle.radius * circle.radius
    }

    fun intersects(other: CollisionRect): Boolean =
        left < other.right && right > other.left && top < other.bottom && bottom > other.top

    override fun distanceTo(point: Vector2): Float {
        val dx = max(max(left - point.x, 0f), point.x - right)
        val dy = max(max(top - point.y, 0f), point.y - bottom)
        return sqrt(dx * dx + dy * dy)
    }

    override fun nearestPointTo(point: Vector2) = Vector2(
        point.x.coerceIn(left, right),
        point.y.coerceIn(top, bottom),
    )

    fun expanded(amount: Float) = CollisionRect(
        left - amount,
        top - amount,
        right + amount,
        bottom + amount,
    )

    fun inset(amount: Float) = CollisionRect(
        min(left + amount, center.x),
        min(top + amount, center.y),
        max(right - amount, center.x),
        max(bottom - amount, center.y),
    )
}

fun snapToTileCenter(position: Vector2, tileSize: Float): Vector2 {
    val safeSize = tileSize.coerceAtLeast(0.05f)
    fun snap(value: Float): Float = kotlin.math.floor(value / safeSize) * safeSize + safeSize * 0.5f
    return Vector2(snap(position.x), snap(position.y))
}

const val FENCE_TILE_SIZE = 1f

fun shapeCenter(shape: CollisionShape): Vector2 = when (shape) {
    is CollisionCircle -> shape.center
    is CollisionRect -> shape.center
}
