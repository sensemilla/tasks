package org.tasks.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.todoroo.andlib.utility.AndroidUtilities
import com.todoroo.astrid.api.FilterListItem.NO_ORDER
import org.tasks.Strings
import org.tasks.themes.CustomIcons.FILTER

@Entity(tableName = "filters")
class Filter {
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    @Transient
    var id: Long = 0

    @ColumnInfo(name = "title")
    var title: String? = null

    @ColumnInfo(name = "sql")
    private var sql: String? = null

    @ColumnInfo(name = "values")
    var values: String? = null

    @ColumnInfo(name = "criterion")
    var criterion: String? = null

    @ColumnInfo(name = "f_color")
    private var color: Int? = 0

    @ColumnInfo(name = "f_icon")
    private var icon: Int? = -1

    @ColumnInfo(name = "f_order")
    var order = NO_ORDER

    fun getSql(): String {
        // TODO: replace dirty hack for missing column
        return sql!!.replace("tasks.userId=0", "1")
    }

    fun setSql(sql: String?) {
        this.sql = sql
    }

    val valuesAsMap: Map<String, Any>?
        get() = if (Strings.isNullOrEmpty(values)) null else AndroidUtilities.mapFromSerializedString(values)

    fun getColor(): Int? {
        return (if (color == null) 0 else color)!!
    }

    fun setColor(color: Int?) {
        this.color = color
    }

    fun getIcon(): Int? {
        return (if (icon == null) FILTER else icon!!)
    }

    fun setIcon(icon: Int?) {
        this.icon = icon
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Filter) return false

        if (id != other.id) return false
        if (title != other.title) return false
        if (sql != other.sql) return false
        if (values != other.values) return false
        if (criterion != other.criterion) return false
        if (color != other.color) return false
        if (icon != other.icon) return false
        if (order != other.order) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + (title?.hashCode() ?: 0)
        result = 31 * result + (sql?.hashCode() ?: 0)
        result = 31 * result + (values?.hashCode() ?: 0)
        result = 31 * result + (criterion?.hashCode() ?: 0)
        result = 31 * result + (color ?: 0)
        result = 31 * result + (icon ?: 0)
        result = 31 * result + order
        return result
    }

    override fun toString(): String {
        return "Filter(id=$id, title=$title, sql=$sql, values=$values, criterion=$criterion, color=$color, icon=$icon, order=$order)"
    }
}