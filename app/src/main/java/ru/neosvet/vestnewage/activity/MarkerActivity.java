package ru.neosvet.vestnewage.activity;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
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

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import ru.neosvet.ui.ResizeAnim;
import ru.neosvet.ui.SoftKeyboard;
import ru.neosvet.utils.Const;
import ru.neosvet.utils.DataBase;
import ru.neosvet.utils.Lib;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.helpers.DateHelper;
import ru.neosvet.vestnewage.list.CheckAdapter;

@SuppressLint("DefaultLocale")
public class MarkerActivity extends AppCompatActivity {
    private float density;
    private String pageCon;
    private CheckAdapter adPage, adCol;
    private TextView tvSel, tvPos, tvCol;
    private EditText etDes, etCol;
    private RadioButton rPar, rPos;
    private ListView lvList;
    private SeekBar sbPos;
    private LinearLayout mainLayout;
    private View pPos, fabOk;
    private String link, sel;
    private SoftKeyboard softKeyboard;
    private int id, heightDialog, k_par;
    private byte modeList = 0;
    private boolean posVisible = false;

    public static void addMarker(Context context, String link, @Nullable String par, @Nullable final String des) {
        Intent marker = new Intent(context, MarkerActivity.class);
        marker.putExtra(Const.LINK, link);
        if (par != null) {
            par = Lib.withOutTags(par);
            DataBase dataBase = new DataBase(context, DataBase.getDatePage(link));
            Cursor cursor = dataBase.query(DataBase.PARAGRAPH, new String[]{DataBase.PARAGRAPH}, DataBase.ID + DataBase.Q, dataBase.getPageId(link));
            StringBuilder s = new StringBuilder();
            if (cursor.moveToFirst()) {
                int n = 0;
                if (des != null && des.equals(context.getResources().getString(R.string.search_for)))
                    n = 1;
                do {
                    if (par.contains(Lib.withOutTags(cursor.getString(0)))) {
                        s.append(n);
                        s.append(", ");
                    }
                    n++;
                } while (cursor.moveToNext());
            }
            cursor.close();
            dataBase.close();
            if (s.length() > 0) {
                s.delete(s.length() - 2, s.length());
                if (s.indexOf(Const.COMMA) > 0) {
                    marker.putExtra(DataBase.PARAGRAPH, s.toString());
                    marker.putExtra(DataBase.ID, -2); //метка о том, что в PARAGRAPH указан список
                } else
                    marker.putExtra(DataBase.PARAGRAPH, Integer.parseInt(s.toString()));
            }
        }
        if (des != null)
            marker.putExtra(Const.DESCTRIPTION, des);
        context.startActivity(marker);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.marker_activity);

        link = getIntent().getStringExtra(Const.LINK);
        id = getIntent().getIntExtra(DataBase.ID, -1);
        density = getResources().getDisplayMetrics().density;
        initViews();
        restoreState(savedInstanceState);
        setViews();
        initKeyboard();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putByte(Const.LINK, modeList);
        outState.putString(Const.PLACE, tvSel.getText().toString());
        outState.putString(DataBase.JOURNAL, sel);
        outState.putString(DataBase.COLLECTIONS, tvCol.getText().toString());
        if (modeList == 1)
            outState.putString(Const.PAGE, getPageList());
        else if (modeList == 2)
            outState.putString(Const.LIST, getColList());
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
                if (message.what == 0)
                    fabOk.setVisibility(View.VISIBLE);
                else
                    fabOk.setVisibility(View.GONE);
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
        DataBase dbMarker = new DataBase(MarkerActivity.this, DataBase.MARKERS);
        Cursor cursor = dbMarker.query(DataBase.COLLECTIONS,
                new String[]{DataBase.ID, Const.TITLE},
                null, null, null, null, Const.PLACE);
        if (cursor.moveToFirst()) {
            int iID = cursor.getColumnIndex(DataBase.ID);
            int iTitle = cursor.getColumnIndex(Const.TITLE);
            do {
                adCol.addItem(cursor.getInt(iID), cursor.getString(iTitle));
            } while (cursor.moveToNext());
        }
        cursor.close();
        dbMarker.close();
    }

    private void restoreState(Bundle state) {
        setResult(RESULT_CANCELED);
        if (state != null) {
            tvCol.setText(state.getString(DataBase.COLLECTIONS));
            String s = state.getString(Const.LIST);
            if (s == null)
                s = tvCol.getText().toString();
            setColList(s);
            s = state.getString(Const.PLACE);
            tvSel.setText(s);
            if (s.contains("%"))
                rPos.setChecked(true);
            else
                rPar.setChecked(true);
            sel = state.getString(DataBase.JOURNAL);
            s = state.getString(Const.PAGE);
            if (s == null) {
                if (sel.contains("%"))
                    s = tvSel.getText().toString();
                else
                    s = sel;
            }
            setPageList(s);

            modeList = state.getByte(Const.LINK, modeList);
            if (modeList > 0) {
                if (modeList == 1)
                    lvList.setAdapter(adPage);
                else
                    lvList.setAdapter(adCol);
                mainLayout.setVisibility(View.GONE);
                lvList.getLayoutParams().height = heightDialog;
                lvList.requestLayout();
                lvList.setVisibility(View.VISIBLE);
            }
            if (state.getBoolean(DataBase.Q, false)) {
                mainLayout.setVisibility(View.GONE);
                pPos.getLayoutParams().height = heightDialog;
                pPos.requestLayout();
                pPos.setVisibility(View.VISIBLE);
                posVisible = true;
            }
        } else if (id < 0) { //add marker mode
            tvCol.setText(getResources().getString(R.string.sel_col) + getResources().getString(R.string.no_collections));
            adCol.getItem(0).setCheck(true);
            if (getIntent().hasExtra(Const.DESCTRIPTION))
                etDes.setText(getIntent().getStringExtra(Const.DESCTRIPTION));
            else {
                DateHelper d = DateHelper.initNow(this);
                etDes.setText(d.toString());
            }
            rPar.setChecked(true);
            if (id == -1) {
                int par = getIntent().getIntExtra(DataBase.PARAGRAPH, -1);
                if (par == -1)
                    tvSel.setText(getResources().getString(R.string.page_entirely));
                else { // случайный стих
                    adPage.getItem(par).setCheck(true);
                    tvSel.setText(getResources().getString(R.string.sel_par) + (par + 1));
                }
            } else { // id==-2 если в PARAGRAPH список (см addMarker)
                String s = getResources().getString(R.string.sel_par) +
                        getIntent().getStringExtra(DataBase.PARAGRAPH);
                tvSel.setText(s);
                setPageList(s);
            }
            sel = getResources().getString(R.string.sel_pos) +
                    String.format("%.1f%%", getIntent().getFloatExtra(Const.PLACE, 0f));
        } else { //edit mode
            DataBase dbMarker = new DataBase(MarkerActivity.this, DataBase.MARKERS);
            Cursor cursor = dbMarker.query(DataBase.MARKERS, null, DataBase.ID + DataBase.Q, id);
            cursor.moveToFirst();
            etDes.setText(cursor.getString(cursor.getColumnIndex(Const.DESCTRIPTION)));
            String s = cursor.getString(cursor.getColumnIndex(Const.PLACE));
            if (s.contains("%")) {
                rPos.setChecked(true);
                setPosText(Float.parseFloat(s.substring(0, s.length() - 1).replace(Const.COMMA, ".")));
                s = getResources().getString(R.string.sel_pos) + s;
                sel = getResources().getString(R.string.page_entirely);
            } else {
                rPar.setChecked(true);
                if (s.equals("0"))
                    s = getResources().getString(R.string.page_entirely);
                else
                    s = getResources().getString(R.string.sel_par) + s.replace(Const.COMMA, ", ");
                sel = getResources().getString(R.string.sel_pos) + "0,0%";
            }
            setPageList(s);
            tvSel.setText(s);
            s = cursor.getString(cursor.getColumnIndex(DataBase.COLLECTIONS));
            cursor.close();

            String[] mId = DataBase.getList(s);
            StringBuilder b = new StringBuilder(getResources().getString(R.string.sel_col));
            for (String id : mId) {
                cursor = dbMarker.query(DataBase.COLLECTIONS, null,
                        DataBase.ID + DataBase.Q, new String[]{id}
                        , null, null, Const.PLACE);
                if (cursor.moveToFirst()) {
                    b.append(cursor.getString(cursor.getColumnIndex(Const.TITLE)));
                    b.append(", ");
                }
                cursor.close();
            }
            dbMarker.close();
            b.delete(b.length() - 2, b.length());
            setColList(b.toString());
            tvCol.setText(b);
        }
    }

    private void loadPage() {
        k_par = 5; // имитация нижнего "колонтитула" страницы
        pageCon = DataBase.getContentPage(this, link, false);
        adPage.clear();
        if (pageCon == null) // страница не загружена...
            return;
        String[] m = pageCon.split(Const.NN);
        int i;
        ((TextView) findViewById(R.id.tvTitle)).setText(m[0]);
        for (i = 0; i < m.length; i++) {
            adPage.addItem(m[i]);
        }
        i = pageCon.indexOf(Const.N);
        while (i > -1) {
            k_par++;
            i = pageCon.indexOf(Const.N, i + 1);
        }
    }

    private void initViews() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        adPage = new CheckAdapter(this);
        loadPage();
        adCol = new CheckAdapter(this);
        loadCol();
        tvSel = findViewById(R.id.tvSel);
        rPar = findViewById(R.id.rPar);
        rPos = findViewById(R.id.rPos);
        pPos = findViewById(R.id.pPos);
        lvList = findViewById(R.id.lvList);
        DisplayMetrics displaymetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
        heightDialog = displaymetrics.heightPixels -
                (int) (getResources().getInteger(R.integer.top_minus) * density);
        mainLayout = findViewById(R.id.content_marker);
        sbPos = findViewById(R.id.sbPos);
        tvPos = findViewById(R.id.tvPos);
        tvCol = findViewById(R.id.tvCol);
        etDes = findViewById(R.id.etDes);
        etCol = findViewById(R.id.etCol);
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
                    float pos = Float.parseFloat(s.replace(Const.COMMA, "."));
                    sbPos.setProgress((int) (pos * 10));
                    setPosText(pos);
                    posVisible = true;
                    pPos.setVisibility(View.VISIBLE);
                    ResizeAnim anim = new ResizeAnim(pPos, false, heightDialog);
                    anim.setDuration(800);
                    pPos.clearAnimation();
                    pPos.startAnimation(anim);
                }
            }
        });
        sbPos.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float f = progress / 10f;
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
        fabOk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (posVisible) {
                    float pos = sbPos.getProgress() / 10f;
                    tvSel.setText(getResources().getString(R.string.sel_pos) +
                            String.format("%.1f%%", pos));
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
                    setResult(RESULT_OK);
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
                        if (s.contains(Const.COMMA)) {
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
                        DataBase dbMarker = new DataBase(MarkerActivity.this, DataBase.MARKERS);
                        ContentValues cv;
                        //освобождаем первую позицию (PLACE) путем смещения всех вперед..
                        Cursor cursor = dbMarker.query(DataBase.COLLECTIONS,
                                new String[]{DataBase.ID, Const.PLACE});
                        if (cursor.moveToFirst()) {
                            int iID = cursor.getColumnIndex(DataBase.ID);
                            int iPlace = cursor.getColumnIndex(Const.PLACE);
                            int id, i;
                            do {
                                i = cursor.getInt(iPlace);
                                if (i > 0) { // нулевую позицию не трогаем ("Вне подборок")
                                    id = cursor.getInt(iID);
                                    cv = new ContentValues();
                                    cv.put(Const.PLACE, i + 1);
                                    dbMarker.update(DataBase.COLLECTIONS, cv, DataBase.ID + DataBase.Q, id);
                                }
                            } while (cursor.moveToNext());
                        }
                        cursor.close();
                        //добавляем новую подборку на первую позицию
                        cv = new ContentValues();
                        cv.put(Const.PLACE, 1);
                        cv.put(Const.TITLE, s);
                        dbMarker.insert(DataBase.COLLECTIONS, cv);
                        cursor = dbMarker.query(DataBase.COLLECTIONS, null,
                                Const.PLACE + DataBase.Q, "1");
                        if (cursor.moveToFirst()) {
                            adCol.insertItem(1, cursor.getInt(cursor.getColumnIndex(DataBase.ID)),
                                    cursor.getString(cursor.getColumnIndex(Const.TITLE)));
                        }
                        cursor.close();
                        dbMarker.close();
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
        DataBase dbMarker = new DataBase(MarkerActivity.this, DataBase.MARKERS);
        ContentValues cv = getMarkerValues();
        //обновляем закладку в базе
        String sid = String.valueOf(id);
        dbMarker.update(DataBase.MARKERS, cv, DataBase.ID + DataBase.Q, sid);

        //обновляем подборки
        Cursor cursor;
        int col_id;
        String s;
        sid = DataBase.closeList(sid);
        for (int i = 0; i < adCol.getCount(); i++) {
            col_id = adCol.getItem(i).getId();
            //получаем список закладок в подборке
            cursor = dbMarker.query(DataBase.COLLECTIONS, new String[]{DataBase.MARKERS}, DataBase.ID + DataBase.Q, col_id);
            if (cursor.moveToFirst()) {
                s = cursor.getString(0); //список закладок в подборке
                if (adCol.getItem(i).isCheck()) { //в этой подоборке должна быть
                    if (!DataBase.closeList(s).contains(sid)) { //отсутствует - добавляем
                        if (s != null)
                            s = Const.COMMA + s;
                        else
                            s = "";
                        //добавляем новую закладку в самое начало
                        cv = new ContentValues();
                        cv.put(DataBase.MARKERS, id + s);
                        dbMarker.update(DataBase.COLLECTIONS, cv, DataBase.ID + DataBase.Q, col_id);
                    }
                } else { //в этой подоборке не должна быть
                    if (DataBase.closeList(s).contains(sid)) {//присутствует - удаляем
                        s = DataBase.closeList(s);
                        s = s.replace(DataBase.closeList(String.valueOf(id)), "");
                        s = DataBase.openList(s);
                        //обновляем подборку
                        cv = new ContentValues();
                        cv.put(DataBase.MARKERS, s);
                        dbMarker.update(DataBase.COLLECTIONS, cv, DataBase.ID + DataBase.Q, col_id);
                    }
                }
            }
            cursor.close();
        }
        dbMarker.close();
    }

    private void newProgPos() {
        float f = sbPos.getProgress() / 10f;
        setPosText(f);
    }

    private void addMarker() {
        //формулируем закладку
        DataBase dbMarker = new DataBase(MarkerActivity.this, DataBase.MARKERS);
        ContentValues cv = getMarkerValues();
        //добавляем в базу и получаем id
        long mar_id = dbMarker.insert(DataBase.MARKERS, null, cv);
        //обновляем подборки, в которые добавлена закладка
        Cursor cursor;
        int col_id;
        String s;
        for (int i = 0; i < adCol.getCount(); i++) {
            if (adCol.getItem(i).isCheck()) {
                col_id = adCol.getItem(i).getId();
                //получаем список закладок в подборке
                cursor = dbMarker.query(DataBase.COLLECTIONS, new String[]{DataBase.MARKERS},
                        DataBase.ID + DataBase.Q, col_id);
                if (cursor.moveToFirst()) {
                    s = cursor.getString(0); //список закладок в подборке
                    if (s != null)
                        s = Const.COMMA + s;
                    else
                        s = "";
                    //добавляем новую закладку в самое начало
                    cv = new ContentValues();
                    cv.put(DataBase.MARKERS, mar_id + s);
                    dbMarker.update(DataBase.COLLECTIONS, cv, DataBase.ID + DataBase.Q, col_id);
                }
                cursor.close();
            }
        }
        dbMarker.close();
    }

    private void setPageList(String s) {
        if (s.contains("№")) {
            for (int i = 0; i < adPage.getCount(); i++)
                adPage.getItem(i).setCheck(false);
            s = s.substring(s.indexOf(":") + 2).replace(", ", Const.COMMA);
            String[] m = s.split(Const.COMMA);
            for (String item : m)
                adPage.getItem(Integer.parseInt(item)).setCheck(true);
        } else {
            for (int i = 0; i < adPage.getCount(); i++)
                adPage.getItem(i).setCheck(true);
        }
    }

    private void setColList(String s) {
        s = DataBase.closeList(s.substring(getResources().getString(R.string.sel_col).length()).replace(", ", Const.COMMA));
        String t;
        for (int i = 0; i < adCol.getCount(); i++) {
            t = DataBase.closeList(adCol.getItem(i).getTitle());
            adCol.getItem(i).setCheck(s.contains(t));
        }
    }

    private void showList() {
        softKeyboard.closeSoftKeyboard();
        lvList.setVisibility(View.VISIBLE);
        ResizeAnim anim = new ResizeAnim(lvList, false, heightDialog);
        anim.setDuration(800);
        lvList.clearAnimation();
        lvList.startAnimation(anim);
    }

    @Override
    public void onBackPressed() {
        if (modeList > 0 || posVisible) {
            View v;
            if (posVisible) {
                v = pPos;
                posVisible = false;
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
            i = pageCon.indexOf(Const.N, u + 1);
        } while (k > 1 && i > -1);
        if (i > -1)
            i = pageCon.indexOf(Const.N, i + 1);
        if (i > -1)
            i = pageCon.indexOf(Const.N, i + 1);
        if (i > -1)
            tvPos.setText(pageCon.substring(u, i).trim());
        else
            tvPos.setText(pageCon.substring(u).trim());
    }

    public ContentValues getMarkerValues() {
        ContentValues cv = new ContentValues();
        cv.put(Const.LINK, link);
        cv.put(Const.DESCTRIPTION, etDes.getText().toString());
        //определяем место
        String s = tvSel.getText().toString();
        if (s.contains(":"))
            s = s.substring(s.indexOf(":") + 2).replace(", ", Const.COMMA);
        else
            s = "0";
        cv.put(Const.PLACE, s);
        //формулируем список подборок
        StringBuilder b = new StringBuilder();
        setColList(tvCol.getText().toString());
        for (int i = 0; i < adCol.getCount(); i++) {
            if (adCol.getItem(i).isCheck()) {
                b.append(adCol.getItem(i).getId());
                b.append(Const.COMMA);
            }
        }
        b.delete(b.length() - 1, b.length());
        cv.put(DataBase.COLLECTIONS, b.toString());
        return cv;
    }
}

