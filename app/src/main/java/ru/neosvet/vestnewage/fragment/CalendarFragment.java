package ru.neosvet.vestnewage.fragment;

import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

import androidx.work.Data;

import java.util.Timer;
import java.util.TimerTask;

import ru.neosvet.ui.dialogs.DateDialog;
import ru.neosvet.utils.BackFragment;
import ru.neosvet.utils.Const;
import ru.neosvet.utils.DataBase;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.activity.BrowserActivity;
import ru.neosvet.vestnewage.activity.MainActivity;
import ru.neosvet.vestnewage.helpers.DateHelper;
import ru.neosvet.vestnewage.helpers.ProgressHelper;
import ru.neosvet.vestnewage.list.CalendarAdapter;
import ru.neosvet.vestnewage.list.CalendarItem;
import ru.neosvet.vestnewage.model.CalendarModel;
import ru.neosvet.vestnewage.model.LoaderModel;

public class CalendarFragment extends BackFragment implements DateDialog.Result, Observer<Data>, View.OnTouchListener {
    private int today_m, today_y;
    private CalendarAdapter adCalendar;
    private RecyclerView rvCalendar;
    private DateHelper dCurrent;
    private TextView tvDate;
    private View container, ivPrev, ivNext, fabRefresh;
    private CalendarModel model;
    private MainActivity act;
    private DateDialog dateDialog;
    private boolean dialog = false;
    final Handler hTimer = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message message) {
            if (message.what == 0)
                tvDate.setBackgroundDrawable(getResources().getDrawable(R.drawable.card_bg));
            else
                act.startAnimMax();
            return false;
        }
    });

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        this.container = inflater.inflate(R.layout.calendar_fragment, container, false);
        act = (MainActivity) getActivity();
        act.setTitle(getResources().getString(R.string.calendar));
        initViews();
        initCalendar();
        initModel();
        restoreState(savedInstanceState);
        return this.container;
    }

    @Override
    public void onPause() {
        super.onPause();
        if (ProgressHelper.isBusy())
            ProgressHelper.removeObservers(act);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (ProgressHelper.isBusy())
            ProgressHelper.addObserver(act, this);
    }

    private void initModel() {
        model = ViewModelProviders.of(act).get(CalendarModel.class);
        if (ProgressHelper.isBusy())
            setStatus(true);
    }

    @Override
    public void onChanged(@Nullable Data data) {
        if (!ProgressHelper.isBusy())
            return;
        if (data.getBoolean(Const.START, false)) {
            act.status.loadText();
            return;
        }
        if (data.getBoolean(Const.DIALOG, false)) {
            act.status.setText(data.getString(Const.MSG));
            return;
        }
        if (data.getBoolean(Const.LIST, false)) {
            openCalendar(false);
            if (LoaderModel.inProgress) {
                ProgressHelper.setBusy(false);
                setStatus(false);
            }
            return;
        }
        if (data.getBoolean(Const.FINISH, false)) {
            act.updateNew();
            openCalendar(false);
            String error = data.getString(Const.ERROR);
            ProgressHelper.removeObservers(act);
            if (error != null) {
                act.status.setError(error);
                return;
            }
            fabRefresh.setVisibility(View.VISIBLE);
            act.status.setLoad(false);
            ProgressHelper.setBusy(false);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(Const.DIALOG, dialog);
        if (dialog)
            dateDialog.dismiss();
        outState.putInt(Const.CURRENT_DATE, dCurrent.getTimeInDays());
        super.onSaveInstanceState(outState);
    }

    private void restoreState(Bundle state) {
        if (state == null) {
            dCurrent = DateHelper.initToday(act);
            dCurrent.setDay(1);
        } else {
            act.setCurFragment(this);
            dCurrent = DateHelper.putDays(act, state.getInt(Const.CURRENT_DATE));
            dialog = state.getBoolean(Const.DIALOG);
            if (dialog)
                showDatePicker();
        }
        createCalendar(0);
    }

    private void initViews() {
        DateHelper d = DateHelper.initToday(act);
        today_m = d.getMonth();
        today_y = d.getYear();
        fabRefresh = container.findViewById(R.id.fabRefresh);
        container.findViewById(R.id.bProm).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openLink("Posyl-na-Edinenie.html");
            }
        });
        fabRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startLoad();
            }
        });
        act.status.setClick(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!act.status.isStop()) {
                    act.status.setLoad(false);
                    ProgressHelper.cancelled();
                    fabRefresh.setVisibility(View.VISIBLE);
                    ProgressHelper.setBusy(false);
                    return;
                }
                if (act.status.onClick()) {
                    fabRefresh.setVisibility(View.VISIBLE);
                } else if (act.status.isTime())
                    startLoad();
            }
        });
        act.fab = fabRefresh;
    }

    private void initCalendar() {
        tvDate = (TextView) container.findViewById(R.id.tvDate);
        ivPrev = container.findViewById(R.id.ivPrev);
        ivNext = container.findViewById(R.id.ivNext);
        rvCalendar = (RecyclerView) container.findViewById(R.id.rvCalendar);
        GridLayoutManager layoutManager = new GridLayoutManager(act, 7);
        adCalendar = new CalendarAdapter(this);
        rvCalendar.setLayoutManager(layoutManager);
        rvCalendar.setAdapter(adCalendar);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            rvCalendar.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (!act.isInMultiWindowMode())
                        return false;
                    if (event.getAction() == MotionEvent.ACTION_MOVE) {
                        act.startAnimMin();
                    }
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        new Timer().schedule(new TimerTask() {
                            @Override
                            public void run() {
                                hTimer.sendEmptyMessage(1);
                            }
                        }, 1000);
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
        tvDate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (act.checkBusy()) return;
                showDatePicker();
            }
        });
    }

    private void openLink(String link) {
        BrowserActivity.openReader(act, link, null);
    }

    private void openMonth(int offset) {
        if (act.checkBusy()) return;
        tvDate.setBackgroundDrawable(getResources().getDrawable(R.drawable.selected));
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                hTimer.sendEmptyMessage(0);
            }
        }, 300);
        createCalendar(offset);
    }

    private void createCalendar(int offsetMonth) {
        DateHelper d = DateHelper.putDays(act, dCurrent.getTimeInDays());
        if (offsetMonth != 0) {
            d.changeMonth(offsetMonth);
            dCurrent.changeMonth(offsetMonth);
        }
        tvDate.setText(d.getMonthString() + Const.N + d.getYear());
        adCalendar.clear();
        for (int i = -1; i > -7; i--) //add label monday-saturday
            adCalendar.addItem(new CalendarItem(act, i, R.color.light_gray));
        adCalendar.addItem(new CalendarItem(act, 0, R.color.light_gray)); //sunday
        final int cur_month = d.getMonth();
        if (d.getDayWeek() != DateHelper.MONDAY) {
            if (d.getDayWeek() == DateHelper.SUNDAY)
                d.changeDay(-6);
            else
                d.changeDay(1 - d.getDayWeek());
            while (d.getMonth() != cur_month) {
                adCalendar.addItem(new CalendarItem(act, d.getDay(), android.R.color.darker_gray));
                d.changeDay(1);
            }
        }
        DateHelper today = DateHelper.initToday(act);
        int n_today = 0;
        if (today.getMonth() == cur_month)
            n_today = today.getDay();
        while (d.getMonth() == cur_month) {
            adCalendar.addItem(new CalendarItem(act, d.getDay(), android.R.color.white));
            if (d.getDay() == n_today)
                adCalendar.getItem(adCalendar.getItemCount() - 1).setBold();
            d.changeDay(1);
        }
        while (d.getDayWeek() != DateHelper.MONDAY) {
            adCalendar.addItem(new CalendarItem(act, d.getDay(), android.R.color.darker_gray));
            d.changeDay(1);
        }
        openCalendar(true);
        if (dCurrent.getYear() == 2016)
            ivPrev.setEnabled(dCurrent.getMonth() != 0);
        if (dCurrent.getYear() == today_y)
            ivNext.setEnabled(dCurrent.getMonth() != today_m);
        else
            ivNext.setEnabled(true);
    }

    private boolean isCurMonth() {
        return dCurrent.getMonth() == today_m && dCurrent.getYear() == today_y;
    }

    private void openCalendar(boolean loadIfNeed) {
        try {
            for (int i = 0; i < adCalendar.getItemCount(); i++)
                adCalendar.getItem(i).clear(false);
            DataBase dataBase = new DataBase(act, dCurrent.getMY());
            SQLiteDatabase db = dataBase.getWritableDatabase();
            Cursor cursor = db.query(Const.TITLE, null, null, null, null, null, null);
            boolean empty = true;
            if (cursor.moveToFirst()) {
                if (loadIfNeed) {
                    checkTime((int) (cursor.getLong(cursor.getColumnIndex(
                            Const.TIME)) / DateHelper.SEC_IN_MILLS));
                }

                int iTitle = cursor.getColumnIndex(Const.TITLE);
                int iLink = cursor.getColumnIndex(Const.LINK);
                int i;
                String title, link;
                while (cursor.moveToNext()) {
                    title = cursor.getString(iTitle);
                    link = cursor.getString(iLink);
                    if (link.contains("@")) {
                        i = Integer.parseInt(link.substring(0, 2));
                        link = link.substring(9);
                    } else {
                        i = link.lastIndexOf("/") + 1;
                        i = Integer.parseInt(link.substring(i, i + 2));
                    }
                    i = adCalendar.indexOf(i);
                    adCalendar.getItem(i).addLink(link);
                    if (dataBase.existsPage(link)) {
                        title = dataBase.getPageTitle(title, link);
                        adCalendar.getItem(i).addTitle(title.substring(title.indexOf(" ") + 1));
                    } else {
                        title = getTitleByLink(link);
                        adCalendar.getItem(i).addTitle(title);
                    }
                    empty = false;
                }
            }
            cursor.close();
            db.close();
            dataBase.close();
            adCalendar.notifyDataSetChanged();

            if (empty && loadIfNeed)
                startLoad();
        } catch (Exception e) {
            e.printStackTrace();
            if (loadIfNeed)
                startLoad();
        }
    }

    private String getTitleByLink(String s) {
        DataBase dataBase = new DataBase(act, "00.00");
        SQLiteDatabase db = dataBase.getWritableDatabase();
        Cursor curTitle = db.query(Const.TITLE, new String[]{Const.TITLE},
                Const.LINK + DataBase.Q, new String[]{s}, null, null, null);
        if (curTitle.moveToFirst())
            s = curTitle.getString(0);
        curTitle.close();
        db.close();
        dataBase.close();
        return s;
    }

    private void checkTime(int sec) {
        if (isCurMonth()) {
            if (act.status.checkTime(sec))
                fabRefresh.setVisibility(View.GONE);
            else
                fabRefresh.setVisibility(View.VISIBLE);
            return;
        }
        if ((dCurrent.getMonth() == today_m - 1 && dCurrent.getYear() == today_y) ||
                (dCurrent.getMonth() == 11 && dCurrent.getYear() == today_y - 1)) {
            DateHelper d = DateHelper.putSeconds(act, sec);
            if (d.getMonth() != today_m)
                act.status.checkTime(sec);
        }
    }

    private void startLoad() {
        if (ProgressHelper.isBusy())
            return;
        setStatus(true);
        act.status.startText();
        model.startLoad(dCurrent.getMonth(), dCurrent.getYear(), isCurMonth());
    }

    private void setStatus(boolean load) {
        act.status.setLoad(load);
        if (load) {
            fabRefresh.setVisibility(View.GONE);
            ProgressHelper.addObserver(act, this);
        } else {
            fabRefresh.setVisibility(View.VISIBLE);
            ProgressHelper.removeObservers(act);
        }
    }

    private void showDatePicker() {
        dialog = true;
        dateDialog = new DateDialog(act, dCurrent);
        dateDialog.setResult(CalendarFragment.this);
        dateDialog.show();
    }

    @Override
    public void putDate(@Nullable DateHelper date) {
        dialog = false;
        if (date == null) // cancel
            return;
        dCurrent = date;
        createCalendar(0);
    }

    private void blinkDay(int d) {
        boolean begin = false;
        for (int i = 6; i < adCalendar.getItemCount(); i++) {
            if (adCalendar.getItem(i).getNum() == 1)
                begin = true;
            if (begin) {
                if (adCalendar.getItem(i).getNum() == d) {
                    View v = rvCalendar.getLayoutManager().findViewByPosition(i);
                    if (v != null) {
                        v.clearAnimation();
                        v.startAnimation(AnimationUtils.loadAnimation(act, R.anim.blink));
                    }
                    break;
                }
            }
        }
    }

    public int getCurrentYear() {
        return dCurrent.getYear();
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) { //click calendar item
        if (event.getAction() != MotionEvent.ACTION_UP)
            return false;
        if (act.checkBusy()) return false;
        final int pos = (int) v.getTag();
        int k = adCalendar.getItem(pos).getCount();
        if (k == 0)
            return false;
        if (k == 1) {
            openLink(adCalendar.getItem(pos).getLink(0));
            return false;
        }
        //k > 1
        PopupMenu pMenu = new PopupMenu(act, rvCalendar.getChildAt(pos));
        for (int i = 0; i < adCalendar.getItem(pos).getCount(); i++) {
            pMenu.getMenu().add(adCalendar.getItem(pos).getTitle(i));
        }
        pMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                String title = item.getTitle().toString();
                for (int i = 0; i < adCalendar.getItem(pos).getCount(); i++) {
                    if (adCalendar.getItem(pos).getTitle(i).equals(title)) {
                        openLink(adCalendar.getItem(pos).getLink(i));
                        break;
                    }
                }
                return true;
            }
        });
        pMenu.show();
        return false;
    }
}
