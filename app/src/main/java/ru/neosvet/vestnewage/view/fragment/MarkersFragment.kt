package ru.neosvet.vestnewage.view.fragment

import android.annotation.SuppressLint
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
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import kotlinx.coroutines.Job
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
import ru.neosvet.vestnewage.view.dialog.InputDialog
import ru.neosvet.vestnewage.view.dialog.PromptDialog
import ru.neosvet.vestnewage.view.dialog.PromptResult
import ru.neosvet.vestnewage.view.list.MarkerAdapter
import ru.neosvet.vestnewage.viewmodel.MarkersToiler
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler
import ru.neosvet.vestnewage.viewmodel.state.BasicState
import ru.neosvet.vestnewage.viewmodel.state.ListState
import ru.neosvet.vestnewage.viewmodel.state.MarkersState
import ru.neosvet.vestnewage.viewmodel.state.NeoState

class MarkersFragment : NeoFragment() {
    private var binding: MarkersFragmentBinding? = null
    private val adapter: MarkerAdapter by lazy {
        MarkerAdapter(toiler::onClick, this::onLongClick)
    }
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
        if (result.resultCode == Activity.RESULT_OK)
            toiler.updateMarkersList()
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
        toiler.setStatus(
            MarkersState.Status(
                isRotate = isRotate,
                selectedIndex = adapter.selectedIndex,
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

    override fun onBackPressed(): Boolean {
        if (adapter.selectedIndex > -1) {
            unSelected()
            return false
        }
        if (isCollections.not()) {
            toiler.openCollectionsList()
            return false
        }
        return true
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

    @SuppressLint("ClickableViewAccessibility")
    private fun setViews() = binding?.content?.run {
        rvMarker.adapter = adapter
        setListEvents(rvMarker)
        bOk.setOnClickListener {
            toiler.saveChange()
            unSelected()
        }
        bTop.setOnClickListener {
            toiler.move(adapter.selectedIndex, adapter.selectedIndex - 1)
        }
        bBottom.setOnClickListener {
            toiler.move(adapter.selectedIndex, adapter.selectedIndex + 1)
        }
        bEdit.setOnClickListener {
            adapter.selectedItem?.let { item ->
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
        bDelete.setOnClickListener { deleteDialog() }
    }

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

    private fun deleteDialog() = adapter.selectedItem?.title?.let { title ->
        binding?.content?.rvMarker?.smoothScrollToPosition(adapter.selectedIndex)
        PromptDialog.newInstance(getString(R.string.format_delete).format(title))
            .show(childFragmentManager, Const.DIALOG)
        collectResultDelete()
    }

    private fun collectResultDelete() {
        collectResult?.cancel()
        collectResult = lifecycleScope.launch {
            PromptDialog.result.collect {
                if (it == PromptResult.Yes)
                    toiler.delete(adapter.selectedIndex)
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
                if (it != null) toiler.rename(adapter.selectedIndex, it)
            }
        }
    }

    private fun onLongClick(index: Int) {
        if (isCollections.not() || index > 0) {
            adapter.selected(index)
            goToEdit()
        }
    }

    private fun goToEdit() {
        binding?.content?.pEdit?.let { p ->
            if (p.isVisible) return
            act?.let {
                it.lockHead()
                it.blocked()
            }
            p.isVisible = true
        }
    }

    private fun unSelected() {
        if (adapter.selectedIndex > -1)
            adapter.selected(-1)
        toiler.cancelChange()
        binding?.content?.pEdit?.isVisible = false
        act?.let {
            it.unlockHead()
            it.unblocked()
        }
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
                toiler.startExport(file)
            } else {
                binding?.tvFileOperation?.text = getString(R.string.import_)
                toiler.startImport(file)
            }
            startRotate()
        }
    }

    override fun onChangedOtherState(state: NeoState) {
        act?.hideToast()
        when (state) {
            is BasicState.Message ->
                act?.showToast(state.message)

            is MarkersState.Primary ->
                updateList(state)

            is MarkersState.Status ->
                restoreStatus(state)

            is ListState.Update<*> ->
                adapter.update(state.index, state.item as MarkerItem)

            is ListState.Remove ->
                adapter.remove(state.index, if (isCollections) 1 else 0)

            is ListState.Move ->
                adapter.notifyItemMoved(state.indexFrom, state.indexTo)

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
        if (state.selectedIndex > -1)
            adapter.selected(state.selectedIndex)
        when (state.dialog) {
            MarkersState.Type.NONE -> {}
            MarkersState.Type.RENAME -> collectResultRename()
            MarkersState.Type.DELETE -> collectResultDelete()
        }
        isRotate = state.isRotate
        if (isRotate) startRotate()
    }

    private fun updateList(state: MarkersState.Primary) {
        isCollections = state.isCollections
        adapter.setItems(state.list)

        if (adapter.selectedIndex == -1)
            setStatus(false)

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
            content.rvMarker.layoutManager = GridLayoutManager(requireContext(), span)
        }

        if (adapter.itemCount == 0) {
            val msg = if (isCollections)
                getString(R.string.no_markers)
            else
                getString(R.string.collection_is_empty)
            act?.showStaticToast(msg)
            unSelected()
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

    private fun canEdit(): Boolean {
        val i = if (isCollections) 1 else 0
        if (adapter.itemCount == i)
            return false
        adapter.selected(i)
        return true
    }

    override fun onAction(title: String) {
        when (title) {
            getString(R.string.edit) -> {
                if (canEdit()) goToEdit()
                else act?.showToast(getString(R.string.nothing_edit))
            }

            getString(R.string.import_) -> if (isBlocked.not())
                selectFile(false)

            getString(R.string.export) -> if (isBlocked.not() && adapter.itemCount > 0)
                selectFile(true)
        }
    }
}