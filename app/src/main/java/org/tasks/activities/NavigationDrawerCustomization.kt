package org.tasks.activities

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.view.MenuItem
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ItemTouchHelper.*
import androidx.recyclerview.widget.ItemTouchHelper.Callback.makeMovementFlags
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.todoroo.astrid.adapter.FilterViewHolder
import com.todoroo.astrid.adapter.NavigationDrawerAdapter
import com.todoroo.astrid.api.*
import com.todoroo.astrid.api.FilterListItem.Type.ITEM
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import org.tasks.LocalBroadcastManager
import org.tasks.R
import org.tasks.caldav.BaseCaldavCalendarSettingsActivity
import org.tasks.data.*
import org.tasks.databinding.ActivityTagOrganizerBinding
import org.tasks.dialogs.NewFilterDialog.Companion.newFilterDialog
import org.tasks.filters.FilterProvider
import org.tasks.filters.NavigationDrawerAction
import org.tasks.filters.PlaceFilter
import org.tasks.injection.ActivityComponent
import org.tasks.injection.ThemedInjectingAppCompatActivity
import org.tasks.preferences.Preferences
import org.tasks.ui.NavigationDrawerFragment.Companion.REQUEST_NEW_FILTER
import javax.inject.Inject

class NavigationDrawerCustomization : ThemedInjectingAppCompatActivity(), Toolbar.OnMenuItemClickListener {

    @Inject lateinit var filterProvider: FilterProvider
    @Inject lateinit var adapter: NavigationDrawerAdapter
    @Inject lateinit var localBroadcastManager: LocalBroadcastManager
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var tagDataDao: TagDataDao
    @Inject lateinit var googleTaskListDao: GoogleTaskListDao
    @Inject lateinit var filterDao: FilterDao
    @Inject lateinit var caldavDao: CaldavDao
    @Inject lateinit var locationDao: LocationDao

    private lateinit var binding: ActivityTagOrganizerBinding
    private lateinit var toolbar: Toolbar
    private var disposables: CompositeDisposable? = null
    private val refreshReceiver = RefreshReceiver()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityTagOrganizerBinding.inflate(layoutInflater)

        setContentView(binding.root)

        toolbar = binding.toolbar.toolbar

        toolbar.title = getString(R.string.manage_lists)
        toolbar.navigationIcon = getDrawable(R.drawable.ic_outline_arrow_back_24px)
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.setOnMenuItemClickListener(this)
        toolbar.inflateMenu(R.menu.menu_nav_drawer_customization)
        themeColor.apply(toolbar)
        themeColor.applyToSystemBars(this)

        adapter.setOnClick(this::onClick)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        val itemTouchHelper = ItemTouchHelper(ItemTouchHelperCallback())
        itemTouchHelper.attachToRecyclerView(binding.recyclerView)
    }

    override fun onPause() {
        super.onPause()
        localBroadcastManager.unregisterReceiver(refreshReceiver)
    }

    override fun onStart() {
        super.onStart()
        disposables = CompositeDisposable()
    }

    override fun onStop() {
        super.onStop()
        disposables?.dispose()
    }

    override fun onResume() {
        super.onResume()
        localBroadcastManager.registerRefreshListReceiver(refreshReceiver)
        updateFilters()
    }

    private fun updateFilters() =
            disposables?.add(
                    Single.fromCallable {
                        filterProvider.drawerCustomizationItems.apply {
                            forEach { f -> f.count = 0 }
                        }
                    }
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(adapter::submitList))

    private fun onClick(item: FilterListItem?) {
        if (item is NavigationDrawerAction) {
            when (item.requestCode) {
                REQUEST_NEW_FILTER ->
                    newFilterDialog().show(supportFragmentManager, FRAG_TAG_NEW_FILTER)
                else -> startActivity(item.intent)
            }
        } else {
            when (item) {
                is GtasksFilter ->
                    Intent(this, GoogleTaskListSettingsActivity::class.java)
                            .putExtra(GoogleTaskListSettingsActivity.EXTRA_STORE_DATA, item.list)
                            .apply(this::startActivity)
                is CaldavFilter ->
                    caldavDao.getAccountByUuid(item.account)?.let {
                        Intent(this, it.listSettingsClass())
                                .putExtra(BaseCaldavCalendarSettingsActivity.EXTRA_CALDAV_CALENDAR, item.calendar)
                                .apply(this::startActivity)
                    }
                is CustomFilter ->
                    Intent(this, FilterSettingsActivity::class.java)
                            .putExtra(FilterSettingsActivity.TOKEN_FILTER, item)
                            .apply(this::startActivity)
                is TagFilter ->
                    Intent(this, TagSettingsActivity::class.java)
                            .putExtra(TagSettingsActivity.EXTRA_TAG_DATA, item.tagData)
                            .apply(this::startActivity)
                is PlaceFilter ->
                    Intent(this, PlaceSettingsActivity::class.java)
                            .putExtra(PlaceSettingsActivity.EXTRA_PLACE, item.place as Parcelable)
                            .apply(this::startActivity)
            }
        }
    }

    override fun inject(component: ActivityComponent) = component.inject(this)

    override fun onMenuItemClick(item: MenuItem): Boolean {
        return if (item.itemId == R.id.reset_sort) {
            filterDao.resetOrders()
            caldavDao.resetOrders()
            googleTaskListDao.resetOrders()
            tagDataDao.resetOrders()
            locationDao.resetOrders()
            updateFilters()
            true
        } else {
            false
        }
    }

    private inner class RefreshReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            val action = intent?.action
            if (LocalBroadcastManager.REFRESH == action || LocalBroadcastManager.REFRESH_LIST == action) {
                updateFilters()
            }
        }
    }

    private inner class ItemTouchHelperCallback : ItemTouchHelper.Callback() {
        private var from = -1
        private var to = -1

        override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) =
                if (viewHolder.itemViewType == ITEM.ordinal) ALLOW_DRAGGING else NO_MOVEMENT

        override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
            super.onSelectedChanged(viewHolder, actionState)
            if (actionState == ACTION_STATE_DRAG) {
                adapter.dragging = true
                (viewHolder as FilterViewHolder).setMoving(true)
            }
        }

        override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
            if (target !is FilterViewHolder) {
                return false
            }
            val sourceFilter = (viewHolder as FilterViewHolder).filter
            val targetFilter = target.filter
            if (sourceFilter::class.java != targetFilter::class.java) {
                return false
            }
            if (sourceFilter is GtasksFilter && targetFilter is GtasksFilter) {
                if (sourceFilter.account != targetFilter.account) {
                    return false
                }
            } else if (sourceFilter is CaldavFilter && targetFilter is CaldavFilter) {
                if (sourceFilter.account != targetFilter.account) {
                    return false
                }
            }
            val sourcePosition = viewHolder.adapterPosition
            if (from == -1) {
                from = sourcePosition
            }
            to = target.adapterPosition

            adapter.notifyItemMoved(sourcePosition, to)
            return true
        }

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)

            (viewHolder as FilterViewHolder).setMoving(false)

            if (from != to) {
                viewHolder.filter.order = to
                adapter.items
                        .apply {
                            removeAt(from)
                            add(to, viewHolder.filter)
                        }
                        .filter(getPredicate(viewHolder.filter))
                        .forEachIndexed { order, filter ->
                            filter.order = order
                            setOrder(order, filter)
                        }
                updateFilters()
            }

            adapter.dragging = false
            from = -1
            to = -1
        }

        private fun getPredicate(item: FilterListItem): (FilterListItem) -> Boolean = { f ->
            item::class.java == f::class.java && when (item) {
                is GtasksFilter -> item.account == (f as GtasksFilter).account
                is CaldavFilter -> item.account == (f as CaldavFilter).account
                else -> true
            }
        }

        private fun setOrder(order: Int, filter: FilterListItem) {
            when (filter) {
                is GtasksFilter -> googleTaskListDao.setOrder(filter.list.id, order)
                is CaldavFilter -> caldavDao.setOrder(filter.calendar.id, order)
                is TagFilter -> tagDataDao.setOrder(filter.tagData.id!!, order)
                is CustomFilter -> filterDao.setOrder(filter.id, order)
                is PlaceFilter -> locationDao.setOrder(filter.place.id, order)
            }
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
    }

    companion object {
        private val NO_MOVEMENT = makeMovementFlags(0, 0)
        private val ALLOW_DRAGGING = makeMovementFlags(UP or DOWN, 0)
        private const val FRAG_TAG_NEW_FILTER = "frag_tag_new_filter"
    }
}