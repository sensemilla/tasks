package org.tasks.data

import androidx.room.Embedded
import com.todoroo.astrid.data.Task
import org.tasks.time.DateTime

class CaldavTaskContainer {
    @Embedded lateinit var task: Task
    @Embedded lateinit var caldavTask: CaldavTask

    val remoteId: String?
        get() = caldavTask.remoteId

    val isDeleted: Boolean
        get() = task.isDeleted

    val vtodo: String?
        get() = caldavTask.vtodo

    val sortOrder: Long
        get() = caldavTask.order ?: DateTime(task.creationDate).toAppleEpoch()

    override fun toString(): String {
        return "CaldavTaskContainer{task=$task, caldavTask=$caldavTask}"
    }
}