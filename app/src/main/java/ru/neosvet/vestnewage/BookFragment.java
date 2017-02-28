package ru.neosvet.vestnewage;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.app.Fragment;
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import ru.neosvet.ui.ListAdapter;
import ru.neosvet.ui.ListItem;
import ru.neosvet.utils.BookTask;
import ru.neosvet.utils.Lib;

public class BookFragment extends Fragment {
    public final String POS = "pos", KAT = "kat", CURRENT_TAB = "tab";
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
        this.container = inflater.inflate(R.layout.fragment_book, container, false);
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
        long t = System.currentTimeMillis();
        dKat = new Date(pref.getLong(KAT, t));
        dPos = new Date(pref.getLong(POS, t));
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
                if (name.equals(KAT))
                    act.setTitle(getResources().getString(R.string.katreny));
                else
                    act.setTitle(getResources().getString(R.string.poslaniya));
                tab = tabHost.getCurrentTab();
                if (getFile().exists())
                    openList(true);
                else
                    startLoad(tab);
            }
        });
    }

    private void openList(boolean boolLoad) {
        try {
            adBook.clear();
            Date d;
            boolean bP = false;
            if (tab == 0) {
                if (dKat.getYear() < 100) {
                    dKat.setMonth(1);
                    dKat.setYear(116);
                }
                d = dKat;
            } else {
                if (dPos.getYear() < 100) {
                    dPos.setMonth(0);
                    dPos.setYear(116);
                }
                d = dPos;
                bP = true;
            }
            File f = getFile(d, bP);
            int m = d.getMonth(), y = d.getYear();
            while (!f.exists()) {
                if (m == 0) {
                    if (y == 116) {
                        if (boolLoad)
                            startLoad(tab);
                        return;
                    } else
                        d.setYear(--y);
                    m = 12;
                }
                d.setMonth(--m);
                f = getFile(d, bP);
            }
            tvDate.setText(getResources().getStringArray(R.array.months)[d.getMonth()]
                    + "\n" + (d.getYear() + 1900));
            ivPrev.setEnabled(getFile(setDate(d, -1), bP).exists());
            ivNext.setEnabled(getFile(setDate(d, 1), bP).exists());
            BufferedReader br = new BufferedReader(new FileReader(f));
            String t, s;
            while ((t = br.readLine()) != null) {
                if (bP) {
                    s = br.readLine();
                    t += " (" + getResources().getString(R.string.from) + " " + s.substring(11, s.lastIndexOf(".")) + ")";
                    adBook.addItem(new ListItem(t, s));
                } else
                    adBook.addItem(new ListItem(t, br.readLine()));
            }
            br.close();
            if (f.equals(getFile(new Date(), bP)))
                bP =act.status.checkTime(f.lastModified());
            else
                bP = act.status.checkTime(System.currentTimeMillis());
            if(bP)
                fabRefresh.setVisibility(View.GONE);
            else
                fabRefresh.setVisibility(View.VISIBLE);
            adBook.notifyDataSetChanged();
            lvBook.smoothScrollToPosition(0);
        } catch (Exception e) {
            e.printStackTrace();
            if (boolLoad)
                startLoad(tab);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        editor.putLong(KAT, dKat.getTime());
        editor.putLong(POS, dPos.getTime());
        editor.apply();
    }

    private File getFile() {
        String f = Lib.LIST;
        if (tab == 0)
            f += df.format(dKat);
        else
            f += df.format(dPos) + "p";
        return new File(act.getFilesDir() + f);
    }

    private File getFile(Date d, boolean addP) {
        String f = Lib.LIST + df.format(d) + (addP ? "p" : "");
        return new File(act.getFilesDir() + f);
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
                startLoad(tab);
            }
        });
        lvBook = (ListView) container.findViewById(R.id.lvBook);
        adBook = new ListAdapter(act);
        lvBook.setAdapter(adBook);
        lvBook.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, final int pos, long l) {
                if (boolNotClick) return;
                BrowserActivity.openActivity(act, adBook.getItem(pos).getLink());
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
                    startLoad(tab);
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

    private void startLoad(int tab) {
        act.status.setCrash(false);
        fabRefresh.setVisibility(View.GONE);
        task = new BookTask(this);
        task.execute((byte) tab);
        act.status.setLoad(true);
    }

    public void finishLoad(boolean suc) {
        task = null;
        if (suc) {
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
