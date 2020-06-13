package com.todoroo.andlib.sql

abstract class Criterion(val operator: Operator) {

    protected abstract fun populate(): String

    override fun toString() = "(${populate()})"

    companion object {
        @JvmStatic fun and(criterion: Criterion?, vararg criterions: Criterion?): Criterion {
            return object : Criterion(Operator.and) {
                override fun populate() = criterion.plus(criterions).joinToString(" AND ")
            }
        }

        @JvmStatic fun or(criterion: Criterion?, vararg criterions: Criterion): Criterion {
            return object : Criterion(Operator.or) {
                override fun populate() = criterion.plus(criterions).joinToString(" OR ")
            }
        }

        operator fun <T> T.plus(tail: Array<out T>): List<T> {
            val list = ArrayList<T>(1 + tail.size)

            list.add(this)
            list.addAll(tail)

            return list
        }
    }
}