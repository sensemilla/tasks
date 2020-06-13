package org.tasks.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.todoroo.astrid.data.Task

/**
 * Data Model which represents a user.
 *
 * @author Tim Su <tim></tim>@todoroo.com>
 */
@Entity(tableName = "task_list_metadata")
class TaskListMetadata {
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    var id: Long? = null

    @ColumnInfo(name = "remoteId")
    var remoteId: String? = Task.NO_UUID

    @ColumnInfo(name = "tag_uuid")
    var tagUuid: String? = Task.NO_UUID

    @ColumnInfo(name = "filter")
    var filter: String? = ""

    @ColumnInfo(name = "task_ids")
    var taskIds: String? = "[]"

    companion object {
        const val FILTER_ID_ALL = "all"
        const val FILTER_ID_TODAY = "today"
    }
}