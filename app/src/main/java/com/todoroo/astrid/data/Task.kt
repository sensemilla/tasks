package com.todoroo.astrid.data

import android.content.ContentValues
import android.os.Parcel
import android.os.Parcelable
import androidx.annotation.IntDef
import androidx.core.os.ParcelCompat
import androidx.room.*
import com.google.ical.values.RRule
import com.todoroo.andlib.data.Table
import com.todoroo.andlib.sql.Field
import com.todoroo.andlib.utility.DateUtilities
import com.todoroo.astrid.dao.TaskDao
import org.tasks.Strings
import org.tasks.backup.XmlReader
import org.tasks.data.Tag
import org.tasks.date.DateTimeUtils
import timber.log.Timber
import java.util.*

@Entity(
        tableName = "tasks",
        indices = [
            Index(name = "t_rid", value = ["remoteId"], unique = true),
            Index(name = "active_and_visible", value = ["completed", "deleted", "hideUntil"])])
class Task : Parcelable {
    /** ID  */
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    @Transient
    var id = NO_ID

    /** Name of Task  */
    @ColumnInfo(name = "title")
    var title: String? = null

    @ColumnInfo(name = "importance")
    var priority = Priority.NONE

    /** Unixtime Task is due, 0 if not set  */
    @ColumnInfo(name = "dueDate")
    var dueDate = 0L

    /** Unixtime Task should be hidden until, 0 if not set  */
    @ColumnInfo(name = "hideUntil")
    var hideUntil = 0L

    /** Unixtime Task was created  */
    @ColumnInfo(name = "created")
    var creationDate = 0L

    /** Unixtime Task was last touched  */
    @ColumnInfo(name = "modified")
    var modificationDate = 0L

    /** Unixtime Task was completed. 0 means active  */
    @ColumnInfo(name = "completed")
    var completionDate = 0L

    /** Unixtime Task was deleted. 0 means not deleted  */
    @ColumnInfo(name = "deleted")
    var deletionDate = 0L

    // --- data access boilerplate
    @ColumnInfo(name = "notes")
    var notes: String? = null

    @ColumnInfo(name = "estimatedSeconds")
    var estimatedSeconds = 0

    @ColumnInfo(name = "elapsedSeconds")
    var elapsedSeconds = 0

    @ColumnInfo(name = "timerStart")
    var timerStart = 0L

    /** Flags for when to send reminders  */
    @ColumnInfo(name = "notificationFlags")
    var reminderFlags = 0

    /** Reminder period, in milliseconds. 0 means disabled  */
    @ColumnInfo(name = "notifications")
    var reminderPeriod = 0L

    /** Unixtime the last reminder was triggered  */
    @ColumnInfo(name = "lastNotified")
    var reminderLast = 0L

    /** Unixtime snooze is set (0 -> no snooze)  */
    @ColumnInfo(name = "snoozeTime")
    var reminderSnooze = 0L

    @ColumnInfo(name = "recurrence")
    var recurrence: String? = null

    @ColumnInfo(name = "repeatUntil")
    var repeatUntil = 0L

    @ColumnInfo(name = "calendarUri")
    var calendarURI: String? = null

    /** Remote id  */
    @ColumnInfo(name = "remoteId")
    var remoteId: String? = NO_UUID

    @ColumnInfo(name = "collapsed")
    var isCollapsed = false

    @ColumnInfo(name = "parent")
    @Transient
    var parent = 0L

    @Deprecated(message = "no longer used")
    @ColumnInfo(name = "parent_uuid")
    var parentUuid: String? = null

    @Ignore
    @Transient
    private var transitoryData: HashMap<String, Any>? = null

    constructor()

    @Ignore
    constructor(reader: XmlReader) {
        calendarURI = reader.readString("calendarUri")
        completionDate = reader.readLong("completed")
        creationDate = reader.readLong("created")
        deletionDate = reader.readLong("deleted")
        dueDate = reader.readLong("dueDate")
        elapsedSeconds = reader.readInteger("elapsedSeconds")
        estimatedSeconds = reader.readInteger("estimatedSeconds")
        hideUntil = reader.readLong("hideUntil")
        priority = reader.readInteger("importance")
        modificationDate = reader.readLong("modified")
        notes = reader.readString("notes")
        recurrence = reader.readString("recurrence")
        reminderFlags = reader.readInteger("notificationFlags")
        reminderLast = reader.readLong("lastNotified")
        reminderPeriod = reader.readLong("notifications")
        reminderSnooze = reader.readLong("snoozeTime")
        repeatUntil = reader.readLong("repeatUntil")
        timerStart = reader.readLong("timerStart")
        title = reader.readString("title")
        remoteId = reader.readString("remoteId")
    }

    @Ignore
    constructor(parcel: Parcel) {
        calendarURI = parcel.readString()
        completionDate = parcel.readLong()
        creationDate = parcel.readLong()
        deletionDate = parcel.readLong()
        dueDate = parcel.readLong()
        elapsedSeconds = parcel.readInt()
        estimatedSeconds = parcel.readInt()
        hideUntil = parcel.readLong()
        id = parcel.readLong()
        priority = parcel.readInt()
        modificationDate = parcel.readLong()
        notes = parcel.readString()
        recurrence = parcel.readString()
        reminderFlags = parcel.readInt()
        reminderLast = parcel.readLong()
        reminderPeriod = parcel.readLong()
        reminderSnooze = parcel.readLong()
        repeatUntil = parcel.readLong()
        timerStart = parcel.readLong()
        title = parcel.readString()
        remoteId = parcel.readString() ?: NO_UUID
        transitoryData = parcel.readHashMap(ContentValues::class.java.classLoader) as HashMap<String, Any>?
        isCollapsed = ParcelCompat.readBoolean(parcel)
        parent = parcel.readLong()
    }

    var uuid: String
        get() = if (Strings.isNullOrEmpty(remoteId)) NO_UUID else remoteId!!
        set(uuid) {
            remoteId = uuid
        }

    /** Checks whether task is done. Requires COMPLETION_DATE  */
    val isCompleted
        get() = completionDate > 0

    /** Checks whether task is deleted. Will return false if DELETION_DATE not read  */
    val isDeleted
        get() = deletionDate > 0

    /** Checks whether task is hidden. Requires HIDDEN_UNTIL  */
    val isHidden
        get() = hideUntil > DateUtilities.now()

    fun hasHideUntilDate() = hideUntil > 0

    /** Checks whether task is done. Requires DUE_DATE  */
    fun hasDueDate() = dueDate > 0

    /**
     * Create hide until for this task.
     *
     * @param setting one of the HIDE_UNTIL_* constants
     * @param customDate if specific day is set, this value
     */
    fun createHideUntil(setting: Int, customDate: Long): Long {
        val date: Long = when (setting) {
            HIDE_UNTIL_NONE -> return 0
            HIDE_UNTIL_DUE, HIDE_UNTIL_DUE_TIME -> dueDate
            HIDE_UNTIL_DAY_BEFORE -> dueDate - DateUtilities.ONE_DAY
            HIDE_UNTIL_WEEK_BEFORE -> dueDate - DateUtilities.ONE_WEEK
            HIDE_UNTIL_SPECIFIC_DAY, HIDE_UNTIL_SPECIFIC_DAY_TIME -> customDate
            else -> throw IllegalArgumentException("Unknown setting $setting")
        }
        if (date <= 0) {
            return date
        }
        var hideUntil = DateTimeUtils.newDateTime(date).withMillisOfSecond(0) // get rid of millis
        hideUntil = if (setting != HIDE_UNTIL_SPECIFIC_DAY_TIME && setting != HIDE_UNTIL_DUE_TIME) {
            hideUntil.withHourOfDay(0).withMinuteOfHour(0).withSecondOfMinute(0)
        } else {
            hideUntil.withSecondOfMinute(1)
        }
        return hideUntil.millis
    }

    /** Checks whether this due date has a due time or only a date  */
    fun hasDueTime(): Boolean = hasDueDate() && hasDueTime(dueDate)

    val isOverdue: Boolean
        get() {
            val dueDate = dueDate
            val compareTo = if (hasDueTime()) DateUtilities.now() else DateTimeUtils.newDateTime().startOfDay().millis
            return dueDate < compareTo && !isCompleted
        }

    fun repeatAfterCompletion(): Boolean = recurrence?.contains("FROM=COMPLETION") ?: false

    fun sanitizedRecurrence(): String? = getRecurrenceWithoutFrom()?.replace("BYDAY=;".toRegex(), "") // $NON-NLS-1$//$NON-NLS-2$

    fun getRecurrenceWithoutFrom(): String? = recurrence?.replace(";?FROM=[^;]*".toRegex(), "")

    fun setDueDateAdjustingHideUntil(newDueDate: Long) {
        if (dueDate > 0) {
            if (hideUntil > 0) {
                hideUntil = if (newDueDate > 0) hideUntil + newDueDate - dueDate else 0
            }
        }
        dueDate = newDueDate
    }

    val isRecurring: Boolean
        get() = !Strings.isNullOrEmpty(recurrence)

    fun setRecurrence(rrule: RRule, afterCompletion: Boolean) {
        recurrence = rrule.toIcal() + if (afterCompletion) ";FROM=COMPLETION" else ""
    }

    fun hasNotes(): Boolean {
        return !Strings.isNullOrEmpty(notes)
    }

    val isNotifyModeNonstop: Boolean
        get() = isReminderFlagSet(NOTIFY_MODE_NONSTOP)

    val isNotifyModeFive: Boolean
        get() = isReminderFlagSet(NOTIFY_MODE_FIVE)

    val isNotifyAfterDeadline: Boolean
        get() = isReminderFlagSet(NOTIFY_AFTER_DEADLINE)

    val isNotifyAtDeadline: Boolean
        get() = isReminderFlagSet(NOTIFY_AT_DEADLINE)

    private fun isReminderFlagSet(flag: Int): Boolean {
        return reminderFlags and flag > 0
    }

    val isNew: Boolean
        get() = id == NO_ID

    /** {@inheritDoc}  */
    override fun describeContents() = 0

    /** {@inheritDoc}  */
    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(calendarURI)
        dest.writeLong(completionDate)
        dest.writeLong(creationDate)
        dest.writeLong(deletionDate)
        dest.writeLong(dueDate)
        dest.writeInt(elapsedSeconds)
        dest.writeInt(estimatedSeconds)
        dest.writeLong(hideUntil)
        dest.writeLong(id)
        dest.writeInt(priority)
        dest.writeLong(modificationDate)
        dest.writeString(notes)
        dest.writeString(recurrence)
        dest.writeInt(reminderFlags)
        dest.writeLong(reminderLast)
        dest.writeLong(reminderPeriod)
        dest.writeLong(reminderSnooze)
        dest.writeLong(repeatUntil)
        dest.writeLong(timerStart)
        dest.writeString(title)
        dest.writeString(remoteId)
        dest.writeMap(transitoryData as Map<*, *>?)
        ParcelCompat.writeBoolean(dest, isCollapsed)
        dest.writeLong(parent)
    }

    fun insignificantChange(task: Task?): Boolean {
        if (this === task) {
            return true
        }
        return if (task == null) {
            false
        } else id == task.id
                && title == task.title
                && priority == task.priority
                && dueDate == task.dueDate
                && hideUntil == task.hideUntil
                && creationDate == task.creationDate
                && modificationDate == task.modificationDate
                && completionDate == task.completionDate
                && deletionDate == task.deletionDate
                && notes == task.notes
                && estimatedSeconds == task.estimatedSeconds
                && elapsedSeconds == task.elapsedSeconds
                && reminderFlags == task.reminderFlags
                && reminderPeriod == task.reminderPeriod
                && recurrence == task.recurrence
                && repeatUntil == task.repeatUntil
                && calendarURI == task.calendarURI
                && parent == task.parent
                && remoteId == task.remoteId
    }

    fun googleTaskUpToDate(original: Task?): Boolean {
        if (this === original) {
            return true
        }
        return if (original == null) {
            false
        } else title == original.title
                && dueDate == original.dueDate
                && completionDate == original.completionDate
                && deletionDate == original.deletionDate
                && parent == original.parent
                && notes == original.notes
    }

    fun caldavUpToDate(original: Task?): Boolean {
        if (this === original) {
            return true
        }
        return if (original == null) {
            false
        } else title == original.title
                && priority == original.priority
                && dueDate == original.dueDate
                && completionDate == original.completionDate
                && deletionDate == original.deletionDate
                && notes == original.notes
                && recurrence == original.recurrence
                && parent == original.parent
                && repeatUntil == original.repeatUntil
    }

    val isSaved: Boolean
        get() = id != NO_ID

    @Synchronized
    fun suppressSync() {
        putTransitory(SyncFlags.SUPPRESS_SYNC, true)
    }

    @Synchronized
    fun suppressRefresh() {
        putTransitory(TaskDao.TRANS_SUPPRESS_REFRESH, true)
    }

    @Synchronized
    fun putTransitory(key: String, value: Any) {
        if (transitoryData == null) {
            transitoryData = HashMap()
        }
        transitoryData!![key] = value
    }

    val tags: ArrayList<String>
        get() {
            return getTransitory(Tag.KEY) ?: ArrayList()
        }

    fun setTags(tags: ArrayList<String>) {
        if (transitoryData == null) {
            transitoryData = HashMap()
        }
        transitoryData!![Tag.KEY] = tags
    }

    fun hasTransitory(key: String?): Boolean {
        return transitoryData != null && transitoryData!!.containsKey(key)
    }

    fun <T> getTransitory(key: String?): T? {
        return if (transitoryData == null) {
            null
        } else transitoryData!![key] as T?
    }

    // --- Convenience wrappers for using transitories as flags
    fun checkTransitory(flag: String?): Boolean {
        val trans = getTransitory<Any>(flag)
        return trans != null
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Task) return false

        if (id != other.id) return false
        if (title != other.title) return false
        if (priority != other.priority) return false
        if (dueDate != other.dueDate) return false
        if (hideUntil != other.hideUntil) return false
        if (creationDate != other.creationDate) return false
        if (modificationDate != other.modificationDate) return false
        if (completionDate != other.completionDate) return false
        if (deletionDate != other.deletionDate) return false
        if (notes != other.notes) return false
        if (estimatedSeconds != other.estimatedSeconds) return false
        if (elapsedSeconds != other.elapsedSeconds) return false
        if (timerStart != other.timerStart) return false
        if (reminderFlags != other.reminderFlags) return false
        if (reminderPeriod != other.reminderPeriod) return false
        if (reminderLast != other.reminderLast) return false
        if (reminderSnooze != other.reminderSnooze) return false
        if (recurrence != other.recurrence) return false
        if (repeatUntil != other.repeatUntil) return false
        if (calendarURI != other.calendarURI) return false
        if (remoteId != other.remoteId) return false
        if (isCollapsed != other.isCollapsed) return false
        if (parent != other.parent) return false
        if (transitoryData != other.transitoryData) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + (title?.hashCode() ?: 0)
        result = 31 * result + priority
        result = 31 * result + dueDate.hashCode()
        result = 31 * result + hideUntil.hashCode()
        result = 31 * result + creationDate.hashCode()
        result = 31 * result + modificationDate.hashCode()
        result = 31 * result + completionDate.hashCode()
        result = 31 * result + deletionDate.hashCode()
        result = 31 * result + (notes?.hashCode() ?: 0)
        result = 31 * result + estimatedSeconds
        result = 31 * result + elapsedSeconds
        result = 31 * result + timerStart.hashCode()
        result = 31 * result + reminderFlags
        result = 31 * result + reminderPeriod.hashCode()
        result = 31 * result + reminderLast.hashCode()
        result = 31 * result + reminderSnooze.hashCode()
        result = 31 * result + (recurrence?.hashCode() ?: 0)
        result = 31 * result + repeatUntil.hashCode()
        result = 31 * result + (calendarURI?.hashCode() ?: 0)
        result = 31 * result + remoteId.hashCode()
        result = 31 * result + isCollapsed.hashCode()
        result = 31 * result + parent.hashCode()
        result = 31 * result + (transitoryData?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "Task(id=$id, title=$title, priority=$priority, dueDate=$dueDate, hideUntil=$hideUntil, creationDate=$creationDate, modificationDate=$modificationDate, completionDate=$completionDate, deletionDate=$deletionDate, notes=$notes, estimatedSeconds=$estimatedSeconds, elapsedSeconds=$elapsedSeconds, timerStart=$timerStart, reminderFlags=$reminderFlags, reminderPeriod=$reminderPeriod, reminderLast=$reminderLast, reminderSnooze=$reminderSnooze, recurrence=$recurrence, repeatUntil=$repeatUntil, calendarURI=$calendarURI, remoteId='$remoteId', isCollapsed=$isCollapsed, parent=$parent, transitoryData=$transitoryData)"
    }

    @Retention(AnnotationRetention.SOURCE)
    @IntDef(Priority.HIGH, Priority.MEDIUM, Priority.LOW, Priority.NONE)
    annotation class Priority {
        companion object {
            const val HIGH = 0
            const val MEDIUM = 1
            const val LOW = 2
            const val NONE = 3
        }
    }

    companion object {
        // --- table and uri
        /** table for this model  */
        @JvmField val TABLE = Table("tasks")
        @JvmField val FIELDS = Field.field("tasks.*")
        const val NO_ID: Long = 0

        // --- properties
        @JvmField val ID = TABLE.column("_id")
        @JvmField val TITLE = TABLE.column("title")
        @JvmField val IMPORTANCE = TABLE.column("importance")
        @JvmField val DUE_DATE = TABLE.column("dueDate")
        @JvmField val HIDE_UNTIL = TABLE.column("hideUntil")
        @JvmField val MODIFICATION_DATE = TABLE.column("modified")
        @JvmField val CREATION_DATE = TABLE.column("created")
        @JvmField val COMPLETION_DATE = TABLE.column("completed")
        @JvmField val DELETION_DATE = TABLE.column("deleted")
        @JvmField val NOTES = TABLE.column("notes")
        @JvmField val TIMER_START = TABLE.column("timerStart")
        @JvmField val PARENT = TABLE.column("parent")

        /** constant value for no uuid  */
        const val NO_UUID = "0" // $NON-NLS-1$
        @JvmField val UUID = TABLE.column("remoteId")

        /** whether to send a reminder at deadline  */
        const val NOTIFY_AT_DEADLINE = 1 shl 1

        /** whether to send reminders while task is overdue  */
        const val NOTIFY_AFTER_DEADLINE = 1 shl 2

        /** reminder mode non-stop  */
        const val NOTIFY_MODE_NONSTOP = 1 shl 3

        /** reminder mode five times (exclusive with non-stop)  */
        const val NOTIFY_MODE_FIVE = 1 shl 4
        @JvmField val CREATOR: Parcelable.Creator<Task> = object : Parcelable.Creator<Task> {
            override fun createFromParcel(source: Parcel): Task? {
                return Task(source)
            }

            override fun newArray(size: Int): Array<Task?> {
                return arrayOfNulls(size)
            }
        }

        /** urgency array index -> significance  */
        const val URGENCY_NONE = 0
        const val URGENCY_SPECIFIC_DAY = 7
        const val URGENCY_SPECIFIC_DAY_TIME = 8

        /** hide until array index -> significance  */
        const val HIDE_UNTIL_NONE = 0
        const val HIDE_UNTIL_DUE = 1
        const val HIDE_UNTIL_DAY_BEFORE = 2
        const val HIDE_UNTIL_WEEK_BEFORE = 3
        const val HIDE_UNTIL_SPECIFIC_DAY = 4

        // --- for astrid.com
        const val HIDE_UNTIL_SPECIFIC_DAY_TIME = 5
        const val HIDE_UNTIL_DUE_TIME = 6
        const val URGENCY_TODAY = 1
        const val URGENCY_TOMORROW = 2

        // --- notification flags
        const val URGENCY_DAY_AFTER = 3
        const val URGENCY_NEXT_WEEK = 4
        const val URGENCY_IN_TWO_WEEKS = 5

        /**
         * Creates due date for this task. If this due date has no time associated, we move it to the last
         * millisecond of the day.
         *
         * @param setting one of the URGENCY_* constants
         * @param customDate if specific day or day & time is set, this value
         */
        @JvmStatic fun createDueDate(setting: Int, customDate: Long): Long {
            val date: Long = when (setting) {
                URGENCY_NONE -> 0
                URGENCY_TODAY -> DateUtilities.now()
                URGENCY_TOMORROW -> DateUtilities.now() + DateUtilities.ONE_DAY
                URGENCY_DAY_AFTER -> DateUtilities.now() + 2 * DateUtilities.ONE_DAY
                URGENCY_NEXT_WEEK -> DateUtilities.now() + DateUtilities.ONE_WEEK
                URGENCY_IN_TWO_WEEKS -> DateUtilities.now() + 2 * DateUtilities.ONE_WEEK
                URGENCY_SPECIFIC_DAY, URGENCY_SPECIFIC_DAY_TIME -> customDate
                else -> throw IllegalArgumentException("Unknown setting $setting")
            }
            if (date <= 0) {
                return date
            }
            var dueDate = DateTimeUtils.newDateTime(date).withMillisOfSecond(0)
            dueDate = if (setting != URGENCY_SPECIFIC_DAY_TIME) {
                dueDate
                        .withHourOfDay(12)
                        .withMinuteOfHour(0)
                        .withSecondOfMinute(0) // Seconds == 0 means no due time
            } else {
                dueDate.withSecondOfMinute(1) // Seconds > 0 means due time exists
            }
            return dueDate.millis
        }

        /** Checks whether provided due date has a due time or only a date  */
        @JvmStatic fun hasDueTime(dueDate: Long): Boolean {
            return dueDate > 0 && dueDate % 60000 > 0
        }

        @JvmStatic fun isValidUuid(uuid: String): Boolean {
            return try {
                val value = uuid.toLong()
                value > 0
            } catch (e: NumberFormatException) {
                Timber.e(e)
                isUuidEmpty(uuid)
            }
        }

        @JvmStatic fun isUuidEmpty(uuid: String?): Boolean {
            return NO_UUID == uuid || Strings.isNullOrEmpty(uuid)
        }
    }
}