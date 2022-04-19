package ru.neosvet.vestnewage.fragment;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.work.Data;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

import ru.neosvet.ui.NeoFragment;
import ru.neosvet.utils.Const;
import ru.neosvet.utils.Lib;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.activity.BrowserActivity;
import ru.neosvet.vestnewage.helpers.DateHelper;
import ru.neosvet.vestnewage.helpers.ProgressHelper;
import ru.neosvet.vestnewage.list.ListAdapter;
import ru.neosvet.vestnewage.list.ListItem;
import ru.neosvet.vestnewage.model.LoaderModel;
import ru.neosvet.vestnewage.model.SummaryModel;

public class SummaryFragment extends NeoFragment {
    private ListView lvSummary;
    private ListAdapter adSummary;
    private View fabRefresh;
    private SummaryModel model;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.summary_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        act.setTitle(getString(R.string.rss));
        initViews(view);
        setViews();
        model = new ViewModelProvider(this).get(SummaryModel.class);
        restoreState(savedInstanceState);
        if (savedInstanceState == null) {
            File f = Lib.getFile(Const.RSS);
            if (!f.exists() || System.currentTimeMillis()
                    - f.lastModified() > DateHelper.HOUR_IN_MILLS)
                startLoad();
        }
        if (ProgressHelper.isBusy())
            setStatus(true);
    }

    @Override
    public void setStatus(boolean load) {
        if (load)
            fabRefresh.setVisibility(View.GONE);
        else
            fabRefresh.setVisibility(View.VISIBLE);
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
            openList(false);
            if (LoaderModel.inProgress) {
                ProgressHelper.setBusy(false);
                finishLoad(null);
            }
            return;
        }
        if (data.getBoolean(Const.FINISH, false)) {
            ProgressHelper.setBusy(false);
            finishLoad(data.getString(Const.ERROR));
        }
    }

    private void restoreState(Bundle state) {
        File f = Lib.getFile(Const.RSS);
        if (f.exists()) {
            if (act.status.checkTime(f.lastModified() / DateHelper.SEC_IN_MILLS))
                fabRefresh.setVisibility(View.GONE);
            else
                fabRefresh.setVisibility(View.VISIBLE);
            openList(true);
        } else
            startLoad();
    }

    private void initViews(View container) {
        fabRefresh = container.findViewById(R.id.fabRefresh);
        lvSummary = container.findViewById(R.id.lvList);
        adSummary = new ListAdapter(act);
        lvSummary.setAdapter(adSummary);
        act.fab = fabRefresh;
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setViews() {
        fabRefresh.setOnClickListener(view -> startLoad());
        act.status.setClick(view -> onStatusClick(false));
        lvSummary.setOnItemClickListener((adapterView, view, pos, l) -> {
            if (act.checkBusy()) return;
            BrowserActivity.openReader(adSummary.getItem(pos).getLink(), null);
        });
        lvSummary.setOnTouchListener((view, motionEvent) -> {
            if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                if (!act.status.startMin())
                    act.startAnimMin();
            } else if (motionEvent.getAction() == MotionEvent.ACTION_UP
                    || motionEvent.getAction() == MotionEvent.ACTION_CANCEL) {
                if (!act.status.startMax())
                    act.startAnimMax();
            }
            return false;
        });
    }

    @Override
    public void onStatusClick(boolean reset) {
        ProgressHelper.cancelled();
        setStatus(false);
        ProgressHelper.setBusy(false);
        openList(false);
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

    private void openList(boolean loadIfNeed) {
        try {
            adSummary.clear();
            DateHelper dateNow = DateHelper.initNow();
            BufferedReader br = new BufferedReader(new FileReader(Lib.getFile(Const.RSS)));
            String title, des, time, link;
            int i = 0;
            while ((title = br.readLine()) != null) {
                link = br.readLine();
                des = br.readLine();
                time = br.readLine();
                adSummary.addItem(new ListItem(title, link));
                adSummary.getItem(i).setDes(
                        dateNow.getDiffDate(Long.parseLong(time))
                                + getString(R.string.back)
                                + Const.N + des);
                i++;
            }
            br.close();
            adSummary.notifyDataSetChanged();
            lvSummary.smoothScrollToPosition(0);
        } catch (Exception e) {
            e.printStackTrace();
            if (loadIfNeed)
                startLoad();
        }
    }

    @Override
    public void startLoad() {
        if (ProgressHelper.isBusy())
            return;
        setStatus(true);
        act.status.setLoad(true);
        act.status.startText();
        model.startLoad();
    }

    private void finishLoad(String error) {
        setStatus(false);
        if (error != null) {
            act.status.setError(error);
            return;
        }
        act.updateNew();
        act.status.setLoad(false);
        fabRefresh.setVisibility(View.VISIBLE);
    }
}
