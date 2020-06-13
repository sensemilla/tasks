package com.todoroo.astrid.reminders

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.natpryce.makeiteasy.MakeItEasy.with
import com.todoroo.andlib.utility.DateUtilities
import com.todoroo.astrid.dao.TaskDao
import com.todoroo.astrid.data.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.tasks.Freeze
import org.tasks.R
import org.tasks.date.DateTimeUtils
import org.tasks.injection.InjectingTestCase
import org.tasks.injection.TestComponent
import org.tasks.jobs.NotificationQueue
import org.tasks.jobs.ReminderEntry
import org.tasks.makers.TaskMaker.COMPLETION_TIME
import org.tasks.makers.TaskMaker.CREATION_TIME
import org.tasks.makers.TaskMaker.DELETION_TIME
import org.tasks.makers.TaskMaker.DUE_DATE
import org.tasks.makers.TaskMaker.DUE_TIME
import org.tasks.makers.TaskMaker.ID
import org.tasks.makers.TaskMaker.RANDOM_REMINDER_PERIOD
import org.tasks.makers.TaskMaker.REMINDERS
import org.tasks.makers.TaskMaker.REMINDER_LAST
import org.tasks.makers.TaskMaker.SNOOZE_TIME
import org.tasks.makers.TaskMaker.newTask
import org.tasks.preferences.Preferences
import org.tasks.reminders.Random
import org.tasks.time.DateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@RunWith(AndroidJUnit4::class)
class ReminderServiceTest : InjectingTestCase() {
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var taskDao: TaskDao
    @Inject lateinit var jobs: NotificationQueue

    private lateinit var service: ReminderService
    private lateinit var random: RandomStub

    override fun setUp() {
        super.setUp()
        random = RandomStub()
        preferences.clear()
        service = ReminderService(preferences, jobs, random, taskDao)
    }

    override fun inject(component: TestComponent) = component.inject(this)

    @Test
    fun dontScheduleDueDateReminderWhenFlagNotSet() {
        service.scheduleAlarm(newTask(with(ID, 1L), with(DUE_TIME, DateTimeUtils.newDateTime())))

        assertTrue(jobs.isEmpty())
    }

    @Test
    fun dontScheduleDueDateReminderWhenTimeNotSet() {
        service.scheduleAlarm(newTask(with(ID, 1L), with(REMINDERS, Task.NOTIFY_AT_DEADLINE)))

        assertTrue(jobs.isEmpty())
    }

    @Test
    fun schedulePastDueDate() {
        val task = newTask(
                with(ID, 1L),
                with(DUE_TIME, DateTimeUtils.newDateTime().minusDays(1)),
                with(REMINDERS, Task.NOTIFY_AT_DEADLINE))

        service.scheduleAlarm(task)

        verify(ReminderEntry(1, task.dueDate, ReminderService.TYPE_DUE))
    }

    @Test
    fun scheduleFutureDueDate() {
        val task = newTask(
                with(ID, 1L),
                with(DUE_TIME, DateTimeUtils.newDateTime().plusDays(1)),
                with(REMINDERS, Task.NOTIFY_AT_DEADLINE))

        service.scheduleAlarm(task)

        verify(ReminderEntry(1, task.dueDate, ReminderService.TYPE_DUE))
    }

    @Test
    fun scheduleReminderAtDefaultDueTime() {
        val now = DateTimeUtils.newDateTime()
        val task = newTask(with(ID, 1L), with(DUE_DATE, now), with(REMINDERS, Task.NOTIFY_AT_DEADLINE))

        service.scheduleAlarm(task)

        verify(ReminderEntry(1, now.startOfDay().withHourOfDay(18).millis, ReminderService.TYPE_DUE))
    }

    @Test
    fun dontScheduleReminderForCompletedTask() {
        val task = newTask(
                with(ID, 1L),
                with(DUE_TIME, DateTimeUtils.newDateTime().plusDays(1)),
                with(COMPLETION_TIME, DateTimeUtils.newDateTime()),
                with(REMINDERS, Task.NOTIFY_AT_DEADLINE))

        service.scheduleAlarm(task)

        assertTrue(jobs.isEmpty())
    }

    @Test
    fun dontScheduleReminderForDeletedTask() {
        val task = newTask(
                with(ID, 1L),
                with(DUE_TIME, DateTimeUtils.newDateTime().plusDays(1)),
                with(DELETION_TIME, DateTimeUtils.newDateTime()),
                with(REMINDERS, Task.NOTIFY_AT_DEADLINE))

        service.scheduleAlarm(task)

        assertTrue(jobs.isEmpty())
    }

    @Test
    fun dontScheduleDueDateReminderWhenAlreadyReminded() {
        val now = DateTimeUtils.newDateTime()
        val task = newTask(
                with(ID, 1L),
                with(DUE_TIME, now),
                with(REMINDER_LAST, now.plusSeconds(1)),
                with(REMINDERS, Task.NOTIFY_AT_DEADLINE))

        service.scheduleAlarm(task)

        assertTrue(jobs.isEmpty())
    }

    @Test
    fun ignoreStaleSnoozeTime() {
        val task = newTask(
                with(ID, 1L),
                with(DUE_TIME, DateTimeUtils.newDateTime()),
                with(SNOOZE_TIME, DateTimeUtils.newDateTime().minusMinutes(5)),
                with(REMINDER_LAST, DateTimeUtils.newDateTime().minusMinutes(4)),
                with(REMINDERS, Task.NOTIFY_AT_DEADLINE))

        service.scheduleAlarm(task)

        verify(ReminderEntry(1, task.dueDate, ReminderService.TYPE_DUE))
    }

    @Test
    fun dontIgnoreMissedSnoozeTime() {
        val dueDate = DateTimeUtils.newDateTime()
        val task = newTask(
                with(ID, 1L),
                with(DUE_TIME, dueDate),
                with(SNOOZE_TIME, dueDate.minusMinutes(4)),
                with(REMINDER_LAST, dueDate.minusMinutes(5)),
                with(REMINDERS, Task.NOTIFY_AT_DEADLINE))

        service.scheduleAlarm(task)

        verify(ReminderEntry(1, task.reminderSnooze, ReminderService.TYPE_SNOOZE))
    }

    @Test
    fun scheduleInitialRandomReminder() {
        random.seed = 0.3865f

        Freeze.freezeClock {
            val now = DateTimeUtils.newDateTime()
            val task = newTask(
                    with(ID, 1L),
                    with(REMINDER_LAST, null as DateTime?),
                    with(CREATION_TIME, now.minusDays(1)),
                    with(RANDOM_REMINDER_PERIOD, DateUtilities.ONE_WEEK))

            service.scheduleAlarm(task)

            verify(ReminderEntry(1L, now.minusDays(1).millis + 584206592, ReminderService.TYPE_RANDOM))
        }
    }

    @Test
    fun scheduleNextRandomReminder() {
        random.seed = 0.3865f

        Freeze.freezeClock {
            val now = DateTimeUtils.newDateTime()
            val task = newTask(
                    with(ID, 1L),
                    with(REMINDER_LAST, now.minusDays(1)),
                    with(CREATION_TIME, now.minusDays(30)),
                    with(RANDOM_REMINDER_PERIOD, DateUtilities.ONE_WEEK))

            service.scheduleAlarm(task)

            verify(ReminderEntry(1L, now.minusDays(1).millis + 584206592, ReminderService.TYPE_RANDOM))
        }
    }

    @Test
    fun scheduleOverdueRandomReminder() {
        random.seed = 0.3865f

        Freeze.freezeClock {
            val now = DateTimeUtils.newDateTime()
            val task = newTask(
                    with(ID, 1L),
                    with(REMINDER_LAST, now.minusDays(14)),
                    with(CREATION_TIME, now.minusDays(30)),
                    with(RANDOM_REMINDER_PERIOD, DateUtilities.ONE_WEEK))

            service.scheduleAlarm(task)

            verify(ReminderEntry(1L, now.millis + 10148400, ReminderService.TYPE_RANDOM))
        }
    }

    @Test
    fun scheduleOverdueNoLastReminder() {
        val task = newTask(
                with(ID, 1L),
                with(DUE_TIME, DateTime(2017, 9, 22, 15, 30)),
                with(REMINDER_LAST, null as DateTime?),
                with(REMINDERS, Task.NOTIFY_AFTER_DEADLINE))

        service.scheduleAlarm(task)

        verify(ReminderEntry(1L, DateTime(2017, 9, 23, 15, 30, 1, 0).millis, ReminderService.TYPE_OVERDUE))
    }

    @Test
    fun scheduleOverduePastLastReminder() {
        val task = newTask(
                with(ID, 1L),
                with(DUE_TIME, DateTime(2017, 9, 22, 15, 30)),
                with(REMINDER_LAST, DateTime(2017, 9, 24, 12, 0)),
                with(REMINDERS, Task.NOTIFY_AFTER_DEADLINE))

        service.scheduleAlarm(task)

        verify(ReminderEntry(1L, DateTime(2017, 9, 24, 15, 30, 1, 0).millis, ReminderService.TYPE_OVERDUE))
    }

    @Test
    fun scheduleOverdueBeforeLastReminder() {
        val task = newTask(
                with(ID, 1L),
                with(DUE_TIME, DateTime(2017, 9, 22, 12, 30)),
                with(REMINDER_LAST, DateTime(2017, 9, 24, 15, 0)),
                with(REMINDERS, Task.NOTIFY_AFTER_DEADLINE))

        service.scheduleAlarm(task)

        verify(ReminderEntry(1L, DateTime(2017, 9, 25, 12, 30, 1, 0).millis, ReminderService.TYPE_OVERDUE))
    }

    @Test
    fun scheduleOverdueWithNoDueTime() {
        preferences.setInt(R.string.p_rmd_time, TimeUnit.HOURS.toMillis(15).toInt())
        val task = newTask(
                with(ID, 1L),
                with(DUE_DATE, DateTime(2017, 9, 22)),
                with(REMINDER_LAST, DateTime(2017, 9, 23, 12, 17, 59, 999)),
                with(REMINDERS, Task.NOTIFY_AFTER_DEADLINE))

        service.scheduleAlarm(task)

        verify(ReminderEntry(1L, DateTime(2017, 9, 23, 15, 0, 0, 0).millis, ReminderService.TYPE_OVERDUE))
    }

    @Test
    fun scheduleSubsequentOverdueReminder() {
        val task = newTask(
                with(ID, 1L),
                with(DUE_TIME, DateTime(2017, 9, 22, 15, 30)),
                with(REMINDER_LAST, DateTime(2017, 9, 23, 15, 30, 59, 999)),
                with(REMINDERS, Task.NOTIFY_AFTER_DEADLINE))

        service.scheduleAlarm(task)

        verify(ReminderEntry(1L, DateTime(2017, 9, 24, 15, 30, 1, 0).millis, ReminderService.TYPE_OVERDUE))
    }

    @Test
    fun scheduleOverdueAfterLastReminder() {
        val task = newTask(
                with(ID, 1L),
                with(DUE_TIME, DateTime(2017, 9, 22, 15, 30)),
                with(REMINDER_LAST, DateTime(2017, 9, 23, 12, 17, 59, 999)),
                with(REMINDERS, Task.NOTIFY_AFTER_DEADLINE))

        service.scheduleAlarm(task)

        verify(ReminderEntry(1L, DateTime(2017, 9, 23, 15, 30, 1, 0).millis, ReminderService.TYPE_OVERDUE))
    }

    @Test
    fun snoozeOverridesAll() {
        val now = DateTimeUtils.newDateTime()
        val task = newTask(
                with(ID, 1L),
                with(DUE_TIME, now),
                with(SNOOZE_TIME, now.plusMonths(12)),
                with(REMINDERS, Task.NOTIFY_AT_DEADLINE or Task.NOTIFY_AFTER_DEADLINE),
                with(RANDOM_REMINDER_PERIOD, DateUtilities.ONE_HOUR))

        service.scheduleAlarm(task)

        verify(ReminderEntry(1, now.plusMonths(12).millis, ReminderService.TYPE_SNOOZE))
    }

    private fun verify(vararg reminders: ReminderEntry) = assertEquals(reminders.toList(), jobs.getJobs())

    internal class RandomStub : Random() {
        var seed = 1.0f

        override fun nextFloat() = seed
    }
}