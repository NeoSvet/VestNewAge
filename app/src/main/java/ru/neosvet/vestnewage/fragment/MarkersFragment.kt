package ru.neosvet.vestnewage.fragment

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
import ru.neosvet.ui.NeoFragment
import ru.neosvet.ui.Tip
import ru.neosvet.ui.dialogs.CustomDialog
import ru.neosvet.utils.Const
import ru.neosvet.utils.DataBase
import ru.neosvet.utils.Lib
import ru.neosvet.utils.ScreenUtils
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.activity.MarkerActivity
import ru.neosvet.vestnewage.databinding.MarkersFragmentBinding
import ru.neosvet.vestnewage.helpers.DateHelper
import ru.neosvet.vestnewage.helpers.ProgressHelper
import ru.neosvet.vestnewage.list.MarkerAdapter
import ru.neosvet.vestnewage.model.MarkersModel
import ru.neosvet.vestnewage.model.basic.*

class MarkersFragment : NeoFragment() {
    private var binding: MarkersFragmentBinding? = null
    private val adapter: MarkerAdapter by lazy {
        MarkerAdapter(model)
    }
    private lateinit var menu: Tip
    private lateinit var anMin: Animation
    private lateinit var anMax: Animation
    private var anRotate: Animation? = null
    private var stopRotate = false
    private val model: MarkersModel
        get() = neomodel as MarkersModel
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
        get() = model.title

    override fun initViewModel(): NeoViewModel =
        ViewModelProvider(this).get(MarkersModel::class.java).apply { init(requireContext()) }

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
        model.onBack()

    private fun restoreState(state: Bundle?) {
        if (state == null) {
            model.loadList()
            return
        }
        model.run {
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
            if (model.canEdit())
                goToEdit()
            else
                Lib.showToast(getString(R.string.nothing_edit))
        }
        fabBack.setOnClickListener { model.loadColList() }
        fabMenu.setOnClickListener {
            if (model.isRun) return@setOnClickListener
            if (menu.isShow) menu.hide()
            else {
                bExport.isVisible = model.list.isNotEmpty()
                menu.show()
            }
        }
        bExport.setOnClickListener { selectFile(true) }
        bImport.setOnClickListener { selectFile(false) }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initContent() = binding?.content?.run {
        rvMarker.layoutManager = GridLayoutManager(requireContext(), ScreenUtils.span)
        rvMarker.adapter = adapter
        rvMarker.setOnTouchListener { _, motionEvent: MotionEvent ->
            if (model.iSel > -1 || model.list.size == 0) return@setOnTouchListener false
            binding?.run {
                if (motionEvent.action == MotionEvent.ACTION_DOWN) {
                    fabEdit.startAnimation(anMin)
                    if (model.isCollections.not()) fabBack.startAnimation(anMin)
                    else fabMenu.startAnimation(anMin)
                } else if (motionEvent.action == MotionEvent.ACTION_UP
                    || motionEvent.action == MotionEvent.ACTION_CANCEL
                ) {
                    fabEdit.isVisible = true
                    fabEdit.startAnimation(anMax)
                    if (model.isCollections.not()) {
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
            model.saveChange()
            val index = model.iSel
            unSelected()
            adapter.notifyItemChanged(index)
        }
        bTop.setOnClickListener { model.moveToTop() }
        bBottom.setOnClickListener { model.moveToBottom() }
        bEdit.setOnClickListener {
            model.selectedItem?.let { item ->
                if (model.isCollections) {
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
        if (model.task == MarkersModel.Type.PAGE) {
            super.setStatus(load)
        } else if (model.workOnFile) {
            if (load) {
                ProgressHelper.setBusy(true)
                initRotate()
            } else {
                ProgressHelper.setBusy(false)
                stopRotate = true
                binding?.fabMenu?.clearAnimation()
            }
        }
    }

    private fun deleteDialog() = model.selectedItem?.title?.let { title ->
        model.diDelete = true
        binding?.content?.rvMarker?.smoothScrollToPosition(model.iSel)
        val dialog = CustomDialog(act)
        dialog.setTitle(getString(R.string.delete) + "?")
        dialog.setMessage(title)
        dialog.setLeftButton(getString(R.string.no)) { dialog.dismiss() }
        dialog.setRightButton(getString(R.string.yes)) {
            model.deleteSelected()
            dialog.dismiss()
        }
        dialog.show { model.diDelete = false }
    }

    private fun renameDialog(old_name: String) {
        model.diName = old_name
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
                model.diName = dialog.inputText
            }
        })
        dialog.setLeftButton(getString(R.string.no)) { dialog.dismiss() }
        dialog.setRightButton(getString(R.string.yes)) {
            model.renameSelected(dialog.inputText)
            dialog.dismiss()
        }
        dialog.show { model.diName = null }
    }

    private fun goToEdit() {
        adapter.notifyItemChanged(model.iSel)
        binding?.run {
            fabEdit.isVisible = false
            fabBack.isVisible = false
            fabMenu.isVisible = false
            content.pEdit.isVisible = true
        }
    }

    private fun unSelected() = binding?.run {
        model.change = false
        if (model.list.isNotEmpty()) fabEdit.isVisible = true
        if (model.isCollections.not()) fabBack.isVisible = true
        else fabMenu.isVisible = true
        content.pEdit.isVisible = false
        model.selected(-1)
    }

    private fun selectFile(isExport: Boolean) {
        menu.hide()
        val intent = Intent(
            if (isExport) Intent.ACTION_CREATE_DOCUMENT
            else Intent.ACTION_OPEN_DOCUMENT
        )
        intent.type = "*/*"
        if (isExport) {
            val date = DateHelper.initToday()
            intent.putExtra(
                Intent.EXTRA_TITLE, DataBase.MARKERS + " "
                        + date.toString().replace(".", "-")
            )
            exportResult.launch(intent)
        } else importResult.launch(intent)
    }

    private fun updateMarkersList() {
        model.updateMarkersList()
    }

    private fun parseFileResult(data: Intent, isExport: Boolean) {
        data.dataString?.let { file ->
            if (isExport)
                model.startExport(file)
            else
                model.startImport(file)
            setStatus(true)
        }
    }

    override fun onChangedState(state: NeoState) {
        when (state) {
            is MessageState -> if (model.workOnFile)
                doneExport(state.message)
            else
                Lib.showToast(state.message)
            is UpdateList -> updateList(state)
            Ready -> {//import model.task==MarkersModel.Type.FILE
                setStatus(false)
                model.loadColList()
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
            fabMenu.isVisible = model.isCollections && model.iSel == -1
            fabBack.isVisible = model.isCollections.not() && model.iSel == -1
        }
        act?.title = title
        if (model.list.isEmpty()) {
            adapter.notifyDataSetChanged()
            tvEmpty.text = if (model.isCollections)
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
                    adapter.notifyItemMoved(state.index, model.iSel)
                ListEvent.RELOAD ->
                    adapter.notifyDataSetChanged()
            }
            tvEmpty.isVisible = false
            if (model.iSel == -1) {
                fabEdit.isVisible = true
                unSelected()
            } else
                adapter.notifyItemChanged(model.iSel)
            //goToEdit()
        }
    }
}