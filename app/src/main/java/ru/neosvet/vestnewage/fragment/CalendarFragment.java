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
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

import androidx.work.Data;
import androidx.work.WorkInfo;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import ru.neosvet.ui.MultiWindowSupport;
import ru.neosvet.ui.RecyclerItemClickListener;
import ru.neosvet.ui.dialogs.DateDialog;
import ru.neosvet.utils.BackFragment;
import ru.neosvet.utils.Const;
import ru.neosvet.utils.DataBase;
import ru.neosvet.utils.Lib;
import ru.neosvet.utils.ProgressModel;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.activity.BrowserActivity;
import ru.neosvet.vestnewage.activity.MainActivity;
import ru.neosvet.vestnewage.helpers.DateHelper;
import ru.neosvet.vestnewage.list.CalendarAdapter;
import ru.neosvet.vestnewage.list.CalendarItem;
import ru.neosvet.vestnewage.model.CalendarModel;

public class CalendarFragment extends BackFragment implements DateDialog.Result {
    private int today_m, today_y;
    private CalendarAdapter adCalendar;
    private RecyclerView rvCalendar;
    private DateHelper dCurrent;
    private TextView tvDate, tvNew;
    private View container, ivPrev, ivNext, fabRefresh;
    private CalendarModel model;
    private MainActivity act;
    private DateDialog dateDialog;
    private boolean dialog = false;
    final Handler hTimer = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message message) {
            tvDate.setBackgroundDrawable(getResources().getDrawable(R.drawable.card_bg));
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (act.isInMultiWindowMode())
                MultiWindowSupport.resizeFloatTextView(tvNew, true);
        }
        return this.container;
    }

    @Override
    public void onPause() {
        super.onPause();
        model.removeObserves(act);
    }

    @Override
    public boolean onBackPressed() {
        if (model.inProgress) {
            model.finish();
            return false;
        }
        return true;
    }

    private void initModel() {
        model = ViewModelProviders.of(act).get(CalendarModel.class);
        model.getProgress().observe(act, new Observer<Data>() {
            @Override
            public void onChanged(@Nullable Data data) {
                String s = data.getString(Const.LINK);
                int i = s.lastIndexOf("/" + 1);
                blinkDay(Integer.parseInt(s.substring(i, i + 2)));
            }
        });
        model.getState().observe(act, new Observer<List<WorkInfo>>() {
            @Override
            public void onChanged(@Nullable List<WorkInfo> workInfos) {
                String tag;
                for (int i = 0; i < workInfos.size(); i++) {
                    tag = ProgressModel.getFirstTag(workInfos.get(i).getTags());
                    if (tag.equals(CalendarModel.TAG) && workInfos.get(i).getState().isFinished())
                        finishLoad(workInfos.get(i).getState().equals(WorkInfo.State.SUCCEEDED));
                    if (workInfos.get(i).getState().equals(WorkInfo.State.FAILED))
                        Lib.showToast(act, workInfos.get(i).getOutputData().getString(Const.ERROR));
                }
            }
        });
        if (model.inProgress)
            setStatus(true);
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

    @Override
    public void onResume() {
        super.onResume();
        act.updateNew();
        if (act.k_new == 0)
            tvNew.setVisibility(View.GONE);
        else {
            tvNew.setVisibility(View.VISIBLE);
            tvNew.setText(String.valueOf(act.k_new));
            tvNew.startAnimation(AnimationUtils.loadAnimation(act, R.anim.blink));
        }
    }

    private void initViews() {
        DateHelper d = DateHelper.initToday(act);
        today_m = d.getMonth();
        today_y = d.getYear();
        tvNew = (TextView) container.findViewById(R.id.tvNew);
        fabRefresh = container.findViewById(R.id.fabRefresh);
        container.findViewById(R.id.bProm).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openLink("Posyl-na-Edinenie.html");
            }
        });
        tvNew.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                act.setFragment(R.id.nav_new);
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
                    if (model.inProgress)
                        model.finish();
                    else
                        act.status.setLoad(false);
                } else if (act.status.onClick()) {
                    if (act.k_new > 0)
                        tvNew.setVisibility(View.VISIBLE);
                    fabRefresh.setVisibility(View.VISIBLE);
                } else if (act.status.isTime())
                    startLoad();
            }
        });
    }

    private void clearDays() {
        for (int i = 0; i < adCalendar.getItemCount(); i++) {
            adCalendar.getItem(i).clear(false);
        }
    }

    private void initCalendar() {
        tvDate = (TextView) container.findViewById(R.id.tvDate);
        ivPrev = container.findViewById(R.id.ivPrev);
        ivNext = container.findViewById(R.id.ivNext);
        rvCalendar = (RecyclerView) container.findViewById(R.id.rvCalendar);
        GridLayoutManager layoutManager = new GridLayoutManager(act, 7);
        adCalendar = new CalendarAdapter();
        rvCalendar.setLayoutManager(layoutManager);
        rvCalendar.setAdapter(adCalendar);

        rvCalendar.addOnItemTouchListener(
                new RecyclerItemClickListener(act, new RecyclerItemClickListener.OnItemClickListener() {
                    @Override
                    public void onItemClick(View view, final int pos) {
                        int k = adCalendar.getItem(pos).getCount();
                        if (k == 1) {
                            openLink(adCalendar.getItem(pos).getLink(0));
                        } else if (k > 1) {
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
                        }
                    }
                })
        );

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
                showDatePicker();
            }
        });
    }

    private void openLink(String link) {
        if (model.inProgress)
            model.finish();
        BrowserActivity.openReader(act, link, null);
    }

    private void openMonth(int offset) {
        if (!model.inProgress) {
            tvDate.setBackgroundDrawable(getResources().getDrawable(R.drawable.selected));
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    hTimer.sendEmptyMessage(1);
                }
            }, 300);
            createCalendar(offset);
        }
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
            if (model.inProgress && model.loadList)
                return;

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
                    i = link.lastIndexOf("/") + 1;
                    i = Integer.parseInt(link.substring(i, i + 2));
                    i = adCalendar.indexOf(i);
                    adCalendar.getItem(i).addLink(link);
                    if (title.contains("/"))
                        adCalendar.getItem(i).addTitle(title);
                    else {
                        title = dataBase.getPageTitle(title, link);
                        adCalendar.getItem(i).addTitle(title.substring(title.indexOf(" ") + 1));
                    }
                    empty = false;
                }
            }
            cursor.close();
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

    private void checkTime(int sec) {
        if (isCurMonth()) {
            act.status.checkTime(sec);
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
        setStatus(true);
        model.startLoad(dCurrent.getMonth(), dCurrent.getYear(), isCurMonth());
    }

    private void finishLoad(boolean suc) {
        model.finish();
        if (suc)
            setStatus(false);
        else {
            act.status.setCrash(true);
            clearDays();
            openCalendar(false);
        }
    }

    private void setStatus(boolean load) {
        act.status.setCrash(false);
        act.status.setLoad(load);
        if (load) {
            tvNew.setVisibility(View.GONE);
            fabRefresh.setVisibility(View.GONE);
        } else {
            tvNew.setVisibility(View.VISIBLE);
            fabRefresh.setVisibility(View.VISIBLE);
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

    @Override
    public void onMultiWindowModeChanged(boolean isInMultiWindowMode) {
        super.onMultiWindowModeChanged(isInMultiWindowMode);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            MultiWindowSupport.resizeFloatTextView(tvNew, isInMultiWindowMode);
    }

    private void blinkDay(int d) {
        boolean begin = false;
        for (int i = 6; i < adCalendar.getItemCount(); i++) {
            if (adCalendar.getItem(i).getNum() == 1)
                begin = true;
            if (begin) {
                if (adCalendar.getItem(i).getNum() == d) {
                    updateTitles(i);
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

    private void updateTitles(int item) {
        adCalendar.getItem(item).clear(true);
        int i = 0;
        if (adCalendar.getItem(item).isBold())
            i = 1;
        DataBase dataBase = new DataBase(act, adCalendar.getItem(item).getLink(i));
        SQLiteDatabase db = dataBase.getWritableDatabase();
        Cursor cursor;
        String title, link;
        for (; i < adCalendar.getItem(item).getCount(); i++) {
            link = adCalendar.getItem(item).getLink(i);
            cursor = db.query(Const.TITLE, new String[]{Const.TITLE},
                    Const.LINK + DataBase.Q, new String[]{link},
                    null, null, null);
            if (cursor.moveToFirst()) {
                title = cursor.getString(0);
                if (title.contains("/"))
                    adCalendar.getItem(item).addTitle(title);
                else {
                    title = dataBase.getPageTitle(title, link);
                    adCalendar.getItem(item).addTitle(title.substring(title.indexOf(" ") + 1));
                }
            }
            cursor.close();
        }
        dataBase.close();
    }

    public int getCurrentYear() {
        return dCurrent.getYear();
    }
}
