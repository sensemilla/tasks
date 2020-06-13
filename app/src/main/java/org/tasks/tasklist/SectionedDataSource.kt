package org.tasks.tasklist

import android.util.SparseArray
import com.todoroo.astrid.core.SortHelper
import org.tasks.data.TaskContainer
import org.tasks.date.DateTimeUtils
import java.util.*

class SectionedDataSource constructor(tasks: List<TaskContainer>, disableHeaders: Boolean, val sortMode: Int, private val collapsed: MutableSet<Long>) {

    private val tasks = tasks.toMutableList()

    private val sections = if (disableHeaders) {
        SparseArray()
    } else {
        getSections()
    }

    fun getItem(position: Int): TaskContainer = tasks[sectionedPositionToPosition(position)]

    fun getHeaderValue(position: Int): Long = sections[position]!!.value

    fun isHeader(position: Int) = sections[position] != null

    private fun sectionedPositionToPosition(sectionedPosition: Int): Int {
        if (isHeader(sectionedPosition)) {
            return sections[sectionedPosition].firstPosition
        }

        var offset = 0
        for (i in 0 until sections.size()) {
            val section = sections.valueAt(i)
            if (section.sectionedPosition > sectionedPosition) {
                break
            }
            --offset
        }
        return sectionedPosition + offset
    }

    val taskCount: Int
        get() = tasks.size

    val size: Int
        get() = tasks.size + sections.size()

    fun getSection(position: Int): AdapterSection = sections[position]

    fun add(position: Int, task: TaskContainer) = tasks.add(sectionedPositionToPosition(position), task)

    fun removeAt(position: Int): TaskContainer = tasks.removeAt(sectionedPositionToPosition(position))

    private fun getSections(): SparseArray<AdapterSection> {
        val sections = ArrayList<AdapterSection>()
        for (i in tasks.indices) {
            val task = tasks[i]
            val sortGroup = task.sortGroup ?: continue
            val header = if (sortMode == SortHelper.SORT_IMPORTANCE || sortGroup == 0L) {
                sortGroup
            } else {
                DateTimeUtils.newDateTime(sortGroup).startOfDay().millis
            }
            val isCollapsed = collapsed.contains(header)
            if (i == 0) {
                sections.add(AdapterSection(i, header, 0, isCollapsed))
            } else {
                val previous = tasks[i - 1].sortGroup
                when (sortMode) {
                    SortHelper.SORT_IMPORTANCE -> if (header != previous) {
                        sections.add(AdapterSection(i, header, 0, isCollapsed))
                    }
                    else -> if (previous > 0 && header != DateTimeUtils.newDateTime(previous).startOfDay().millis) {
                        sections.add(AdapterSection(i, header, 0, isCollapsed))
                    }
                }
            }
        }

        var adjustment = 0
        for (i in sections.indices) {
            val section = sections[i]
            section.firstPosition -= adjustment
            if (section.collapsed) {
                val next = sections.getOrNull(i + 1)?.firstPosition?.minus(adjustment) ?: tasks.size
                tasks.subList(section.firstPosition, next).clear()
                adjustment += next - section.firstPosition
            }
        }

        return setSections(sections)
    }

    private fun setSections(newSections: List<AdapterSection>): SparseArray<AdapterSection> {
        val sections = SparseArray<AdapterSection>()
        newSections.forEachIndexed { index, section ->
            section.sectionedPosition = section.firstPosition + index
            sections.append(section.sectionedPosition, section)
        }
        return sections
    }

    fun moveSection(toPosition: Int, offset: Int) {
        val old = sections[toPosition]
        sections.remove(toPosition)
        val newSectionedPosition = old.sectionedPosition + offset
        val previousSection = if (isHeader(newSectionedPosition - 1)) sections[newSectionedPosition - 1] else null
        val newFirstPosition = previousSection?.firstPosition ?: old.firstPosition + offset
        val new = AdapterSection(newFirstPosition, old.value, newSectionedPosition, old.collapsed)
        sections.append(new.sectionedPosition, new)
    }

    tailrec fun getNearestHeader(sectionedPosition: Int): Long = if (isHeader(sectionedPosition)) {
        getHeaderValue(sectionedPosition)
    } else {
        getNearestHeader(sectionedPosition - 1)
    }
}