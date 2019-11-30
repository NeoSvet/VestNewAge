package ru.neosvet.vestnewage.fragment;

import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.work.Data;

import ru.neosvet.ui.Tip;
import ru.neosvet.ui.dialogs.CustomDialog;
import ru.neosvet.utils.BackFragment;
import ru.neosvet.utils.Const;
import ru.neosvet.utils.DataBase;
import ru.neosvet.utils.Lib;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.activity.BrowserActivity;
import ru.neosvet.vestnewage.activity.MainActivity;
import ru.neosvet.vestnewage.activity.MarkerActivity;
import ru.neosvet.vestnewage.helpers.DateHelper;
import ru.neosvet.vestnewage.helpers.ProgressHelper;
import ru.neosvet.vestnewage.list.MarkAdapter;
import ru.neosvet.vestnewage.list.MarkItem;
import ru.neosvet.vestnewage.model.LoaderModel;
import ru.neosvet.vestnewage.model.MarkersModel;

public class CollectionsFragment extends BackFragment implements Observer<Data> {
    public static final int MARKER_REQUEST = 11, EXPORT_REQUEST = 12, IMPORT_REQUEST = 13;
    private ListView lvMarker;
    private View container, fabEdit, fabMenu, fabBack, pEdit, bExport;
    private TextView tvEmpty;
    private MainActivity act;
    private MarkAdapter adMarker;
    private Tip menu;
    private int iSel = -1;
    private boolean change = false, delete = false, stopRotate, load;
    private String sCol = null, sName = null;
    private Animation anMin, anMax, anRotate;
    private MarkersModel model;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        this.container = inflater.inflate(R.layout.collections_fragment, container, false);
        act = (MainActivity) getActivity();

        initViews();
        setViews();
        if (savedInstanceState != null) {
            act.setCurFragment(this);
            sCol = savedInstanceState.getString(DataBase.COLLECTIONS);
            iSel = savedInstanceState.getInt(Const.SELECT, -1);
            sName = savedInstanceState.getString(Const.RENAME, null);
            delete = savedInstanceState.getBoolean(Const.DIALOG, false);
            if (savedInstanceState.getBoolean(Const.PAGE, false)
                    && LoaderModel.inProgress)
                initLoad();
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

        initModel();
        return this.container;
    }

    @Override
    public void onPause() {
        super.onPause();
        if (ProgressHelper.isBusy())
            ProgressHelper.removeObservers(act);
    }

    @Override
    public void onResume() {
        super.onResume();
        ProgressHelper.addObserver(act, this);
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
        outState.putBoolean(Const.PAGE, load);
        super.onSaveInstanceState(outState);
    }

    private void initModel() {
        model = ViewModelProviders.of(act).get(MarkersModel.class);
        if (ProgressHelper.isBusy())
            initRotate();
    }

    @Override
    public void onChanged(@Nullable Data data) {
        if (!ProgressHelper.isBusy() && !load)
            return;
        if (data.getBoolean(Const.START, false)) {
            act.status.loadText();
            return;
        }
        if (data.getBoolean(Const.FINISH, false)) {
            ProgressHelper.removeObservers(act);
            String error = data.getString(Const.ERROR);
            if (load) {
                load = false;
                LoaderModel.inProgress = false;
                act.status.setLoad(false);
                if (error != null)
                    act.status.setError(error);
                fabBack.setVisibility(View.VISIBLE);
            } else {
                stopRotate = true;
                ProgressHelper.setBusy(false);
                if (error != null) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(act, R.style.NeoDialog)
                            .setTitle(getResources().getString(R.string.error))
                            .setMessage(error)
                            .setPositiveButton(getResources().getString(android.R.string.ok),
                                    new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int id) {

                                        }
                                    });
                    builder.create().show();
                    return;
                }
            }
            if (data.getString(Const.LINK) != null) { //finish download page
                ProgressHelper.removeObservers(act);
                loadMarList();
                act.status.setLoad(false);
                return;
            }
            ProgressHelper.setBusy(false);
            if (data.getBoolean(Const.MODE, false)) { //export
                final String file = data.getString(Const.FILE);
                AlertDialog.Builder builder = new AlertDialog.Builder(act, R.style.NeoDialog)
                        .setMessage(getResources().getString(R.string.send_file))
                        .setPositiveButton(getResources().getString(R.string.yes),
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        Intent sendIntent = new Intent(Intent.ACTION_SEND);
                                        sendIntent.setType("text/plain");
                                        sendIntent.putExtra(Intent.EXTRA_STREAM, Uri.parse(file));
                                        startActivity(sendIntent);
                                    }
                                })
                        .setNegativeButton(getResources().getString(R.string.no),
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {

                                    }
                                });
                builder.create().show();
            } else { //import
                sCol = null;
                loadColList();
                Lib.showToast(act, getResources().getString(R.string.completed));
            }
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
        DataBase dbMarker = new DataBase(act, DataBase.MARKERS);
        SQLiteDatabase db = dbMarker.getWritableDatabase();
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
        for (int i = 0; i < mId.length; i++) {
            cursor = db.query(DataBase.MARKERS, null,
                    DataBase.ID + DataBase.Q, new String[]{mId[i]},
                    null, null, null);
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
        dbMarker.close();
        adMarker.notifyDataSetChanged();
        if (adMarker.getCount() == 0) {
            tvEmpty.setText(getResources().getString(R.string.collection_is_empty));
            tvEmpty.setVisibility(View.VISIBLE);
        }
    }

    private String getPlace(String link, String p) {
        if (p.equals("0"))
            return getResources().getString(R.string.page_entirely);
        try {
            StringBuilder b = new StringBuilder();
            if (p.contains("%")) { //позиция
                b.append(Const.N);
                b.append(DataBase.getContentPage(act, link, false));
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
                    b = new StringBuilder(getResources().getString(R.string.not_load_page));
                b.insert(0, getResources().getString(R.string.pos_n) +
                        p.replace(".", Const.COMMA) + ":" + Const.N);
            } else { //абзацы
                b.append(getResources().getString(R.string.par_n));
                b.append(p.replace(Const.COMMA, ", "));
                b.append(":");
                b.append(Const.N);
                p = DataBase.closeList(p);

                DataBase dataBase = new DataBase(act, link);
                SQLiteDatabase db = dataBase.getWritableDatabase();
                Cursor cursor = db.query(Const.TITLE, null,
                        Const.LINK + DataBase.Q, new String[]{link},
                        null, null, null);
                int id;
                if (cursor.moveToFirst()) {
                    id = cursor.getInt(cursor.getColumnIndex(DataBase.ID));
                } else { // страница не загружена...
                    cursor.close();
                    db.close();
                    dataBase.close();
                    b.append(getResources().getString(R.string.not_load_page));
                    return b.toString();
                }
                cursor.close();
                cursor = db.query(DataBase.PARAGRAPH, new String[]{DataBase.PARAGRAPH},
                        DataBase.ID + DataBase.Q, new String[]{String.valueOf(id)},
                        null, null, null);
                int i = 1;
                if (cursor.moveToFirst()) {
                    do {
                        if (p.contains(DataBase.closeList(String.valueOf(i)))) {
                            b.append(Lib.withOutTags(cursor.getString(0)));
                            b.append(Const.N);
                            b.append(Const.N);
                        }
                        i++;
                    } while (cursor.moveToNext());
                } else { // страница не загружена...
                    cursor.close();
                    db.close();
                    dataBase.close();
                    throw new Exception();
                }
                cursor.close();
                db.close();
                dataBase.close();
                b.delete(b.length() - 2, b.length());
            }
            if (b.length() > 0)
                return b.toString();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        // java.lang.IllegalStateException: Fragment CollectionsFragment{138a01e4} not attached to Activity ?
        if (p.contains("%"))
            p = act.getResources().getString(R.string.sel_pos) + p;
        else
            p = act.getResources().getString(R.string.sel_par) + DataBase.openList(p).replace(Const.COMMA, ", ");
        return p;
    }

    private String getTitle(String link) {
        String t = DataBase.getContentPage(act, link, true);
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
        act.setTitle(getResources().getString(R.string.collections));
        DataBase dbMarker = new DataBase(act, DataBase.MARKERS);
        SQLiteDatabase db = dbMarker.getWritableDatabase();
        Cursor cursor = db.query(DataBase.COLLECTIONS, null, null, null, null, null, Const.PLACE);
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
        dbMarker.close();
        if (isNull && adMarker.getCount() == 1) {
            adMarker.clear();
            tvEmpty.setText(getResources().getString(R.string.empty_collections));
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
        fabEdit = container.findViewById(R.id.fabEdit);
        fabMenu = container.findViewById(R.id.fabMenu);
        fabBack = container.findViewById(R.id.fabBack);
        pEdit = container.findViewById(R.id.pEdit);
        bExport = container.findViewById(R.id.bExport);
        menu = new Tip(act, container.findViewById(R.id.pMenu));
        tvEmpty = (TextView) container.findViewById(R.id.tvEmptyCollections);
        lvMarker = (ListView) container.findViewById(R.id.lvMarker);
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

    private void setViews() {
        lvMarker.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int pos, long l) {
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
                        if (ProgressHelper.isBusy() || load)
                            return;
                        initLoad();
                        act.status.startText();
                        LoaderModel load = ViewModelProviders.of(act).get(LoaderModel.class);
                        load.startLoad(false, adMarker.getItem(pos).getData());
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
            }
        });
        lvMarker.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
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
            }
        });
        fabEdit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (sCol == null) {
                    if (adMarker.getCount() == 1) {
                        Lib.showToast(act, getResources().getString(R.string.nothing_edit));
                        return;
                    }
                    iSel = 1;
                } else
                    iSel = 0;
                goToEdit();
            }
        });
        fabBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sCol = null;
                loadColList();
            }
        });
        container.findViewById(R.id.bOk).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveChange();
                adMarker.getItem(iSel).setSelect(false);
                adMarker.notifyDataSetChanged();
                unSelect();
            }
        });
        container.findViewById(R.id.bTop).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
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
            }
        });
        container.findViewById(R.id.bBottom).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
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
            }
        });
        container.findViewById(R.id.bEdit).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (sCol == null) {
                    renameDialog(adMarker.getItem(iSel).getTitle());
                } else {
                    saveChange();
                    Intent marker = new Intent(act, MarkerActivity.class);
                    marker.putExtra(DataBase.ID, adMarker.getItem(iSel).getId());
                    marker.putExtra(Const.LINK, adMarker.getItem(iSel).getData());
                    act.startActivityForResult(marker, MARKER_REQUEST);
                }
            }
        });
        container.findViewById(R.id.bDelete).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                delete = true;
                deleteDialog();
            }
        });
        if (android.os.Build.VERSION.SDK_INT > 18) {
            fabMenu.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
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
                }
            });
            bExport.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    selectFile(true);

                }
            });
            container.findViewById(R.id.bImport).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    selectFile(false);
                }
            });
        } else {
            fabMenu.setVisibility(View.GONE);
            fabMenu = null;
        }
    }

    private void initLoad() {
        fabBack.setVisibility(View.GONE);
        load = true;
        act.status.setLoad(true);
    }

    private void deleteDialog() {
        lvMarker.smoothScrollToPosition(iSel);
        final CustomDialog dialog = new CustomDialog(act);
        dialog.setTitle(getResources().getString(R.string.delete) + "?");
        dialog.setMessage(adMarker.getItem(iSel).getTitle());
        dialog.setLeftButton(getResources().getString(R.string.no), new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.dismiss();
            }
        });
        dialog.setRightButton(getResources().getString(R.string.yes), new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                deleteElement();
                dialog.dismiss();
            }
        });
        dialog.show(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialogInterface) {
                delete = false;
            }
        });
    }

    private void renameDialog(String old_name) {
        sName = old_name;
        final CustomDialog dialog = new CustomDialog(act);
        dialog.setTitle(getResources().getString(R.string.new_name));
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

        dialog.setLeftButton(getResources().getString(android.R.string.no), new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.dismiss();
            }
        });
        dialog.setRightButton(getResources().getString(android.R.string.yes), new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                renameCol(dialog.getInputText());
                dialog.dismiss();
            }
        });
        dialog.show(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialogInterface) {
                sName = null;
            }
        });
    }

    private void renameCol(String name) {
        boolean bCancel = (name.length() == 0);
        if (!bCancel) {
            if (name.contains(Const.COMMA)) {
                Lib.showToast(act, getResources().getString(R.string.unuse_dot));
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
            Lib.showToast(act, getResources().getString(R.string.cancel_rename));
            return;
        }
        DataBase dbMarker = new DataBase(act, DataBase.MARKERS);
        SQLiteDatabase db = dbMarker.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(Const.TITLE, name);
        int r = db.update(DataBase.COLLECTIONS, cv, DataBase.ID + DataBase.Q,
                adMarker.getItem(iSel).getStrId());
        dbMarker.close();
        if (r == 1) {
            adMarker.getItem(iSel).setTitle(name);
            adMarker.notifyDataSetChanged();
        } else
            Lib.showToast(act, getResources().getString(R.string.cancel_rename));
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
            DataBase dbMarker = new DataBase(act, DataBase.MARKERS);
            SQLiteDatabase db = dbMarker.getWritableDatabase();
            ContentValues cv;
            if (sCol == null) {
                for (int i = 1; i < adMarker.getCount(); i++) {
                    cv = new ContentValues();
                    cv.put(Const.PLACE, i);
                    db.update(DataBase.COLLECTIONS, cv, DataBase.ID + DataBase.Q,
                            adMarker.getItem(i).getStrId());
                }
            } else {
                cv = new ContentValues();
                final String t = getListId();
                cv.put(DataBase.MARKERS, t);
                db.update(DataBase.COLLECTIONS, cv, Const.TITLE + DataBase.Q,
                        new String[]{sCol.substring(0, sCol.indexOf(Const.N))});
                sCol = sCol.substring(0, sCol.indexOf(Const.N) + 1) + t;
            }
            dbMarker.close();
            change = false;
        }
    }

    private void deleteElement() {
        DataBase dbMarker = new DataBase(act, DataBase.MARKERS);
        SQLiteDatabase db = dbMarker.getWritableDatabase();
        int n;
        String s, id = String.valueOf(adMarker.getItem(iSel).getId());
        ContentValues cv;
        Cursor cursor;
        String[] mId;
        StringBuilder b = new StringBuilder();
        if (sCol == null) { //удаляем подборку
            n = 1;
            //получаем список закладок у удаляемой подборки:
            s = adMarker.getItem(iSel).getData();
            mId = DataBase.getList(s);
            cursor = db.query(DataBase.COLLECTIONS, new String[]{DataBase.ID},
                    Const.TITLE + DataBase.Q, new String[]{getResources().getString(R.string.no_collections)},
                    null, null, null);
            cursor.moveToFirst();
            int nc_id = cursor.getInt(0); //id подборки "Вне подборок"
            cursor.close();
            for (int i = 0; i < mId.length; i++) { //перебираем список закладок.
                cursor = db.query(DataBase.MARKERS, new String[]{DataBase.COLLECTIONS},
                        DataBase.ID + DataBase.Q, new String[]{mId[i]},
                        null, null, null);
                if (!cursor.moveToFirst()) continue;
                s = DataBase.closeList(cursor.getString(0)); //список подборок у закладки
                cursor.close();
                s = s.replace(DataBase.closeList(id), Const.COMMA); //убираем удаляемую подборку
                if (s.length() == 1) { //в списке не осталось подборок
                    s = String.valueOf(nc_id); //указываем "Вне подборок"
                    // добавляем в список на добавление в "Вне подборок":
                    b.append(mId[i]);
                    b.append(Const.COMMA);
                } else
                    s = DataBase.openList(s);
                //обновляем закладку:
                cv = new ContentValues();
                cv.put(DataBase.COLLECTIONS, s);
                db.update(DataBase.MARKERS, cv, DataBase.ID + DataBase.Q, new String[]{mId[i]});
            }
            //удаляем подборку:
            db.delete(DataBase.COLLECTIONS, DataBase.ID + DataBase.Q, new String[]{id});
            //дополняем список "Вне подоборок"
            if (b.length() > 0) { //список на добавление не пуст
                //получаем список закладок в "Вне подоборок":
                cursor = db.query(DataBase.COLLECTIONS, new String[]{DataBase.MARKERS},
                        DataBase.ID + DataBase.Q, new String[]{String.valueOf(nc_id)},
                        null, null, null);
                if (cursor.moveToFirst())
                    s = cursor.getString(0);
                else
                    s = "";
                cursor.close();
                //дополняем список:
                cv = new ContentValues();
                cv.put(DataBase.MARKERS, b.toString() + s);
                db.update(DataBase.COLLECTIONS, cv, DataBase.ID + DataBase.Q, new String[]{String.valueOf(nc_id)});
                loadColList(); //обновляем список подборок
            } else //иначе просто удаляем подборку из списка
                adMarker.removeAt(iSel);
        } else { //удаляем закладку
            n = 0;
            cursor = db.query(DataBase.MARKERS, new String[]{DataBase.COLLECTIONS},
                    DataBase.ID + DataBase.Q, new String[]{id},
                    null, null, null);
            if (cursor.moveToFirst()) {
                s = DataBase.closeList(cursor.getString(0)); //список подборок у закладки
                mId = DataBase.getList(s);
                for (int i = 0; i < mId.length; i++) { //перебираем список подборок
                    cursor = db.query(DataBase.COLLECTIONS, new String[]{DataBase.MARKERS},
                            DataBase.ID + DataBase.Q, new String[]{mId[i]},
                            null, null, null);
                    if (!cursor.moveToFirst()) continue;
                    s = DataBase.closeList(cursor.getString(0)); //список закладок у подборки
                    s = s.replace(DataBase.closeList(String.valueOf(id)), Const.COMMA); //убираем удаляемую закладку
                    s = DataBase.openList(s);
                    //обновляем подборку:
                    cv = new ContentValues();
                    cv.put(DataBase.MARKERS, s);
                    db.update(DataBase.COLLECTIONS, cv, DataBase.ID + DataBase.Q, new String[]{mId[i]});
                }
            }
            cursor.close();
            //удаляем закладку:
            db.delete(DataBase.MARKERS, DataBase.ID + DataBase.Q, new String[]{id});
            adMarker.removeAt(iSel);
        }
        dbMarker.close();
        if (adMarker.getCount() == n) { //не осталось элементов для редактирования
            if (n == 0) { //список закладок пуст
                tvEmpty.setText(getResources().getString(R.string.collection_is_empty));
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

    public void putResult(int resultCode) {
        if (resultCode == 1) {
            sCol = sCol.substring(0, sCol.indexOf(Const.N));
            DataBase dbMarker = new DataBase(act, DataBase.MARKERS);
            SQLiteDatabase db = dbMarker.getWritableDatabase();
            Cursor cursor = db.query(DataBase.COLLECTIONS, new String[]{DataBase.MARKERS},
                    Const.TITLE + DataBase.Q, new String[]{String.valueOf(sCol)},
                    null, null, null);
            if (cursor.moveToFirst())
                sCol += Const.N + cursor.getString(0); //список закладок в подборке
            else
                sCol += Const.N;
            cursor.close();
            dbMarker.close();

            loadMarList();
            if (iSel == adMarker.getCount())
                iSel--;
            if (iSel == -1) {
                unSelect();
            } else {
                adMarker.getItem(iSel).setSelect(true);
                adMarker.notifyDataSetChanged();
            }
        }
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

    @RequiresApi(19)
    private void selectFile(boolean export) {
        menu.hide();
        Intent intent = new Intent(export ? Intent.ACTION_CREATE_DOCUMENT : Intent.ACTION_OPEN_DOCUMENT);
        if (export) {
            DateHelper date = DateHelper.initToday(act);
            intent.putExtra(Intent.EXTRA_TITLE, DataBase.MARKERS + " "
                    + date.toString().replace(".", "-"));
        }
        intent.setType("*/*");
        act.startActivityForResult(intent, export ? EXPORT_REQUEST : IMPORT_REQUEST);
    }

    public void startModel(int code, Uri data) {
        initRotate();
        model.start(code == EXPORT_REQUEST, data.toString());
    }
}
