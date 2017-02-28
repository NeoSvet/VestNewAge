package ru.neosvet.vestnewage;

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
import android.widget.AdapterView;
import android.widget.ListView;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import ru.neosvet.ui.ListAdapter;
import ru.neosvet.ui.ListItem;
import ru.neosvet.ui.Tip;
import ru.neosvet.utils.DataBase;
import ru.neosvet.utils.Lib;

public class JournalFragment extends Fragment {
    private final String OFFSET = "offset", FINISH = "finish";
    private final int MAX = 10;
    private DataBase dbJournal;
    private ListAdapter adJournal;
    private MainActivity act;
    private Tip tip;
    private View container, fabClear, fabPrev, fabNext;
    private int offset = 0;
    private Animation anMin, anMax;
    private boolean boolFinish = true;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        this.container = inflater.inflate(R.layout.fragment_journal, container, false);
        act = (MainActivity) getActivity();
        act.setTitle(getResources().getString(R.string.journal));
        initViews();
        if (savedInstanceState != null) {
            offset = savedInstanceState.getInt(OFFSET, 0);
            boolFinish = savedInstanceState.getBoolean(FINISH, true);
            if (offset > 0 || !boolFinish) {
                fabPrev.setVisibility(View.VISIBLE);
                fabNext.setVisibility(View.VISIBLE);
            }
        }
        return this.container;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putInt(OFFSET, offset);
        outState.putBoolean(FINISH, boolFinish);
        super.onSaveInstanceState(outState);
    }

    private void createList() {
        tip.hide();
        SQLiteDatabase db = dbJournal.getWritableDatabase();
        Cursor cursor = db.query(DataBase.JOURNAL, null, null, null, null, null, DataBase.TIME + DataBase.DESC);
        if (cursor.moveToFirst()) {
            int iTime = cursor.getColumnIndex(DataBase.TIME);
            int iLink = cursor.getColumnIndex(DataBase.LINK);
            int iTitle = cursor.getColumnIndex(DataBase.TITLE);
            int i = 0;
            ListItem item;
            DateFormat df = new SimpleDateFormat("HH:mm:ss dd.MM.yy");
            long now = System.currentTimeMillis();
            long t;
            if (offset > 0)
                cursor.moveToPosition(offset);
            do {
                item = new ListItem(cursor.getString(iTitle), cursor.getString(iLink));
                t = cursor.getLong(iTime);
                item.setDes(act.lib.getDiffDate(now, t) + "\n(" + df.format(new Date(t)) + ")");
                adJournal.addItem(item);
                i++;
            } while (cursor.moveToNext() && i < MAX);
            if (cursor.moveToNext()) {
                fabPrev.setVisibility(View.VISIBLE);
                fabNext.setVisibility(View.VISIBLE);
                boolFinish = false;
            } else
                boolFinish = true;
        }
        db.close();
        adJournal.notifyDataSetChanged();
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
                    offset -= MAX;
                    adJournal.clear();
                    createList();
                }
            }
        });
        fabNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (boolFinish) {
                    tip.show();
                } else {
                    offset += MAX;
                    adJournal.clear();
                    createList();
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
                boolFinish = true;
                offset = 0;
            }
        });
        dbJournal = new DataBase(act, DataBase.JOURNAL);
        ListView lvJournal = (ListView) container.findViewById(R.id.lvJournal);
        adJournal = new ListAdapter(act);
        lvJournal.setAdapter(adJournal);
        lvJournal.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int pos, long l) {
                String link = adJournal.getItem(pos).getLink();
                if (link.indexOf(BrowserActivity.ARTICLE) == Lib.LINK.length()) //атавизм-рудимент
                    link = link.substring(BrowserActivity.ARTICLE.length());
                BrowserActivity.openPage(act, link, "");
                adJournal.clear();
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
                    if (offset > 0 || !boolFinish) {
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
            createList();
    }
}
