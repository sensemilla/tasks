package com.todoroo.astrid.service

import android.content.Context
import androidx.annotation.ColorRes
import com.google.common.collect.ImmutableListMultimap
import com.google.common.collect.ListMultimap
import com.google.common.collect.Multimaps
import com.todoroo.astrid.api.GtasksFilter
import com.todoroo.astrid.dao.TaskDao
import org.tasks.R
import org.tasks.Strings.isNullOrEmpty
import org.tasks.caldav.iCalendar
import org.tasks.caldav.iCalendar.Companion.fromVtodo
import org.tasks.caldav.iCalendar.Companion.getParent
import org.tasks.caldav.iCalendar.Companion.order
import org.tasks.data.*
import org.tasks.injection.ApplicationContext
import org.tasks.preferences.DefaultFilterProvider
import org.tasks.preferences.Preferences
import org.tasks.widget.AppWidgetManager
import org.tasks.widget.WidgetPreferences
import java.io.File
import java.util.*
import javax.inject.Inject

class Upgrader @Inject constructor(
        @param:ApplicationContext private val context: Context,
        private val preferences: Preferences,
        private val tagDataDao: TagDataDao,
        private val tagDao: TagDao,
        private val filterDao: FilterDao,
        private val defaultFilterProvider: DefaultFilterProvider,
        private val googleTaskListDao: GoogleTaskListDao,
        private val userActivityDao: UserActivityDao,
        private val taskAttachmentDao: TaskAttachmentDao,
        private val caldavDao: CaldavDao,
        private val taskDao: TaskDao,
        private val locationDao: LocationDao,
        private val iCal: iCalendar,
        private val widgetManager: AppWidgetManager,
        private val taskMover: TaskMover) {

    fun upgrade(from: Int, to: Int) {
        if (from > 0) {
            run(from, V4_9_5) { removeDuplicateTags() }
            run(from, V5_3_0) { migrateFilters() }
            run(from, V6_0_beta_1) { migrateDefaultSyncList() }
            run(from, V6_0_beta_2) { migrateGoogleTaskAccount() }
            run(from, V6_4) { migrateUris() }
            run(from, V6_7) { this.migrateGoogleTaskFilters() }
            run(from, V6_8_1) { this.migrateCaldavFilters() }
            run(from, V6_9) { applyCaldavCategories() }
            run(from, V7_0) { applyCaldavSubtasks() }
            run(from, V8_2) { migrateColors() }
            run(from, V8_5) { applyCaldavGeo() }
            run(from, V8_8) { preferences.setBoolean(R.string.p_linkify_task_edit, true) }
            run(from, V8_10) { migrateWidgets() }
            run(from, V9_3) { applyCaldavOrder() }
            run(from, V9_6) {
                val chips = preferences.getBoolean("show_list_indicators", true)
                preferences.showSubtaskChip = chips
                preferences.showPlaceChip = chips
                preferences.showListChip = chips
                preferences.showTagChip = chips
                preferences.setBoolean(R.string.p_astrid_sort_enabled, true)
                taskMover.migrateLocalTasks()
            }
            run(from, V9_7) { googleTaskListDao.resetOrders() }
            preferences.setBoolean(R.string.p_just_updated, true)
        }
        preferences.setCurrentVersion(to)
    }

    private fun run(from: Int, version: Int, runnable: () -> Unit) {
        if (from < version) {
            runnable.invoke()
            preferences.setCurrentVersion(version)
        }
    }

    private fun migrateWidgets() {
        for (widgetId in widgetManager.widgetIds) {
            val widgetPreferences = WidgetPreferences(context, preferences, widgetId)
            widgetPreferences.maintainExistingConfiguration()
        }
    }

    private fun migrateColors() {
        preferences.setInt(
                R.string.p_theme_color, getAndroidColor(preferences.getInt(R.string.p_theme_color, 7)))
        for (calendar in caldavDao.getCalendars()) {
            calendar.color = getAndroidColor(calendar.color)
            caldavDao.update(calendar)
        }
        for (list in googleTaskListDao.getAllLists()) {
            list.setColor(getAndroidColor(list.getColor()!!))
            googleTaskListDao.update(list)
        }
        for (tagData in tagDataDao.getAll()) {
            tagData.setColor(getAndroidColor(tagData.getColor()!!))
            tagDataDao.update(tagData)
        }
        for (filter in filterDao.getFilters()) {
            filter.setColor(getAndroidColor(filter.getColor()!!))
            filterDao.update(filter)
        }
    }

    private fun getAndroidColor(index: Int): Int {
        return getAndroidColor(context, index)
    }

    private fun applyCaldavOrder() {
        for (task in caldavDao.getTasks().map(CaldavTaskContainer::caldavTask)) {
            val remoteTask = fromVtodo(task.vtodo!!) ?: continue
            val order: Long? = remoteTask.order
            if (order != null) {
                task.order = order
                caldavDao.update(task)
            }
        }
    }

    private fun applyCaldavGeo() {
        val tasksWithLocations = locationDao.getActiveGeofences().map(Location::task)
        for (task in caldavDao.getTasks().map(CaldavTaskContainer::caldavTask)) {
            val taskId = task.task
            if (tasksWithLocations.contains(taskId)) {
                continue
            }
            val remoteTask = fromVtodo(task.vtodo!!) ?: continue
            val geo = remoteTask.geoPosition ?: continue
            iCal.setPlace(taskId, geo)
        }
        taskDao.touch(tasksWithLocations)
    }

    private fun applyCaldavSubtasks() {
        val updated: MutableList<CaldavTask> = ArrayList()
        for (task in caldavDao.getTasks().map(CaldavTaskContainer::caldavTask)) {
            val remoteTask = fromVtodo(task.vtodo!!) ?: continue
            task.remoteParent = getParent(remoteTask)
            if (!isNullOrEmpty(task.remoteParent)) {
                updated.add(task)
            }
        }
        caldavDao.update(updated)
        caldavDao.updateParents()
    }

    private fun applyCaldavCategories() {
        val tasksWithTags: List<Long> = caldavDao.getTasksWithTags()
        for (container in caldavDao.getTasks()) {
            val remoteTask = fromVtodo(container.vtodo!!)
            if (remoteTask != null) {
                tagDao.insert(container.task, iCal.getTags(remoteTask.categories))
            }
        }
        taskDao.touch(tasksWithTags)
    }

    private fun removeDuplicateTags() {
        val tagsByUuid: ListMultimap<String, TagData> = Multimaps.index(tagDataDao.tagDataOrderedByName()) { it!!.remoteId }
        for (uuid in tagsByUuid.keySet()) {
            removeDuplicateTagData(tagsByUuid[uuid])
            removeDuplicateTagMetadata(uuid)
        }
    }

    private fun migrateGoogleTaskFilters() {
        for (filter in filterDao.getAll()) {
            filter.setSql(migrateGoogleTaskFilters(filter.getSql()))
            filter.criterion = migrateGoogleTaskFilters(filter.criterion)
            filterDao.update(filter)
        }
    }

    private fun migrateCaldavFilters() {
        for (filter in filterDao.getAll()) {
            filter.setSql(migrateCaldavFilters(filter.getSql()))
            filter.criterion = migrateCaldavFilters(filter.criterion)
            filterDao.update(filter)
        }
    }

    private fun migrateFilters() {
        for (filter in filterDao.getFilters()) {
            filter.setSql(migrateMetadata(filter.getSql()))
            filter.criterion = migrateMetadata(filter.criterion)
            filterDao.update(filter)
        }
    }

    private fun migrateDefaultSyncList() {
        val account = preferences.getStringValue("gtasks_user")
        if (isNullOrEmpty(account)) {
            return
        }
        val defaultGoogleTaskList = preferences.getStringValue("gtasks_defaultlist")
        if (isNullOrEmpty(defaultGoogleTaskList)) {
            // TODO: look up default list
        } else {
            val googleTaskList = googleTaskListDao.getByRemoteId(defaultGoogleTaskList!!)
            if (googleTaskList != null) {
                defaultFilterProvider.defaultList = GtasksFilter(googleTaskList)
            }
        }
    }

    private fun migrateGoogleTaskAccount() {
        val account = preferences.getStringValue("gtasks_user")
        if (!isNullOrEmpty(account)) {
            val googleTaskAccount = GoogleTaskAccount()
            googleTaskAccount.account = account
            googleTaskListDao.insert(googleTaskAccount)
            for (list in googleTaskListDao.getAllLists()) {
                list.account = account
                googleTaskListDao.insertOrReplace(list)
            }
        }
    }

    private fun migrateUris() {
        migrateUriPreference(R.string.p_backup_dir)
        migrateUriPreference(R.string.p_attachment_dir)
        for (userActivity in userActivityDao.getComments()) {
            userActivity.convertPictureUri()
            userActivityDao.update(userActivity)
        }
        for (attachment in taskAttachmentDao.getAttachments()) {
            attachment.convertPathUri()
            taskAttachmentDao.update(attachment)
        }
    }

    private fun migrateUriPreference(pref: Int) {
        val path = preferences.getStringValue(pref)
        if (isNullOrEmpty(path)) {
            return
        }
        val file = File(path)
        try {
            if (file.canWrite()) {
                preferences.setUri(pref, file.toURI())
            } else {
                preferences.remove(pref)
            }
        } catch (ignored: SecurityException) {
            preferences.remove(pref)
        }
    }

    private fun migrateGoogleTaskFilters(input: String?): String {
        return input.orEmpty()
                .replace("SELECT task FROM google_tasks", "SELECT gt_task as task FROM google_tasks")
                .replace("(list_id", "(gt_list_id")
                .replace("google_tasks.list_id", "google_tasks.gt_list_id")
                .replace("google_tasks.task", "google_tasks.gt_task")
    }

    private fun migrateCaldavFilters(input: String?): String {
        return input.orEmpty()
                .replace("SELECT task FROM caldav_tasks", "SELECT cd_task as task FROM caldav_tasks")
                .replace("(calendar", "(cd_calendar")
    }

    private fun migrateMetadata(input: String?): String {
        return input.orEmpty()
                .replace(
                        "SELECT metadata\\.task AS task FROM metadata INNER JOIN tasks ON \\(\\(metadata\\.task=tasks\\._id\\)\\) WHERE \\(\\(\\(tasks\\.completed=0\\) AND \\(tasks\\.deleted=0\\) AND \\(tasks\\.hideUntil<\\(strftime\\(\\'%s\\',\\'now\\'\\)\\*1000\\)\\)\\) AND \\(metadata\\.key=\\'tags-tag\\'\\) AND \\(metadata\\.value".toRegex(),
                        "SELECT tags.task AS task FROM tags INNER JOIN tasks ON ((tags.task=tasks._id)) WHERE (((tasks.completed=0) AND (tasks.deleted=0) AND (tasks.hideUntil<(strftime('%s','now')*1000))) AND (tags.name")
                .replace(
                        "SELECT metadata\\.task AS task FROM metadata INNER JOIN tasks ON \\(\\(metadata\\.task=tasks\\._id\\)\\) WHERE \\(\\(\\(tasks\\.completed=0\\) AND \\(tasks\\.deleted=0\\) AND \\(tasks\\.hideUntil<\\(strftime\\(\\'%s\\',\\'now\\'\\)\\*1000\\)\\)\\) AND \\(metadata\\.key=\\'gtasks\\'\\) AND \\(metadata\\.value2".toRegex(),
                        "SELECT google_tasks.task AS task FROM google_tasks INNER JOIN tasks ON ((google_tasks.task=tasks._id)) WHERE (((tasks.completed=0) AND (tasks.deleted=0) AND (tasks.hideUntil<(strftime('%s','now')*1000))) AND (google_tasks.list_id")
                .replace("AND \\(metadata\\.deleted=0\\)".toRegex(), "")
    }

    private fun removeDuplicateTagData(tagData: List<TagData>) {
        if (tagData.size > 1) {
            tagDataDao.delete(tagData.subList(1, tagData.size))
        }
    }

    private fun removeDuplicateTagMetadata(uuid: String) {
        val metadatas = tagDao.getByTagUid(uuid)
        val metadataByTask: ImmutableListMultimap<Long, Tag> = Multimaps.index(metadatas) { it!!.task }
        for (key in metadataByTask.keySet()) {
            val tags = metadataByTask[key]
            if (tags.size > 1) {
                tagDao.delete(tags.subList(1, tags.size))
            }
        }
    }

    companion object {
        private const val V4_9_5 = 434
        private const val V5_3_0 = 491
        private const val V6_0_beta_1 = 522
        private const val V6_0_beta_2 = 523
        private const val V6_4 = 546
        private const val V6_7 = 585
        private const val V6_8_1 = 607
        private const val V6_9 = 608
        private const val V7_0 = 617
        const val V8_2 = 675
        private const val V8_5 = 700
        private const val V8_8 = 717
        private const val V8_10 = 735
        private const val V9_3 = 90300
        const val V9_6 = 90600
        const val V9_7 = 90700

        @JvmStatic
        fun getAndroidColor(context: Context, index: Int): Int {
            val legacyColor = getLegacyColor(index, 0)
            return if (legacyColor == 0) 0 else context.getColor(legacyColor)
        }

        @JvmStatic
        @ColorRes
        fun getLegacyColor(index: Int, def: Int): Int {
            return when (index) {
                0 -> R.color.blue_grey_500
                1 -> R.color.grey_900
                2 -> R.color.red_500
                3 -> R.color.pink_500
                4 -> R.color.purple_500
                5 -> R.color.deep_purple_500
                6 -> R.color.indigo_500
                7 -> R.color.blue_500
                8 -> R.color.light_blue_500
                9 -> R.color.cyan_500
                10 -> R.color.teal_500
                11 -> R.color.green_500
                12 -> R.color.light_green_500
                13 -> R.color.lime_500
                14 -> R.color.yellow_500
                15 -> R.color.amber_500
                16 -> R.color.orange_500
                17 -> R.color.deep_orange_500
                18 -> R.color.brown_500
                19 -> R.color.grey_500
                20 -> R.color.white_100
                else -> def
            }
        }
    }
}