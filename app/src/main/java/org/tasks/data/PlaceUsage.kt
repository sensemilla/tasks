package org.tasks.data

import androidx.room.Embedded

class PlaceUsage {
    @Embedded lateinit var place: Place
    var count = 0

    val color: Int
        get() = place.color

    val icon: Int
        get() = place.getIcon()!!

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PlaceUsage) return false

        if (place != other.place) return false
        if (count != other.count) return false

        return true
    }

    override fun hashCode(): Int {
        var result = place.hashCode()
        result = 31 * result + count
        return result
    }

    override fun toString(): String {
        return "PlaceUsage(place=$place, count=$count)"
    }
}