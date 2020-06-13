package com.todoroo.astrid.adapter

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.natpryce.makeiteasy.MakeItEasy.with
import com.natpryce.makeiteasy.PropertyValue
import com.todoroo.astrid.api.GtasksFilter
import com.todoroo.astrid.dao.TaskDao
import com.todoroo.astrid.data.Task
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.tasks.LocalBroadcastManager
import org.tasks.R
import org.tasks.data.CaldavDao
import org.tasks.data.GoogleTaskDao
import org.tasks.data.TaskContainer
import org.tasks.data.TaskListQuery.getQuery
import org.tasks.injection.InjectingTestCase
import org.tasks.injection.TestComponent
import org.tasks.makers.GoogleTaskListMaker.REMOTE_ID
import org.tasks.makers.GoogleTaskListMaker.newGoogleTaskList
import org.tasks.makers.GoogleTaskMaker
import org.tasks.makers.GoogleTaskMaker.LIST
import org.tasks.makers.GoogleTaskMaker.TASK
import org.tasks.makers.GoogleTaskMaker.newGoogleTask
import org.tasks.makers.TaskMaker.PARENT
import org.tasks.makers.TaskMaker.newTask
import org.tasks.preferences.Preferences
import javax.inject.Inject

@RunWith(AndroidJUnit4::class)
class GoogleTaskManualSortAdapterTest : InjectingTestCase() {

    @Inject lateinit var taskDao: TaskDao
    @Inject lateinit var caldavDao: CaldavDao
    @Inject lateinit var googleTaskDao: GoogleTaskDao
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var localBroadcastManager: LocalBroadcastManager

    private lateinit var adapter: GoogleTaskManualSortAdapter
    private val tasks = ArrayList<TaskContainer>()
    private val filter = GtasksFilter(newGoogleTaskList(with(REMOTE_ID, "1234")))
    private val dataSource = object : TaskAdapterDataSource {
        override fun getItem(position: Int) = tasks[position]

        override fun getTaskCount() = tasks.size
    }

    @Test
    fun moveTaskToTopOfList() {
        addTask()
        addTask()
        addTask()

        move(2, 0)

        checkOrder(0, 2)
        checkOrder(1, 0)
        checkOrder(2, 1)
    }

    @Test
    fun moveTaskToBottomOfList() {
        addTask()
        addTask()
        addTask()

        move(0, 2)

        checkOrder(0, 1)
        checkOrder(1, 2)
        checkOrder(2, 0)
    }

    @Test
    fun moveTaskToBottomOfListAsNewSubtask() {
        addTask()
        addTask()
        val parent = addTask()

        move(0, 2, 1)

        checkOrder(0, 1)
        checkOrder(1, 2)
        checkOrder(0, 0, parent)
    }

    @Test
    fun moveTaskToBottomOfListAndSubtask() {
        addTask()
        val parent = addTask()
        addTask(with(PARENT, parent))

        move(0, 2, 1)

        checkOrder(0, 1)
        checkOrder(0, 2, parent)
        checkOrder(1, 0, parent)
    }

    @Test
    fun moveTaskToMiddleOfList() {
        addTask()
        addTask()
        addTask()

        move(0, 1)

        checkOrder(0, 1)
        checkOrder(1, 0)
        checkOrder(2, 2)
    }

    @Test
    fun moveTaskDownAsNewSubtask() {
        addTask()
        val parent = addTask()
        addTask()

        move(0, 1, 1)

        checkOrder(0, 1)
        checkOrder(0, 0, parent)
        checkOrder(1, 2)
    }

    @Test
    fun moveTaskDownToFrontOfSubtasks() {
        addTask()
        val parent = addTask()
        addTask(with(PARENT, parent))

        move(0, 1, 1)

        checkOrder(0, 1)
        checkOrder(0, 0, parent)
        checkOrder(1, 2, parent)
    }

    @Test
    fun moveTaskDownToMiddleOfSubtasks() {
        addTask()
        val parent = addTask()
        addTask(with(PARENT, parent))
        addTask(with(PARENT, parent))

        move(0, 2, 1)

        checkOrder(0, 1)
        checkOrder(0, 2, parent)
        checkOrder(1, 0, parent)
        checkOrder(2, 3, parent)
    }

    @Test
    fun moveTaskDownToEndOfSubtasks() {
        addTask()
        val parent = addTask()
        addTask(with(PARENT, parent))
        addTask()

        move(0, 2, 1)

        checkOrder(0, 1)
        checkOrder(0, 2, parent)
        checkOrder(1, 0, parent)
        checkOrder(1, 3)
    }

    @Test
    fun moveTaskUpAsNewSubtask() {
        val parent = addTask()
        addTask()
        addTask()

        move(2, 1, 1)

        checkOrder(0, 0)
        checkOrder(0, 2, parent)
        checkOrder(1, 1)
    }

    @Test
    fun moveTaskUpToFrontOfSubtasks() {
        val parent = addTask()
        addTask(with(PARENT, parent))
        addTask()

        move(2, 1, 1)

        checkOrder(0, 0)
        checkOrder(0, 2, parent)
        checkOrder(1, 1, parent)
    }

    @Test
    fun moveTaskUpToMiddleOfSubtasks() {
        val parent = addTask()
        addTask(with(PARENT, parent))
        addTask(with(PARENT, parent))
        addTask()

        move(3, 2, 1)

        checkOrder(0, 0)
        checkOrder(0, 1, parent)
        checkOrder(1, 3, parent)
        checkOrder(2, 2, parent)
    }

    @Test
    fun moveTaskUpToEndOfSubtasks() {
        val parent = addTask()
        addTask(with(PARENT, parent))
        addTask()
        addTask()

        move(3, 2, 1)

        checkOrder(0, 0)
        checkOrder(0, 1, parent)
        checkOrder(1, 3, parent)
        checkOrder(1, 2)
    }

    @Test
    fun indentTask() {
        val parent = addTask()
        addTask()
        addTask()

        move(1, 1, 1)

        checkOrder(0, 0)
        checkOrder(0, 1, parent)
        checkOrder(1, 2)
    }

    @Test
    fun moveSubtaskFromTopToBottom() {
        val parent = addTask()
        addTask(with(PARENT, parent))
        addTask(with(PARENT, parent))
        addTask(with(PARENT, parent))
        addTask()

        move(1, 3, 1)

        checkOrder(0, 0)
        checkOrder(0, 2, parent)
        checkOrder(1, 3, parent)
        checkOrder(2, 1, parent)
    }

    @Test
    fun moveSubtaskFromTopToBottomAtEndOfList() {
        val parent = addTask()
        addTask(with(PARENT, parent))
        addTask(with(PARENT, parent))
        addTask(with(PARENT, parent))

        move(1, 3, 1)

        checkOrder(0, 0)
        checkOrder(0, 2, parent)
        checkOrder(1, 3, parent)
        checkOrder(2, 1, parent)
    }

    @Test
    fun moveSubtaskFromBottomToTop() {
        val parent = addTask()
        addTask(with(PARENT, parent))
        addTask(with(PARENT, parent))
        addTask(with(PARENT, parent))

        move(3, 1, 1)

        checkOrder(0, 0)
        checkOrder(0, 3, parent)
        checkOrder(1, 1, parent)
        checkOrder(2, 2, parent)
    }

    @Test
    fun moveSubtaskFromTopToMiddle() {
        val parent = addTask()
        addTask(with(PARENT, parent))
        addTask(with(PARENT, parent))
        addTask(with(PARENT, parent))

        move(1, 2, 1)

        checkOrder(0, 0)
        checkOrder(0, 2, parent)
        checkOrder(1, 1, parent)
        checkOrder(2, 3, parent)
    }

    @Test
    fun moveSubtaskFromBottomToMiddle() {
        val parent = addTask()
        addTask(with(PARENT, parent))
        addTask(with(PARENT, parent))
        addTask(with(PARENT, parent))

        move(3, 2, 1)

        checkOrder(0, 0)
        checkOrder(0, 1, parent)
        checkOrder(1, 3, parent)
        checkOrder(2, 2, parent)
    }

    @Test
    fun moveSubtaskFromMiddleToTop() {
        val parent = addTask()
        addTask(with(PARENT, parent))
        addTask(with(PARENT, parent))
        addTask(with(PARENT, parent))

        move(2, 1, 1)

        checkOrder(0, 0)
        checkOrder(0, 2, parent)
        checkOrder(1, 1, parent)
        checkOrder(2, 3, parent)
    }

    @Test
    fun moveSubtaskFromMiddleToBottom() {
        val parent = addTask()
        addTask(with(PARENT, parent))
        addTask(with(PARENT, parent))
        addTask(with(PARENT, parent))

        move(2, 3, 1)

        checkOrder(0, 0)
        checkOrder(0, 1, parent)
        checkOrder(1, 3, parent)
        checkOrder(2, 2, parent)
    }

    @Test
    fun moveSubtaskUpToTopLevel() {
        addTask()
        val parent = addTask()
        addTask(with(PARENT, parent))
        addTask(with(PARENT, parent))
        addTask(with(PARENT, parent))

        move(3, 1, 0)

        checkOrder(0, 0)
        checkOrder(1, 3)
        checkOrder(2, 1)
        checkOrder(0, 2, parent)
        checkOrder(1, 4, parent)
    }

    @Test
    fun moveSubtaskDownToTopLevel() {
        val parent = addTask()
        addTask(with(PARENT, parent))
        addTask(with(PARENT, parent))
        addTask(with(PARENT, parent))
        addTask()
        addTask()

        move(2, 4, 0)

        checkOrder(0, 0)
        checkOrder(0, 1, parent)
        checkOrder(1, 3, parent)
        checkOrder(1, 4)
        checkOrder(2, 2)
        checkOrder(3, 5)
    }
    
    @Test
    fun moveSubtaskToEndOfListAndDeindent() {
        val parent = addTask()
        addTask(with(PARENT, parent))
        addTask(with(PARENT, parent))
        addTask(with(PARENT, parent))
        addTask()

        move(2, 3, 0)

        checkOrder(0, 0)
        checkOrder(0, 1, parent)
        checkOrder(1, 3, parent)
        checkOrder(1, 2)
        checkOrder(2, 4)
    }

    @Test
    fun deindentTask() {
        val parent = addTask()
        addTask(with(PARENT, parent))
        addTask()

        move(1, 1, 0)

        checkOrder(0, 0)
        checkOrder(1, 1)
        checkOrder(2, 2)
    }

    @Before
    override fun setUp() {
        super.setUp()
        preferences.clear()
        preferences.setBoolean(R.string.p_manual_sort, true)
        tasks.clear()
        adapter = GoogleTaskManualSortAdapter(googleTaskDao, caldavDao, taskDao, localBroadcastManager)
        adapter.setDataSource(dataSource)
    }

    private fun move(from: Int, to: Int, indent: Int = 0) {
        tasks.addAll(taskDao.fetchTasks { getQuery(preferences, filter, it) })
        val adjustedTo = if (from < to) to + 1 else to
        adapter.moved(from, adjustedTo, indent)
    }

    private fun checkOrder(order: Long, index: Int, parent: Long = 0) {
        val googleTask = googleTaskDao.getByTaskId(adapter.getTask(index).id)!!
        assertEquals(order, googleTask.order)
        assertEquals(parent, googleTask.parent)
    }

    private fun addTask(vararg properties: PropertyValue<in Task?, *>): Long {
        val task = newTask(*properties)
        val parent = task.parent
        task.parent = 0
        taskDao.createNew(task)
        googleTaskDao.insertAndShift(
                newGoogleTask(
                        with(TASK, task.id),
                        with(LIST, "1234"),
                        with(GoogleTaskMaker.PARENT, parent)),
                false)
        return task.id
    }

    override fun inject(component: TestComponent) = component.inject(this)
}