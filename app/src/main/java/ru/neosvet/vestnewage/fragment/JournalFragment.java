package ru.neosvet.vestnewage.fragment;

import android.database.Cursor;
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import ru.neosvet.ui.Tip;
import ru.neosvet.utils.Const;
import ru.neosvet.utils.DataBase;
import ru.neosvet.utils.Lib;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.activity.BrowserActivity;
import ru.neosvet.vestnewage.activity.MainActivity;
import ru.neosvet.vestnewage.activity.MarkerActivity;
import ru.neosvet.vestnewage.helpers.DateHelper;
import ru.neosvet.vestnewage.list.ListAdapter;
import ru.neosvet.vestnewage.list.ListItem;

public class JournalFragment extends Fragment {
    private DataBase dbJournal;
    private ListAdapter adJournal;
    private MainActivity act;
    private Tip tip;
    private View tvEmptyJournal, fabClear, fabPrev, fabNext;
    private int offset = 0;
    private Animation anMin, anMax;
    private ListView lvJournal;
    private boolean finish = true, scrollToFirst = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.journal_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        act = (MainActivity) getActivity();
        act.setTitle(getResources().getString(R.string.journal));
        initViews(view);
        if (savedInstanceState != null) {
            offset = savedInstanceState.getInt(Const.START, 0);
            finish = savedInstanceState.getBoolean(Const.END, true);
            if (offset > 0 || !finish) {
                fabPrev.setVisibility(View.VISIBLE);
                fabNext.setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putInt(Const.START, offset);
        outState.putBoolean(Const.END, finish);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        dbJournal.close();
    }

    private void openList() {
        tip.hide();
        Cursor curJ = dbJournal.query(DataBase.JOURNAL, null, null, null, null, null, Const.TIME + DataBase.DESC);
        if (curJ.moveToFirst()) {
            DataBase dataBase;
            Cursor cursor;
            int iTime = curJ.getColumnIndex(Const.TIME);
            int iID = curJ.getColumnIndex(DataBase.ID);
            int i = 0;
            String s;
            String[] id;
            ListItem item;
            DateHelper now = DateHelper.initNow(act);
            if (offset > 0)
                curJ.moveToPosition(offset);
            DateHelper d;
            long t;
            do {
                id = curJ.getString(iID).split(Const.AND);
                dataBase = new DataBase(act, id[0]);
                cursor = dataBase.query(Const.TITLE, null, DataBase.ID + DataBase.Q, id[1]);
                if (cursor.moveToFirst()) {
                    s = cursor.getString(cursor.getColumnIndex(Const.LINK));
                    item = new ListItem(dataBase.getPageTitle(cursor.getString(
                            cursor.getColumnIndex(Const.TITLE)), s), s);
                    t = curJ.getLong(iTime);
                    d = DateHelper.putMills(act, t);
                    item.setDes(now.getDiffDate(t) +
                            getResources().getString(R.string.back)
                            + "\n(" + d.toString() + ")");
                    if (id.length == 3) { //случайные
                        if (id[2].equals("-1")) { //случайный катрен или послание
                            if (s.contains(Const.POEMS))
                                s = getResources().getString(R.string.rnd_kat);
                            else
                                s = getResources().getString(R.string.rnd_pos);
                        } else { //случаный стих
                            cursor.close();
                            cursor = dataBase.query(DataBase.PARAGRAPH, new String[]{DataBase.PARAGRAPH}, DataBase.ID + DataBase.Q, id[1]);
                            s = getResources().getString(R.string.rnd_stih);
                            if (cursor.moveToPosition(Integer.parseInt(id[2])))
                                s += ":" + Const.N + Lib.withOutTags(cursor.getString(0));
                        }
                        item.setDes(item.getDes() + Const.N + s);
                    }
                    adJournal.addItem(item);
                    i++;
                } else { //материал отсутствует в базе - удаляем запись о нём из журнала
                    dbJournal.delete(DataBase.JOURNAL, DataBase.ID + DataBase.Q, curJ.getString(iID));
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
        adJournal.notifyDataSetChanged();
        if (lvJournal.getFirstVisiblePosition() > 0) {
            scrollToFirst = true;
            lvJournal.smoothScrollToPosition(0);
        }
        if (adJournal.getCount() == 0) {
            fabClear.setVisibility(View.GONE);
            tvEmptyJournal.setVisibility(View.VISIBLE);
        } else
            fabClear.setVisibility(View.VISIBLE);
    }

    private void initViews(View container) {
        tip = new Tip(act, container.findViewById(R.id.tvFinish));
        fabPrev = container.findViewById(R.id.fabPrev);
        fabNext = container.findViewById(R.id.fabNext);
        fabClear = container.findViewById(R.id.fabClear);
        tvEmptyJournal = container.findViewById(R.id.tvEmptyJournal);
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
                dbJournal.delete(DataBase.JOURNAL);
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
        lvJournal = container.findViewById(R.id.lvJournal);
        adJournal = new ListAdapter(act);
        lvJournal.setAdapter(adJournal);
        lvJournal.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int pos, long l) {
                if (act.checkBusy()) return;
                String link = adJournal.getItem(pos).getLink();
                String s = adJournal.getItem(pos).getDes();
                if (s.contains(getResources().getString(R.string.rnd_stih))) {
                    s = s.substring(s.indexOf(Const.N, s.indexOf(
                            getResources().getString(R.string.rnd_stih))) + 1);
                    Lib.showToast(act, getResources().getString(R.string.long_press_for_mark));
                } else
                    s = null;
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
        anMin.setAnimationListener(new Animation.AnimationListener() {
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
        lvJournal.setOnTouchListener(new View.OnTouchListener() {
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
