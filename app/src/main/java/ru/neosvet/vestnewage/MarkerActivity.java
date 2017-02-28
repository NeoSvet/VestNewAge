package ru.neosvet.vestnewage;

import android.app.Service;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import ru.neosvet.ui.CheckAdapter;
import ru.neosvet.ui.ResizeAnim;
import ru.neosvet.ui.SoftKeyboard;
import ru.neosvet.utils.DataBase;
import ru.neosvet.utils.Lib;

public class MarkerActivity extends AppCompatActivity {
    private final String PAGE = "page", COL = "col";
    private final int height_pos = 230;
    private float density;
    private DataBase dbCol, dbMar;
    private StringBuilder pageCon = new StringBuilder();
    private CheckAdapter adPage, adCol;
    private TextView tvSel, tvPos, tvCol;
    private EditText etDes, etCol;
    private RadioButton rPar, rPos;
    private ListView lvList;
    private SeekBar sbPos;
    private LinearLayout mainLayout;
    private View pPos, fabHelp, fabOk;
    private String link, sel;
    private SoftKeyboard softKeyboard;
    private int id, heightList, k_par;
    private byte modeList = 0;
    private boolean boolPosVis = false;
    private ContentValues markerValues;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.marker_activity);

        if (PresentationActivity.checkPresentation(this, 2))
            PresentationActivity.startPresentation(this, 2, true);

        link = getIntent().getStringExtra(DataBase.LINK);
        id = getIntent().getIntExtra(DataBase.ID, -1);
        density = getResources().getDisplayMetrics().density;
        initViews();
        restoreActivityState(savedInstanceState);
        setViews();
        initKeyboard();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putByte(Lib.LIST, modeList);
        outState.putString(DataBase.PLACE, tvSel.getText().toString());
        outState.putString(DataBase.JOURNAL, sel);
        outState.putString(DataBase.COLLECTIONS, tvCol.getText().toString());
        if (modeList == 1)
            outState.putString(PAGE, getPageList());
        else if (modeList == 2)
            outState.putString(COL, getColList());
        outState.putBoolean(DataBase.Q, (pPos.getVisibility() == View.VISIBLE));
        super.onSaveInstanceState(outState);
    }

    private String getPageList() {
        StringBuilder s = new StringBuilder();
        if (adPage.getItem(0).isCheck()) {
            s.append(getResources().getString(R.string.page_entirely));
        } else {
            s.append(getResources().getString(R.string.sel_par));
            for (int i = 1; i < adPage.getCount(); i++) {
                if (adPage.getItem(i).isCheck()) {
                    s.append(i);
                    s.append(", ");
                }
            }
            s.delete(s.length() - 2, s.length());
            if (!s.toString().contains(":")) {
                return null;
            }
        }
        return s.toString();
    }

    public String getColList() {
        StringBuilder s = new StringBuilder(getResources().getString(R.string.sel_col));
        for (int i = 0; i < adCol.getCount(); i++) {
            if (adCol.getItem(i).isCheck()) {
                s.append(adCol.getItem(i).getTitle());
                s.append(", ");
            }
        }
        s.delete(s.length() - 2, s.length());
        if (s.toString().contains(":"))
            return s.toString();
        return null;
    }

    private void initKeyboard() {
        InputMethodManager im = (InputMethodManager) getSystemService(Service.INPUT_METHOD_SERVICE);
        softKeyboard = new SoftKeyboard(mainLayout, im);
        final Handler hFab = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message message) {
                if (message.what == 0) {
                    fabHelp.setVisibility(View.VISIBLE);
                    fabOk.setVisibility(View.VISIBLE);
                } else {
                    fabHelp.setVisibility(View.GONE);
                    fabOk.setVisibility(View.GONE);
                }
                return false;
            }
        });
        softKeyboard.setSoftKeyboardCallback(new SoftKeyboard.SoftKeyboardChanged() {
            @Override
            public void onSoftKeyboardHide() {
                hFab.sendEmptyMessage(0);
            }

            @Override
            public void onSoftKeyboardShow() {
                hFab.sendEmptyMessage(1);
            }
        });
//        softKeyboard.openSoftKeyboard();
        softKeyboard.closeSoftKeyboard();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        softKeyboard.unRegisterSoftKeyboardCallback();
    }

    private void loadCol() {
        SQLiteDatabase db = dbCol.getWritableDatabase();
        Cursor cursor = db.query(DataBase.COLLECTIONS,
                new String[]{DataBase.ID, DataBase.TITLE},
                null, null, null, null, DataBase.PLACE);
        if (cursor.moveToFirst()) {
            int iID = cursor.getColumnIndex(DataBase.ID);
            int iTitle = cursor.getColumnIndex(DataBase.TITLE);
            do {
                adCol.addItem(cursor.getInt(iID), cursor.getString(iTitle));
            } while (cursor.moveToNext());
        }
        dbCol.close();
    }

    private void restoreActivityState(Bundle state) {
        if (state != null) {
            tvCol.setText(state.getString(DataBase.COLLECTIONS));
            String s = state.getString(COL);
            if (s == null)
                s = tvCol.getText().toString();
            setColList(s);
            s = state.getString(DataBase.PLACE);
            tvSel.setText(s.replace(",", ", "));
            if (s.contains("%"))
                rPos.setChecked(true);
            else
                rPar.setChecked(true);
            sel = state.getString(DataBase.JOURNAL);
            s = state.getString(PAGE);
            if (s == null) {
                if (sel.contains("%"))
                    s = tvSel.getText().toString();
                else
                    s = sel;
            }
            setPageList(s);

            modeList = state.getByte(Lib.LIST, modeList);
            if (modeList > 0) {
                if (modeList == 1)
                    lvList.setAdapter(adPage);
                else
                    lvList.setAdapter(adCol);
                mainLayout.setVisibility(View.GONE);
                lvList.getLayoutParams().height = heightList;
                lvList.requestLayout();
                fabHelp.setVisibility(View.GONE);
                lvList.setVisibility(View.VISIBLE);
            }
            if (state.getBoolean(DataBase.Q, false)) {
                mainLayout.setVisibility(View.GONE);
                fabHelp.setVisibility(View.GONE);
                pPos.getLayoutParams().height = (int) (height_pos * density);
                pPos.requestLayout();
                pPos.setVisibility(View.VISIBLE);
                boolPosVis = true;
            }
        } else if (id == -1) { //add marker mode
            DateFormat df = new SimpleDateFormat("HH:mm:ss dd.MM.yy");
            tvCol.setText(getResources().getString(R.string.sel_col) + getResources().getString(R.string.no_collections));
            adPage.getItem(0).setCheck(true);
            etDes.setText(df.format(new Date()));
            rPar.setChecked(true);
            tvSel.setText(getResources().getString(R.string.page_entirely));
            sel = getResources().getString(R.string.sel_pos) +
                    String.format("%.1f", getIntent().getFloatExtra(DataBase.PLACE, 0f)) + "%";
        } else { //edit mode
            setResult(0);
            SQLiteDatabase db = dbMar.getWritableDatabase();
            Cursor cursor = db.query(DataBase.MARKERS, null,
                    DataBase.ID + DataBase.Q, new String[]{String.valueOf(id)}
                    , null, null, null);
            cursor.moveToFirst();
            int n = cursor.getColumnIndex(DataBase.LINK);
            link = cursor.getString(n);
            n = cursor.getColumnIndex(DataBase.DESCTRIPTION);
            etDes.setText(cursor.getString(n));
            n = cursor.getColumnIndex(DataBase.PLACE);
            String s = cursor.getString(n);
            loadPage();
            if (s.contains("%")) {
                rPos.setChecked(true);
                setPosText(Float.parseFloat(s.substring(0, s.length() - 1).replace(",", ".")));
                s = getResources().getString(R.string.sel_pos) + s;
                sel = getResources().getString(R.string.page_entirely);
            } else {
                rPar.setChecked(true);
                if (s.equals("0"))
                    s = getResources().getString(R.string.page_entirely);
                else
                    s = getResources().getString(R.string.sel_par) + s.replace(",", ", ");
                sel = getResources().getString(R.string.sel_pos) + "0,0%";
            }
            setPageList(s);
            tvSel.setText(s);
            s = cursor.getString(cursor.getColumnIndex(DataBase.COLLECTIONS));
            dbMar.close();

            db = dbCol.getWritableDatabase();
            String[] mId = DataBase.getList(s);
            StringBuilder b = new StringBuilder(getResources().getString(R.string.sel_col));
            for (int i = 0; i < mId.length; i++) {
                cursor = db.query(DataBase.COLLECTIONS, null,
                        DataBase.ID + DataBase.Q, new String[]{mId[i]}
                        , null, null, DataBase.PLACE);
                if (cursor.moveToFirst()) {
                    b.append(cursor.getString(cursor.getColumnIndex(DataBase.TITLE)));
                    b.append(", ");
                }
            }
            dbCol.close();
            b.delete(b.length() - 2, b.length());
            setColList(b.toString());
            tvCol.setText(b);
        }
    }

    private void loadPage() {
        try {
            File f;
            Lib lib = new Lib(this);
            if (!link.contains("/"))
                f = lib.getFile("/" + BrowserActivity.ARTICLE + "/" + link);
            else
                f = lib.getPageFile(link);
//            if (file.exists()) {
            String s;
            BufferedReader br = new BufferedReader(new FileReader(f));
            while ((s = br.readLine()) != null) {
                if (s.contains("<title") || s.contains("<p")) {
                    s = lib.withOutTags(s);
                    pageCon.append(s);
                    pageCon.append(Lib.N);
                    pageCon.append(Lib.N);
                    adPage.addItem(s);
                }
            }
            br.close();
            k_par = 0;
            int i = pageCon.indexOf(Lib.N);
            while (i > -1) {
                k_par++;
                i = pageCon.indexOf(Lib.N, i + 1);
            }
        } catch (Exception ex) {
            adPage.clear();
        }
    }

    private void initViews() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        dbCol = new DataBase(MarkerActivity.this, DataBase.COLLECTIONS);
        dbMar = new DataBase(MarkerActivity.this, DataBase.MARKERS);
        adPage = new CheckAdapter(this);
        adCol = new CheckAdapter(this);
        loadPage();
        loadCol();
        tvSel = (TextView) findViewById(R.id.tvTitle);
        tvSel.setText(getIntent().getStringExtra(DataBase.TITLE)); //title
        tvSel = (TextView) findViewById(R.id.tvSel);
        rPar = (RadioButton) findViewById(R.id.rPar);
        rPos = (RadioButton) findViewById(R.id.rPos);
        pPos = findViewById(R.id.pPos);
        lvList = (ListView) findViewById(R.id.lvList);
        DisplayMetrics displaymetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
        heightList = displaymetrics.heightPixels -
                (int) (getResources().getInteger(R.integer.top_minus) * density);
        mainLayout = (LinearLayout) findViewById(R.id.content_marker);
        sbPos = (SeekBar) findViewById(R.id.sbPos);
        tvPos = (TextView) findViewById(R.id.tvPos);
        tvCol = (TextView) findViewById(R.id.tvCol);
        etDes = (EditText) findViewById(R.id.etDes);
        etCol = (EditText) findViewById(R.id.etCol);
        fabHelp = findViewById(R.id.fabHelp);
        fabOk = findViewById(R.id.fabOk);
    }

    public byte getModeList() {
        return modeList;
    }

    private void setViews() {
        CompoundButton.OnCheckedChangeListener typeSel = new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean check) {
                if (check) {
                    String s = sel;
                    sel = tvSel.getText().toString();
                    tvSel.setText(s);
                }
            }
        };
        rPar.setOnCheckedChangeListener(typeSel);
        rPos.setOnCheckedChangeListener(typeSel);
        tvSel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mainLayout.setVisibility(View.GONE);
                if (rPar.isChecked()) {
                    modeList = 1;
                    setPageList(tvSel.getText().toString());
                    lvList.setAdapter(adPage);
                    showList();
                } else {
                    softKeyboard.closeSoftKeyboard();
                    String s = tvSel.getText().toString();
                    s = s.substring(s.indexOf(":") + 2, s.indexOf("%"));
                    float pos = Float.parseFloat(s.replace(",", "."));
                    sbPos.setProgress((int) (pos * 10));
                    setPosText(pos);
                    boolPosVis = true;
                    fabHelp.setVisibility(View.GONE);
                    pPos.setVisibility(View.VISIBLE);
                    ResizeAnim anim = new ResizeAnim(pPos, false,
                            (int) (height_pos * density));
                    anim.setDuration(800);
                    pPos.clearAnimation();
                    pPos.startAnimation(anim);
                }
            }
        });
        sbPos.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                float f = i / 10f;
                tvPos.setText(String.format("%.1f", f) + "% \n" +
                        getResources().getString(R.string.stop_for_text));

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                newProgPos();
            }
        });
        tvCol.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mainLayout.setVisibility(View.GONE);
                modeList = 2;
                setColList(tvCol.getText().toString());
                lvList.setAdapter(adCol);
                showList();
            }
        });
        fabHelp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                PresentationActivity.startPresentation(MarkerActivity.this, 2, false);
            }
        });
        fabOk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (boolPosVis) {
                    float pos = sbPos.getProgress() / 10f;
                    tvSel.setText(getResources().getString(R.string.sel_pos) +
                            String.format("%.1f", pos) + "%");
                } else if (modeList == 1) { //page
                    String s = getPageList();
                    if (s == null) {
                        Lib.showToast(MarkerActivity.this, getResources().getString(R.string.one_for_sel));
                        return;
                    }
                    tvSel.setText(s);
                } else if (modeList == 2) { //col
                    String s = getColList();
                    if (s == null) {
                        Lib.showToast(MarkerActivity.this, getResources().getString(R.string.one_for_sel));
                        return;
                    }
                    tvCol.setText(s);
                } else if (id > -1) { //edit marker
//                    Intent data = new Intent();
//                    data.putExtra()
//                    setResult(1,data);
                    setResult(1);
                    updateMarker();
                } else {
                    addMarker();
                }
                onBackPressed();
            }
        });
        findViewById(R.id.bMinus).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String s = tvPos.getText().toString();
                while (sbPos.getProgress() > 4 && s.equals(tvPos.getText().toString())) {
                    sbPos.setProgress(sbPos.getProgress() - 5);
                    newProgPos();
                }
            }
        });
        findViewById(R.id.bPlus).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String s = tvPos.getText().toString();
                while (sbPos.getProgress() < 996 && s.equals(tvPos.getText().toString())) {
                    sbPos.setProgress(sbPos.getProgress() + 5);
                    newProgPos();
                }
            }
        });
        etCol.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View view, int keyCode, KeyEvent keyEvent) {
                if ((keyEvent.getAction() == KeyEvent.ACTION_DOWN && keyEvent.getKeyCode() == KeyEvent.KEYCODE_ENTER)
                        || keyCode == EditorInfo.IME_ACTION_GO) {
                    softKeyboard.closeSoftKeyboard();
                    if (view.equals(etCol)) { //add col
                        String s = etCol.getText().toString();
                        if (s.contains(",")) {
                            Lib.showToast(MarkerActivity.this,
                                    getResources().getString(R.string.unuse_dot));
                            return true;
                        }
                        for (int i = 0; i < adCol.getCount(); i++) {
                            if (adCol.getItem(i).equals(s)) {
                                Lib.showToast(MarkerActivity.this,
                                        getResources().getString(R.string.title_already_used));
                                return true;
                            }
                        }
                        etCol.setText("");
                        SQLiteDatabase db = dbCol.getWritableDatabase();
                        ContentValues cv;
                        //освобождаем первую позицию (PLACE) путем смещения всех вперед..
                        Cursor cursor = db.query(DataBase.COLLECTIONS,
                                new String[]{DataBase.ID, DataBase.PLACE},
                                null, null, null, null, null);
                        if (cursor.moveToFirst()) {
                            int iID = cursor.getColumnIndex(DataBase.ID);
                            int iPlace = cursor.getColumnIndex(DataBase.PLACE);
                            int id, i;
                            do {
                                i = cursor.getInt(iPlace);
                                if (i > 0) { // нулевую позицию не трогаем ("Вне подборок")
                                    id = cursor.getInt(iID);
                                    cv = new ContentValues();
                                    cv.put(DataBase.PLACE, i + 1);
                                    db.update(DataBase.COLLECTIONS, cv, DataBase.ID + DataBase.Q,
                                            new String[]{String.valueOf(id)});
                                }
                            } while (cursor.moveToNext());
                        }
                        //добавляем новую подборку на первую позицию
                        cv = new ContentValues();
                        cv.put(DataBase.PLACE, 1);
                        cv.put(DataBase.TITLE, s);
                        db.insert(DataBase.COLLECTIONS, null, cv);
                        cursor = db.query(DataBase.COLLECTIONS, null,
                                DataBase.PLACE + DataBase.Q, new String[]{"1"}, null, null, null);
                        if (cursor.moveToFirst()) {
                            adCol.insertItem(1, cursor.getInt(cursor.getColumnIndex(DataBase.ID)),
                                    cursor.getString(cursor.getColumnIndex(DataBase.TITLE)));
                        }
                        dbCol.close();
                        //добавляем подборку в поле
                        tvCol.setText(tvCol.getText() + ", " + s);
                    }
                    return true;
                }
                return false;
            }
        });
    }

    private void updateMarker() {
        //формуируем закладку
        SQLiteDatabase db = dbMar.getWritableDatabase();
        ContentValues cv = getMarkerValues();
        //обновляем закладку в базе
        String sid = String.valueOf(id);
        db.update(DataBase.MARKERS, cv,
                DataBase.ID + DataBase.Q, new String[]{sid});
        dbMar.close();
        //обновляем подборки
        db = dbCol.getWritableDatabase();
        Cursor cursor;
        int col_id;
        String s;
        sid = DataBase.closeList(sid);
        for (int i = 0; i < adCol.getCount(); i++) {
            col_id = adCol.getItem(i).getId();
            //получаем список закладок в подборке
            cursor = db.query(DataBase.COLLECTIONS, new String[]{DataBase.MARKERS},
                    DataBase.ID + DataBase.Q, new String[]{String.valueOf(col_id)}
                    , null, null, null);
            if (cursor.moveToFirst()) {
                s = cursor.getString(0); //список закладок в подборке
                if (adCol.getItem(i).isCheck()) { //в этой подоборке должна быть
                    if (!DataBase.closeList(s).contains(sid)) { //отсутствует - добавляем
                        if (s != null)
                            s = "," + s;
                        else
                            s = "";
                        //добавляем новую закладку в самое начало
                        cv = new ContentValues();
                        cv.put(DataBase.MARKERS, id + s);
                        db.update(DataBase.COLLECTIONS, cv, DataBase.ID + DataBase.Q,
                                new String[]{String.valueOf(col_id)});
                    }
                } else { //в этой подоборке не должна быть
                    if (DataBase.closeList(s).contains(sid)) {//присутствует - удаляем
                        s = DataBase.closeList(s);
                        s = s.replace(DataBase.closeList(String.valueOf(id)), "");
                        s = DataBase.openList(s);
                        //обновляем подборку
                        cv = new ContentValues();
                        cv.put(DataBase.MARKERS, s);
                        db.update(DataBase.COLLECTIONS, cv, DataBase.ID + DataBase.Q,
                                new String[]{String.valueOf(col_id)});
                    }
                }
            }
        }
        dbCol.close();
    }

    private void newProgPos() {
        float f = sbPos.getProgress() / 10f;
        setPosText(f);
    }

    private void addMarker() {
        //формулируем закладку
        SQLiteDatabase db = dbMar.getWritableDatabase();
        ContentValues cv = getMarkerValues();
        //добавляем в базу и получаем id
        long mar_id = db.insert(DataBase.MARKERS, null, cv);
        dbMar.close();
        //обновляем подборки, в которые добавлена закладка
        db = dbCol.getWritableDatabase();
        Cursor cursor;
        int col_id;
        String s;
        for (int i = 0; i < adCol.getCount(); i++) {
            if (adCol.getItem(i).isCheck()) {
                col_id = adCol.getItem(i).getId();
                //получаем список закладок в подборке
                cursor = db.query(DataBase.COLLECTIONS, new String[]{DataBase.MARKERS},
                        DataBase.ID + DataBase.Q, new String[]{String.valueOf(col_id)}
                        , null, null, null);
                if (cursor.moveToFirst()) {
                    s = cursor.getString(0); //список закладок в подборке
                    if (s != null)
                        s = "," + s;
                    else
                        s = "";
                    //добавляем новую закладку в самое начало
                    cv = new ContentValues();
                    cv.put(DataBase.MARKERS, mar_id + s);
                    db.update(DataBase.COLLECTIONS, cv, DataBase.ID + DataBase.Q,
                            new String[]{String.valueOf(col_id)});
                }
            }
        }
        dbCol.close();
    }

    private void setPageList(String s) {
        if (s.contains("№")) {
            for (int i = 0; i < adPage.getCount(); i++)
                adPage.getItem(i).setCheck(false);
            s = s.substring(s.indexOf(":") + 2).replace(", ", ",");
            String[] m = s.split(",");
            for (int i = 0; i < m.length; i++)
                adPage.getItem(Integer.parseInt(m[i])).setCheck(true);
        } else {
            for (int i = 0; i < adPage.getCount(); i++)
                adPage.getItem(i).setCheck(true);
        }
    }

    private void setColList(String s) {
        s = DataBase.closeList(s.substring(getResources().getString(R.string.sel_col).length()).replace(", ", ","));
        String t;
        for (int i = 0; i < adCol.getCount(); i++) {
            t = DataBase.closeList(adCol.getItem(i).getTitle());
            adCol.getItem(i).setCheck(s.contains(t));
        }
    }

    private void showList() {
        fabHelp.setVisibility(View.GONE);
        softKeyboard.closeSoftKeyboard();
        lvList.setVisibility(View.VISIBLE);
        ResizeAnim anim = new ResizeAnim(lvList, false, heightList);
        anim.setDuration(800);
        lvList.clearAnimation();
        lvList.startAnimation(anim);
    }

    @Override
    public void onBackPressed() {
        if (modeList > 0 || boolPosVis) {
            View v;
            if (boolPosVis) {
                v = pPos;
                boolPosVis = false;
            } else {
                v = lvList;
                modeList = 0;
            }
            ResizeAnim anim = new ResizeAnim(v, false, 10);
            anim.setDuration(600);
            anim.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {

                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    fabHelp.setVisibility(View.VISIBLE);
                    lvList.setVisibility(View.GONE);
                    pPos.setVisibility(View.GONE);
                    mainLayout.setVisibility(View.VISIBLE);
                }

                @Override
                public void onAnimationRepeat(Animation animation) {

                }
            });
            v.clearAnimation();
            v.startAnimation(anim);
        } else
            super.onBackPressed();
    }

    public void update() {
        adPage.notifyDataSetChanged();
    }

    public void setPosText(float p) {
        int k = (int) ((float) k_par * p / 100f) + 1;
        int u, i = 0;
        do {
            k--;
            u = i;
            i = pageCon.indexOf(Lib.N, u + 1);
        } while (k > 1);
        if (i > -1)
            i = pageCon.indexOf(Lib.N, i + 1);
        if (i > -1)
            i = pageCon.indexOf(Lib.N, i + 1);
        if (i > -1)
            tvPos.setText(pageCon.substring(u, i).trim());
        else
            tvPos.setText(pageCon.substring(u).trim());
    }

    public ContentValues getMarkerValues() {
        ContentValues cv = new ContentValues();
        cv.put(DataBase.LINK, link);
        cv.put(DataBase.DESCTRIPTION, etDes.getText().toString());
        //определяем место
        String s = tvSel.getText().toString();
        if (s.contains(":"))
            s = s.substring(s.indexOf(":") + 2).replace(", ", ",");
        else
            s = "0";
        cv.put(DataBase.PLACE, s);
        //формулируем список подборок
        StringBuilder b = new StringBuilder();
        setColList(tvCol.getText().toString());
        for (int i = 0; i < adCol.getCount(); i++) {
            if (adCol.getItem(i).isCheck()) {
                b.append(adCol.getItem(i).getId());
                b.append(",");
            }
        }
        b.delete(b.length() - 1, b.length());
        cv.put(DataBase.COLLECTIONS, b.toString());
        return cv;
    }
}

