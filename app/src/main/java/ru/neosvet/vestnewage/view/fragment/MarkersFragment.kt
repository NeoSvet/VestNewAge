package ru.neosvet.vestnewage.view.fragment

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.DataBase
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.data.MarkerItem
import ru.neosvet.vestnewage.databinding.MarkersFragmentBinding
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.ScreenUtils
import ru.neosvet.vestnewage.view.activity.MarkerActivity
import ru.neosvet.vestnewage.view.basic.NeoFragment
import ru.neosvet.vestnewage.view.basic.fromDpi
import ru.neosvet.vestnewage.view.dialog.InputDialog
import ru.neosvet.vestnewage.view.dialog.PromptDialog
import ru.neosvet.vestnewage.view.dialog.PromptResult
import ru.neosvet.vestnewage.view.list.MarkerAdapter
import ru.neosvet.vestnewage.view.list.MarkerHolder
import ru.neosvet.vestnewage.view.list.MarkersListHelper
import ru.neosvet.vestnewage.view.list.SwipeButton
import ru.neosvet.vestnewage.viewmodel.MarkersToiler
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler
import ru.neosvet.vestnewage.viewmodel.state.BasicState
import ru.neosvet.vestnewage.viewmodel.state.ListState
import ru.neosvet.vestnewage.viewmodel.state.MarkersState
import ru.neosvet.vestnewage.viewmodel.state.NeoState

class MarkersFragment : NeoFragment(), MarkersListHelper.Events, MarkerHolder.Events {
    private var binding: MarkersFragmentBinding? = null
    private lateinit var adapter: MarkerAdapter
    private val anRotate: Animation by lazy {
        initAnimation()
    }
    private var isRotate = false
    private var isCollections = true
    private var collectResult: Job? = null
    private val toiler: MarkersToiler
        get() = neotoiler as MarkersToiler
    private val markerResult = registerForActivityResult(
        StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK) {
            if (selectedIndex > -1)
                toiler.updateMarker(selectedIndex)
            else toiler.openList()
        }
    }
    private val importResult = registerForActivityResult(
        StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK) result.data?.let {
            parseFileResult(it, false)
        }
    }
    private val exportResult = registerForActivityResult(
        StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK) result.data?.let {
            parseFileResult(it, true)
        }
    }
    private var listHelper: MarkersListHelper? = null
    private var selectedIndex = -1
    private val selectedItem: MarkerItem?
        get() = if (selectedIndex == -1) null else adapter.items[selectedIndex]

    override val title: String
        get() = getString(R.string.markers)

    override fun initViewModel(): NeoToiler =
        ViewModelProvider(this)[MarkersToiler::class.java]

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = MarkersFragmentBinding.inflate(inflater, container, false).also {
        binding = it
    }.root

    override fun onViewCreated(savedInstanceState: Bundle?) {
        setViews()
        setToolbar()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        val type = childFragmentManager.findFragmentByTag(Const.DIALOG)?.let {
            if (it is PromptDialog) MarkersState.Type.DELETE
            else MarkersState.Type.RENAME
        } ?: MarkersState.Type.NONE
        val index = if (selectedIndex == -1) {
            binding?.run {
                val manager = rvList.layoutManager as GridLayoutManager
                manager.findFirstVisibleItemPosition()
            } ?: 0
        } else selectedIndex
        toiler.setStatus(
            MarkersState.Status(
                isRotate = isRotate,
                showIndex = index,
                dialog = type
            )
        )
        super.onSaveInstanceState(outState)
    }

    private fun setToolbar() = binding?.run {
        act?.setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
    }

    override fun onDestroyView() {
        collectResult?.cancel()
        binding = null
        super.onDestroyView()
    }

    override fun onBackPressed() = when {
        adapter.isEditor -> {
            toiler.restore()
            false
        }

        isCollections.not() -> {
            selectedIndex = -1
            toiler.openCollectionsList()
            false
        }

        else -> true
    }

    private fun initAnimation(): Animation {
        val animation = AnimationUtils.loadAnimation(requireContext(), R.anim.rotate)
        animation.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {}
            override fun onAnimationEnd(animation: Animation) {
                if (isRotate) binding?.ivMarker?.startAnimation(anRotate)
            }

            override fun onAnimationRepeat(animation: Animation) {}
        })
        return animation
    }

    private fun setViews() = binding?.run {
        setListEvents(rvList)
    }

    private fun getListHelper(): MarkersListHelper =
        listHelper ?: requireContext().let { ctx ->
            MarkersListHelper(
                events = this,
                buttonParameters = SwipeButton.Parameters(
                    size = ctx.fromDpi(R.dimen.double_indent),
                    padding = ctx.fromDpi(R.dimen.quarter_indent),
                    radius = ctx.fromDpi(R.dimen.half_indent).toFloat()
                ),
                leftButton = SwipeButton(
                    ContextCompat.getDrawable(ctx, R.drawable.ic_clear),
                    ContextCompat.getColor(ctx, android.R.color.holo_red_dark)
                ),
                rightButton = SwipeButton(
                    ContextCompat.getDrawable(ctx, R.drawable.ic_edit),
                    ContextCompat.getColor(ctx, android.R.color.holo_green_dark)
                )
            )
        }.also { listHelper = it }

    private fun startRotate() = binding?.run {
        isRotate = true
        pFileOperation.isVisible = true
        ivMarker.startAnimation(anRotate)
    }

    private fun stopRotate() = binding?.run {
        isRotate = false
        pFileOperation.isVisible = false
        ivMarker.clearAnimation()
    }

    private fun deleteDialog() = selectedItem?.title?.let { title ->
        PromptDialog.newInstance(getString(R.string.format_delete).format(title))
            .show(childFragmentManager, Const.DIALOG)
        collectResultDelete()
    }

    private fun collectResultDelete() {
        collectResult?.cancel()
        collectResult = lifecycleScope.launch {
            PromptDialog.result.collect {
                if (it == PromptResult.Yes)
                    toiler.delete(selectedIndex)
            }
        }
    }

    private fun renameDialog(oldName: String) {
        InputDialog.newInstance(getString(R.string.new_name), oldName)
            .show(childFragmentManager, Const.DIALOG)
        collectResultRename()
    }

    private fun collectResultRename() {
        collectResult?.cancel()
        collectResult = lifecycleScope.launch {
            InputDialog.result.collect {
                if (it != null) toiler.rename(selectedIndex, it)
            }
        }
    }

    override fun onClick(index: Int) {
        if (isBlocked) return
        selectedIndex = index
        when {
            isCollections -> {
                toiler.openMarkersList(index)
                selectedIndex = -1
            }

            adapter.items[index].title.contains("/") ->
                toiler.loadPage(index)

            else -> toiler.openPage(index)
        }
    }

    override fun onLongClick(index: Int) {
        if (isBlocked || (isCollections && index == 0)) return
        selectedIndex = index
        toiler.edit()
    }

    override fun onItemMove(holder: RecyclerView.ViewHolder) {
        listHelper?.startMove(holder)
    }

    private fun selectFile(isExport: Boolean) {
        val intent = Intent(
            if (isExport) Intent.ACTION_CREATE_DOCUMENT
            else Intent.ACTION_OPEN_DOCUMENT
        )
        intent.type = "*/*"
        if (isExport) {
            val date = DateUnit.initToday()
            intent.putExtra(
                Intent.EXTRA_TITLE, DataBase.MARKERS + " "
                        + date.toString().replace(".", "-")
            )
            exportResult.launch(intent)
        } else importResult.launch(intent)
    }

    private fun parseFileResult(data: Intent, isExport: Boolean) {
        data.dataString?.let { file ->
            if (isExport) {
                binding?.tvFileOperation?.text = getString(R.string.export)
                toiler.export(file)
            } else {
                binding?.tvFileOperation?.text = getString(R.string.import_)
                toiler.import(file)
            }
            startRotate()
        }
    }

    override fun onChangedOtherState(state: NeoState) {
        act?.hideToast()
        when (state) {
            is ListState.Move ->
                adapter.notifyItemMoved(state.indexFrom, state.indexTo)

            is BasicState.Message ->
                act?.showToast(state.message)

            is MarkersState.Primary ->
                openList(state)

            is MarkersState.Status ->
                restoreStatus(state)

            is ListState.Update<*> ->
                adapter.notifyItemChanged(state.index)

            is ListState.Remove ->
                adapter.notifyItemRemoved(state.index)

            is BasicState.Ready ->
                setStatus(false)

            is MarkersState.FinishExport ->
                doneExport(state.message)

            MarkersState.FinishImport -> {
                stopRotate()
                toiler.openCollectionsList()
                act?.showToast(getString(R.string.completed))
            }
        }
    }

    private fun restoreStatus(state: MarkersState.Status) {
        if (state.showIndex > -1) {
            selectedIndex = state.showIndex
            scrollToSelected()
        }
        when (state.dialog) {
            MarkersState.Type.NONE -> {}
            MarkersState.Type.RENAME -> collectResultRename()
            MarkersState.Type.DELETE -> collectResultDelete()
        }
        isRotate = state.isRotate
        if (isRotate) startRotate()
    }

    private fun scrollToSelected() {
        binding?.rvList?.let {
            lifecycleScope.launch {
                delay(100)
                it.post { it.scrollToPosition(selectedIndex) }
            }
        }
    }

    private fun openList(state: MarkersState.Primary) {
        isCollections = state.isCollections
        adapter = MarkerAdapter(
            events = this, isEditor = state.isEditor,
            items = state.list
        )

        val title: String
        val span: Int
        if (isCollections) {
            act?.supportActionBar?.setDisplayHomeAsUpEnabled(false)
            span = ScreenUtils.span
            title = state.title
        } else {
            act?.supportActionBar?.setDisplayHomeAsUpEnabled(true)
            span = 1
            title = String.format(getString(R.string.format_collection), state.title)
        }
        binding?.run {
            toolbar.title = title
            rvList.layoutManager = GridLayoutManager(requireContext(), span)
            rvList.adapter = adapter
            if (state.isEditor) {
                act?.setAction(R.drawable.ic_ok)
                getListHelper().attach(rvList, if (isCollections) 0 else -1)
            } else {
                act?.setAction(R.drawable.star)
                listHelper?.detach()
            }
        }
        if (selectedIndex > -1) scrollToSelected()

        if (adapter.itemCount == 0) {
            val msg = if (isCollections)
                getString(R.string.no_markers)
            else getString(R.string.collection_is_empty)
            act?.showStaticToast(msg)
        }
    }

    private fun doneExport(file: String) {
        stopRotate()
        if (childFragmentManager.findFragmentByTag(Const.FILE) == null)
            PromptDialog.newInstance(getString(R.string.send_file))
                .show(childFragmentManager, Const.FILE)
        collectResult?.cancel()
        collectResult = lifecycleScope.launch {
            PromptDialog.result.collect {
                if (it == PromptResult.Yes) {
                    val sendIntent = Intent(Intent.ACTION_SEND)
                    sendIntent.type = "text/plain"
                    sendIntent.putExtra(Intent.EXTRA_STREAM, Uri.parse(file))
                    startActivity(sendIntent)
                }
                toiler.clearStates()
            }
        }
    }

    override fun onAction(title: String) {
        if (isBlocked) return
        when (title) {
            getString(R.string.edit) -> {
                if (adapter.itemCount > if (isCollections) 1 else 0)
                    toiler.edit()
                else act?.showToast(getString(R.string.nothing_edit))
            }

            getString(R.string.import_) ->
                selectFile(false)

            getString(R.string.export) -> if (adapter.itemCount > 0)
                selectFile(true)

            else -> toiler.save()
        }
    }

    override fun onMove(fromIndex: Int, toIndex: Int) {
        toiler.move(fromIndex, toIndex)
    }

    override fun onSwipe(index: Int, toLeft: Boolean) {
        selectedIndex = index
        if (toLeft) {
            deleteDialog()
            return
        }
        selectedItem?.let { item ->
            if (isCollections) {
                renameDialog(item.title)
            } else {
                val marker = Intent(requireContext(), MarkerActivity::class.java)
                marker.putExtra(DataBase.ID, item.id)
                marker.putExtra(Const.LINK, item.data)
                markerResult.launch(marker)
            }
        }
    }
}