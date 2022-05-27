package ru.neosvet.vestnewage.view.fragment

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
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
import ru.neosvet.vestnewage.viewmodel.MarkersToiler
import ru.neosvet.vestnewage.viewmodel.basic.*
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.Lib
import ru.neosvet.vestnewage.utils.ScreenUtils
import ru.neosvet.vestnewage.view.activity.MarkerActivity
import ru.neosvet.vestnewage.view.basic.NeoFragment
import ru.neosvet.vestnewage.view.basic.Tip
import ru.neosvet.vestnewage.view.dialog.CustomDialog
import ru.neosvet.vestnewage.view.list.MarkerAdapter

class MarkersFragment : NeoFragment() {
    private var binding: MarkersFragmentBinding? = null
    private val adapter: MarkerAdapter by lazy {
        MarkerAdapter(toiler)
    }
    private lateinit var menu: Tip
    private lateinit var anMin: Animation
    private lateinit var anMax: Animation
    private var anRotate: Animation? = null
    private var stopRotate = false
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
        get() = toiler.title

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
        initAnim()
        initFragment()
        initContent()
        restoreState(savedInstanceState)
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    override fun onBackPressed(): Boolean =
        toiler.onBack()

    private fun restoreState(state: Bundle?) {
        if (state == null) {
            toiler.openList()
            return
        }
        toiler.run {
            adapter.notifyDataSetChanged()
            if (iSel > -1) {
                goToEdit()
                if (diName != null) renameDialog(diName!!)
                else if (diDelete) deleteDialog()
            }
        }
    }

    private fun initRotate() {
        stopRotate = false
        if (anRotate == null) {
            anRotate = AnimationUtils.loadAnimation(act, R.anim.rotate)
            anRotate?.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation) {}
                override fun onAnimationEnd(animation: Animation) {
                    if (!stopRotate) binding?.fabMenu?.startAnimation(anRotate)
                }

                override fun onAnimationRepeat(animation: Animation) {}
            })
        }
        binding?.fabMenu?.startAnimation(anRotate)
    }

    private fun initAnim() {
        anMin = AnimationUtils.loadAnimation(requireContext(), R.anim.minimize)
        anMin.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {}
            override fun onAnimationEnd(animation: Animation) {
                binding?.run {
                    fabEdit.isVisible = false
                    fabBack.isVisible = false
                    fabMenu.isVisible = false
                }
            }

            override fun onAnimationRepeat(animation: Animation) {}
        })
        anMax = AnimationUtils.loadAnimation(act, R.anim.maximize)
    }

    private fun initFragment() = binding?.run {
        menu = Tip(act, pMenu)
        fabEdit.setOnClickListener {
            if (toiler.canEdit())
                goToEdit()
            else
                Lib.showToast(getString(R.string.nothing_edit))
        }
        fabBack.setOnClickListener { toiler.openColList() }
        fabMenu.setOnClickListener {
            if (toiler.isRun) return@setOnClickListener
            if (menu.isShow) menu.hide()
            else {
                bExport.isVisible = toiler.list.isNotEmpty()
                menu.show()
            }
        }
        bExport.setOnClickListener { selectFile(true) }
        bImport.setOnClickListener { selectFile(false) }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initContent() = binding?.content?.run {
        rvMarker.adapter = adapter
        rvMarker.setOnTouchListener { _, motionEvent: MotionEvent ->
            if (toiler.iSel > -1 || toiler.list.size == 0) return@setOnTouchListener false
            binding?.run {
                if (motionEvent.action == MotionEvent.ACTION_DOWN) {
                    fabEdit.startAnimation(anMin)
                    if (toiler.isCollections.not()) fabBack.startAnimation(anMin)
                    else fabMenu.startAnimation(anMin)
                } else if (motionEvent.action == MotionEvent.ACTION_UP
                    || motionEvent.action == MotionEvent.ACTION_CANCEL
                ) {
                    fabEdit.isVisible = true
                    fabEdit.startAnimation(anMax)
                    if (toiler.isCollections.not()) {
                        fabBack.isVisible = true
                        fabBack.startAnimation(anMax)
                    } else {
                        fabMenu.isVisible = true
                        fabMenu.startAnimation(anMax)
                    }
                }
            }
            false
        }
        bOk.setOnClickListener {
            toiler.saveChange()
            val index = toiler.iSel
            unSelected()
            adapter.notifyItemChanged(index)
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
        if (load)
            binding?.fabBack?.isVisible = false
        if (toiler.workOnFile) {
            if (load) {
                initRotate()
            } else {
                stopRotate = true
                binding?.fabMenu?.clearAnimation()
            }
        } else
            super.setStatus(load)
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

    private fun goToEdit() {
        adapter.notifyItemChanged(toiler.iSel)
        binding?.run {
            fabEdit.isVisible = false
            fabBack.isVisible = false
            fabMenu.isVisible = false
            content.pEdit.isVisible = true
        }
    }

    private fun unSelected() = binding?.run {
        toiler.change = false
        if (toiler.list.isNotEmpty()) fabEdit.isVisible = true
        if (toiler.isCollections.not()) fabBack.isVisible = true
        else fabMenu.isVisible = true
        content.pEdit.isVisible = false
        toiler.selected(-1)
    }

    private fun selectFile(isExport: Boolean) {
        menu.hide()
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
            if (isExport)
                toiler.startExport(file)
            else
                toiler.startImport(file)
            setStatus(true)
        }
    }

    override fun onChangedState(state: NeoState) {
        when (state) {
            is MessageState -> if (toiler.workOnFile)
                doneExport(state.message)
            else
                Lib.showToast(state.message)
            is UpdateList -> updateList(state)
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
        setStatus(false)
        binding?.run {
            fabEdit.clearAnimation()
            fabMenu.clearAnimation()
            fabMenu.isVisible = toiler.isCollections && toiler.iSel == -1
            fabBack.isVisible = toiler.isCollections.not() && toiler.iSel == -1
        }
        act?.title = title
        if (toiler.list.isEmpty()) {
            adapter.notifyDataSetChanged()
            tvEmpty.text = if (toiler.isCollections)
                getString(R.string.no_markers)
            else
                getString(R.string.collection_is_empty)
            tvEmpty.isVisible = true
            fabEdit.isVisible = false
            content.pEdit.isVisible = false
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
            if (toiler.iSel == -1) {
                fabEdit.isVisible = true
                unSelected()
            } else
                adapter.notifyItemChanged(toiler.iSel)
            //goToEdit()
        }
    }
}