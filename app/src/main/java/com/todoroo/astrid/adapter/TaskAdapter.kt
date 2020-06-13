/*
 * Copyright (c) 2012 Todoroo Inc
 *
 * See the file "LICENSE" for the full license governing this code.
 */
package com.todoroo.astrid.adapter

import com.todoroo.astrid.core.SortHelper.SORT_DUE
import com.todoroo.astrid.core.SortHelper.SORT_IMPORTANCE
import com.todoroo.astrid.dao.TaskDao
import com.todoroo.astrid.data.Task
import org.tasks.BuildConfig
import org.tasks.LocalBroadcastManager
import org.tasks.data.*
import org.tasks.date.DateTimeUtils.toAppleEpoch
import org.tasks.date.DateTimeUtils.toDateTime
import java.util.*
import kotlin.collections.HashSet

open class TaskAdapter(
        private val newTasksOnTop: Boolean,
        private val googleTaskDao: GoogleTaskDao,
        private val caldavDao: CaldavDao,
        private val taskDao: TaskDao,
        private val localBroadcastManager: LocalBroadcastManager) {

    private val selected = HashSet<Long>()
    private val collapsed = HashSet<Long>()
    private lateinit var dataSource: TaskAdapterDataSource

    val count: Int
        get() = dataSource.getTaskCount()

    fun setDataSource(dataSource: TaskAdapterDataSource) {
        this.dataSource = dataSource
    }

    val numSelected: Int
        get() = selected.size

    fun getSelected(): ArrayList<Long> = ArrayList(selected)

    fun setSelected(ids: Collection<Long>) {
        clearSelections()
        selected.addAll(ids)
    }

    fun clearSelections() = selected.clear()

    fun getCollapsed(): MutableSet<Long> = HashSet(collapsed)

    fun setCollapsed(groups: LongArray?) {
        clearCollapsed()
        groups?.toList()?.let(collapsed::addAll)
    }

    fun clearCollapsed() = collapsed.clear()

    open fun getIndent(task: TaskContainer): Int = task.getIndent()

    open fun canMove(source: TaskContainer, from: Int, target: TaskContainer, to: Int): Boolean {
        if (target.isGoogleTask) {
            return if (!source.hasChildren() || to <= 0 || to >= count - 1) {
                true
            } else if (from < to) {
                when {
                    target.hasChildren() -> false
                    target.hasParent() -> !getTask(to + 1).hasParent()
                    else -> true
                }
            } else {
                when {
                    target.hasChildren() -> true
                    target.hasParent() -> target.parent == source.id && target.secondarySort == 0L
                    else -> true
                }
            }
        } else {
            return !taskIsChild(source, to)
        }
    }

    open fun maxIndent(previousPosition: Int, task: TaskContainer): Int {
        val previous = getTask(previousPosition)
        return if (previous.isGoogleTask) {
            if (task.hasChildren()) 0 else 1
        } else {
            previous.indent + 1
        }
    }

    fun minIndent(nextPosition: Int, task: TaskContainer): Int {
        (nextPosition until count).forEach {
            if (isHeader(it)) {
                return 0
            }
            val next = getTask(it)
            if (next.isGoogleTask) {
                return if (task.hasChildren() || !next.hasParent()) 0 else 1
            }
            if (!taskIsChild(task, it)) {
                return next.indent
            }
        }
        return 0
    }

    fun isSelected(task: TaskContainer): Boolean = selected.contains(task.id)

    fun toggleSelection(task: TaskContainer) {
        val id = task.id
        if (selected.contains(id)) {
            selected.remove(id)
        } else {
            selected.add(id)
        }
    }

    fun toggleCollapsed(group: Long) {
        if (collapsed.contains(group)) {
            collapsed.remove(group)
        } else {
            collapsed.add(group)
        }
    }

    open fun supportsAstridSorting(): Boolean = false

    open fun moved(from: Int, to: Int, indent: Int) {
        val task = getTask(from)
        val newParent = findParent(indent, to)
        if (newParent?.id ?: 0 == task.parent) {
            if (indent == 0) {
                changeSortGroup(task, if (from < to) to - 1 else to)
            }
            return
        } else if (newParent != null) {
            when {
                task.isGoogleTask -> if (task.googleTaskList != newParent.googleTaskList) {
                    googleTaskDao.markDeleted(task.id)
                    task.googletask = null
                }
                task.isCaldavTask -> if (task.caldav != newParent.caldav) {
                    caldavDao.markDeleted(listOf(task.id))
                    task.caldavTask = null
                }
            }
        }
        when {
            newParent == null -> {
                moveToTopLevel(task)
                changeSortGroup(task, if (from < to) to - 1 else to)
            }
            newParent.isGoogleTask -> changeGoogleTaskParent(task, newParent)
            newParent.isCaldavTask -> changeCaldavParent(task, newParent)
        }
    }

    fun isHeader(position: Int): Boolean = dataSource.isHeader(position)

    fun getTask(position: Int): TaskContainer = dataSource.getItem(position)!!

    fun getItemUuid(position: Int): String = getTask(position).uuid

    open fun onCompletedTask(task: TaskContainer, newState: Boolean) {}

    open fun onTaskCreated(uuid: String) {}

    open fun onTaskDeleted(task: Task) {}

    open fun supportsHiddenTasks(): Boolean = true

    private fun taskIsChild(source: TaskContainer, destinationIndex: Int): Boolean {
        (destinationIndex downTo 0).forEach {
            if (isHeader(it)) {
                return false
            }
            when (getTask(it).parent) {
                0L -> return false
                source.parent -> return false
                source.id -> return true
            }
        }
        return false
    }

    internal fun findParent(indent: Int, to: Int): TaskContainer? {
        if (indent == 0 || to == 0) {
            return null
        }
        for (i in to - 1 downTo 0) {
            val previous = getTask(i)
            if (indent > previous.getIndent()) {
                return previous
            }
        }
        return null
    }

    private fun changeSortGroup(task: TaskContainer, pos: Int) {
        when(dataSource.sortMode) {
            SORT_IMPORTANCE -> {
                val newPriority = dataSource.nearestHeader(if (pos == 0) 1 else pos).toInt()
                if (newPriority != task.priority) {
                    val t = task.getTask()
                    t.priority = newPriority
                    taskDao.save(t)
                }
            }
            SORT_DUE -> applyDate(task.task, dataSource.nearestHeader(if (pos == 0) 1 else pos))
        }
    }

    private fun applyDate(task: Task, date: Long) {
        val original = task.dueDate
        task.setDueDateAdjustingHideUntil(if (date == 0L) {
            0L
        } else {
            date.toDateTime().withMillisOfDay(task.dueDate.toDateTime().millisOfDay).millis
        })
        if (original != task.dueDate) {
            taskDao.save(task)
        }
    }

    private fun moveToTopLevel(task: TaskContainer) {
        when {
            task.isGoogleTask -> changeGoogleTaskParent(task, null)
            task.isCaldavTask -> changeCaldavParent(task, null)
        }
    }

    private fun changeGoogleTaskParent(task: TaskContainer, newParent: TaskContainer?) {
        val list = newParent?.googleTaskList ?: task.googleTaskList!!
        if (newParent == null || task.googleTaskList == newParent.googleTaskList) {
            googleTaskDao.move(
                    task.googleTask,
                    newParent?.id ?: 0,
                    if (newTasksOnTop) 0 else googleTaskDao.getBottom(list, newParent?.id ?: 0))
        } else {
            val googleTask = GoogleTask(task.id, list)
            googleTask.parent = newParent.id
            googleTaskDao.insertAndShift(googleTask, newTasksOnTop)
            task.googletask = SubsetGoogleTask().apply {
                gt_id = googleTask.id
                gt_list_id = googleTask.listId
                gt_order = googleTask.order
                gt_parent = googleTask.parent
            }
        }
        taskDao.touch(task.id)
        if (BuildConfig.DEBUG) {
            googleTaskDao.validateSorting(list)
        }
    }

    private fun changeCaldavParent(task: TaskContainer, newParent: TaskContainer?) {
        val list = newParent?.caldav ?: task.caldav!!
        val caldavTask = task.getCaldavTask() ?: SubsetCaldav()
        val newParentId = newParent?.id ?: 0
        if (newParentId == 0L) {
            caldavTask.cd_remote_parent = ""
        } else {
            val parentTask = caldavDao.getTask(newParentId) ?: return
            caldavTask.cd_calendar = list
            caldavTask.cd_remote_parent = parentTask.remoteId
        }
        caldavTask.cd_order = if (newTasksOnTop) {
            caldavDao.findFirstTask(list, newParentId)
                    ?.takeIf { task.creationDate.toAppleEpoch() >= it}
                    ?.minus(1)
        } else {
            caldavDao.findLastTask(list, newParentId)
                    ?.takeIf { task.creationDate.toAppleEpoch() <= it }
                    ?.plus(1)
        }
        if (caldavTask.cd_id == 0L) {
            val newTask = CaldavTask(task.id, list)
            newTask.order = caldavTask.cd_order
            newTask.remoteParent = caldavTask.cd_remote_parent
            caldavTask.cd_id = caldavDao.insert(newTask)
            task.caldavTask = caldavTask
        } else {
            caldavDao.update(caldavTask)
        }
        taskDao.setParent(newParentId, listOf(task.id))
        taskDao.touch(task.id)
        localBroadcastManager.broadcastRefresh()
    }
}