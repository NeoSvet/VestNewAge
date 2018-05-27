package ru.neosvet.vestnewage.fragment;

import android.app.Fragment;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ListView;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

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

public class JournalFragment extends Fragment {
    private final String OFFSET = "offset", FINISH = "finish";
    private DataBase dbJournal;
    private ListAdapter adJournal;
    private MainActivity act;
    private Tip tip;
    private View container, fabClear, fabPrev, fabNext;
    private int offset = 0;
    private Animation anMin, anMax;
    private ListView lvJournal;
    private boolean finish = true, scrollToFirst = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        this.container = inflater.inflate(R.layout.journal_fragment, container, false);
        act = (MainActivity) getActivity();
        act.setTitle(getResources().getString(R.string.journal));
        initViews();
        if (savedInstanceState != null) {
            offset = savedInstanceState.getInt(OFFSET, 0);
            finish = savedInstanceState.getBoolean(FINISH, true);
            if (offset > 0 || !finish) {
                fabPrev.setVisibility(View.VISIBLE);
                fabNext.setVisibility(View.VISIBLE);
            }
        }
        return this.container;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putInt(OFFSET, offset);
        outState.putBoolean(FINISH, finish);
        super.onSaveInstanceState(outState);
    }

    private void openList() {
        tip.hide();
        SQLiteDatabase dbJ = dbJournal.getWritableDatabase();
        Cursor curJ = dbJ.query(DataBase.JOURNAL, null, null, null, null, null, DataBase.TIME + DataBase.DESC);
        if (curJ.moveToFirst()) {
            DataBase dataBase;
            SQLiteDatabase db;
            Cursor cursor;
            int iTime = curJ.getColumnIndex(DataBase.TIME);
            int iID = curJ.getColumnIndex(DataBase.ID);
            int i = 0;
            String s;
            String[] id;
            ListItem item;
            DateFormat df = new SimpleDateFormat("HH:mm:ss dd.MM.yy");
            long now = System.currentTimeMillis();
            long t;
            if (offset > 0)
                curJ.moveToPosition(offset);
            do {
                id = curJ.getString(iID).split(Const.AND);
                dataBase = new DataBase(act, id[0]);
                db = dataBase.getWritableDatabase();
                cursor = db.query(DataBase.TITLE, null, DataBase.ID + DataBase.Q,
                        new String[]{id[1]}, null, null, null);
                if (cursor.moveToFirst()) {
                    s = cursor.getString(cursor.getColumnIndex(DataBase.LINK));
                    item = new ListItem(dataBase.getPageTitle(cursor.getString(
                            cursor.getColumnIndex(DataBase.TITLE)), s), s);
                    t = curJ.getLong(iTime);
                    item.setDes(act.lib.getDiffDate(now, t) +
                            getResources().getString(R.string.back)
                            + "\n(" + df.format(new Date(t)) + ")");
                    if (id.length == 3) { //случайные
                        if (id[2].equals("-1")) { //случайный катрен или послание
                            if (s.contains(Const.POEMS))
                                s = getResources().getString(R.string.rnd_kat);
                            else
                                s = getResources().getString(R.string.rnd_pos);
                        } else { //случаный стих
                            cursor.close();
                            cursor = db.query(DataBase.PARAGRAPH,
                                    new String[]{DataBase.PARAGRAPH},
                                    DataBase.ID + DataBase.Q,
                                    new String[]{id[1]}, null, null, null);
                            s = getResources().getString(R.string.rnd_stih);
                            if (cursor.moveToPosition(Integer.parseInt(id[2])))
                                s += ":" + Const.N + Lib.withOutTags(cursor.getString(0));
                        }
                        item.setDes(item.getDes() + Const.N + s);
                    }
                    adJournal.addItem(item);
                    i++;
                } else { //материал отсутствует в базе - удаляем запись о нём из журнала
                    dbJ.delete(DataBase.JOURNAL, DataBase.ID + DataBase.Q,
                            new String[]{curJ.getString(iID)});
                }
                cursor.close();
                dataBase.close();
            } while (curJ.moveToNext() && i < Const.MAX_ON_PAGE);
            if (curJ.moveToNext()) {
                fabPrev.setVisibility(View.VISIBLE);
                fabNext.setVisibility(View.VISIBLE);
                finish = false;
            } else
                finish = true;
        }
        curJ.close();
        dbJ.close();
        adJournal.notifyDataSetChanged();
        if (lvJournal.getFirstVisiblePosition() > 0) {
            scrollToFirst = true;
            lvJournal.smoothScrollToPosition(0);
        }
        if (adJournal.getCount() == 0) {
            fabClear.setVisibility(View.GONE);
            container.findViewById(R.id.tvEmptyJournal).setVisibility(View.VISIBLE);
        } else
            fabClear.setVisibility(View.VISIBLE);
    }

    private void initViews() {
        tip = new Tip(act, container.findViewById(R.id.tvFinish));
        fabPrev = container.findViewById(R.id.fabPrev);
        fabNext = container.findViewById(R.id.fabNext);
        fabClear = container.findViewById(R.id.fabClear);
        fabPrev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (offset == 0) {
                    tip.show();
                } else {
                    offset -= Const.MAX_ON_PAGE;
                    adJournal.clear();
                    openList();
                }
            }
        });
        fabNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (finish) {
                    tip.show();
                } else {
                    offset += Const.MAX_ON_PAGE;
                    adJournal.clear();
                    openList();
                }
            }
        });
        fabClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                fabClear.setVisibility(View.GONE);
                container.findViewById(R.id.tvEmptyJournal).setVisibility(View.VISIBLE);
                SQLiteDatabase db = dbJournal.getWritableDatabase();
                db.delete(DataBase.JOURNAL, null, null);
                dbJournal.close();
                adJournal.clear();
                adJournal.notifyDataSetChanged();
                fabPrev.setVisibility(View.GONE);
                fabNext.setVisibility(View.GONE);
                finish = true;
                offset = 0;
            }
        });
        dbJournal = new DataBase(act, DataBase.JOURNAL);
        lvJournal = (ListView) container.findViewById(R.id.lvJournal);
        adJournal = new ListAdapter(act);
        lvJournal.setAdapter(adJournal);
        lvJournal.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int pos, long l) {
                String link = adJournal.getItem(pos).getLink();
                String s = adJournal.getItem(pos).getDes();
                if (s.contains(getResources().getString(R.string.rnd_stih))) {
                    s = s.substring(s.indexOf(Const.N, s.indexOf(
                            getResources().getString(R.string.rnd_stih))) + 1);
                    Lib.showToast(act, getResources().getString(R.string.long_press_for_mark));
                } else
                    s = "";
                BrowserActivity.openReader(act, link, s);
                adJournal.clear();
            }
        });
        lvJournal.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> adapterView, View view, int pos, long l) {
                String des = adJournal.getItem(pos).getDes();
                String par = null;
                int i = des.indexOf(getResources().getString(R.string.rnd_stih));
                if (i > -1 && i < des.lastIndexOf(Const.N)) {
                    par = des.substring(des.indexOf(Const.N, i) + 1);
                    i = des.indexOf("«");
                    des = des.substring(i, des.indexOf(Const.N, i) - 1);
                } else if (des.contains("«")) {
                    des = des.substring(des.indexOf("«"));
                } else
                    des = des.substring(des.indexOf("(") + 1, des.indexOf(")"));
                MarkerActivity.addMarker(act, adJournal.getItem(pos).getLink(), par, des);
                return true;
            }
        });
        lvJournal.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView absListView, int scrollState) {
                if (scrollState == SCROLL_STATE_IDLE && scrollToFirst) {
                    if (lvJournal.getFirstVisiblePosition() > 0)
                        lvJournal.smoothScrollToPosition(0);
                    else
                        scrollToFirst = false;
                }
            }

            @Override
            public void onScroll(AbsListView absListView, int firstVisibleItem,
                                 int visibleItemCount, int totalItemCount) {
            }
        });
        anMin = AnimationUtils.loadAnimation(act, R.anim.minimize);
        anMin.setAnimationListener(new Animation.AnimationListener()

        {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                fabClear.setVisibility(View.GONE);
                fabPrev.setVisibility(View.GONE);
                fabNext.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
        anMax = AnimationUtils.loadAnimation(act, R.anim.maximize);
        lvJournal.setOnTouchListener(new View.OnTouchListener()

        {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (adJournal.getCount() == 0)
                    return false;
                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                    fabClear.startAnimation(anMin);
                    if (fabNext.getVisibility() == View.VISIBLE) {
                        fabPrev.startAnimation(anMin);
                        fabNext.startAnimation(anMin);
                    }
                } else if (motionEvent.getAction() == MotionEvent.ACTION_UP
                        || motionEvent.getAction() == MotionEvent.ACTION_CANCEL) {
                    fabClear.setVisibility(View.VISIBLE);
                    fabClear.startAnimation(anMax);
                    if (offset > 0 || !finish) {
                        fabPrev.setVisibility(View.VISIBLE);
                        fabNext.setVisibility(View.VISIBLE);
                        fabPrev.startAnimation(anMax);
                        fabNext.startAnimation(anMax);
                    }
                }
                return false;
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (adJournal.getCount() == 0)
            openList();
    }
}
