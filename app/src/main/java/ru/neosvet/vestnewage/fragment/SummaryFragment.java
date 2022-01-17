package ru.neosvet.vestnewage.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.work.Data;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

import ru.neosvet.utils.BackFragment;
import ru.neosvet.utils.Const;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.activity.BrowserActivity;
import ru.neosvet.vestnewage.activity.MainActivity;
import ru.neosvet.vestnewage.helpers.DateHelper;
import ru.neosvet.vestnewage.helpers.ProgressHelper;
import ru.neosvet.vestnewage.list.ListAdapter;
import ru.neosvet.vestnewage.list.ListItem;
import ru.neosvet.vestnewage.model.LoaderModel;
import ru.neosvet.vestnewage.model.SummaryModel;

public class SummaryFragment extends BackFragment implements Observer<Data> {
    private ListView lvSummary;
    private ListAdapter adSummary;
    private MainActivity act;
    private View fabRefresh;
    private SummaryModel model;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.summary_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        act = (MainActivity) getActivity();
        act.setTitle(getResources().getString(R.string.rss));
        initViews(view);
        setViews();
        model = new ViewModelProvider(this).get(SummaryModel.class);
        restoreState(savedInstanceState);
        if (savedInstanceState == null) {
            File f = new File(act.getFilesDir() + Const.RSS);
            if (!f.exists() || System.currentTimeMillis()
                    - f.lastModified() > DateHelper.HOUR_IN_MILLS)
                startLoad();
        }
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
            initLoad();
    }

    private void initLoad() {
        fabRefresh.setVisibility(View.GONE);
        act.status.setLoad(true);
        ProgressHelper.addObserver(act, this);
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
        if (state != null)
            act.setCurFragment(this);
        File f = new File(act.getFilesDir() + Const.RSS);
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

    private void setViews() {
        fabRefresh.setOnClickListener(view -> startLoad());
        act.status.setClick(view -> {
            if (!act.status.isStop()) {
                act.status.setLoad(false);
                ProgressHelper.cancelled();
                fabRefresh.setVisibility(View.VISIBLE);
                ProgressHelper.setBusy(false);
                return;
            }
            if (act.status.onClick())
                fabRefresh.setVisibility(View.VISIBLE);
            else if (act.status.isTime())
                startLoad();
        });
        lvSummary.setOnItemClickListener((adapterView, view, pos, l) -> {
            if (act.checkBusy()) return;
            BrowserActivity.openReader(act, adSummary.getItem(pos).getLink(), null);
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

    private void openList(boolean loadIfNeed) {
        try {
            adSummary.clear();
            DateHelper dateNow = DateHelper.initNow(act);
            BufferedReader br = new BufferedReader(new FileReader(act.getFilesDir() + Const.RSS));
            String title, des, time, link;
            int i = 0;
            while ((title = br.readLine()) != null) {
                link = br.readLine();
                des = br.readLine();
                time = br.readLine();
                adSummary.addItem(new ListItem(title, link));
                adSummary.getItem(i).setDes(
                        dateNow.getDiffDate(Long.parseLong(time))
                                + getResources().getString(R.string.back)
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
        initLoad();
        act.status.startText();
        model.startLoad();
    }

    private void finishLoad(String error) {
        ProgressHelper.removeObservers(act);
        if (error != null) {
            act.status.setError(error);
            return;
        }
        act.updateNew();
        act.status.setLoad(false);
        fabRefresh.setVisibility(View.VISIBLE);
    }
}
