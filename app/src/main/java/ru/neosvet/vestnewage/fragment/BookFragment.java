package ru.neosvet.vestnewage.fragment;

import android.app.Fragment;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TabHost;
import android.widget.TabWidget;
import android.widget.TextView;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import ru.neosvet.ui.CustomDialog;
import ru.neosvet.ui.DateDialog;
import ru.neosvet.ui.ListAdapter;
import ru.neosvet.ui.ListItem;
import ru.neosvet.ui.Tip;
import ru.neosvet.utils.Const;
import ru.neosvet.utils.DataBase;
import ru.neosvet.utils.Lib;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.activity.BrowserActivity;
import ru.neosvet.vestnewage.activity.MainActivity;
import ru.neosvet.vestnewage.activity.MarkerActivity;
import ru.neosvet.vestnewage.task.BookTask;

public class BookFragment extends Fragment implements DateDialog.Result, View.OnClickListener {
    private final String POS = "pos", KAT = "kat", OTKR = "otkr", CURRENT_TAB = "tab";
    private MainActivity act;
    private View container;
    private Animation anMin, anMax;
    private final DateFormat df = new SimpleDateFormat("MM.yy");
    private ListAdapter adBook;
    private View fabRefresh, fabRndMenu, ivPrev, ivNext;
    private TextView tvDate;
    private DateDialog dateDialog;
    private CustomDialog alertRnd;
    private BookTask task;
    private TabHost tabHost;
    private ListView lvBook;
    private int x, y, tab = 0;
    private Tip menuRnd;
    private String dialog = "";
    private boolean notClick = false, fromOtkr;
    private Date dKatren, dPoslanie;
    private SharedPreferences pref;
    private SharedPreferences.Editor editor;
    final Handler hTimer = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message message) {
            tvDate.setBackgroundDrawable(getResources().getDrawable(R.drawable.press));
            return false;
        }
    });

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        this.container = inflater.inflate(R.layout.book_fragment, container, false);
        act = (MainActivity) getActivity();
        initViews();
        setViews();
        initTabs();
        restoreActivityState(savedInstanceState);
        return this.container;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putString(Const.DIALOG, dialog);
        if (dialog.length() == 1)
            dateDialog.dismiss();
        else if (dialog.length() > 1)
            alertRnd.dismiss();
        outState.putInt(CURRENT_TAB, tab);
        outState.putSerializable(Const.TASK, task);
        super.onSaveInstanceState(outState);
    }

    private void restoreActivityState(Bundle state) {
        Date d = new Date();
        d.setYear(100);
        dKatren = new Date(pref.getLong(KAT, d.getTime()));
        dPoslanie = new Date(pref.getLong(POS, d.getTime()));
        fromOtkr = pref.getBoolean(OTKR, false);
        if (state != null) {
            tab = state.getInt(CURRENT_TAB);
            task = (BookTask) state.getSerializable(Const.TASK);
            if (task != null) {
                if (task.getStatus() == AsyncTask.Status.RUNNING) {
                    fabRefresh.setVisibility(View.GONE);
                    fabRndMenu.setVisibility(View.GONE);
                    task.setFrm(this);
                    act.status.setLoad(true);
                } else task = null;
            } else {
                dialog = state.getString(Const.DIALOG);
                if (dialog.length() == 1)
                    showDatePicker();
                else if (dialog.length() > 1) {
                    String[] m = dialog.split(Const.AND);
                    showRndAlert(m[0], m[1], m[2], m[3], Integer.parseInt(m[4]));
                }
            }
        }
        tabHost.setCurrentTab(tab);
        openList(true);
    }

    private void initTabs() {
        tabHost = (TabHost) container.findViewById(R.id.thBook);
        tabHost.setup();
        TabHost.TabSpec tabSpec;

        tabSpec = tabHost.newTabSpec(KAT);
        tabSpec.setIndicator(getResources().getString(R.string.katreny),
                getResources().getDrawable(R.drawable.none));
        tabSpec.setContent(R.id.pBook);
        tabHost.addTab(tabSpec);

        tabSpec = tabHost.newTabSpec(POS);
        tabSpec.setIndicator(getResources().getString(R.string.poslaniya),
                getResources().getDrawable(R.drawable.none));
        tabSpec.setContent(R.id.pBook);
        tabHost.addTab(tabSpec);

        TabWidget widget = tabHost.getTabWidget();
        for (int i = 0; i < widget.getChildCount(); i++) {
            View v = widget.getChildAt(i);
            TextView tv = (TextView) v.findViewById(android.R.id.title);
            if (tv != null) {
                tv.setMaxLines(1);
                v.setBackgroundResource(R.drawable.table_selector);
            }
        }
        tabHost.setCurrentTab(1);
        tabHost.setOnTabChangedListener(new TabHost.OnTabChangeListener() {
            public void onTabChanged(String name) {
                if (task != null) return;
                if (name.equals(KAT))
                    act.setTitle(getResources().getString(R.string.katreny));
                else
                    act.setTitle(getResources().getString(R.string.poslaniya));
                tab = tabHost.getCurrentTab();
                openList(true);
            }
        });
    }

    private void openList(boolean loadIfNeed) {
        try {
            Date d;
            boolean katren;
            if (tab == 0) {
                d = dKatren;
                katren = true;
            } else {
                d = dPoslanie;
                katren = false;
            }
            if (!existsList(d, katren)) {
                if (loadIfNeed)
                    startLoad();
                return;
            }
            adBook.clear();
            tvDate.setText(getResources().getStringArray(R.array.months)[d.getMonth()]
                    + "\n" + (d.getYear() + 1900));
            if (d.getMonth() == 0 && d.getYear() == 116 && !fromOtkr)
                // доступна для того, чтобы предложить скачать Послания за 2004-2015
                ivPrev.setEnabled(true);
            else
                ivPrev.setEnabled(existsList(setDate(d, -1), katren));
            ivNext.setEnabled(existsList(setDate(d, 1), katren));

            DataBase dataBase = new DataBase(act, df.format(d));
            SQLiteDatabase db = dataBase.getWritableDatabase();
            String t, s;
            Cursor cursor = db.query(DataBase.TITLE, null, null, null, null, null, null);
            Date dModList;
            if (cursor.moveToFirst()) {
                dModList = new Date(cursor.getLong(cursor.getColumnIndex(DataBase.TIME)));
                if (d.getYear() > 115) { //списки скаченные с сайта Откровений не надо открывать с фильтром - там и так всё по порядку
                    cursor.close();
                    if (katren) { // катрены
                        cursor = db.query(DataBase.TITLE, null,
                                DataBase.LINK + DataBase.LIKE,
                                new String[]{"%" + Const.POEMS + "%"}
                                , null, null, DataBase.LINK);
                    } else { // послания
                        cursor = db.query(DataBase.TITLE, null,
                                DataBase.LINK + " NOT" + DataBase.LIKE,
                                new String[]{"%" + Const.POEMS + "%"}
                                , null, null, DataBase.LINK);
                    }
                    cursor.moveToFirst();
                } else // в случае списков с сайта Откровений надо просто перейти к следующей записи
                    cursor.moveToNext();
                int iTitle = cursor.getColumnIndex(DataBase.TITLE);
                int iLink = cursor.getColumnIndex(DataBase.LINK);
                do {
                    s = cursor.getString(iLink);
                    if (s.contains("2004") || s.contains("pred"))
                        t = cursor.getString(iTitle);
                    else {
                        t = s.substring(s.lastIndexOf("/") + 1, s.lastIndexOf("."));
                        if (t.contains("_")) t = t.substring(0, t.indexOf("_"));
                        if (t.contains("#")) t = t.substring(0, t.indexOf("#"));
                        t = cursor.getString(iTitle) +
                                " (" + getResources().getString(R.string.from)
                                + " " + t + ")";
                    }
                    adBook.addItem(new ListItem(t, s));
                } while (cursor.moveToNext());
            } else
                dModList = d;
            cursor.close();
            dataBase.close();
            Date n = new Date();
            if (d.getMonth() == n.getMonth() && d.getYear() == n.getYear()) {
                //если выбранный месяц - текущий
                katren = act.status.checkTime(dModList.getTime());
            } else {
                //если выбранный месяц - предыдущий, то проверяем когда список был обновлен
                if ((d.getMonth() == n.getMonth() - 1 && d.getYear() == n.getYear()) ||
                        (d.getMonth() == 11 && d.getYear() == n.getYear() - 1)) {
                    if (dModList.getMonth() != n.getMonth())
                        act.status.checkTime(dModList.getTime());
                    else
                        katren = act.status.checkTime(System.currentTimeMillis()); //hide "ref?"
                } else
                    katren = act.status.checkTime(System.currentTimeMillis()); //hide "ref?"
            }
            if (katren)
                fabRefresh.setVisibility(View.GONE);
            else
                fabRefresh.setVisibility(View.VISIBLE);
            adBook.notifyDataSetChanged();
            lvBook.smoothScrollToPosition(0);
        } catch (Exception e) {
            e.printStackTrace();
            if (loadIfNeed)
                startLoad();
        }
    }

    private boolean existsList(Date d, boolean katren) {
        if (d.getYear() == 100) return false; //def year
        DataBase dataBase = new DataBase(act, df.format(d));
        SQLiteDatabase db = dataBase.getWritableDatabase();
        Cursor cursor = db.query(DataBase.TITLE, new String[]{DataBase.LINK},
                null, null, null, null, null);
        String s;
        if (cursor.moveToFirst()) {
            // первую запись пропускаем, т.к. там дата изменения списка
            while (cursor.moveToNext()) {
                s = cursor.getString(0);
                if ((s.contains(Const.POEMS) && katren) ||
                        (!s.contains(Const.POEMS) && !katren)) {
                    cursor.close();
                    return true;
                }
            }
        }
        cursor.close();
        return false;
    }

    @Override
    public void onPause() {
        super.onPause();
        editor.putLong(KAT, dKatren.getTime());
        editor.putLong(POS, dPoslanie.getTime());
        editor.apply();
    }

    private void initViews() {
        pref = act.getSharedPreferences(this.getClass().getSimpleName(), act.MODE_PRIVATE);
        editor = pref.edit();
        menuRnd = new Tip(act, container.findViewById(R.id.pRnd));
        fabRefresh = container.findViewById(R.id.fabRefresh);
        fabRndMenu = container.findViewById(R.id.fabRndMenu);
        tvDate = (TextView) container.findViewById(R.id.tvDate);
        ivPrev = container.findViewById(R.id.ivPrev);
        ivNext = container.findViewById(R.id.ivNext);
        anMin = AnimationUtils.loadAnimation(act, R.anim.minimize);
        anMin.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                fabRefresh.setVisibility(View.GONE);
                fabRndMenu.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
        anMax = AnimationUtils.loadAnimation(act, R.anim.maximize);
    }

    private void setViews() {
        fabRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startLoad();
            }
        });
        lvBook = (ListView) container.findViewById(R.id.lvBook);
        adBook = new ListAdapter(act);
        lvBook.setAdapter(adBook);
        lvBook.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, final int pos, long l) {
                if (notClick) return;
                BrowserActivity.openReader(act, adBook.getItem(pos).getLink(), null);
            }
        });
        lvBook.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        if (!act.status.startMin()) {
                            fabRefresh.startAnimation(anMin);
                            fabRndMenu.startAnimation(anMin);
                            menuRnd.hide();
                        }
                        x = (int) event.getX(0);
                        y = (int) event.getY(0);
                        break;
                    case MotionEvent.ACTION_UP:
                        final int x2 = (int) event.getX(0), r = Math.abs(x - x2);
                        notClick = false;
                        if (r > (int) (30 * getResources().getDisplayMetrics().density))
                            if (r > Math.abs(y - (int) event.getY(0))) {
                                if (x > x2) { // next
                                    if (ivNext.isEnabled())
                                        openMonth(1);
                                    notClick = true;
                                } else if (x < x2) { // prev
                                    if (ivPrev.isEnabled())
                                        openMonth(-1);
                                    notClick = true;
                                }
                            }
                    case MotionEvent.ACTION_CANCEL:
                        if (!act.status.startMax()) {
                            fabRefresh.setVisibility(View.VISIBLE);
                            fabRefresh.startAnimation(anMax);
                            fabRndMenu.setVisibility(View.VISIBLE);
                            fabRndMenu.startAnimation(anMax);
                        }
                        break;
                }
                return false;
            }
        });
        ivPrev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openMonth(-1);
            }
        });
        ivNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openMonth(1);
            }
        });
        act.status.setClick(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (act.status.onClick()) {
                    fabRefresh.setVisibility(View.VISIBLE);
                    fabRndMenu.setVisibility(View.VISIBLE);
                } else if (act.status.isTime())
                    startLoad();
            }
        });
        tvDate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog = "1";
                showDatePicker();
            }
        });
        fabRndMenu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (menuRnd.isShow())
                    menuRnd.hide();
                else
                    menuRnd.show();
            }
        });
        container.findViewById(R.id.bRndStih).setOnClickListener(this);
        container.findViewById(R.id.bRndPos).setOnClickListener(this);
        container.findViewById(R.id.bRndKat).setOnClickListener(this);
    }

    private void openMonth(int v) {
        if (task == null) {
            if (v == -1 && tab == 1) {
                if (dPoslanie.getMonth() == 0 && dPoslanie.getYear() == 116 && !fromOtkr) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(act, R.style.NeoDialog);
                    builder.setMessage(getResources().getString(R.string.alert_download_otkr));
                    builder.setNegativeButton(getResources().getString(R.string.no),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    dialog.dismiss();
                                }
                            });
                    builder.setPositiveButton(getResources().getString(R.string.yes),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    task = new BookTask(BookFragment.this);
                                    task.execute(3);
                                    dialog.dismiss();
                                }
                            });
                    builder.create().show();
                    return;
                }
            }
            setDate(v);
            tvDate.setBackgroundDrawable(getResources().getDrawable(R.drawable.selected));
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    hTimer.sendEmptyMessage(1);
                }
            }, 300);
            openList(true);
        }
    }

    private void setDate(int i) {
        if (tab == 0)
            dKatren = setDate(dKatren, i);
        else
            dPoslanie = setDate(dPoslanie, i);
    }

    private Date setDate(Date d, int i) {
        int m, y;
        m = d.getMonth() + i;
        y = d.getYear();
        if (m == -1) {
            y--;
            m = 11;
        } else if (m == 12) {
            y++;
            m = 0;
        }
        return new Date(y, m, 1);
    }

    private void startLoad() {
        act.status.setCrash(false);
        fabRefresh.setVisibility(View.GONE);
        fabRndMenu.setVisibility(View.GONE);
        task = new BookTask(this);
        task.execute(tab, (fromOtkr ? 1 : 0));
        act.status.setLoad(true);
    }

    public void finishLoad(String result) {
        if (tabHost.getCurrentTab() != tab)
            tabHost.setCurrentTab(tab);
        task = null;
        if (result.length() > 0) {
            Date d;
            if (tab == 0)
                d = dKatren;
            else
                d = dPoslanie;
            if (result.length() == 6) { //значит была загрузка с сайта Откровений
                if (result.substring(5).equals("0")) // загрузка не была завершена
                    return;
                fromOtkr = true;
                editor.putBoolean(OTKR, fromOtkr);
                result = result.substring(0, 5);
                d.setYear(100);
            }
            fabRefresh.setVisibility(View.VISIBLE);
            fabRndMenu.setVisibility(View.VISIBLE);
            act.status.setLoad(false);
            if (d.getYear() == 100 || !existsList(d, tab == 0)) {
                // 100 - year in default date
                d = new Date();
                d.setYear(100 + Integer.parseInt(result.substring(3, 5)));
                d.setMonth(Integer.parseInt(result.substring(0, 2)) - 1);
                if (tab == 0)
                    dKatren = d;
                else
                    dPoslanie = d;
            }
            if (existsList(d, tab == 0)) {
                openList(false);
            } else {
                Date n = new Date();
                if (n.getMonth() == d.getMonth() && n.getYear() == d.getYear())
                    Lib.showToast(act, getResources().getString(R.string.list_is_empty));
                else
                    startLoad();
            }
        } else {
            act.status.setCrash(true);
        }
    }

    public void setTab(int tab) {
        this.tab = tab;
    }

    private void showDatePicker() {
        Date d;
        if (tab == 0)
            d = dKatren;
        else
            d = dPoslanie;
        dateDialog = new DateDialog(act, d);
        dateDialog.setResult(BookFragment.this);
        if (tab == 0) { //katreny
            dateDialog.setMinMonth(1); //feb
        } else { //poslyania
            if (fromOtkr) {
                dateDialog.setMinMonth(7); //aug
                dateDialog.setMinYear(104); //2004
            }
            dateDialog.setMaxMonth(8); //sep
            dateDialog.setMaxYear(116); //2016
        }
        dateDialog.show();
    }

    @Override
    public void putDate(@Nullable Date date) {
        dialog = "";
        if (date == null) //cancel
            return;
        if (tab == 0)
            dKatren = date;
        else
            dPoslanie = date;
        openList(true);
    }

    @Override
    public void onClick(View view) {
        menuRnd.hide();
        //Определяем диапозон дат:
        Date d = new Date();
        int m, y, max_m = d.getMonth() + 1, max_y = d.getYear() - 100;
        if (view.getId() == R.id.bRndKat) {
            m = 2;
            y = 16;
        } else {
            if (fromOtkr) {
                m = 8;
                y = 4;
            } else {
                m = 1;
                y = 16;
            }
            if (view.getId() == R.id.bRndPos) {
                max_m = 9;
                max_y = 16;
            }
        }
        int n = (max_y - y) * 12 + max_m - m;
        if (max_y > 16) { //проверка на существование текущего месяца
            File f = new File(act.lib.getDBFolder() + "/" + df.format(d));
            if (!f.exists()) n--;
        }
        //определяем случайный месяц:
        Random g = new Random();
        n = g.nextInt(n);
        while (n > 0) { //определяем случайную дату
            if (m == 12) {
                m = 1;
                y++;
            } else
                m++;
            n--;
        }
        //открываем базу по случайной дате:
        String name = (m < 10 ? "0" : "") + m + "." + (y < 10 ? "0" : "") + y;
        DataBase dataBase = new DataBase(act, name);
        SQLiteDatabase db = dataBase.getWritableDatabase();
        Cursor curTitle;
        String title = null;
        //определяем условие отбора в соотвтствии с выбранным пунктом:
        if (view.getId() == R.id.bRndKat) { //случайный катрен
            title = getResources().getString(R.string.rnd_kat);
            curTitle = db.query(DataBase.TITLE, null,
                    DataBase.LINK + DataBase.LIKE,
                    new String[]{"%" + Const.POEMS + "%"}
                    , null, null, null);
        } else if (view.getId() == R.id.bRndPos) { //случайное послание
            title = getResources().getString(R.string.rnd_pos);
            curTitle = db.query(DataBase.TITLE, null,
                    DataBase.LINK + " NOT" + DataBase.LIKE,
                    new String[]{"%" + Const.POEMS + "%"}
                    , null, null, null);
        } else { //случайных стих
            curTitle = db.query(DataBase.TITLE, null, null, null, null, null, null);
        }
        //определяем случайных текст:
        if (curTitle.getCount() < 2)
            n = 0;
        else {
            g = new Random();
            n = g.nextInt(curTitle.getCount() - 2) + 2; //0 - отсуствует, 1 - дата изменения списка
        }
        if (curTitle.moveToPosition(n)) { //если случайный текст найден
            String s = "";
            if (view.getId() == R.id.bRndStih) { //случайных стих
                s = String.valueOf(curTitle.getInt(curTitle.getColumnIndex(DataBase.ID)));
                Cursor curPar = db.query(DataBase.PARAGRAPH,
                        new String[]{DataBase.PARAGRAPH},
                        DataBase.ID + DataBase.Q,
                        new String[]{s}, null, null, null);
                if (curPar.getCount() > 0) { //если текст скачен
                    g = new Random();
                    n = curPar.getCount(); //номер случайного стиха
                    if (y > 13 || (y == 13 && m > 7))
                        n--; //исключаем подпись
                    n = g.nextInt(n);
                    if (curPar.moveToPosition(n)) { //если случайный стих найден
                        s = Lib.withOutTags(curPar.getString(0));
                    } else {
                        s = "";
                        n = -1;
                    }
                } else
                    s = "";
                curPar.close();
                if (s.equals("")) {//случайный стих не найден
                    Lib.showToast(act, getResources().getString(R.string.alert_rnd));
                    title = getResources().getString(R.string.rnd_stih);
                }
            } else // случайный катрен или послание
                n = -1;
            //выводим на экран:
            String link = curTitle.getString(curTitle.getColumnIndex(DataBase.LINK));
            String msg = dataBase.getPageTitle(curTitle.getString(curTitle.getColumnIndex(DataBase.TITLE)), link);
            if (title == null) {
                title = msg;
                msg = s;
            }
            dialog = title + Const.AND + link + Const.AND + msg + Const.AND + s + Const.AND + n;
            showRndAlert(title, link, msg, s, n);
            //добавляем в журнал:
            ContentValues cv = new ContentValues();
            cv.put(DataBase.TIME, System.currentTimeMillis());
            String id = dataBase.getDatePage(link) + Const.AND + dataBase.getPageId(link) + Const.AND + n;
            DataBase dbJournal = new DataBase(act, DataBase.JOURNAL);
            db = dbJournal.getWritableDatabase();
            cv.put(DataBase.ID, id);
            db.insert(DataBase.JOURNAL, null, cv);
            dbJournal.close();
        } else
            Lib.showToast(act, getResources().getString(R.string.alert_rnd));
        curTitle.close();
    }

    private void showRndAlert(String title, final String link, String msg, final String place, final int par) {
        alertRnd = new CustomDialog(act);
        alertRnd.setTitle(title);
        alertRnd.setMessage(msg);
        alertRnd.setLeftButton(getResources().getString(R.string.in_markers), new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent marker = new Intent(act, MarkerActivity.class);
                marker.putExtra(DataBase.LINK, link);
                marker.putExtra(DataBase.PARAGRAPH, par);
                startActivity(marker);
                alertRnd.dismiss();
            }
        });
        alertRnd.setRightButton(getResources().getString(R.string.open), new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                BrowserActivity.openReader(act, link, place);
                alertRnd.dismiss();
            }
        });
        alertRnd.show(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialogInterface) {
                dialog = "";
            }
        });
    }
}
