package ru.neosvet.vestnewage.fragment;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ListView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;
import androidx.work.Data;

import ru.neosvet.ui.NeoFragment;
import ru.neosvet.ui.Tip;
import ru.neosvet.ui.dialogs.CustomDialog;
import ru.neosvet.utils.Const;
import ru.neosvet.utils.DataBase;
import ru.neosvet.utils.Lib;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.activity.BrowserActivity;
import ru.neosvet.vestnewage.activity.MarkerActivity;
import ru.neosvet.vestnewage.helpers.DateHelper;
import ru.neosvet.vestnewage.helpers.ProgressHelper;
import ru.neosvet.vestnewage.list.MarkAdapter;
import ru.neosvet.vestnewage.list.MarkItem;
import ru.neosvet.vestnewage.model.CollectionsModel;
import ru.neosvet.vestnewage.model.LoaderModel;
import ru.neosvet.vestnewage.storage.MarkersStorage;
import ru.neosvet.vestnewage.storage.PageStorage;

public class CollectionsFragment extends NeoFragment {
    private MarkersStorage dbMarker;
    private ListView lvMarker;
    private View container, fabEdit, fabMenu, fabBack, pEdit, bExport;
    private TextView tvEmpty;
    private MarkAdapter adMarker;
    private Tip menu;
    private int iSel = -1;
    private boolean change = false, delete = false, stopRotate;
    private String sCol = null, sName = null;
    private Animation anMin, anMax, anRotate;
    private CollectionsModel model;

    private final ActivityResultLauncher<Intent> markerResult = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK)
                    updateMarkersList();
            });

    private final ActivityResultLauncher<Intent> importResult = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null)
                    parseFileResult(result.getData(), false);
            });

    private final ActivityResultLauncher<Intent> exportResult = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null)
                    parseFileResult(result.getData(), true);
            });

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        this.container = inflater.inflate(R.layout.collections_fragment, container, false);
        return this.container;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        dbMarker = new MarkersStorage(requireContext());
        initViews();
        setViews();
        initModel();
        restoreState(savedInstanceState);
    }

    @Override
    public void onDestroyView() {
        dbMarker.close();
        super.onDestroyView();
    }

    @Override
    public boolean onBackPressed() {
        if (iSel > -1) {
            unSelect();
            if (sCol == null)
                loadColList();
            else
                loadMarList();
            return false;
        }
        return true;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        saveChange();
        outState.putString(DataBase.COLLECTIONS, sCol);
        outState.putInt(Const.SELECT, iSel);
        outState.putString(Const.RENAME, sName);
        outState.putBoolean(Const.DIALOG, delete);
        super.onSaveInstanceState(outState);
    }

    private void restoreState(Bundle state) {
        if (state != null) {
            sCol = state.getString(DataBase.COLLECTIONS);
            iSel = state.getInt(Const.SELECT, -1);
            sName = state.getString(Const.RENAME, null);
            delete = state.getBoolean(Const.DIALOG, false);
            if (state.getBoolean(Const.PAGE, false)
                    && LoaderModel.inProgress)
                setStatus(true);
        }

        if (sCol == null)
            loadColList();
        else
            loadMarList();

        if (iSel > -1) {
            goToEdit();
            if (sName != null)
                renameDialog(sName);
            else if (delete)
                deleteDialog();
        }
    }

    private void initModel() {
        model = new ViewModelProvider(this).get(CollectionsModel.class);
        switch (model.task) {
            case 1:
                setStatus(true);
                break;
            case 2:
                initRotate();
                break;
        }
    }

    @Override
    public void onChanged(Data data) {
        if (data.getBoolean(Const.START, false)) {
            act.status.loadText();
            return;
        }
        if (!data.getBoolean(Const.FINISH, false))
            return;
        ProgressHelper.setBusy(false);

        String error = data.getString(Const.ERROR);
        if (model.task == 2) { //finish download page
            model.task = 0;
            stopLoad();
            if (error != null) {
                AlertDialog.Builder builder = new AlertDialog.Builder(act, R.style.NeoDialog)
                        .setTitle(getString(R.string.error))
                        .setMessage(error)
                        .setPositiveButton(getString(android.R.string.ok),
                                (dialog, id) -> {
                                });
                builder.create().show();
            } else
                loadMarList();
            return;
        }

        model.task = 0;
        stopRotate = true;
        if (error != null) {
            act.status.setError(error);
            return;
        }

        if (data.getBoolean(Const.MODE, false)) { //export
            final String file = data.getString(Const.FILE);
            AlertDialog.Builder builder = new AlertDialog.Builder(act, R.style.NeoDialog)
                    .setMessage(getString(R.string.send_file))
                    .setPositiveButton(getString(R.string.yes),
                            (dialog, id) -> {
                                Intent sendIntent = new Intent(Intent.ACTION_SEND);
                                sendIntent.setType("text/plain");
                                sendIntent.putExtra(Intent.EXTRA_STREAM, Uri.parse(file));
                                startActivity(sendIntent);
                            })
                    .setNegativeButton(getString(R.string.no),
                            (dialog, id) -> {
                            });
            builder.create().show();
        } else { //import
            sCol = null;
            loadColList();
            Lib.showToast(act, getString(R.string.completed));
        }
    }

    private void initRotate() {
        stopRotate = false;
        if (anRotate == null) {
            anRotate = AnimationUtils.loadAnimation(act, R.anim.rotate);
            anRotate.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {

                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    if (!stopRotate)
                        fabMenu.startAnimation(anRotate);
                }

                @Override
                public void onAnimationRepeat(Animation animation) {

                }
            });
        }
        fabMenu.startAnimation(anRotate);
    }

    private void loadMarList() {
        fabEdit.clearAnimation();
        if (fabMenu != null) {
            fabMenu.clearAnimation();
            fabMenu.setVisibility(View.GONE);
        }
        fabBack.setVisibility(View.VISIBLE);
        adMarker.clear();
        act.setTitle(sCol.substring(0, sCol.indexOf(Const.N)));
        int iID, iPlace, iLink, iDes;
        String[] mId;
        String link = sCol.substring(sCol.indexOf(Const.N) + 1);
        if (link.contains(Const.COMMA)) {
            mId = link.split(Const.COMMA);
        } else
            mId = new String[]{link};
        Cursor cursor;
        String place;
        int k = 0;
        for (String s : mId) {
            cursor = dbMarker.getMarker(s);
            if (cursor.moveToFirst()) {
                iID = cursor.getColumnIndex(DataBase.ID);
                iPlace = cursor.getColumnIndex(Const.PLACE);
                iLink = cursor.getColumnIndex(Const.LINK);
                iDes = cursor.getColumnIndex(Const.DESCTRIPTION);
                link = cursor.getString(iLink);
                place = cursor.getString(iPlace);
                adMarker.addItem(new MarkItem(getTitle(link), cursor.getInt(iID), link));
                adMarker.getItem(k).setPlace(place);
                adMarker.getItem(k).setDes(
                        cursor.getString(iDes) + Const.N + getPlace(link, place));
                k++;
            }
            cursor.close();
        }
        adMarker.notifyDataSetChanged();
        if (adMarker.getCount() == 0) {
            tvEmpty.setText(getString(R.string.collection_is_empty));
            tvEmpty.setVisibility(View.VISIBLE);
        }
    }

    @SuppressLint("Range")
    private String getPlace(String link, String p) {
        if (p.equals("0"))
            return getString(R.string.page_entirely);
        try {
            StringBuilder b = new StringBuilder();
            PageStorage storage = new PageStorage(requireContext(), link);
            if (p.contains("%")) { //позиция
                b.append(Const.N);
                b.append(storage.getContentPage(link, false));
                int k = 5; // имитация нижнего "колонтитула" страницы
                int i = b.indexOf(Const.N);
                while (i > -1) {
                    k++;
                    i = b.indexOf(Const.N, i + 1);
                }
                float f = Float.parseFloat(p.substring(0, p.length() - 1).replace(Const.COMMA, ".")) / 100f;
                k = (int) ((float) k * f) + 1;
                i = 0;
                int u;
                do {
                    k--;
                    u = i;
                    i = b.indexOf(Const.N, u + 1);
                } while (k > 1 && i > -1);
                if (b.substring(u + 1, u + 2).equals(Const.N))
                    u++;
                if (i > -1)
                    i = b.indexOf(Const.N, i + 1);
                if (i > -1)
                    i = b.indexOf(Const.N, i + 1);
                b.delete(0, u + 1);
                i -= u;
                if (i > -1) {
                    if (b.substring(i - 1, i).equals(Const.N))
                        i--;
                    b.delete(i - 1, b.length());
                }
                if (b.toString().equals("null"))
                    b = new StringBuilder(getString(R.string.not_load_page));
                b.insert(0, getString(R.string.pos_n) +
                        p.replace(".", Const.COMMA) + ":" + Const.N);
            } else { //абзацы
                b.append(getString(R.string.par_n));
                b.append(p.replace(Const.COMMA, ", "));
                b.append(":");
                b.append(Const.N);
                p = dbMarker.closeList(p);

                Cursor cursor = storage.getPage(link);
                int id;
                if (cursor.moveToFirst()) {
                    id = cursor.getInt(cursor.getColumnIndex(DataBase.ID));
                } else { // страница не загружена...
                    cursor.close();
                    storage.close();
                    b.append(getString(R.string.not_load_page));
                    return b.toString();
                }
                cursor.close();
                cursor = storage.getParagraphs(id);
                int i = 1;
                if (cursor.moveToFirst()) {
                    do {
                        if (p.contains(dbMarker.closeList(String.valueOf(i)))) {
                            b.append(Lib.withOutTags(cursor.getString(0)));
                            b.append(Const.N);
                            b.append(Const.N);
                        }
                        i++;
                    } while (cursor.moveToNext());
                } else { // страница не загружена...
                    cursor.close();
                    storage.close();
                    throw new Exception();
                }
                cursor.close();
                storage.close();
                b.delete(b.length() - 2, b.length());
            }
            if (b.length() > 0)
                return b.toString();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        // java.lang.IllegalStateException: Fragment CollectionsFragment{138a01e4} not attached to Activity ?
        if (p.contains("%"))
            p = act.getString(R.string.sel_pos) + p;
        else
            p = act.getString(R.string.sel_par) + dbMarker.openList(p).replace(Const.COMMA, ", ");
        return p;
    }

    private String getTitle(String link) {
        PageStorage storage = new PageStorage(requireContext(), link);
        String t = storage.getContentPage(link, true);
        if (t == null)
            return link;
        return t;
    }

    private void loadColList() {
        fabEdit.clearAnimation();
        fabEdit.setVisibility(View.GONE);
        if (fabMenu != null) {
            fabMenu.clearAnimation();
            fabMenu.setVisibility(View.VISIBLE);
        }
        fabBack.setVisibility(View.GONE);
        adMarker.clear();
        act.setTitle(getString(R.string.collections));
        Cursor cursor = dbMarker.getCollections(Const.PLACE);
        String s;
        boolean isNull = false;
        if (cursor.moveToFirst()) {
            int iID = cursor.getColumnIndex(DataBase.ID);
            int iTitle = cursor.getColumnIndex(Const.TITLE);
            int iMarkers = cursor.getColumnIndex(DataBase.MARKERS);
            do {
                s = cursor.getString(iMarkers);
                if (s == null || s.equals("")) {
                    isNull = true;
                    s = "";
                }
                adMarker.addItem(new MarkItem(cursor.getString(iTitle), cursor.getInt(iID), s));
            } while (cursor.moveToNext());
        }
        cursor.close();
        if (isNull && adMarker.getCount() == 1) {
            adMarker.clear();
            tvEmpty.setText(getString(R.string.empty_collections));
            tvEmpty.setVisibility(View.VISIBLE);
        } else {
            fabEdit.setVisibility(View.VISIBLE);
            if (fabMenu != null)
                fabMenu.setVisibility(View.VISIBLE);
            tvEmpty.setVisibility(View.GONE);
        }
        adMarker.notifyDataSetChanged();
    }

    private void initViews() {
        act.status.setClick(view -> stopLoad());
        fabEdit = container.findViewById(R.id.fabEdit);
        fabMenu = container.findViewById(R.id.fabMenu);
        fabBack = container.findViewById(R.id.fabBack);
        pEdit = container.findViewById(R.id.pEdit);
        bExport = container.findViewById(R.id.bExport);
        menu = new Tip(act, container.findViewById(R.id.pMenu));
        tvEmpty = container.findViewById(R.id.tvEmptyCollections);
        lvMarker = container.findViewById(R.id.lvMarker);
        adMarker = new MarkAdapter(act);
        lvMarker.setAdapter(adMarker);
        anMin = AnimationUtils.loadAnimation(act, R.anim.minimize);
        anMin.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                fabEdit.setVisibility(View.GONE);
                fabBack.setVisibility(View.GONE);
                if (fabMenu != null)
                    fabMenu.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
        anMax = AnimationUtils.loadAnimation(act, R.anim.maximize);
    }

    private void stopLoad() {
        fabBack.setVisibility(View.VISIBLE);
        act.status.setLoad(false);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setViews() {
        lvMarker.setOnItemClickListener((adapterView, view, pos, l) -> {
            if (act.checkBusy()) return;
            if (iSel > -1) {
                if (sCol == null && pos == 0)
                    return;
                adMarker.getItem(iSel).setSelect(false);
                iSel = pos;
                adMarker.getItem(iSel).setSelect(true);
                adMarker.notifyDataSetChanged();
            } else if (sCol == null) {
                sCol = adMarker.getItem(pos).getTitle()
                        + Const.N + adMarker.getItem(pos).getData();
                loadMarList();
            } else {
                if (adMarker.getItem(pos).getTitle().contains("/")) {
                    if (ProgressHelper.isBusy())
                        return;
                    ProgressHelper.setBusy(true);
                    setStatus(true);
                    act.status.setLoad(true);
                    act.status.startText();
                    model.loadPage(adMarker.getItem(pos).getData());
                    return;
                }
                String p;
                if (adMarker.getItem(pos).getPlace().equals("0"))
                    p = null;
                else {
                    p = adMarker.getItem(pos).getDes();
                    p = p.substring(p.indexOf(Const.N, p.indexOf(Const.N) + 1) + 1);
                }
                BrowserActivity.openReader(act, adMarker.getItem(pos).getData(), p);
            }
        });
        lvMarker.setOnTouchListener((view, motionEvent) -> {
            if (iSel > -1 || adMarker.getCount() == 0)
                return false;
            if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                fabEdit.startAnimation(anMin);
                if (sCol != null)
                    fabBack.startAnimation(anMin);
                else if (fabMenu != null)
                    fabMenu.startAnimation(anMin);
            } else if (motionEvent.getAction() == MotionEvent.ACTION_UP
                    || motionEvent.getAction() == MotionEvent.ACTION_CANCEL) {
                fabEdit.setVisibility(View.VISIBLE);
                fabEdit.startAnimation(anMax);
                if (sCol != null) {
                    fabBack.setVisibility(View.VISIBLE);
                    fabBack.startAnimation(anMax);
                } else if (fabMenu != null) {
                    fabMenu.setVisibility(View.VISIBLE);
                    fabMenu.startAnimation(anMax);
                }
            }
            return false;
        });
        fabEdit.setOnClickListener(view -> {
            if (sCol == null) {
                if (adMarker.getCount() == 1) {
                    Lib.showToast(act, getString(R.string.nothing_edit));
                    return;
                }
                iSel = 1;
            } else
                iSel = 0;
            goToEdit();
        });
        fabBack.setOnClickListener(view -> {
            sCol = null;
            loadColList();
        });
        container.findViewById(R.id.bOk).setOnClickListener(view -> {
            saveChange();
            adMarker.getItem(iSel).setSelect(false);
            adMarker.notifyDataSetChanged();
            unSelect();
        });
        container.findViewById(R.id.bTop).setOnClickListener(view -> {
            int n = 0;
            if (sCol == null)
                n = 1;
            if (iSel > n) {
                change = true;
                n = iSel - 1;
                MarkItem item = adMarker.getItem(n);
                adMarker.removeAt(n);
                adMarker.insertItem(iSel, item);
                iSel = n;
                adMarker.notifyDataSetChanged();
                lvMarker.smoothScrollToPosition(iSel);
            }
        });
        container.findViewById(R.id.bBottom).setOnClickListener(view -> {
            if (iSel < adMarker.getCount() - 1) {
                change = true;
                int n = iSel + 1;
                MarkItem item = adMarker.getItem(n);
                adMarker.removeAt(n);
                adMarker.insertItem(iSel, item);
                iSel = n;
                adMarker.notifyDataSetChanged();
                lvMarker.smoothScrollToPosition(iSel);
            }
        });
        container.findViewById(R.id.bEdit).setOnClickListener(view -> {
            if (sCol == null) {
                renameDialog(adMarker.getItem(iSel).getTitle());
            } else {
                saveChange();
                Intent marker = new Intent(act, MarkerActivity.class);
                marker.putExtra(DataBase.ID, adMarker.getItem(iSel).getId());
                marker.putExtra(Const.LINK, adMarker.getItem(iSel).getData());
                markerResult.launch(marker);
            }
        });
        container.findViewById(R.id.bDelete).setOnClickListener(view -> {
            delete = true;
            deleteDialog();
        });
        fabMenu.setOnClickListener(view -> {
            if (ProgressHelper.isBusy()) return;
            if (menu.isShow())
                menu.hide();
            else {
                if (adMarker.getCount() == 0)
                    bExport.setVisibility(View.GONE);
                else
                    bExport.setVisibility(View.VISIBLE);
                menu.show();
            }
        });
        bExport.setOnClickListener(view -> selectFile(true));
        container.findViewById(R.id.bImport).setOnClickListener(view -> selectFile(false));
    }

    @Override
    public void setStatus(boolean load) {
        if (load)
            fabBack.setVisibility(View.GONE);
        else
            fabBack.setVisibility(View.VISIBLE);
    }

    private void deleteDialog() {
        lvMarker.smoothScrollToPosition(iSel);
        final CustomDialog dialog = new CustomDialog(act);
        dialog.setTitle(getString(R.string.delete) + "?");
        dialog.setMessage(adMarker.getItem(iSel).getTitle());
        dialog.setLeftButton(getString(R.string.no), view -> dialog.dismiss());
        dialog.setRightButton(getString(R.string.yes), view -> {
            deleteElement();
            dialog.dismiss();
        });
        dialog.show(dialogInterface -> delete = false);
    }

    private void renameDialog(String old_name) {
        sName = old_name;
        final CustomDialog dialog = new CustomDialog(act);
        dialog.setTitle(getString(R.string.new_name));
        dialog.setMessage(null);
        dialog.setInputText(sName, new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                sName = dialog.getInputText();
            }
        });

        dialog.setLeftButton(getString(android.R.string.no), view -> dialog.dismiss());
        dialog.setRightButton(getString(android.R.string.yes), view -> {
            renameColumn(dialog.getInputText());
            dialog.dismiss();
        });
        dialog.show(dialogInterface -> sName = null);
    }

    private void renameColumn(String name) {
        boolean bCancel = (name.length() == 0);
        if (!bCancel) {
            if (name.contains(Const.COMMA)) {
                Lib.showToast(act, getString(R.string.unuse_dot));
                return;
            }
            for (int i = 0; i < adMarker.getCount(); i++) {
                if (name.equals(adMarker.getItem(i).getTitle())) {
                    bCancel = true;
                    break;
                }
            }
        }
        if (bCancel) {
            Lib.showToast(act, getString(R.string.cancel_rename));
            return;
        }
        ContentValues row = new ContentValues();
        row.put(Const.TITLE, name);
        if (dbMarker.updateCollection(adMarker.getItem(iSel).getId(), row)) {
            adMarker.getItem(iSel).setTitle(name);
            adMarker.notifyDataSetChanged();
        } else
            Lib.showToast(act, getString(R.string.cancel_rename));
    }

    private void goToEdit() {
        adMarker.getItem(iSel).setSelect(true);
        adMarker.notifyDataSetChanged();
        fabEdit.setVisibility(View.GONE);
        fabBack.setVisibility(View.GONE);
        if (fabMenu != null)
            fabMenu.setVisibility(View.GONE);
        pEdit.setVisibility(View.VISIBLE);
    }

    private void saveChange() {
        if (change) {
            ContentValues row;
            if (sCol == null) {
                for (int i = 1; i < adMarker.getCount(); i++) {
                    row = new ContentValues();
                    row.put(Const.PLACE, i);
                    dbMarker.updateCollection(adMarker.getItem(i).getId(), row);
                }
            } else {
                row = new ContentValues();
                final String t = getListId();
                row.put(DataBase.MARKERS, t);
                dbMarker.updateCollectionByTitle(sCol.substring(0, sCol.indexOf(Const.N)), row);
                sCol = sCol.substring(0, sCol.indexOf(Const.N) + 1) + t;
            }
            change = false;
        }
    }

    private void deleteElement() {
        int n;
        String id = String.valueOf(adMarker.getItem(iSel).getId());
        if (sCol == null) { //удаляем подборку
            n = 1;
            String s = adMarker.getItem(iSel).getData();
            dbMarker.deleteCollection(id, dbMarker.getList(s),
                    getString(R.string.no_collections));
        } else { //удаляем закладку
            n = 0;
            dbMarker.deleteMarker(id);
        }
        adMarker.removeAt(iSel);
        if (adMarker.getCount() == n) { //не осталось элементов для редактирования
            if (n == 0) { //список закладок пуст
                tvEmpty.setText(getString(R.string.collection_is_empty));
                tvEmpty.setVisibility(View.VISIBLE);
            }
            unSelect(); //снимаем выделение
        } else { //иначе выделяем другой элемент
            if (adMarker.getCount() == iSel)
                iSel--;
            adMarker.getItem(iSel).setSelect(true);
        }
        adMarker.notifyDataSetChanged();
    }

    private void unSelect() {
        change = false;
        if (adMarker.getCount() > 0)
            fabEdit.setVisibility(View.VISIBLE);
        if (sCol != null)
            fabBack.setVisibility(View.VISIBLE);
        else if (fabMenu != null)
            fabMenu.setVisibility(View.VISIBLE);
        pEdit.setVisibility(View.GONE);
        iSel = -1;
    }

    public String getListId() {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < adMarker.getCount(); i++) {
            s.append(adMarker.getItem(i).getId());
            s.append(Const.COMMA);
        }
        s.delete(s.length() - 1, s.length());
        return s.toString();
    }

    private void selectFile(boolean isExport) {
        menu.hide();
        Intent intent = new Intent(isExport ? Intent.ACTION_CREATE_DOCUMENT : Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        if (isExport) {
            DateHelper date = DateHelper.initToday(act);
            intent.putExtra(Intent.EXTRA_TITLE, DataBase.MARKERS + " "
                    + date.toString().replace(".", "-"));
            exportResult.launch(intent);
        } else
            importResult.launch(intent);
    }

    private void updateMarkersList() {
        sCol = sCol.substring(0, sCol.indexOf(Const.N));
        Cursor cursor = dbMarker.getMarkersListByTitle(sCol);
        if (cursor.moveToFirst())
            sCol += Const.N + cursor.getString(0); //список закладок в подборке
        else
            sCol += Const.N;
        cursor.close();

        loadMarList();
        if (iSel == adMarker.getCount())
            iSel--;
        if (iSel == -1) {
            unSelect();
        } else {
            adMarker.getItem(iSel).setSelect(true);
            adMarker.notifyDataSetChanged();
        }

        goToEdit();
    }

    private void parseFileResult(Intent data, boolean isExport) {
        ProgressHelper.setBusy(true);
        initRotate();
        model.start(isExport, data.getDataString());
    }
}
