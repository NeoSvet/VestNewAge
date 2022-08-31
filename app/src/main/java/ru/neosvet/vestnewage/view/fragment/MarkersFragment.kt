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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.DataBase
import ru.neosvet.vestnewage.data.DateUnit
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
import ru.neosvet.vestnewage.viewmodel.basic.ListEvent
import ru.neosvet.vestnewage.viewmodel.basic.NeoState
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler

class MarkersFragment : NeoFragment() {
    private var binding: MarkersFragmentBinding? = null
    private val adapter: MarkerAdapter by lazy {
        MarkerAdapter(toiler, this::longClick)
    }

    private val anRotate: Animation by lazy {
        initAnimation()
    }
    private var isStopRotate = false
    private var isNeedRestore = false
    private var collectResult: Job? = null
    private val toiler: MarkersToiler
        get() = neotoiler as MarkersToiler
    private val markerResult = registerForActivityResult(
        StartActivityForResult()
    ) { result: ActivityResult -> if (result.resultCode == Activity.RESULT_OK) updateMarkersList() }
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
        ViewModelProvider(this).get(MarkersToiler::class.java).apply { init(requireContext()) }

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
        restoreState(savedInstanceState)
    }

    private fun setToolbar() {
        act?.setSupportActionBar(binding?.toolbar)
        binding?.toolbar?.setNavigationOnClickListener {
            onBackPressed()
        }
    }

    override fun onDestroyView() {
        collectResult?.cancel()
        binding = null
        super.onDestroyView()
    }

    override fun onBackPressed(): Boolean {
        if (toiler.iSel > -1) {
            unSelected()
            return false
        }
        if (toiler.isCollections.not()) {
            toiler.openColList()
            return false
        }
        return true
    }

    private fun restoreState(state: Bundle?) {
        if (state == null) {
            toiler.openList()
            return
        }
        toiler.run {
            isNeedRestore = true
            if (iSel > -1) {
                goToEdit()
                lifecycleScope.launch {
                    delay(150)
                    binding?.content?.rvMarker?.run {
                        post { smoothScrollToPosition(iSel) }
                    }
                }
                when (state.getString(Const.DIALOG)) {
                    Const.STRING -> collectResultDelete()
                    Const.TITLE -> collectResultRename()
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        childFragmentManager.findFragmentByTag(Const.DIALOG)?.let { frag ->
            val d = if (frag is PromptDialog)
                Const.STRING else Const.TITLE
            outState.putString(Const.DIALOG, d)
        }
        super.onSaveInstanceState(outState)
    }

    private fun initAnimation(): Animation {
        val animation = AnimationUtils.loadAnimation(requireContext(), R.anim.rotate)
        animation.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {}
            override fun onAnimationEnd(animation: Animation) {
                if (!isStopRotate) binding?.ivMarker?.startAnimation(anRotate)
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
        bTop.setOnClickListener { toiler.moveToTop() }
        bBottom.setOnClickListener { toiler.moveToBottom() }
        bEdit.setOnClickListener {
            toiler.selectedItem?.let { item ->
                if (toiler.isCollections) {
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

    override fun setStatus(load: Boolean) {
        if (toiler.workOnFile) {
            if (load)
                startRotate()
            else
                stopRotate()
        } else
            super.setStatus(load)
    }

    private fun startRotate() = binding?.run {
        isStopRotate = false
        pFileOperation.isVisible = true
        ivMarker.startAnimation(anRotate)
    }

    private fun stopRotate() = binding?.run {
        isStopRotate = true
        pFileOperation.isVisible = false
        ivMarker.clearAnimation()
    }

    private fun deleteDialog() = toiler.selectedItem?.title?.let { title ->
        binding?.content?.rvMarker?.smoothScrollToPosition(toiler.iSel)
        PromptDialog.newInstance(getString(R.string.format_delete).format(title))
            .show(childFragmentManager, Const.DIALOG)
        collectResultDelete()
    }

    private fun collectResultDelete() {
        collectResult?.cancel()
        collectResult = lifecycleScope.launch {
            PromptDialog.result.collect {
                if (it == PromptResult.Yes)
                    toiler.deleteSelected()
            }
        }
    }

    private fun renameDialog(old_name: String) {
        InputDialog.newInstance(getString(R.string.new_name), old_name)
            .show(childFragmentManager, Const.DIALOG)
        collectResultRename()
    }

    private fun collectResultRename() {
        collectResult?.cancel()
        collectResult = lifecycleScope.launch {
            InputDialog.result.collect {
                if (it != null) toiler.renameSelected(it)
            }
        }
    }

    private fun longClick(index: Int): Boolean {
        if (toiler.isCollections.not() || index > 0) {
            toiler.selected(index)
            goToEdit()
        }
        return true
    }

    private fun goToEdit() {
        act?.hideHead()
        adapter.notifyItemChanged(toiler.iSel)
        act?.blocked()
        binding?.content?.pEdit?.isVisible = true
    }

    private fun unSelected() {
        if (toiler.iSel > -1) {
            val i = toiler.iSel
            toiler.selected(-1)
            adapter.notifyItemChanged(i)
        }
        toiler.change = false
        binding?.content?.pEdit?.isVisible = false
        act?.unblocked()
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

    private fun updateMarkersList() {
        toiler.updateMarkersList()
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
            setStatus(true)
        }
    }

    override fun onChangedOtherState(state: NeoState) {
        when (state) {
            is NeoState.Message -> if (toiler.workOnFile)
                doneExport(state.message)
            else
                act?.showToast(state.message)
            is NeoState.ListState -> {
                if (isNeedRestore && state.event != ListEvent.RELOAD) {
                    isNeedRestore = false
                    toiler.restoreList()
                    return
                }
                updateList(state)
            }
            NeoState.Ready -> {//import toiler.task==MarkersModel.Type.FILE
                setStatus(false)
                toiler.openColList()
                act?.showToast(getString(R.string.completed))
            }
            else -> {}
        }
    }

    private fun doneExport(file: String) {
        setStatus(false)
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

    @SuppressLint("NotifyDataSetChanged")
    private fun updateList(state: NeoState.ListState) = binding?.run {
        if (toiler.iSel == -1)
            setStatus(false)
        if (toiler.isCollections) {
            act?.supportActionBar?.setDisplayHomeAsUpEnabled(false)
            toolbar.title = toiler.title
        } else {
            act?.supportActionBar?.setDisplayHomeAsUpEnabled(true)
            toolbar.title = String.format(getString(R.string.format_collection), toiler.title)
        }
        if (toiler.list.isEmpty()) {
            adapter.notifyDataSetChanged()
            val msg = if (toiler.isCollections)
                getString(R.string.no_markers)
            else
                getString(R.string.collection_is_empty)
            act?.showStaticToast(msg)
            unSelected()
        } else {
            when (state.event) {
                ListEvent.REMOTE ->
                    adapter.notifyItemRemoved(state.index)
                ListEvent.CHANGE -> if (state.index > -1)
                    adapter.notifyItemChanged(state.index)
                ListEvent.MOVE ->
                    adapter.notifyItemMoved(state.index, toiler.iSel)
                ListEvent.RELOAD -> {
                    binding?.content?.rvMarker?.layoutManager = GridLayoutManager(
                        requireContext(),
                        if (toiler.isCollections) ScreenUtils.span else 1
                    )
                    adapter.notifyDataSetChanged()
                }
            }
            act?.hideToast()
            if (toiler.iSel == -1)
                unSelected()
            else
                adapter.notifyItemChanged(toiler.iSel)
        }
    }

    override fun onAction(title: String) {
        when (title) {
            getString(R.string.edit) -> {
                if (toiler.canEdit())
                    goToEdit()
                else
                    act?.showToast(getString(R.string.nothing_edit))
            }
            getString(R.string.import_) -> if (toiler.isRun.not())
                selectFile(false)
            getString(R.string.export) -> if (toiler.list.isNotEmpty() && toiler.isRun.not())
                selectFile(true)
        }
    }
}