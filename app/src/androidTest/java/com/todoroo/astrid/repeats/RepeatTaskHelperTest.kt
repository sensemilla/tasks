package com.todoroo.astrid.repeats

import android.annotation.SuppressLint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.ical.values.RRule
import com.natpryce.makeiteasy.MakeItEasy.with
import com.todoroo.astrid.alarms.AlarmService
import com.todoroo.astrid.dao.TaskDao
import com.todoroo.astrid.data.Task
import com.todoroo.astrid.gcal.GCalHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.InOrder
import org.mockito.Mockito
import org.tasks.LocalBroadcastManager
import org.tasks.injection.InjectingTestCase
import org.tasks.injection.TestComponent
import org.tasks.makers.TaskMaker.AFTER_COMPLETE
import org.tasks.makers.TaskMaker.COMPLETION_TIME
import org.tasks.makers.TaskMaker.DUE_TIME
import org.tasks.makers.TaskMaker.ID
import org.tasks.makers.TaskMaker.RRULE
import org.tasks.makers.TaskMaker.newTask
import org.tasks.time.DateTime
import java.text.ParseException
import javax.inject.Inject

@SuppressLint("NewApi")
@RunWith(AndroidJUnit4::class)
class RepeatTaskHelperTest : InjectingTestCase() {
    @Inject lateinit var taskDao: TaskDao
    private lateinit var localBroadcastManager: LocalBroadcastManager
    private lateinit var alarmService: AlarmService
    private lateinit var gCalHelper: GCalHelper
    private lateinit var helper: RepeatTaskHelper
    private lateinit var mocks: InOrder

    @Before
    fun before() {
        alarmService = Mockito.mock(AlarmService::class.java)
        gCalHelper = Mockito.mock(GCalHelper::class.java)
        localBroadcastManager = Mockito.mock(LocalBroadcastManager::class.java)
        mocks = Mockito.inOrder(alarmService, gCalHelper, localBroadcastManager)
        helper = RepeatTaskHelper(gCalHelper, alarmService, taskDao, localBroadcastManager)
    }

    @After
    fun after() {
        Mockito.verifyNoMoreInteractions(localBroadcastManager, gCalHelper, alarmService)
    }

    @Test
    fun noRepeat() {
        helper.handleRepeat(newTask(with(DUE_TIME, DateTime(2017, 10, 4, 13, 30))))
    }

    @Test
    @Throws(ParseException::class)
    fun testMinutelyRepeat() {
        val task = newTask(
                with(ID, 1L),
                with(DUE_TIME, DateTime(2017, 10, 4, 13, 30)),
                with(RRULE, RRule("RRULE:FREQ=MINUTELY;INTERVAL=30")))
        repeatAndVerify(
                task, DateTime(2017, 10, 4, 13, 30, 1), DateTime(2017, 10, 4, 14, 0, 1))
    }

    @Test
    @Throws(ParseException::class)
    fun testMinutelyRepeatAfterCompletion() {
        val task = newTask(
                with(ID, 1L),
                with(DUE_TIME, DateTime(2017, 10, 4, 13, 30)),
                with(COMPLETION_TIME, DateTime(2017, 10, 4, 13, 17, 45, 340)),
                with(RRULE, RRule("RRULE:FREQ=MINUTELY;INTERVAL=30")),
                with(AFTER_COMPLETE, true))
        repeatAndVerify(
                task, DateTime(2017, 10, 4, 13, 30, 1), DateTime(2017, 10, 4, 13, 47, 1))
    }

    @Test
    @Throws(ParseException::class)
    fun testMinutelyDecrementCount() {
        val task = newTask(
                with(ID, 1L),
                with(DUE_TIME, DateTime(2017, 10, 4, 13, 30)),
                with(RRULE, RRule("RRULE:FREQ=MINUTELY;COUNT=2;INTERVAL=30")))
        repeatAndVerify(
                task, DateTime(2017, 10, 4, 13, 30, 1), DateTime(2017, 10, 4, 14, 0, 1))
        assertEquals(1, RRule(task.getRecurrenceWithoutFrom()).count)
    }

    @Test
    @Throws(ParseException::class)
    fun testMinutelyLastOccurrence() {
        val task = newTask(
                with(ID, 1L),
                with(DUE_TIME, DateTime(2017, 10, 4, 13, 30)),
                with(RRULE, RRule("RRULE:FREQ=MINUTELY;COUNT=1;INTERVAL=30")))
        helper.handleRepeat(task)
    }

    @Test
    @Throws(ParseException::class)
    fun testHourlyRepeat() {
        val task = newTask(
                with(ID, 1L),
                with(DUE_TIME, DateTime(2017, 10, 4, 13, 30)),
                with(RRULE, RRule("RRULE:FREQ=HOURLY;INTERVAL=6")))
        repeatAndVerify(
                task, DateTime(2017, 10, 4, 13, 30, 1), DateTime(2017, 10, 4, 19, 30, 1))
    }

    @Test
    @Throws(ParseException::class)
    fun testHourlyRepeatAfterCompletion() {
        val task = newTask(
                with(ID, 1L),
                with(DUE_TIME, DateTime(2017, 10, 4, 13, 30)),
                with(COMPLETION_TIME, DateTime(2017, 10, 4, 13, 17, 45, 340)),
                with(RRULE, RRule("RRULE:FREQ=HOURLY;INTERVAL=6")),
                with(AFTER_COMPLETE, true))
        repeatAndVerify(
                task, DateTime(2017, 10, 4, 13, 30, 1), DateTime(2017, 10, 4, 19, 17, 1))
    }

    @Test
    @Throws(ParseException::class)
    fun testDailyRepeat() {
        val task = newTask(
                with(ID, 1L),
                with(DUE_TIME, DateTime(2017, 10, 4, 13, 30)),
                with(RRULE, RRule("RRULE:FREQ=DAILY;INTERVAL=6")))
        repeatAndVerify(
                task, DateTime(2017, 10, 4, 13, 30, 1), DateTime(2017, 10, 10, 13, 30, 1))
    }

    @Test
    @Throws(ParseException::class)
    fun testRepeatWeeklyNoDays() {
        val task = newTask(
                with(ID, 1L),
                with(DUE_TIME, DateTime(2017, 10, 4, 13, 30)),
                with(RRULE, RRule("RRULE:FREQ=WEEKLY;INTERVAL=2")))
        repeatAndVerify(
                task, DateTime(2017, 10, 4, 13, 30, 1), DateTime(2017, 10, 18, 13, 30, 1))
    }

    @Test
    @Throws(ParseException::class)
    fun testYearly() {
        val task = newTask(
                with(ID, 1L),
                with(DUE_TIME, DateTime(2017, 10, 4, 13, 30)),
                with(RRULE, RRule("RRULE:FREQ=YEARLY;INTERVAL=3")))
        repeatAndVerify(
                task, DateTime(2017, 10, 4, 13, 30, 1), DateTime(2020, 10, 4, 13, 30, 1))
    }

    @Test
    @Throws(ParseException::class)
    fun testMonthlyRepeat() {
        val task = newTask(
                with(ID, 1L),
                with(DUE_TIME, DateTime(2017, 10, 4, 13, 30)),
                with(RRULE, RRule("RRULE:FREQ=MONTHLY;INTERVAL=3")))
        repeatAndVerify(
                task, DateTime(2017, 10, 4, 13, 30, 1), DateTime(2018, 1, 4, 13, 30, 1))
    }

    @Test
    @Throws(ParseException::class)
    fun testMonthlyRepeatAtEndOfMonth() {
        val task = newTask(
                with(ID, 1L),
                with(DUE_TIME, DateTime(2017, 1, 31, 13, 30)),
                with(RRULE, RRule("RRULE:FREQ=MONTHLY;INTERVAL=1")))
        repeatAndVerify(
                task, DateTime(2017, 1, 31, 13, 30, 1), DateTime(2017, 2, 28, 13, 30, 1))
    }

    private fun repeatAndVerify(task: Task, oldDueDate: DateTime, newDueDate: DateTime) {
        helper.handleRepeat(task)
        mocks.verify(gCalHelper).rescheduleRepeatingTask(task)
        mocks.verify(alarmService).rescheduleAlarms(1, oldDueDate.millis, newDueDate.millis)
        mocks.verify(localBroadcastManager).broadcastRepeat(1, oldDueDate.millis, newDueDate.millis)
    }

    override fun inject(component: TestComponent) = component.inject(this)
}