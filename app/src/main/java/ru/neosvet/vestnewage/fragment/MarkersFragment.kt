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
import android.widget.AdapterView.OnItemClickListener
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import ru.neosvet.ui.NeoFragment
import ru.neosvet.ui.Tip
import ru.neosvet.ui.dialogs.CustomDialog
import ru.neosvet.utils.Const
import ru.neosvet.utils.DataBase
import ru.neosvet.utils.Lib
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.activity.BrowserActivity.Companion.openReader
import ru.neosvet.vestnewage.activity.MarkerActivity
import ru.neosvet.vestnewage.databinding.MarkersContentBinding
import ru.neosvet.vestnewage.databinding.MarkersFragmentBinding
import ru.neosvet.vestnewage.helpers.DateHelper
import ru.neosvet.vestnewage.helpers.ProgressHelper
import ru.neosvet.vestnewage.list.MarkAdapter
import ru.neosvet.vestnewage.list.MarkItem
import ru.neosvet.vestnewage.model.MarkersModel
import ru.neosvet.vestnewage.model.basic.MessageState
import ru.neosvet.vestnewage.model.basic.NeoState
import ru.neosvet.vestnewage.model.basic.NeoViewModel
import ru.neosvet.vestnewage.model.basic.Ready

class MarkersFragment : NeoFragment() {
    private var binding: MarkersFragmentBinding? = null
    private var binding2: MarkersContentBinding? = null
    private val adMarker: MarkAdapter by lazy {
        MarkAdapter(requireContext(), model)
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
        binding2 = MarkersContentBinding.bind(it.root.findViewById(R.id.content_markers))
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
        binding2 = null
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
            adMarker.notifyDataSetChanged()
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
                bExport.isVisible = adMarker.count > 0
                menu.show()
            }
        }
        bExport.setOnClickListener { selectFile(true) }
        bImport.setOnClickListener { selectFile(false) }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initContent() = binding2?.run {
        lvMarker.adapter = adMarker
        lvMarker.onItemClickListener =
            OnItemClickListener { _, _, pos: Int, _ ->
                if (act?.checkBusy() == true) return@OnItemClickListener
                when {
                    model.iSel > -1 -> {
                        if (model.isCollections && pos == 0) return@OnItemClickListener // вне подборок
                        adMarker.getItem(model.iSel).isSelect = false
                        model.iSel = pos
                        adMarker.getItem(pos).isSelect = true
                        adMarker.notifyDataSetChanged()
                    }
                    model.isCollections -> {
                        model.loadMarList(pos)
                    }
                    adMarker.getItem(pos).title.contains("/") -> {
                        setStatus(true)
                        model.loadPage(pos)
                        return@OnItemClickListener
                    }
                    else -> {
                        val p = getPlace(adMarker.getItem(pos))
                        openReader(adMarker.getItem(pos).data, p)
                    }
                }
            }
        lvMarker.setOnTouchListener { _, motionEvent: MotionEvent ->
            if (model.iSel > -1 || adMarker.count == 0) return@setOnTouchListener false
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
            adMarker.getItem(model.iSel).isSelect = false
            adMarker.notifyDataSetChanged()
            unSelect()
        }
        bTop.setOnClickListener { model.moveToTop() }
        bBottom.setOnClickListener { model.moveToBottom() }
        bEdit.setOnClickListener {
            if (model.isCollections) {
                renameDialog(model.selectedItem.title)
            } else {
                val marker = Intent(requireContext(), MarkerActivity::class.java)
                marker.putExtra(DataBase.ID, model.selectedItem.id)
                marker.putExtra(Const.LINK, model.selectedItem.data)
                markerResult.launch(marker)
            }
        }
        bDelete.setOnClickListener { deleteDialog() }
    }

    private fun getPlace(item: MarkItem): String? {
        return if (item.place == "0") null
        else item.des.let { d ->
            d.substring(d.indexOf(Const.N, d.indexOf(Const.N) + 1) + 1)
        }
    }

    override fun setStatus(load: Boolean) {
        if (model.task == MarkersModel.Type.PAGE) {
            binding?.fabBack?.isVisible = load.not()
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

    private fun deleteDialog() {
        model.diDelete = true
        binding2?.lvMarker?.smoothScrollToPosition(model.iSel)
        val dialog = CustomDialog(act)
        dialog.setTitle(getString(R.string.delete) + "?")
        dialog.setMessage(model.selectedItem.title)
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
        adMarker.getItem(model.iSel).isSelect = true
        adMarker.notifyDataSetChanged()
        binding?.run {
            fabEdit.isVisible = false
            fabBack.isVisible = false
            fabMenu.isVisible = false
        }
        binding2?.pEdit?.isVisible = true
    }

    private fun unSelect() = binding?.run {
        model.change = false
        if (adMarker.count > 0) fabEdit.isVisible = true
        if (model.isCollections.not()) fabBack.isVisible = true
        else fabMenu.isVisible = true
        binding2?.pEdit?.isVisible = false
        model.iSel = -1
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
            Ready -> when (model.task) {
                MarkersModel.Type.FILE -> { //import
                    setStatus(false)
                    model.loadColList()
                    Lib.showToast(getString(R.string.completed))
                }
                else -> updateList()
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

    private fun updateList() = binding?.run {
        setStatus(false)
        binding?.run {
            fabEdit.clearAnimation()
            fabMenu.clearAnimation()
            fabMenu.isVisible = model.isCollections
            fabBack.isVisible = model.isCollections.not()
        }
        act?.title = title
        if (model.list.isEmpty()) {
            tvEmpty.text = if (model.isCollections)
                getString(R.string.collection_is_empty)
            else
                getString(R.string.empty_collections)
            tvEmpty.isVisible = true
            fabEdit.isVisible = false
            binding2?.pEdit?.isVisible = false
            unSelect()
        } else {
            fabEdit.isVisible = true
            tvEmpty.isVisible = false
            adMarker.notifyDataSetChanged()
            if (model.iSel == -1)
                unSelect()
            else
                goToEdit()
        }
    }
}