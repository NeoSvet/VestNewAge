package ru.neosvet.vestnewage;

import android.app.Fragment;
import android.content.SharedPreferences;
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
import android.widget.ListView;
import android.widget.TabHost;
import android.widget.TabWidget;
import android.widget.TextView;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import ru.neosvet.ui.ListAdapter;
import ru.neosvet.ui.ListItem;
import ru.neosvet.utils.BookTask;
import ru.neosvet.utils.DataBase;
import ru.neosvet.utils.Lib;

public class BookFragment extends Fragment {
    private final String POS = "pos", KAT = "kat", CURRENT_TAB = "tab";
    private MainActivity act;
    private View container;
    private Animation anMin, anMax;
    private final DateFormat df = new SimpleDateFormat("MM.yy");
    private ListAdapter adBook;
    private View fabRefresh, ivPrev, ivNext;
    private TextView tvDate;
    private BookTask task;
    private TabHost tabHost;
    private ListView lvBook;
    private int x, y, tab = 0;
    private boolean boolNotClick = false;
    private Date dKat, dPos;
    private SharedPreferences pref;
    private SharedPreferences.Editor editor;

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
        outState.putInt(CURRENT_TAB, tab);
        outState.putSerializable(Lib.TASK, task);
        super.onSaveInstanceState(outState);
    }

    private void restoreActivityState(Bundle state) {
        Date d = new Date();
        d.setYear(2000);
        dKat = new Date(pref.getLong(KAT, d.getTime()));
        if (dKat.getYear() < 2000) dKat.setYear(dKat.getYear() + 1900);
        dPos = new Date(pref.getLong(POS, d.getTime()));
        if (dPos.getYear() < 2000) dPos.setYear(dPos.getYear() + 1900);
        if (state != null) {
            tab = state.getInt(CURRENT_TAB);
            task = (BookTask) state.getSerializable(Lib.TASK);
            if (task != null) {
//                fabRefresh.setVisibility(View.GONE);
                task.setFrm(this);
                act.status.setLoad(true);
            }
        }
        if (tab == 1) {
            tabHost.setCurrentTab(0);
            tabHost.setCurrentTab(1);
        } else
            tabHost.setCurrentTab(tab);
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

    private void openList(boolean boolLoad) {
        try {
            Date d;
            boolean bKat;
            if (tab == 0) {
                d = dKat;
                bKat = true;
            } else {
                d = dPos;
                bKat = false;
            }

            if (!existsList(d, bKat)) {
                if (boolLoad)
                    startLoad();
                return;
            }

            adBook.clear();
            tvDate.setText(getResources().getStringArray(R.array.months)[d.getMonth()]
                    + "\n" + d.getYear());
            ivPrev.setEnabled(existsList(setDate(d, -1), bKat));
            ivNext.setEnabled(existsList(setDate(d, 1), bKat));

            DataBase dataBase = new DataBase(act, df.format(d));
            SQLiteDatabase db = dataBase.getWritableDatabase();
            String t, s;
            Cursor cursor = db.query(DataBase.TITLE, null, null, null, null, null, null);
            Date dModList;
            if (cursor.moveToFirst()) {
                int iTitle = cursor.getColumnIndex(DataBase.TITLE);
                int iLink = cursor.getColumnIndex(DataBase.LINK);
                dModList = new Date(cursor.getLong(cursor.getColumnIndex(DataBase.TIME)));
                while (cursor.moveToNext()) {
                    s = cursor.getString(iLink);
                    if (s == null) continue; // need?
                    if ((s.contains(Lib.POEMS) && bKat) ||
                            (!s.contains(Lib.POEMS) && !bKat)) {
                        t = s.substring(s.lastIndexOf("/") + 1, s.lastIndexOf("."));
                        if (t.contains("_"))
                            t = t.substring(0, t.indexOf("_"));
                        t = cursor.getString(iTitle) +
                                " (" + getResources().getString(R.string.from) + " " + t + ")";
                        adBook.addItem(new ListItem(t, s));
                    }
                }
            } else {
                dModList = d;
            }
            cursor.close();
            dataBase.close();
            Date n = new Date();
            if (d.getMonth() == n.getMonth() && d.getYear() == n.getYear()) {
                bKat = act.status.checkTime(dModList.getTime());
            } else {
                //если выбранный месяц - предыдущий, то проверяем когда список был обновлен
                if ((d.getMonth() == n.getMonth() - 1 && d.getYear() == n.getYear()) ||
                        (d.getMonth() == 11 && d.getYear() == n.getYear() - 1)) {
                    if (dModList.getMonth() != n.getMonth())
                        act.status.checkTime(dModList.getTime());
                    else
                        bKat = act.status.checkTime(System.currentTimeMillis()); //hide "ref?"
                } else
                    bKat = act.status.checkTime(System.currentTimeMillis()); //hide "ref?"
            }
            if (bKat)
                fabRefresh.setVisibility(View.GONE);
            else
                fabRefresh.setVisibility(View.VISIBLE);
            adBook.notifyDataSetChanged();
            lvBook.smoothScrollToPosition(0);
        } catch (Exception e) {
            e.printStackTrace();
            if (boolLoad)
                startLoad();
        }
    }

    private boolean existsList(Date d, boolean bKat) {
        DataBase dataBase = new DataBase(act, df.format(d));
        SQLiteDatabase db = dataBase.getWritableDatabase();
        Cursor cursor = db.query(DataBase.TITLE, new String[]{DataBase.LINK},
                null, null, null, null, null);
        String s;
        if (cursor.moveToFirst()) {
            // первую запись пропускаем, т.к. там дата изменения списка
            while (cursor.moveToNext()) {
                s = cursor.getString(0);
                if (s == null) continue; // need?
                if ((s.contains(Lib.POEMS) && bKat) ||
                        (!s.contains(Lib.POEMS) && !bKat)) {
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
        editor.putLong(KAT, dKat.getTime());
        editor.putLong(POS, dPos.getTime());
        editor.apply();
    }

    private void initViews() {
        pref = act.getSharedPreferences(this.getClass().getSimpleName(), act.MODE_PRIVATE);
        editor = pref.edit();
        fabRefresh = container.findViewById(R.id.fabRefresh);
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
                if (boolNotClick) return;
                BrowserActivity.openReader(act, adBook.getItem(pos).getLink(), "");
            }
        });
        lvBook.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        if (!act.status.startMin())
                            fabRefresh.startAnimation(anMin);
                        x = (int) event.getX(0);
                        y = (int) event.getY(0);
                        break;
                    case MotionEvent.ACTION_UP:
                        final int x2 = (int) event.getX(0), r = Math.abs(x - x2);
                        boolNotClick = false;
                        if (r > (int) (30 * getResources().getDisplayMetrics().density))
                            if (r > Math.abs(y - (int) event.getY(0))) {
                                if (x > x2) { // next
                                    if (ivNext.isEnabled())
                                        openMonth(1);
                                    boolNotClick = true;
                                } else if (x < x2) { // prev
                                    if (ivPrev.isEnabled())
                                        openMonth(-1);
                                    boolNotClick = true;
                                }
                            }
                    case MotionEvent.ACTION_CANCEL:
                        if (!act.status.startMax()) {
                            fabRefresh.setVisibility(View.VISIBLE);
                            fabRefresh.startAnimation(anMax);
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
                if (act.status.onClick())
                    fabRefresh.setVisibility(View.VISIBLE);
                else if (act.status.isTime())
                    startLoad();
            }
        });
    }

    private void openMonth(int v) {
        if (task == null) {
            setDate(v);
            openList(true);
        }
    }

    private void setDate(int i) {
        if (tab == 0)
            dKat = setDate(dKat, i);
        else
            dPos = setDate(dPos, i);
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
        task = new BookTask(this);
        task.execute((byte) tab);
        act.status.setLoad(true);
    }

    public void finishLoad(String result) {
        if (tabHost.getCurrentTab() != tab)
            tabHost.setCurrentTab(tab);
        task = null;
        if (result.length() > 0) {
            boolean b;
            if (tab == 0)
                b = dKat.getYear() == 2000;
            else
                b = dPos.getYear() == 2000;
            if (b) {
                Date d = new Date();
                d.setYear(2000 + Integer.parseInt(result.substring(3, 5)));
                d.setMonth(Integer.parseInt(result.substring(0, 2)) - 1);
                if (tab == 0)
                    dKat = d;
                else
                    dPos = d;
            }
            fabRefresh.setVisibility(View.VISIBLE);
            act.status.setLoad(false);
            openList(false);
        } else {
            act.status.setCrash(true);
        }
    }

    public void setTab(int tab) {
        this.tab = tab;
    }
}
