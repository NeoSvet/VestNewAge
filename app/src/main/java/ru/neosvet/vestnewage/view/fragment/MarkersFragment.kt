package ru.neosvet.vestnewage.view.fragment

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.DataBase
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.databinding.MarkersFragmentBinding
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.Lib
import ru.neosvet.vestnewage.utils.ScreenUtils
import ru.neosvet.vestnewage.view.activity.MarkerActivity
import ru.neosvet.vestnewage.view.basic.NeoFragment
import ru.neosvet.vestnewage.view.dialog.CustomDialog
import ru.neosvet.vestnewage.view.list.MarkerAdapter
import ru.neosvet.vestnewage.viewmodel.MarkersToiler
import ru.neosvet.vestnewage.viewmodel.basic.*

class MarkersFragment : NeoFragment() {
    private var binding: MarkersFragmentBinding? = null
    private val adapter: MarkerAdapter by lazy {
        MarkerAdapter(toiler, this::longClick)
    }

    private val anRotate: Animation by lazy {
        initAnimation()
    }
    private var isStopRotate = false
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
            if (iSel > -1) {
                goToEdit()
                if (diName != null) renameDialog(diName!!)
                else if (diDelete) deleteDialog()
            }
        }
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
        toiler.diDelete = true
        binding?.content?.rvMarker?.smoothScrollToPosition(toiler.iSel)
        val dialog = CustomDialog(act)
        dialog.setTitle(getString(R.string.delete) + "?")
        dialog.setMessage(title)
        dialog.setLeftButton(getString(R.string.no)) { dialog.dismiss() }
        dialog.setRightButton(getString(R.string.yes)) {
            toiler.deleteSelected()
            dialog.dismiss()
        }
        dialog.show { toiler.diDelete = false }
    }

    private fun renameDialog(old_name: String) {
        toiler.diName = old_name
        val dialog = CustomDialog(act)
        dialog.setTitle(getString(R.string.new_name))
        dialog.setMessage(null)
        dialog.setInputText(old_name, object : TextWatcher {
            override fun beforeTextChanged(
                charSequence: CharSequence,
                start: Int,
                count: Int,
                after: Int
            ) {
            }

            override fun onTextChanged(
                charSequence: CharSequence,
                start: Int,
                before: Int,
                count: Int
            ) {
            }

            override fun afterTextChanged(editable: Editable) {
                toiler.diName = dialog.inputText
            }
        })
        dialog.setLeftButton(getString(R.string.no)) { dialog.dismiss() }
        dialog.setRightButton(getString(R.string.yes)) {
            toiler.renameSelected(dialog.inputText)
            dialog.dismiss()
        }
        dialog.show { toiler.diName = null }
    }

    private fun longClick(index: Int): Boolean {
        if (toiler.canEdit() || index > 0) {
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

    override fun onChangedState(state: NeoState) {
        when (state) {
            is MessageState -> if (toiler.workOnFile)
                doneExport(state.message)
            else
                Lib.showToast(state.message)
            is UpdateList ->
                updateList(state)
            Ready -> {//import toiler.task==MarkersModel.Type.FILE
                setStatus(false)
                toiler.openColList()
                Lib.showToast(getString(R.string.completed))
            }

        }
    }

    private fun doneExport(file: String) {
        setStatus(false)
        val builder = AlertDialog.Builder(requireContext(), R.style.NeoDialog)
            .setMessage(getString(R.string.send_file))
            .setPositiveButton(
                getString(R.string.yes)
            ) { _, _ ->
                val sendIntent = Intent(Intent.ACTION_SEND)
                sendIntent.type = "text/plain"
                sendIntent.putExtra(Intent.EXTRA_STREAM, Uri.parse(file))
                startActivity(sendIntent)
            }
            .setNegativeButton(
                getString(R.string.no)
            ) { _, _ -> }
        builder.create().show()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun updateList(state: UpdateList) = binding?.run {
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
            tvEmpty.text = if (toiler.isCollections)
                getString(R.string.no_markers)
            else
                getString(R.string.collection_is_empty)
            tvEmpty.isVisible = true
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
            tvEmpty.isVisible = false
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
                    Lib.showToast(getString(R.string.nothing_edit))
            }
            getString(R.string.import_) -> if (toiler.isRun.not())
                selectFile(false)
            getString(R.string.export) -> if (toiler.list.isNotEmpty() && toiler.isRun.not())
                selectFile(true)
        }
    }
}