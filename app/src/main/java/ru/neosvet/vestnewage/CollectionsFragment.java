package ru.neosvet.vestnewage;

import android.app.AlertDialog;
import android.app.Fragment;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

import ru.neosvet.ui.MarkAdapter;
import ru.neosvet.ui.MarkItem;
import ru.neosvet.utils.DataBase;
import ru.neosvet.utils.Lib;

public class CollectionsFragment extends Fragment {
    private final String SEL = "sel", RENAME = "rename";
    public final int MARKER_REQUEST = 11;
    private ListView lvMarker;
    private View container, fabEdit, fabHelp, divEdit, pEdit, pRename;
    private TextView tvEmpty;
    private EditText etName;
    private MainActivity act;
    private DataBase dbCol, dbMar;
    private MarkAdapter adMarker;
    private int iSel = -1;
    private boolean boolRename = false, boolChange = false;
    private String sCol = null;
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
            boolRename = savedInstanceState.getBoolean(RENAME, false);
        }

        if (sCol == null)
            loadColList();
        else
            loadMarList();

        if (iSel > -1) {
            goToEdit();
            if (boolRename) {
                pRename.setVisibility(View.VISIBLE);
                setAllVis(View.GONE);
            }
        }

        return this.container;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        saveChange();
        outState.putString(DataBase.COLLECTIONS, sCol);
        outState.putInt(SEL, iSel);
        outState.putBoolean(RENAME, boolRename);
        super.onSaveInstanceState(outState);
    }

    private void loadMarList() {
        fabEdit.clearAnimation();
        fabHelp.clearAnimation();
        fabEdit.setVisibility(View.GONE);
        fabHelp.setVisibility(View.GONE);
        adMarker.clear();
        act.setTitle(sCol.substring(0, sCol.indexOf(Lib.N)));
        SQLiteDatabase db = dbMar.getWritableDatabase();
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
        }
        dbMar.close();
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
            File file;
            if (!link.contains("/"))
                file = act.lib.getFile("/" + BrowserActivity.ARTICLE + "/" + link);
            else
                file = act.lib.getPageFile(link);
//            if (file.exists()) {
            String s;
            StringBuilder b = new StringBuilder();
            BufferedReader br = new BufferedReader(new FileReader(file));
            if (p.contains("%")) {
                b.append(Lib.N);
                while ((s = br.readLine()) != null) {
                    if (s.contains("<title") || s.contains("<p")) {
                        b.append(act.lib.withOutTags(s));
                        b.append(Lib.N);
                        b.append(Lib.N);
                    }
                }
                int k = 0;
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
                } while (k > 1);
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
                int i = 1;
                while ((s = br.readLine()) != null) {
                    if (s.contains("<p")) {
                        if (p.contains(DataBase.closeList(String.valueOf(i)))) {
                            b.append(act.lib.withOutTags(s));
                            b.append(Lib.N);
                            b.append(Lib.N);
                        }
                        i++;
                    }
                }
                b.delete(b.length() - 2, b.length());
            }
            br.close();
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
        try {
            File f;
            if (!link.contains("/"))
                f = act.lib.getFile("/" + BrowserActivity.ARTICLE + "/" + link);
            else
                f = act.lib.getPageFile(link);
//            if (file.exists()) {
            String s;
            BufferedReader br = new BufferedReader(new FileReader(f));
            do {
                s = br.readLine();
            } while (s != null && !s.contains("<title"));
            br.close();
            if (s != null)
                return s.substring(s.indexOf(">") + 1, s.indexOf("</"));
        } catch (Exception ex) {
        }
        return getResources().getString(R.string.not_found_file);
    }

    private void loadColList() {
        fabEdit.clearAnimation();
        fabHelp.clearAnimation();
        fabEdit.setVisibility(View.GONE);
        fabHelp.setVisibility(View.GONE);
        adMarker.clear();
        act.setTitle(getResources().getString(R.string.collections));
        SQLiteDatabase db = dbCol.getWritableDatabase();
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
        dbCol.close();
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
        if (boolRename) {
            closeRename();
            return false;
        } else if (iSel > -1) {
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

    private void closeRename() {
        boolRename = false;
        setAllVis(View.VISIBLE);
        pRename.setVisibility(View.GONE);
    }

    private void initViews() {
        dbMar = new DataBase(act, DataBase.MARKERS);
        dbCol = new DataBase(act, DataBase.COLLECTIONS);
        fabEdit = container.findViewById(R.id.fabEdit);
        fabHelp = container.findViewById(R.id.fabHelp);
        divEdit = container.findViewById(R.id.divEdit);
        pEdit = container.findViewById(R.id.pEdit);
        pRename = container.findViewById(R.id.pRename);
        tvEmpty = (TextView) container.findViewById(R.id.tvEmptyCollections);
        etName = (EditText) container.findViewById(R.id.etName);
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
                    BrowserActivity.openPage(act, adMarker.getItem(pos).getData(), p);
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
        container.findViewById(R.id.bCancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                closeRename();
            }
        });
        container.findViewById(R.id.bRename).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                closeRename();
                String name = etName.getText().toString();
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
                SQLiteDatabase db = dbCol.getWritableDatabase();
                ContentValues cv = new ContentValues();
                cv.put(DataBase.TITLE, name);
                int r = db.update(DataBase.COLLECTIONS, cv, DataBase.ID + DataBase.Q,
                        adMarker.getItem(iSel).getStrId());
                dbCol.close();
//                if (r == 1) {
                adMarker.getItem(iSel).setTitle(name);
                adMarker.notifyDataSetChanged();
//                }
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
                    etName.setText(adMarker.getItem(iSel).getTitle());
                    pRename.setVisibility(View.VISIBLE);
                    setAllVis(View.GONE);
                    boolRename = true;
                } else {
                    saveChange();
                    Intent marker = new Intent(act, MarkerActivity.class);
                    marker.putExtra(DataBase.TITLE, adMarker.getItem(iSel).getTitle());
                    marker.putExtra(DataBase.ID, adMarker.getItem(iSel).getId());
                    act.startActivityForResult(marker, MARKER_REQUEST);
                }
            }
        });
        container.findViewById(R.id.bDelete).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                lvMarker.smoothScrollToPosition(iSel);
                AlertDialog.Builder builder = new AlertDialog.Builder(act);
                builder.setTitle(getResources().getString(R.string.delete));
                builder.setMessage(adMarker.getItem(iSel).getTitle());
                builder.setPositiveButton(getResources().getString(android.R.string.yes),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                deleteElement();
                            }
                        });
                builder.setNegativeButton(getResources().getString(android.R.string.no),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {

                            }
                        });
                builder.create().show();
            }
        });
        fabHelp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                PresentationActivity.startPresentation(act, 1, false);
            }
        });
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
            SQLiteDatabase db = dbCol.getWritableDatabase();
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
            dbCol.close();
            boolChange = false;
        }
    }

    private void deleteElement() {
        SQLiteDatabase dbM = dbMar.getWritableDatabase();
        SQLiteDatabase dbC = dbCol.getWritableDatabase();
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
                cursor = dbM.query(DataBase.MARKERS, new String[]{DataBase.COLLECTIONS},
                        DataBase.ID + DataBase.Q, new String[]{mId[i]}
                        , null, null, null);
                if (!cursor.moveToFirst()) continue;
                s = DataBase.closeList(cursor.getString(0)); //список подборок у закладки
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
                dbM.update(DataBase.MARKERS, cv, DataBase.ID + DataBase.Q,
                        new String[]{mId[i]});
            }
            //удаляем подборку:
            dbC.delete(DataBase.COLLECTIONS, DataBase.ID + DataBase.Q, new String[]{id});
            //дополняем список "Вне подоборок"
            if (b.length() > 0) { //список на добавление не пуст
                //получаем список закладок в "Вне подоборок":
                cursor = dbC.query(DataBase.COLLECTIONS, new String[]{DataBase.MARKERS},
                        DataBase.ID + DataBase.Q, new String[]{"1"}
                        , null, null, null);
                if (cursor.moveToFirst())
                    s = cursor.getString(0);
                else
                    s = "";
                //дополняем список:
                cv = new ContentValues();
                cv.put(DataBase.MARKERS, b.toString() + s);
                dbC.update(DataBase.COLLECTIONS, cv, DataBase.ID + DataBase.Q,
                        new String[]{"1"});
                loadColList(); //обновляем список подборок
            } else //иначе просто удаляем подборку из списка
                adMarker.removeAt(iSel);
        } else { //удаляем закладку
            n = 0;
            cursor = dbM.query(DataBase.MARKERS, new String[]{DataBase.COLLECTIONS},
                    DataBase.ID + DataBase.Q, new String[]{id}
                    , null, null, null);
            if (cursor.moveToFirst()) {
                s = DataBase.closeList(cursor.getString(0)); //список подборок у закладки
                mId = DataBase.getList(s);
                for (int i = 0; i < mId.length; i++) { //перебираем список подборок
                    cursor = dbC.query(DataBase.COLLECTIONS, new String[]{DataBase.MARKERS},
                            DataBase.ID + DataBase.Q, new String[]{mId[i]}
                            , null, null, null);
                    if (!cursor.moveToFirst()) continue;
                    s = DataBase.closeList(cursor.getString(0)); //список закладок у подборки
                    s = s.replace(DataBase.closeList(String.valueOf(id)), ""); //убираем удаляемую закладку
                    s = DataBase.openList(s);
                    //обновляем подборку:
                    cv = new ContentValues();
                    cv.put(DataBase.MARKERS, s);
                    dbC.update(DataBase.COLLECTIONS, cv, DataBase.ID + DataBase.Q,
                            new String[]{mId[i]});
                }
            }
            //удаляем закладку:
            dbM.delete(DataBase.MARKERS, DataBase.ID + DataBase.Q, new String[]{id});
            adMarker.removeAt(iSel);
        }
        dbMar.close();
        dbCol.close();
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

    public void setAllVis(int vis) {
        lvMarker.setVisibility(vis);
        divEdit.setVisibility(vis);
        pEdit.setVisibility(vis);
    }

    public void putResult(int resultCode) {
        if (resultCode == 1) {
            sCol = sCol.substring(0, sCol.indexOf(Lib.N));
            SQLiteDatabase db = dbCol.getWritableDatabase();
            Cursor cursor = db.query(DataBase.COLLECTIONS, new String[]{DataBase.MARKERS},
                    DataBase.TITLE + DataBase.Q, new String[]{String.valueOf(sCol)}
                    , null, null, null);
            if (cursor.moveToFirst())
                sCol += Lib.N + cursor.getString(0); //список закладок в подборке
            else
                sCol += Lib.N;
            dbCol.close();

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
