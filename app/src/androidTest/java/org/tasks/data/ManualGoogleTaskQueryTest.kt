package org.tasks.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.natpryce.makeiteasy.MakeItEasy.with
import com.todoroo.astrid.api.GtasksFilter
import com.todoroo.astrid.dao.TaskDao
import com.todoroo.astrid.helper.UUIDHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.tasks.R
import org.tasks.injection.InjectingTestCase
import org.tasks.injection.TestComponent
import org.tasks.makers.GoogleTaskListMaker.REMOTE_ID
import org.tasks.makers.GoogleTaskListMaker.newGoogleTaskList
import org.tasks.makers.GoogleTaskMaker.LIST
import org.tasks.makers.GoogleTaskMaker.ORDER
import org.tasks.makers.GoogleTaskMaker.PARENT
import org.tasks.makers.GoogleTaskMaker.TASK
import org.tasks.makers.GoogleTaskMaker.newGoogleTask
import org.tasks.makers.TaskMaker
import org.tasks.makers.TaskMaker.ID
import org.tasks.makers.TaskMaker.UUID
import org.tasks.preferences.Preferences
import javax.inject.Inject

@RunWith(AndroidJUnit4::class)
class ManualGoogleTaskQueryTest : InjectingTestCase() {

    @Inject lateinit var googleTaskDao: GoogleTaskDao
    @Inject lateinit var taskDao: TaskDao
    @Inject lateinit var preferences: Preferences
    private val filter: GtasksFilter = GtasksFilter(newGoogleTaskList(with(REMOTE_ID, "1234")))

    @Before
    override fun setUp() {
        super.setUp()
        preferences.clear()
        preferences.setBoolean(R.string.p_manual_sort, true)
    }

    @Test
    fun setIndentOnSubtask() {
        newTask(1, 0, 0)
        newTask(2, 0, 1)

        val subtask = query()[1]

        assertEquals(1, subtask.indent)
    }

    @Test
    fun setParentOnSubtask() {
        newTask(2, 0, 0)
        newTask(1, 0, 2)

        val subtask = query()[1]

        assertEquals(2, subtask.parent)
    }

    @Test
    fun querySetsPrimarySort() {
        newTask(1, 0, 0)
        newTask(2, 1, 0)
        newTask(3, 0, 2)

        val subtasks = query()

        assertEquals(0, subtasks[0].primarySort)
        assertEquals(1, subtasks[1].primarySort)
        assertEquals(1, subtasks[2].primarySort)
    }

    @Test
    fun querySetsSecondarySortOnSubtasks() {
        newTask(1, 0, 0)
        newTask(2, 0, 1)
        newTask(3, 1, 1)

        val subtasks = query()

        assertEquals(0, subtasks[0].secondarySort)
        assertEquals(0, subtasks[1].secondarySort)
        assertEquals(1, subtasks[2].secondarySort)
    }

    @Test
    fun ignoreDisableSubtasksPreference() {
        preferences.setBoolean(R.string.p_use_paged_queries, true)
        newTask(1, 0, 0)
        newTask(2, 0, 1)

        val parent = query()[0]

        assertTrue(parent.hasChildren())
    }

    private fun newTask(id: Long, order: Long, parent: Long = 0) {
        taskDao.insert(TaskMaker.newTask(with(ID, id), with(UUID, UUIDHelper.newUUID())))
        googleTaskDao.insert(newGoogleTask(with(LIST, filter.list.remoteId), with(TASK, id), with(PARENT, parent), with(ORDER, order)))
    }

    private fun query(): List<TaskContainer> = taskDao.fetchTasks {
        TaskListQuery.getQuery(preferences, filter, it)
    }

    override fun inject(component: TestComponent) = component.inject(this)
}