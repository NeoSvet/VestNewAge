package ru.neosvet.vestnewage;

import android.app.AlertDialog;
import android.app.Fragment;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import ru.neosvet.ui.MarkAdapter;
import ru.neosvet.ui.MarkItem;
import ru.neosvet.utils.DataBase;
import ru.neosvet.utils.Lib;

public class CollectionsFragment extends Fragment {
    private final String SEL = "sel", RENAME = "rename";
    public final int MARKER_REQUEST = 11;
    private ListView lvMarker;
    private View container, fabEdit, fabHelp, divEdit, pEdit;
    private TextView tvEmpty;
    private MainActivity act;
    private MarkAdapter adMarker;
    private int iSel = -1;
    private boolean boolChange = false, boolDelete = false;
    private String sCol = null, sName = null;
    private Animation anMin, anMax;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        this.container = inflater.inflate(R.layout.collections_fragment, container, false);
        act = (MainActivity) getActivity();

        initViews();
        setViews();
        if (savedInstanceState != null) {
            act.setFrCollections(this);
            sCol = savedInstanceState.getString(DataBase.COLLECTIONS);
            iSel = savedInstanceState.getInt(SEL, -1);
            sName = savedInstanceState.getString(RENAME, null);
            boolDelete = savedInstanceState.getBoolean(Lib.DIALOG, false);
        }

        if (sCol == null)
            loadColList();
        else
            loadMarList();

        if (iSel > -1) {
            goToEdit();
            if (sName != null)
                renameDialog(sName);
            else if (boolDelete)
                deleteDialog();
        }

        return this.container;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        saveChange();
        outState.putString(DataBase.COLLECTIONS, sCol);
        outState.putInt(SEL, iSel);
        outState.putString(RENAME, sName);
        outState.putBoolean(Lib.DIALOG, boolDelete);
        super.onSaveInstanceState(outState);
    }

    private void loadMarList() {
        fabEdit.clearAnimation();
        fabHelp.clearAnimation();
        fabEdit.setVisibility(View.GONE);
        fabHelp.setVisibility(View.GONE);
        adMarker.clear();
        act.setTitle(sCol.substring(0, sCol.indexOf(Lib.N)));
        DataBase dbMarker = new DataBase(act, DataBase.MARKERS);
        SQLiteDatabase db = dbMarker.getWritableDatabase();
        int iID, iPlace, iLink, iDes;
        String[] mId;
        String link = sCol.substring(sCol.indexOf(Lib.N) + 1);
        if (link.contains(",")) {
            mId = link.split(",");
        } else
            mId = new String[]{link};
        Cursor cursor;
        String place;
        int k = 0;
        for (int i = 0; i < mId.length; i++) {
            cursor = db.query(DataBase.MARKERS, null,
                    DataBase.ID + DataBase.Q, new String[]{mId[i]}
                    , null, null, null);
            if (cursor.moveToFirst()) {
                iID = cursor.getColumnIndex(DataBase.ID);
                iPlace = cursor.getColumnIndex(DataBase.PLACE);
                iLink = cursor.getColumnIndex(DataBase.LINK);
                iDes = cursor.getColumnIndex(DataBase.DESCTRIPTION);
                link = cursor.getString(iLink);
                place = cursor.getString(iPlace);
                adMarker.addItem(new MarkItem(getTitle(link), cursor.getInt(iID), link));
                adMarker.getItem(k).setPlace(place);
                adMarker.getItem(k).setDes(
                        cursor.getString(iDes) + Lib.N
                                + getPlace(link, place));
                k++;
            }
            cursor.close();
        }
        dbMarker.close();
        adMarker.notifyDataSetChanged();
        if (adMarker.getCount() == 0) {
            tvEmpty.setText(getResources().getString(R.string.collection_is_empty));
            tvEmpty.setVisibility(View.VISIBLE);
        } else if (iSel == -1) {
            fabEdit.setVisibility(View.VISIBLE);
            fabHelp.setVisibility(View.VISIBLE);
        }
    }

    private String getPlace(String link, String p) {
        if (p.equals("0"))
            return getResources().getString(R.string.page_entirely);
        try {
            StringBuilder b = new StringBuilder();
            if (p.contains("%")) {
                b.append(Lib.N);
                b.append(DataBase.getContentPage(act, link, false));
                int k = 5; // имитация нижнего "колонтитула" страницы
                int i = b.indexOf(Lib.N);
                while (i > -1) {
                    k++;
                    i = b.indexOf(Lib.N, i + 1);
                }
                float f = Float.parseFloat(p.substring(0, p.length() - 1).replace(",", ".")) / 100f;
                k = (int) ((float) k * f) + 1;
                i = 0;
                int u;
                do {
                    k--;
                    u = i;
                    i = b.indexOf(Lib.N, u + 1);
                } while (k > 1 && i > -1);
                if (b.substring(u + 1, u + 2).equals(Lib.N))
                    u++;
                if (i > -1)
                    i = b.indexOf(Lib.N, i + 1);
                if (i > -1)
                    i = b.indexOf(Lib.N, i + 1);
                b.delete(0, u + 1);
                i -= u;
                if (i > -1) {
                    if (b.substring(i - 1, i).equals(Lib.N))
                        i--;
                    b.delete(i - 1, b.length());
                }
                b.insert(0, getResources().getString(R.string.pos_n) + p + ":" + Lib.N);
            } else {
                b.append(getResources().getString(R.string.par_n));
                b.append(p.replace(",", ", "));
                b.append(":");
                b.append(Lib.N);
                p = DataBase.closeList(p);

                DataBase dataBase = new DataBase(act, link);
                SQLiteDatabase db = dataBase.getWritableDatabase();
                Cursor cursor = db.query(DataBase.TITLE, null,
                        DataBase.LINK + DataBase.Q, new String[]{link},
                        null, null, null);
                int id;
                if (cursor.moveToFirst()) {
                    id = cursor.getInt(cursor.getColumnIndex(DataBase.ID));
                } else { // страница не загружена...
                    cursor.close();
                    dataBase.close();
                    throw new Exception();
                }
                cursor.close();
                cursor = db.query(DataBase.PARAGRAPH, new String[]{DataBase.PARAGRAPH},
                        DataBase.ID + DataBase.Q, new String[]{String.valueOf(id)},
                        null, null, null);
                int i = 1;
                if (cursor.moveToFirst()) {
                    do {
                        if (p.contains(DataBase.closeList(String.valueOf(i)))) {
                            b.append(act.lib.withOutTags(cursor.getString(0)));
                            b.append(Lib.N);
                            b.append(Lib.N);
                        }
                        i++;
                    } while (cursor.moveToNext());
                } else { // страница не загружена...
                    cursor.close();
                    dataBase.close();
                    throw new Exception();
                }
                cursor.close();
                dataBase.close();
                b.delete(b.length() - 2, b.length());
            }
            if (b.length() > 0)
                return b.toString();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        if (p.contains("%"))
            p = getResources().getString(R.string.sel_pos) + p;
        else {
            p = getResources().getString(R.string.sel_par) + p.replace(",", ", ");
        }
        return p;
    }

    private String getTitle(String link) {
        String t = DataBase.getContentPage(act, link, true);
        if (t == null)
            return getResources().getString(R.string.not_found_page);
        return t;
    }

    private void loadColList() {
        fabEdit.clearAnimation();
        fabHelp.clearAnimation();
        fabEdit.setVisibility(View.GONE);
        fabHelp.setVisibility(View.GONE);
        adMarker.clear();
        act.setTitle(getResources().getString(R.string.collections));
        DataBase dbMarker = new DataBase(act, DataBase.MARKERS);
        SQLiteDatabase db = dbMarker.getWritableDatabase();
        Cursor cursor = db.query(DataBase.COLLECTIONS, null, null, null, null, null, DataBase.PLACE);
        String s;
        boolean boolNull = false;
        if (cursor.moveToFirst()) {
            int iID = cursor.getColumnIndex(DataBase.ID);
            int iTitle = cursor.getColumnIndex(DataBase.TITLE);
            int iMarkers = cursor.getColumnIndex(DataBase.MARKERS);
            do {
                s = cursor.getString(iMarkers);
                if (s == null || s.equals("")) {
                    boolNull = true;
                    s = "";
                }
                adMarker.addItem(new MarkItem(cursor.getString(iTitle), cursor.getInt(iID), s));
            } while (cursor.moveToNext());
        }
        cursor.close();
        dbMarker.close();
        if (boolNull && adMarker.getCount() == 1) {
            adMarker.clear();
            tvEmpty.setText(getResources().getString(R.string.empty_collections));
            tvEmpty.setVisibility(View.VISIBLE);
        } else {
            fabEdit.setVisibility(View.VISIBLE);
            fabHelp.setVisibility(View.VISIBLE);
            tvEmpty.setVisibility(View.GONE);
            if (PresentationActivity.checkPresentation(act, 1))
                PresentationActivity.startPresentation(act, 1, true);
        }
        adMarker.notifyDataSetChanged();
    }

    public boolean onBackPressed() {
        if (iSel > -1) {
            unSelect();
            if (sCol == null)
                loadColList();
            else
                loadMarList();
            return false;
        } else if (sCol != null) {
            sCol = null;
            loadColList();
            return false;
        }
        return true;
    }

    private void initViews() {
        fabEdit = container.findViewById(R.id.fabEdit);
        fabHelp = container.findViewById(R.id.fabHelp);
        divEdit = container.findViewById(R.id.divEdit);
        pEdit = container.findViewById(R.id.pEdit);
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
                fabHelp.setVisibility(View.GONE);
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
                if (iSel > -1) {
                    if (sCol == null && pos == 0)
                        return;
                    adMarker.getItem(iSel).setSelect(false);
                    iSel = pos;
                    adMarker.getItem(iSel).setSelect(true);
                    adMarker.notifyDataSetChanged();
                } else if (sCol == null) {
                    sCol = adMarker.getItem(pos).getTitle()
                            + Lib.N + adMarker.getItem(pos).getData();
                    loadMarList();
                } else {
                    String p;
                    if (adMarker.getItem(pos).getPlace().equals("0"))
                        p = "";
                    else {
                        p = adMarker.getItem(pos).getDes();
                        p = p.substring(p.indexOf(Lib.N, p.indexOf(Lib.N) + 1) + 1);
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
                    fabHelp.startAnimation(anMin);
                } else if (motionEvent.getAction() == MotionEvent.ACTION_UP
                        || motionEvent.getAction() == MotionEvent.ACTION_CANCEL) {
                    fabEdit.setVisibility(View.VISIBLE);
                    fabEdit.startAnimation(anMax);
                    fabHelp.setVisibility(View.VISIBLE);
                    fabHelp.startAnimation(anMax);
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
                    boolChange = true;
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
                    boolChange = true;
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
                    marker.putExtra(DataBase.LINK, adMarker.getItem(iSel).getData());
                    act.startActivityForResult(marker, MARKER_REQUEST);
                }
            }
        });
        container.findViewById(R.id.bDelete).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                boolDelete = true;
                deleteDialog();
            }
        });
        fabHelp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                PresentationActivity.startPresentation(act, 1, false);
            }
        });
    }

    private void deleteDialog() {
        lvMarker.smoothScrollToPosition(iSel);
        AlertDialog.Builder builder = new AlertDialog.Builder(act);
        LayoutInflater inflater = act.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_layout, null);
        builder.setView(dialogView);
        TextView tv = (TextView) dialogView.findViewById(R.id.title);
        tv.setText(getResources().getString(R.string.delete));
        tv = (TextView) dialogView.findViewById(R.id.message);
        tv.setText(adMarker.getItem(iSel).getTitle());
        Button button = (Button) dialogView.findViewById(R.id.leftButton);
        button.setText(getResources().getString(R.string.no));
        button.setVisibility(View.VISIBLE);
        final AlertDialog dialog = builder.create();
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialogInterface) {
                boolDelete = false;
            }
        });
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.dismiss();
            }
        });
        button = (Button) dialogView.findViewById(R.id.rightButton);
        button.setText(getResources().getString(R.string.yes));
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                deleteElement();
                dialog.dismiss();
            }
        });
        dialog.show();
    }

    private void renameDialog(String old_name) {
        AlertDialog.Builder builder = new AlertDialog.Builder(act);
        LayoutInflater inflater = act.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_layout, null);
        builder.setView(dialogView);
        TextView tv = (TextView) dialogView.findViewById(R.id.title);
        tv.setText(getResources().getString(R.string.new_name));
        tv = (TextView) dialogView.findViewById(R.id.message);
        tv.setVisibility(View.GONE);
        final EditText input = (EditText) dialogView.findViewById(R.id.input);
        input.setVisibility(View.VISIBLE);
        sName = old_name;
        input.setText(old_name);
        input.setSelection(old_name.length());
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                sName = input.getText().toString();
            }
        });
        Button button = (Button) dialogView.findViewById(R.id.leftButton);
        button.setText(getResources().getString(android.R.string.no));
        button.setVisibility(View.VISIBLE);
        final AlertDialog dialog = builder.create();
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialogInterface) {
                sName = null;
            }
        });
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.dismiss();
            }
        });
        button = (Button) dialogView.findViewById(R.id.rightButton);
        button.setText(getResources().getString(android.R.string.yes));
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                renameCol(input.getText().toString());
                dialog.dismiss();
            }
        });
        dialog.show();
    }

    private void renameCol(String name) {
        boolean bCancel = (name.length() == 0);
        if (!bCancel) {
            if (name.contains(",")) {
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
        cv.put(DataBase.TITLE, name);
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
        fabHelp.setVisibility(View.GONE);
        divEdit.setVisibility(View.VISIBLE);
        pEdit.setVisibility(View.VISIBLE);
    }

    private void saveChange() {
        if (boolChange) {
            DataBase dbMarker = new DataBase(act, DataBase.MARKERS);
            SQLiteDatabase db = dbMarker.getWritableDatabase();
            ContentValues cv;
            if (sCol == null) {
                for (int i = 1; i < adMarker.getCount(); i++) {
                    cv = new ContentValues();
                    cv.put(DataBase.PLACE, i);
                    db.update(DataBase.COLLECTIONS, cv, DataBase.ID + DataBase.Q,
                            adMarker.getItem(i).getStrId());
                }
            } else {
                cv = new ContentValues();
                final String t = getListId();
                cv.put(DataBase.MARKERS, t);
                db.update(DataBase.COLLECTIONS, cv, DataBase.TITLE + DataBase.Q,
                        new String[]{sCol.substring(0, sCol.indexOf(Lib.N))});
                sCol = sCol.substring(0, sCol.indexOf(Lib.N) + 1) + t;
            }
            dbMarker.close();
            boolChange = false;
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
            for (int i = 0; i < mId.length; i++) { //перебираем список закладок.
                cursor = db.query(DataBase.MARKERS, new String[]{DataBase.COLLECTIONS},
                        DataBase.ID + DataBase.Q, new String[]{mId[i]}
                        , null, null, null);
                if (!cursor.moveToFirst()) continue;
                s = DataBase.closeList(cursor.getString(0)); //список подборок у закладки
                cursor.close();
                s = s.replace(DataBase.closeList(id), ""); //убираем удаляемую подборку
                if (s.length() == 0) { //в списке не осталось подборок
                    s = "1"; //указываем "Вне подборок"
                    // добавляем в списк на добавление в "Вне подборок":
                    b.append(mId[i]);
                    b.append(",");
                } else {
                    s = DataBase.openList(s);
                }
                //обновляем закладку:
                cv = new ContentValues();
                cv.put(DataBase.COLLECTIONS, s);
                db.update(DataBase.MARKERS, cv, DataBase.ID + DataBase.Q,
                        new String[]{mId[i]});
            }
            //удаляем подборку:
            db.delete(DataBase.COLLECTIONS, DataBase.ID + DataBase.Q, new String[]{id});
            //дополняем список "Вне подоборок"
            if (b.length() > 0) { //список на добавление не пуст
                //получаем список закладок в "Вне подоборок":
                cursor = db.query(DataBase.COLLECTIONS, new String[]{DataBase.MARKERS},
                        DataBase.ID + DataBase.Q, new String[]{"1"}
                        , null, null, null);
                if (cursor.moveToFirst())
                    s = cursor.getString(0);
                else
                    s = "";
                cursor.close();
                //дополняем список:
                cv = new ContentValues();
                cv.put(DataBase.MARKERS, b.toString() + s);
                db.update(DataBase.COLLECTIONS, cv, DataBase.ID + DataBase.Q,
                        new String[]{"1"});
                loadColList(); //обновляем список подборок
            } else //иначе просто удаляем подборку из списка
                adMarker.removeAt(iSel);
        } else { //удаляем закладку
            n = 0;
            cursor = db.query(DataBase.MARKERS, new String[]{DataBase.COLLECTIONS},
                    DataBase.ID + DataBase.Q, new String[]{id}
                    , null, null, null);
            if (cursor.moveToFirst()) {
                s = DataBase.closeList(cursor.getString(0)); //список подборок у закладки
                mId = DataBase.getList(s);
                for (int i = 0; i < mId.length; i++) { //перебираем список подборок
                    cursor = db.query(DataBase.COLLECTIONS, new String[]{DataBase.MARKERS},
                            DataBase.ID + DataBase.Q, new String[]{mId[i]}
                            , null, null, null);
                    if (!cursor.moveToFirst()) continue;
                    s = DataBase.closeList(cursor.getString(0)); //список закладок у подборки
                    s = s.replace(DataBase.closeList(String.valueOf(id)), ""); //убираем удаляемую закладку
                    s = DataBase.openList(s);
                    //обновляем подборку:
                    cv = new ContentValues();
                    cv.put(DataBase.MARKERS, s);
                    db.update(DataBase.COLLECTIONS, cv, DataBase.ID + DataBase.Q,
                            new String[]{mId[i]});
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
        boolChange = false;
        if (adMarker.getCount() > 0) {
            fabEdit.setVisibility(View.VISIBLE);
            fabHelp.setVisibility(View.VISIBLE);
        }
        divEdit.setVisibility(View.GONE);
        pEdit.setVisibility(View.GONE);
        iSel = -1;
    }

    public void putResult(int resultCode) {
        if (resultCode == 1) {
            sCol = sCol.substring(0, sCol.indexOf(Lib.N));
            DataBase dbMarker = new DataBase(act, DataBase.MARKERS);
            SQLiteDatabase db = dbMarker.getWritableDatabase();
            Cursor cursor = db.query(DataBase.COLLECTIONS, new String[]{DataBase.MARKERS},
                    DataBase.TITLE + DataBase.Q, new String[]{String.valueOf(sCol)}
                    , null, null, null);
            if (cursor.moveToFirst())
                sCol += Lib.N + cursor.getString(0); //список закладок в подборке
            else
                sCol += Lib.N;
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
            s.append(",");
        }
        s.delete(s.length() - 1, s.length());
        return s.toString();
    }
}
