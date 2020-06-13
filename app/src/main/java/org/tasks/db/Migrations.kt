package org.tasks.db

import android.database.sqlite.SQLiteException
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.todoroo.astrid.api.FilterListItem.NO_ORDER
import timber.log.Timber

object Migrations {
    private val MIGRATION_35_36: Migration = object : Migration(35, 36) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `tagdata` ADD COLUMN `color` INTEGER DEFAULT -1")
        }
    }

    private val MIGRATION_36_37: Migration = object : Migration(36, 37) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `store` ADD COLUMN `deleted` INTEGER DEFAULT 0")
        }
    }

    private val MIGRATION_37_38: Migration = object : Migration(37, 38) {
        override fun migrate(database: SupportSQLiteDatabase) {
            try {
                database.execSQL("ALTER TABLE `store` ADD COLUMN `value4` TEXT DEFAULT -1")
            } catch (e: SQLiteException) {
                Timber.w(e)
            }
        }
    }

    private val MIGRATION_38_39: Migration = object : Migration(38, 39) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `notification` (`uid` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `task` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, `type` INTEGER NOT NULL)")
            database.execSQL(
                    "CREATE UNIQUE INDEX `index_notification_task` ON `notification` (`task`)")
        }
    }

    private val MIGRATION_46_47: Migration = object : Migration(46, 47) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `alarms` (`_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `task` INTEGER NOT NULL, `time` INTEGER NOT NULL)")
            database.execSQL(
                    "INSERT INTO `alarms` (`task`, `time`) SELECT `task`, `value` FROM `metadata` WHERE `key` = 'alarm' AND `deleted` = 0")
            database.execSQL("DELETE FROM `metadata` WHERE `key` = 'alarm'")
        }
    }

    private val MIGRATION_47_48: Migration = object : Migration(47, 48) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `locations` (`_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `task` INTEGER NOT NULL, `name` TEXT, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `radius` INTEGER NOT NULL)")
            database.execSQL("INSERT INTO `locations` (`task`, `name`, `latitude`, `longitude`, `radius`) "
                    + "SELECT `task`, `value`, `value2`, `value3`, `value4` FROM `metadata` WHERE `key` = 'geofence' AND `deleted` = 0")
            database.execSQL("DELETE FROM `metadata` WHERE `key` = 'geofence'")
        }
    }

    private val MIGRATION_48_49: Migration = object : Migration(48, 49) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tags` (`_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `task` INTEGER NOT NULL, `name` TEXT, `tag_uid` TEXT, `task_uid` TEXT)")
            database.execSQL("INSERT INTO `tags` (`task`, `name`, `tag_uid`, `task_uid`) "
                    + "SELECT `task`, `value`, `value2`, `value3` FROM `metadata` WHERE `key` = 'tags-tag' AND `deleted` = 0")
            database.execSQL("DELETE FROM `metadata` WHERE `key` = 'tags-tag'")
        }
    }

    private val MIGRATION_49_50: Migration = object : Migration(49, 50) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `google_tasks` (`_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `task` INTEGER NOT NULL, `remote_id` TEXT, `list_id` TEXT, `parent` INTEGER NOT NULL, `indent` INTEGER NOT NULL, `order` INTEGER NOT NULL, `remote_order` INTEGER NOT NULL, `last_sync` INTEGER NOT NULL, `deleted` INTEGER NOT NULL)")
            database.execSQL("INSERT INTO `google_tasks` (`task`, `remote_id`, `list_id`, `parent`, `indent`, `order`, `remote_order`, `last_sync`, `deleted`) "
                    + "SELECT `task`, `value`, `value2`, IFNULL(`value3`, 0), IFNULL(`value4`, 0), IFNULL(`value5`, 0), IFNULL(`value6`, 0), IFNULL(`value7`, 0), IFNULL(`deleted`, 0) FROM `metadata` WHERE `key` = 'gtasks'")
            database.execSQL("DROP TABLE IF EXISTS `metadata`")
        }
    }

    private val MIGRATION_50_51: Migration = object : Migration(50, 51) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `filters` (`_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT, `sql` TEXT, `values` TEXT, `criterion` TEXT)")
            database.execSQL("INSERT INTO `filters` (`title`, `sql`, `values`, `criterion`) "
                    + "SELECT `item`, `value`, `value2`, `value3` FROM `store` WHERE `type` = 'filter' AND `deleted` = 0")
            database.execSQL("DELETE FROM `store` WHERE `type` = 'filter'")
        }
    }
    private val MIGRATION_51_52: Migration = object : Migration(51, 52) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `google_task_lists` (`_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `remote_id` TEXT, `title` TEXT, `remote_order` INTEGER NOT NULL, `last_sync` INTEGER NOT NULL, `deleted` INTEGER NOT NULL, `color` INTEGER)")
            database.execSQL("INSERT INTO `google_task_lists` (`remote_id`, `title`, `remote_order`, `last_sync`, `color`, `deleted`) "
                    + "SELECT `item`, `value`, `value2`, `value3`, `value4`, `deleted` FROM `store` WHERE `type` = 'gtasks-list'")
            database.execSQL("DROP TABLE IF EXISTS `store`")
        }
    }

    private val MIGRATION_52_53: Migration = object : Migration(52, 53) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `tagdata` RENAME TO `tagdata-temp`")
            database.execSQL(
                    "CREATE TABLE `tagdata` (`_id` INTEGER PRIMARY KEY AUTOINCREMENT, `remoteId` TEXT, `name` TEXT, `color` INTEGER, `tagOrdering` TEXT)")
            database.execSQL("INSERT INTO `tagdata` (`remoteId`, `name`, `color`, `tagOrdering`) "
                    + "SELECT `remoteId`, `name`, `color`, `tagOrdering` FROM `tagdata-temp`")
            database.execSQL("DROP TABLE `tagdata-temp`")
            database.execSQL("ALTER TABLE `userActivity` RENAME TO `userActivity-temp`")
            database.execSQL(
                    "CREATE TABLE `userActivity` (`_id` INTEGER PRIMARY KEY AUTOINCREMENT, `remoteId` TEXT, `message` TEXT, `picture` TEXT, `target_id` TEXT, `created_at` INTEGER)")
            database.execSQL("INSERT INTO `userActivity` (`remoteId`, `message`, `picture`, `target_id`, `created_at`) "
                    + "SELECT `remoteId`, `message`, `picture`, `target_id`, `created_at` FROM `userActivity-temp`")
            database.execSQL("DROP TABLE `userActivity-temp`")
            database.execSQL("ALTER TABLE `task_attachments` RENAME TO `task_attachments-temp`")
            database.execSQL(
                    "CREATE TABLE `task_attachments` (`_id` INTEGER PRIMARY KEY AUTOINCREMENT, `remoteId` TEXT, `task_id` TEXT, `name` TEXT, `path` TEXT, `content_type` TEXT)")
            database.execSQL("INSERT INTO `task_attachments` (`remoteId`, `task_id`, `name`, `path`, `content_type`) "
                    + "SELECT `remoteId`, `task_id`, `name`, `path`, `content_type` FROM `task_attachments-temp`")
            database.execSQL("DROP TABLE `task_attachments-temp`")
        }
    }

    private val MIGRATION_53_54: Migration = object : Migration(53, 54) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // need to drop columns that were removed in the past
            database.execSQL("ALTER TABLE `task_list_metadata` RENAME TO `task_list_metadata-temp`")
            database.execSQL(
                    "CREATE TABLE `task_list_metadata` (`_id` INTEGER PRIMARY KEY AUTOINCREMENT, `remoteId` TEXT, `tag_uuid` TEXT, `filter` TEXT, `task_ids` TEXT)")
            database.execSQL("INSERT INTO `task_list_metadata` (`remoteId`, `tag_uuid`, `filter`, `task_ids`) "
                    + "SELECT `remoteId`, `tag_uuid`, `filter`, `task_ids` FROM `task_list_metadata-temp`")
            database.execSQL("DROP TABLE `task_list_metadata-temp`")
            database.execSQL("ALTER TABLE `tasks` RENAME TO `tasks-temp`")
            database.execSQL(
                    "CREATE TABLE `tasks` (`_id` INTEGER PRIMARY KEY AUTOINCREMENT, `title` TEXT, `importance` INTEGER, `dueDate` INTEGER, `hideUntil` INTEGER, `created` INTEGER, `modified` INTEGER, `completed` INTEGER, `deleted` INTEGER, `notes` TEXT, `estimatedSeconds` INTEGER, `elapsedSeconds` INTEGER, `timerStart` INTEGER, `notificationFlags` INTEGER, `notifications` INTEGER, `lastNotified` INTEGER, `snoozeTime` INTEGER, `recurrence` TEXT, `repeatUntil` INTEGER, `calendarUri` TEXT, `remoteId` TEXT)")
            database.execSQL("DROP INDEX `t_rid`")
            database.execSQL("CREATE UNIQUE INDEX `t_rid` ON `tasks` (`remoteId`)")
            database.execSQL("INSERT INTO `tasks` (`_id`, `title`, `importance`, `dueDate`, `hideUntil`, `created`, `modified`, `completed`, `deleted`, `notes`, `estimatedSeconds`, `elapsedSeconds`, `timerStart`, `notificationFlags`, `notifications`, `lastNotified`, `snoozeTime`, `recurrence`, `repeatUntil`, `calendarUri`, `remoteId`) "
                    + "SELECT `_id`, `title`, `importance`, `dueDate`, `hideUntil`, `created`, `modified`, `completed`, `deleted`, `notes`, `estimatedSeconds`, `elapsedSeconds`, `timerStart`, `notificationFlags`, `notifications`, `lastNotified`, `snoozeTime`, `recurrence`, `repeatUntil`, `calendarUri`, `remoteId` FROM `tasks-temp`")
            database.execSQL("DROP TABLE `tasks-temp`")
        }
    }

    private val MIGRATION_54_58: Migration = object : Migration(54, 58) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `caldav_account` (`_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT, `name` TEXT, `url` TEXT, `username` TEXT, `password` TEXT)")
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `caldav_calendar` (`_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `account` TEXT, `uuid` TEXT, `name` TEXT, `color` INTEGER NOT NULL, `ctag` TEXT, `url` TEXT)")
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `caldav_tasks` (`_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `task` INTEGER NOT NULL, `calendar` TEXT, `object` TEXT, `remote_id` TEXT, `etag` TEXT, `last_sync` INTEGER NOT NULL, `deleted` INTEGER NOT NULL, `vtodo` TEXT)")
        }
    }

    private val MIGRATION_58_59: Migration = object : Migration(58, 59) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `google_task_accounts` (`_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `account` TEXT, `error` TEXT)")
            database.execSQL("ALTER TABLE `google_task_lists` ADD COLUMN `account` TEXT")
            database.execSQL("ALTER TABLE `caldav_account` ADD COLUMN `error` TEXT")
        }
    }

    private val MIGRATION_59_60: Migration = object : Migration(59, 60) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `locations` ADD COLUMN `address` TEXT")
            database.execSQL("ALTER TABLE `locations` ADD COLUMN `phone` TEXT")
            database.execSQL("ALTER TABLE `locations` ADD COLUMN `url` TEXT")
            database.execSQL(
                    "ALTER TABLE `locations` ADD COLUMN `arrival` INTEGER DEFAULT 1 NOT NULL")
            database.execSQL(
                    "ALTER TABLE `locations` ADD COLUMN `departure` INTEGER DEFAULT 0 NOT NULL")
            database.execSQL("ALTER TABLE `notification` ADD COLUMN `location` INTEGER")
        }
    }

    private val MIGRATION_60_61: Migration = object : Migration(60, 61) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `places` (`place_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uid` TEXT, `name` TEXT, `address` TEXT, `phone` TEXT, `url` TEXT, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL)")
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `geofences` (`geofence_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `task` INTEGER NOT NULL, `place` TEXT, `radius` INTEGER NOT NULL, `arrival` INTEGER NOT NULL, `departure` INTEGER NOT NULL)")
            database.execSQL(
                    "INSERT INTO `places` (`place_id`, `uid`, `name`, `address`, `phone`, `url`, `latitude`, `longitude`) SELECT `_id`, hex(randomblob(16)), `name`, `address`, `phone`, `url`, `latitude`, `longitude` FROM `locations`")
            database.execSQL(
                    "INSERT INTO `geofences` (`geofence_id`, `task`, `place`, `radius`, `arrival`, `departure`) SELECT `_id`, `task`, `uid`, `radius`, `arrival`, `departure` FROM `locations` INNER JOIN `places` ON `_id` = `place_id`")
            database.execSQL("DROP TABLE `locations`")
        }
    }

    private val MIGRATION_61_62: Migration = object : Migration(61, 62) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `google_task_accounts` ADD COLUMN `etag` TEXT")
        }
    }

    private val MIGRATION_62_63: Migration = object : Migration(62, 63) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `google_tasks` RENAME TO `gt-temp`")
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `google_tasks` (`gt_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `gt_task` INTEGER NOT NULL, `gt_remote_id` TEXT, `gt_list_id` TEXT, `gt_parent` INTEGER NOT NULL, `gt_remote_parent` TEXT, `gt_moved` INTEGER NOT NULL, `gt_order` INTEGER NOT NULL, `gt_remote_order` INTEGER NOT NULL, `gt_last_sync` INTEGER NOT NULL, `gt_deleted` INTEGER NOT NULL)")
            database.execSQL("INSERT INTO `google_tasks` (`gt_id`, `gt_task`, `gt_remote_id`, `gt_list_id`, `gt_parent`, `gt_remote_parent`, `gt_moved`, `gt_order`, `gt_remote_order`, `gt_last_sync`, `gt_deleted`) "
                    + "SELECT `_id`, `task`, `remote_id`, `list_id`, `parent`, '', 0, `order`, `remote_order`, `last_sync`, `deleted` FROM `gt-temp`")
            database.execSQL("DROP TABLE `gt-temp`")
            database.execSQL("UPDATE `google_task_lists` SET `last_sync` = 0")
        }
    }

    private val MIGRATION_63_64: Migration = object : Migration(63, 64) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `caldav_tasks` RENAME TO `caldav-temp`")
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `caldav_tasks` (`cd_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `cd_task` INTEGER NOT NULL, `cd_calendar` TEXT, `cd_object` TEXT, `cd_remote_id` TEXT, `cd_etag` TEXT, `cd_last_sync` INTEGER NOT NULL, `cd_deleted` INTEGER NOT NULL, `cd_vtodo` TEXT)")
            database.execSQL("INSERT INTO `caldav_tasks` (`cd_id`, `cd_task`, `cd_calendar`, `cd_object`, `cd_remote_id`, `cd_etag`, `cd_last_sync`, `cd_deleted`, `cd_vtodo`)"
                    + "SELECT `_id`, `task`, `calendar`, `object`, `remote_id`, `etag`, `last_sync`, `deleted`, `vtodo` FROM `caldav-temp`")
            database.execSQL("DROP TABLE `caldav-temp`")
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `caldav_accounts` (`cda_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `cda_uuid` TEXT, `cda_name` TEXT, `cda_url` TEXT, `cda_username` TEXT, `cda_password` TEXT, `cda_error` TEXT)")
            database.execSQL("INSERT INTO `caldav_accounts` (`cda_id`, `cda_uuid`, `cda_name`, `cda_url`, `cda_username`, `cda_password`, `cda_error`) "
                    + "SELECT `_id`, `uuid`, `name`, `url`, `username`, `password`, `error` FROM `caldav_account`")
            database.execSQL("DROP TABLE `caldav_account`")
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `caldav_lists` (`cdl_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `cdl_account` TEXT, `cdl_uuid` TEXT, `cdl_name` TEXT, `cdl_color` INTEGER NOT NULL, `cdl_ctag` TEXT, `cdl_url` TEXT, `cdl_icon` INTEGER)")
            database.execSQL("INSERT INTO `caldav_lists` (`cdl_id`, `cdl_account`, `cdl_uuid`, `cdl_name`, `cdl_color`, `cdl_ctag`, `cdl_url`) "
                    + "SELECT `_id`, `account`, `uuid`, `name`, `color`, `ctag`, `url` FROM caldav_calendar")
            database.execSQL("DROP TABLE `caldav_calendar`")
            database.execSQL("ALTER TABLE `google_task_accounts` RENAME TO `gta-temp`")
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `google_task_accounts` (`gta_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `gta_account` TEXT, `gta_error` TEXT, `gta_etag` TEXT)")
            database.execSQL("INSERT INTO `google_task_accounts` (`gta_id`, `gta_account`, `gta_error`, `gta_etag`) "
                    + "SELECT `_id`, `account`, `error`, `etag` FROM `gta-temp`")
            database.execSQL("DROP TABLE `gta-temp`")
            database.execSQL("ALTER TABLE `google_task_lists` RENAME TO `gtl-temp`")
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `google_task_lists` (`gtl_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `gtl_account` TEXT, `gtl_remote_id` TEXT, `gtl_title` TEXT, `gtl_remote_order` INTEGER NOT NULL, `gtl_last_sync` INTEGER NOT NULL, `gtl_color` INTEGER, `gtl_icon` INTEGER)")
            database.execSQL("INSERT INTO `google_task_lists` (`gtl_id`, `gtl_account`, `gtl_remote_id`, `gtl_title`, `gtl_remote_order`, `gtl_last_sync`, `gtl_color`) "
                    + "SELECT `_id`, `account`, `remote_id`, `title`, `remote_order`, `last_sync`, `color` FROM `gtl-temp`")
            database.execSQL("DROP TABLE `gtl-temp`")
            database.execSQL("ALTER TABLE `filters` ADD COLUMN `f_color` INTEGER")
            database.execSQL("ALTER TABLE `filters` ADD COLUMN `f_icon` INTEGER")
            database.execSQL("ALTER TABLE `tagdata` ADD COLUMN `td_icon` INTEGER")
        }
    }

    private val MIGRATION_64_65: Migration = object : Migration(64, 65) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                    "ALTER TABLE `caldav_tasks` ADD COLUMN `cd_parent` INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE `caldav_tasks` ADD COLUMN `cd_remote_parent` TEXT")
        }
    }

    private val MIGRATION_65_66: Migration = object : Migration(65, 66) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("CREATE UNIQUE INDEX `place_uid` ON `places` (`uid`)")
            database.execSQL("CREATE INDEX `geo_task` ON `geofences` (`task`)")
            database.execSQL("CREATE INDEX `tag_task` ON `tags` (`task`)")
            database.execSQL("CREATE INDEX `gt_list_parent` ON `google_tasks` (`gt_list_id`, `gt_parent`)")
            database.execSQL("CREATE INDEX `gt_task` ON `google_tasks` (`gt_task`)")
            database.execSQL("CREATE INDEX `cd_calendar_parent` ON `caldav_tasks` (`cd_calendar`, `cd_parent`)")
            database.execSQL("CREATE INDEX `cd_task` ON `caldav_tasks` (`cd_task`)")
        }
    }

    private val MIGRATION_66_67: Migration = object : Migration(66, 67) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                    "ALTER TABLE `caldav_accounts` ADD COLUMN `cda_repeat` INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_67_68: Migration = object : Migration(67, 68) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                    "CREATE INDEX `active_and_visible` ON `tasks` (`completed`, `deleted`, `hideUntil`)")
        }
    }

    private val MIGRATION_68_69: Migration = object : Migration(68, 69) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                    "ALTER TABLE `tasks` ADD COLUMN `collapsed` INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_69_70: Migration = object : Migration(69, 70) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `tasks` ADD COLUMN `parent` INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE `tasks` ADD COLUMN `parent_uuid` TEXT")
            database.execSQL(
                    "UPDATE `tasks` SET `parent` = IFNULL(("
                            + " SELECT p.cd_task FROM caldav_tasks"
                            + "  INNER JOIN caldav_tasks AS p ON p.cd_remote_id = caldav_tasks.cd_remote_parent"
                            + "  WHERE caldav_tasks.cd_task = tasks._id"
                            + "    AND caldav_tasks.cd_deleted = 0"
                            + "    AND p.cd_calendar = caldav_tasks.cd_calendar"
                            + "    AND p.cd_deleted = 0), 0)")
            database.execSQL("ALTER TABLE `caldav_tasks` RENAME TO `caldav_tasks-temp`")
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `caldav_tasks` (`cd_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `cd_task` INTEGER NOT NULL, `cd_calendar` TEXT, `cd_object` TEXT, `cd_remote_id` TEXT, `cd_etag` TEXT, `cd_last_sync` INTEGER NOT NULL, `cd_deleted` INTEGER NOT NULL, `cd_vtodo` TEXT, `cd_remote_parent` TEXT)")
            database.execSQL("INSERT INTO `caldav_tasks` (`cd_id`, `cd_task`, `cd_calendar`, `cd_object`, `cd_remote_id`, `cd_etag`, `cd_last_sync`, `cd_deleted`, `cd_vtodo`, `cd_remote_parent`) "
                    + "SELECT `cd_id`, `cd_task`, `cd_calendar`, `cd_object`, `cd_remote_id`, `cd_etag`, `cd_last_sync`, `cd_deleted`, `cd_vtodo`, `cd_remote_parent` FROM `caldav_tasks-temp`")
            database.execSQL("DROP TABLE `caldav_tasks-temp`")
            database.execSQL("CREATE INDEX `cd_task` ON `caldav_tasks` (`cd_task`)")
        }
    }

    private val MIGRATION_70_71: Migration = object : Migration(70, 71) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `caldav_accounts` ADD COLUMN `cda_encryption_key` TEXT")
            database.execSQL(
                    "ALTER TABLE `caldav_accounts` ADD COLUMN `cda_account_type` INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_71_72: Migration = object : Migration(71, 72) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                    "ALTER TABLE `caldav_accounts` ADD COLUMN `cda_collapsed` INTEGER NOT NULL DEFAULT 0")
            database.execSQL(
                    "ALTER TABLE `google_task_accounts` ADD COLUMN `gta_collapsed` INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_72_73: Migration = object : Migration(72, 73) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `places` ADD COLUMN `place_color` INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE `places` ADD COLUMN `place_icon` INTEGER NOT NULL DEFAULT -1")
        }
    }

    private val MIGRATION_73_74: Migration = object : Migration(73, 74) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `tasks` RENAME TO `tasks-temp`")
            database.execSQL("DROP INDEX `t_rid`")
            database.execSQL("DROP INDEX `active_and_visible`")
            database.execSQL("CREATE TABLE IF NOT EXISTS `tasks` (`_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT, `importance` INTEGER NOT NULL, `dueDate` INTEGER NOT NULL, `hideUntil` INTEGER NOT NULL, `created` INTEGER NOT NULL, `modified` INTEGER NOT NULL, `completed` INTEGER NOT NULL, `deleted` INTEGER NOT NULL, `notes` TEXT, `estimatedSeconds` INTEGER NOT NULL, `elapsedSeconds` INTEGER NOT NULL, `timerStart` INTEGER NOT NULL, `notificationFlags` INTEGER NOT NULL, `notifications` INTEGER NOT NULL, `lastNotified` INTEGER NOT NULL, `snoozeTime` INTEGER NOT NULL, `recurrence` TEXT, `repeatUntil` INTEGER NOT NULL, `calendarUri` TEXT, `remoteId` TEXT, `collapsed` INTEGER NOT NULL, `parent` INTEGER NOT NULL, `parent_uuid` TEXT)")
            database.execSQL("INSERT INTO `tasks` (`_id`, `title`, `importance`, `dueDate`, `hideUntil`, `created`, `modified`, `completed`, `deleted`, `notes`, `estimatedSeconds`, `elapsedSeconds`, `timerStart`, `notificationFlags`, `notifications`, `lastNotified`, `snoozeTime`, `recurrence`, `repeatUntil`, `calendarUri`, `remoteId`, `collapsed`, `parent`, `parent_uuid`) "
                    + "SELECT `_id`, `title`, IFNULL(`importance`, 3), IFNULL(`dueDate`, 0), IFNULL(`hideUntil`, 0), IFNULL(`created`, 0), IFNULL(`modified`, 0), IFNULL(`completed`, 0), IFNULL(`deleted`, 0), `notes`, IFNULL(`estimatedSeconds`, 0), IFNULL(`elapsedSeconds`, 0), IFNULL(`timerStart`, 0), IFNULL(`notificationFlags`, 0), IFNULL(`notifications`, 0), IFNULL(`lastNotified`, 0), IFNULL(`snoozeTime`, 0), `recurrence`, IFNULL(`repeatUntil`, 0), `calendarUri`, `remoteId`, `collapsed`, IFNULL(`parent`, 0), `parent_uuid` FROM `tasks-temp`")
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `t_rid` ON `tasks` (`remoteId`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `active_and_visible` ON `tasks` (`completed`, `deleted`, `hideUntil`)")
            database.execSQL("DROP TABLE `tasks-temp`")
        }
    }

    private val MIGRATION_74_75: Migration = object : Migration(74, 75) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `caldav_tasks` ADD COLUMN `cd_order` INTEGER")
        }
    }

    private val MIGRATION_75_76: Migration = object : Migration(75, 76) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `tagdata` ADD COLUMN `td_order` INTEGER NOT NULL DEFAULT $NO_ORDER")
            database.execSQL("ALTER TABLE `caldav_lists` ADD COLUMN `cdl_order` INTEGER NOT NULL DEFAULT $NO_ORDER")
            database.execSQL("ALTER TABLE `filters` ADD COLUMN `f_order` INTEGER NOT NULL DEFAULT $NO_ORDER")
            database.execSQL("ALTER TABLE `places` ADD COLUMN `place_order` INTEGER NOT NULL DEFAULT $NO_ORDER")
        }
    }

    val MIGRATIONS = arrayOf(
            MIGRATION_35_36,
            MIGRATION_36_37,
            MIGRATION_37_38,
            MIGRATION_38_39,
            noop(39, 46),
            MIGRATION_46_47,
            MIGRATION_47_48,
            MIGRATION_48_49,
            MIGRATION_49_50,
            MIGRATION_50_51,
            MIGRATION_51_52,
            MIGRATION_52_53,
            MIGRATION_53_54,
            MIGRATION_54_58,
            MIGRATION_58_59,
            MIGRATION_59_60,
            MIGRATION_60_61,
            MIGRATION_61_62,
            MIGRATION_62_63,
            MIGRATION_63_64,
            MIGRATION_64_65,
            MIGRATION_65_66,
            MIGRATION_66_67,
            MIGRATION_67_68,
            MIGRATION_68_69,
            MIGRATION_69_70,
            MIGRATION_70_71,
            MIGRATION_71_72,
            MIGRATION_72_73,
            MIGRATION_73_74,
            MIGRATION_74_75,
            MIGRATION_75_76
    )

    private fun noop(from: Int, to: Int): Migration {
        return object : Migration(from, to) {
            override fun migrate(database: SupportSQLiteDatabase) {}
        }
    }
}