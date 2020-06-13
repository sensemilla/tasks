package org.tasks.activities

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import butterknife.BindView
import butterknife.OnClick
import butterknife.OnTextChanged
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.todoroo.andlib.sql.Field
import com.todoroo.andlib.sql.Query
import com.todoroo.andlib.sql.UnaryCriterion
import com.todoroo.andlib.utility.AndroidUtilities
import com.todoroo.astrid.activity.MainActivity
import com.todoroo.astrid.activity.TaskListFragment
import com.todoroo.astrid.api.*
import com.todoroo.astrid.core.CriterionInstance
import com.todoroo.astrid.core.CustomFilterAdapter
import com.todoroo.astrid.core.CustomFilterItemTouchHelper
import com.todoroo.astrid.dao.Database
import com.todoroo.astrid.dao.TaskDao.TaskCriteria.activeAndVisible
import com.todoroo.astrid.data.Task
import org.tasks.R
import org.tasks.Strings
import org.tasks.data.Filter
import org.tasks.data.FilterDao
import org.tasks.filters.FilterCriteriaProvider
import org.tasks.injection.ActivityComponent
import org.tasks.locale.Locale
import java.util.*
import javax.inject.Inject
import kotlin.math.max

class FilterSettingsActivity : BaseListSettingsActivity() {
    @Inject lateinit var filterDao: FilterDao
    @Inject lateinit var locale: Locale
    @Inject lateinit var database: Database
    @Inject lateinit var filterCriteriaProvider: FilterCriteriaProvider

    @BindView(R.id.name) 
    lateinit var name: TextInputEditText
    
    @BindView(R.id.name_layout) 
    lateinit var nameLayout: TextInputLayout
    
    @BindView(R.id.recycler_view) 
    lateinit var recyclerView: RecyclerView

    @BindView(R.id.fab)
    lateinit var fab: ExtendedFloatingActionButton
    
    private var filter: CustomFilter? = null
    private lateinit var adapter: CustomFilterAdapter
    private lateinit var criteria: MutableList<CriterionInstance>

    override fun onCreate(savedInstanceState: Bundle?) {
        filter = intent.getParcelableExtra(TOKEN_FILTER)
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null && filter != null) {
            selectedColor = filter!!.tint
            selectedIcon = filter!!.icon
            name.setText(filter!!.listingTitle)
        }
        when {
            savedInstanceState != null -> {
                criteria = CriterionInstance.fromString(
                        filterCriteriaProvider, savedInstanceState.getString(EXTRA_CRITERIA))
            }
            filter != null -> {
                criteria = CriterionInstance.fromString(filterCriteriaProvider, filter!!.criterion)
            }
            intent.hasExtra(EXTRA_CRITERIA) -> {
                name.setText(intent.getStringExtra(EXTRA_TITLE))
                criteria = CriterionInstance.fromString(
                        filterCriteriaProvider, intent.getStringExtra(EXTRA_CRITERIA))
            }
            else -> {
                val instance = CriterionInstance()
                instance.criterion = filterCriteriaProvider.startingUniverse
                instance.type = CriterionInstance.TYPE_UNIVERSE
                criteria = mutableListOf(instance)
            }
        }
        adapter = CustomFilterAdapter(criteria, locale) { replaceId: String -> onClick(replaceId) }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        ItemTouchHelper(
                CustomFilterItemTouchHelper(this, this::onMove, this::onDelete, this::updateList))
                .attachToRecyclerView(recyclerView)
        fab.isExtended = isNew || adapter.itemCount <= 1
        if (isNew) {
            toolbar.inflateMenu(R.menu.menu_help)
        }
        updateList()
        updateTheme()
    }

    private fun onDelete(index: Int) {
        criteria.removeAt(index)
        updateList()
        return
    }

    private fun onMove(from: Int, to: Int) {
        val criterion = criteria.removeAt(from)
        criteria.add(to, criterion)
        adapter.notifyItemMoved(from, to)
        return
    }

    private fun onClick(replaceId: String) {
        val criterionInstance = criteria.find { it.id == replaceId }!!
        val view = layoutInflater.inflate(R.layout.dialog_custom_filter_row_edit, recyclerView, false)
        val group: MaterialButtonToggleGroup = view.findViewById(R.id.button_toggle)
        val selected = getSelected(criterionInstance)
        group.check(selected)
        dialogBuilder
                .newDialog(criterionInstance.titleFromCriterion)
                .setView(view)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    criterionInstance.type = getType(group.checkedButtonId)
                    updateList()
                }
                .setNeutralButton(R.string.help) { _, _ -> help() }
                .show()
        return
    }

    private fun getSelected(instance: CriterionInstance): Int {
        return when (instance.type) {
            CriterionInstance.TYPE_ADD -> R.id.button_or
            CriterionInstance.TYPE_SUBTRACT -> R.id.button_not
            else -> R.id.button_and
        }
    }

    private fun getType(selected: Int): Int {
        return when (selected) {
            R.id.button_or -> CriterionInstance.TYPE_ADD
            R.id.button_not -> CriterionInstance.TYPE_SUBTRACT
            else -> CriterionInstance.TYPE_INTERSECT
        }
    }

    @OnClick(R.id.fab)
    fun addCriteria() {
        AndroidUtilities.hideKeyboard(this)
        fab.shrink()
        val all = filterCriteriaProvider.all
        val names = all.map(CustomFilterCriterion::getName)
        dialogBuilder.newDialog()
                .setItems(names) { dialog: DialogInterface, which: Int ->
                    val instance = CriterionInstance()
                    instance.criterion = all[which]
                    showOptionsFor(instance, Runnable {
                        criteria.add(instance)
                        updateList()
                    })
                    dialog.dismiss()
                }
                .show()
    }

    /** Show options menu for the given criterioninstance  */
    private fun showOptionsFor(item: CriterionInstance, onComplete: Runnable?) {
        val dialog = dialogBuilder.newDialog(item.criterion.name)
        if (item.criterion is MultipleSelectCriterion) {
            val multiSelectCriterion = item.criterion as MultipleSelectCriterion
            val titles = multiSelectCriterion.entryTitles
            val listener = DialogInterface.OnClickListener { _: DialogInterface?, which: Int ->
                item.selectedIndex = which
                onComplete?.run()
            }
            dialog.setItems(titles, listener)
        } else if (item.criterion is TextInputCriterion) {
            val textInCriterion = item.criterion as TextInputCriterion
            val frameLayout = FrameLayout(this)
            frameLayout.setPadding(10, 0, 10, 0)
            val editText = EditText(this)
            editText.setText(item.selectedText)
            editText.hint = textInCriterion.hint
            frameLayout.addView(
                    editText,
                    FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
            dialog
                    .setView(frameLayout)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        item.selectedText = editText.text.toString()
                        onComplete?.run()
                    }
        }
        dialog.show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(EXTRA_CRITERIA, CriterionInstance.serialize(criteria))
    }

    override fun isNew(): Boolean {
        return filter == null
    }

    override fun getToolbarTitle(): String {
        return if (isNew) getString(R.string.FLA_new_filter) else filter!!.listingTitle
    }

    @OnTextChanged(R.id.name)
    fun onTextChanged() {
        nameLayout.error = null
    }

    override fun inject(component: ActivityComponent) = component.inject(this)

    override fun save() {
        val newName = newName
        if (Strings.isNullOrEmpty(newName)) {
            nameLayout.error = getString(R.string.name_cannot_be_empty)
            return
        }
        if (hasChanges()) {
            val f = Filter()
            f.title = newName
            f.setColor(selectedColor)
            f.setIcon(selectedIcon)
            f.values = AndroidUtilities.mapToSerializedString(values)
            f.criterion = CriterionInstance.serialize(criteria)
            f.setSql(sql)
            if (isNew) {
                f.id = filterDao.insert(f)
            } else {
                filter?.let {
                    f.id = it.id
                    f.order = it.order
                    filterDao.update(f)
                }
            }
            setResult(
                    Activity.RESULT_OK,
                    Intent(TaskListFragment.ACTION_RELOAD)
                            .putExtra(MainActivity.OPEN_FILTER, CustomFilter(f)))
        }
        finish()
    }

    private val newName: String
        get() = name.text.toString().trim { it <= ' ' }

    override fun hasChanges(): Boolean {
        return if (isNew) {
            (!Strings.isNullOrEmpty(newName)
                    || selectedColor != 0 || selectedIcon != -1 || criteria.size > 1)
        } else newName != filter!!.listingTitle
                || selectedColor != filter!!.tint || selectedIcon != filter!!.icon || CriterionInstance.serialize(criteria) != filter!!.criterion
                || values != filter!!.valuesForNewTasks
                || sql != filter!!.originalSqlQuery
    }

    override fun finish() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(name.windowToken, 0)
        super.finish()
    }

    override fun getLayout(): Int {
        return R.layout.filter_settings_activity
    }

    override fun delete() {
        filterDao.delete(filter!!.id)
        setResult(
                Activity.RESULT_OK, Intent(TaskListFragment.ACTION_DELETED).putExtra(TOKEN_FILTER, filter))
        finish()
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        return if (item.itemId == R.id.menu_help) {
            help()
            true
        } else {
            super.onMenuItemClick(item)
        }
    }

    private fun help() {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://tasks.org/filters")))
    }

    private fun updateList() {
        var max = 0
        var last = -1
        val sql = StringBuilder(Query.select(Field.COUNT).from(Task.TABLE).toString())
                .append(" WHERE ")
        for (instance in criteria) {
            var value = instance.valueFromCriterion
            if (value == null && instance.criterion.sql != null && instance.criterion.sql.contains("?")) {
                value = ""
            }
            when (instance.type) {
                CriterionInstance.TYPE_ADD -> sql.append("OR ")
                CriterionInstance.TYPE_SUBTRACT -> sql.append("AND NOT ")
                CriterionInstance.TYPE_INTERSECT -> sql.append("AND ")
            }

            // special code for all tasks universe
            if (instance.type == CriterionInstance.TYPE_UNIVERSE || instance.criterion.sql == null) {
                sql.append(activeAndVisible()).append(' ')
            } else {
                var subSql: String? = instance.criterion.sql.replace("?", UnaryCriterion.sanitize(value))
                subSql = PermaSql.replacePlaceholdersForQuery(subSql)
                sql.append(Task.ID).append(" IN (").append(subSql).append(") ")
            }
            database.query(sql.toString(), null).use { cursor ->
                cursor.moveToNext()
                instance.start = if (last == -1) cursor.getInt(0) else last
                instance.end = cursor.getInt(0)
                last = instance.end
                max = max(max, last)
            }
        }
        for (instance in criteria) {
            instance.max = max
        }
        adapter.submitList(criteria)
    }

    private fun getValue(instance: CriterionInstance): String? {
        var value = instance.valueFromCriterion
        if (value == null && instance.criterion.sql != null && instance.criterion.sql.contains("?")) {
            value = ""
        }
        return value
    }

    // special code for all tasks universe
    private val sql: String
        get() {
            val sql = StringBuilder(" WHERE ")
            for (instance in criteria) {
                val value = getValue(instance)
                when (instance.type) {
                    CriterionInstance.TYPE_ADD -> sql.append("OR ")
                    CriterionInstance.TYPE_SUBTRACT -> sql.append("AND NOT ")
                    CriterionInstance.TYPE_INTERSECT -> sql.append("AND ")
                }

                // special code for all tasks universe
                if (instance.type == CriterionInstance.TYPE_UNIVERSE || instance.criterion.sql == null) {
                    sql.append(activeAndVisible()).append(' ')
                } else {
                    val subSql = instance.criterion.sql.replace("?", UnaryCriterion.sanitize(value!!))
                    sql.append(Task.ID).append(" IN (").append(subSql).append(") ")
                }
            }
            return sql.toString()
        }

    private val values: Map<String, Any>
        get() {
            val values: MutableMap<String, Any> = HashMap()
            for (instance in criteria) {
                val value = getValue(instance)
                if (instance.criterion.valuesForNewTasks != null
                        && instance.type == CriterionInstance.TYPE_INTERSECT) {
                    for ((key, value1) in instance.criterion.valuesForNewTasks) {
                        values[key.replace("?", value!!)] = value1.toString().replace("?", value)
                    }
                }
            }
            return values
        }

    companion object {
        const val TOKEN_FILTER = "token_filter"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_CRITERIA = "extra_criteria"
    }
}