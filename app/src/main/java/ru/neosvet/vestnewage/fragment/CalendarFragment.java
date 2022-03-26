package ru.neosvet.vestnewage.fragment;

import android.annotation.SuppressLint;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.Data;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import ru.neosvet.ui.NeoFragment;
import ru.neosvet.ui.dialogs.DateDialog;
import ru.neosvet.utils.Const;
import ru.neosvet.utils.Lib;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.activity.BrowserActivity;
import ru.neosvet.vestnewage.helpers.DateHelper;
import ru.neosvet.vestnewage.helpers.ProgressHelper;
import ru.neosvet.vestnewage.list.CalendarAdapter;
import ru.neosvet.vestnewage.list.CalendarItem;
import ru.neosvet.vestnewage.model.LoaderModel;
import ru.neosvet.vestnewage.presenter.CalendarPresenter;
import ru.neosvet.vestnewage.presenter.view.CalendarView;

public class CalendarFragment extends NeoFragment implements CalendarView, DateDialog.Result, CalendarAdapter.Clicker {
    private CalendarAdapter adCalendar;
    private RecyclerView rvCalendar;
    private TextView tvDate;
    private View ivPrev, ivNext, fabRefresh;
    private CalendarPresenter presenter;
    private DateDialog dateDialog;
    private boolean dialog = false;
    final Handler hTimer = new Handler(message -> {
        if (message.what == 0)
            tvDate.setBackgroundResource(R.drawable.card_bg);
        else
            act.startAnimMax();
        return false;
    });

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.calendar_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        act.setTitle(getString(R.string.calendar));
        presenter = new CalendarPresenter(this);
        initViews(view);
        initCalendar();
        restoreState(savedInstanceState);
        if (savedInstanceState == null && presenter.isNeedReload()) {
            startLoad();
        } else if (ProgressHelper.isBusy())
            setStatus(true);
    }

    @Override
    public void onChanged(Data data) {
        if (data.getBoolean(Const.START, false)) {
            act.status.loadText();
            return;
        }
        if (data.getBoolean(Const.DIALOG, false)) {
            act.status.setProgress(data.getInt(Const.PROG, 0));
            return;
        }
        if (data.getBoolean(Const.LIST, false)) {
            presenter.openCalendar(false);
            if (LoaderModel.inProgress) {
                ProgressHelper.setBusy(false);
                setStatus(false);
            }
            return;
        }
        if (data.getBoolean(Const.FINISH, false)) {
            act.updateNew();
            presenter.openCalendar(false);
            String error = data.getString(Const.ERROR);
            if (error != null) {
                act.status.setError(error);
                return;
            }
            setStatus(false);
            act.status.setLoad(false);
            ProgressHelper.setBusy(false);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(Const.DIALOG, dialog);
        if (dialog)
            dateDialog.dismiss();
        outState.putInt(Const.CURRENT_DATE, presenter.getDate().getTimeInDays());
        super.onSaveInstanceState(outState);
    }

    private void restoreState(Bundle state) {
        if (state == null) {
            presenter.createCalendar(0);
        } else {
            DateHelper date = DateHelper.putDays(state.getInt(Const.CURRENT_DATE));
            presenter.changeDate(date);
            dialog = state.getBoolean(Const.DIALOG);
            if (dialog)
                showDatePicker();
        }
    }

    private void initViews(View container) {
        tvDate = container.findViewById(R.id.tvDate);
        ivPrev = container.findViewById(R.id.ivPrev);
        ivNext = container.findViewById(R.id.ivNext);
        rvCalendar = container.findViewById(R.id.rvCalendar);
        fabRefresh = container.findViewById(R.id.fabRefresh);
        container.findViewById(R.id.bProm).setOnClickListener(v -> openLink("Posyl-na-Edinenie.html"));
        fabRefresh.setOnClickListener(view -> startLoad());
        act.status.setClick(view -> onStatusClick(false));
        act.fab = fabRefresh;
    }

    @Override
    public void onStatusClick(boolean reset) {
        ProgressHelper.cancelled();
        ProgressHelper.setBusy(false);
        setStatus(false);
        if (!act.status.isStop()) {
            act.status.setLoad(false);
            return;
        }
        if (reset) {
            act.status.setError(null);
            return;
        }
        if (!act.status.onClick() && act.status.isTime())
            startLoad();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initCalendar() {
        GridLayoutManager layoutManager = new GridLayoutManager(requireContext(), 7);
        adCalendar = new CalendarAdapter(this);
        rvCalendar.setLayoutManager(layoutManager);
        rvCalendar.setAdapter(adCalendar);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            rvCalendar.setOnTouchListener((v, event) -> {
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
            });
        ivPrev.setOnClickListener(view -> openMonth(-1));
        ivNext.setOnClickListener(view -> openMonth(1));
        tvDate.setOnClickListener(view -> {
            if (act.checkBusy()) return;
            showDatePicker();
        });
    }

    private void openLink(String link) {
        BrowserActivity.openReader(link, null);
    }

    private void openMonth(int offset) {
        if (act.checkBusy()) return;
        tvDate.setBackgroundResource(R.drawable.selected);
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                hTimer.sendEmptyMessage(0);
            }
        }, 300);
        presenter.createCalendar(offset);
    }

    @Override
    public void startLoad() {
        presenter.startLoad();
    }

    @Override
    public void setStatus(boolean load) {
        if (load)
            fabRefresh.setVisibility(View.GONE);
        else
            fabRefresh.setVisibility(View.VISIBLE);
    }

    private void showDatePicker() {
        dialog = true;
        dateDialog = new DateDialog(act, presenter.getDate());
        dateDialog.setResult(CalendarFragment.this);
        dateDialog.show();
    }

    @Override
    public void putDate(@Nullable DateHelper date) {
        dialog = false;
        if (date == null) // cancel
            return;
        presenter.changeDate(date);
    }

    public int getCurrentYear() {
        return presenter.getDate().getYear();
    }

    @Override
    public void showLoading() {
        setStatus(true);
        act.status.setLoad(true);
        act.status.startText();
    }

    @Override
    public void onClick(View view, CalendarItem item) {
        if (act.checkBusy()) return;
        switch (item.getCount()) {
            case 1:
                openLink(item.getLink(0));
            case 0:
                return;
        }
        PopupMenu pMenu = new PopupMenu(act, view);
        for (int i = 0; i < item.getCount(); i++) {
            pMenu.getMenu().add(item.getTitle(i));
        }
        pMenu.setOnMenuItemClickListener(menuItem -> {
            String title = menuItem.getTitle().toString();
            for (int i = 0; i < item.getCount(); i++) {
                if (item.getTitle(i).equals(title)) {
                    openLink(item.getLink(i));
                    break;
                }
            }
            return true;
        });
        pMenu.show();
    }

    @Override
    public void updateData(@NonNull String date, boolean prev, boolean next) {
        tvDate.setText(date);
        ivPrev.setEnabled(prev);
        ivNext.setEnabled(next);
    }

    @Override
    public void updateCalendar(ArrayList<CalendarItem> data) {
        adCalendar.setItems(data);
    }

    @Override
    public void checkTime(int sec, boolean isCurMonth) {
        if (isCurMonth)
            setStatus(act.status.checkTime(sec));
        else
            act.status.checkTime(sec);
    }
}
