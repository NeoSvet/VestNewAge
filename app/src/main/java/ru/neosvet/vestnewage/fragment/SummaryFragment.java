package ru.neosvet.vestnewage.fragment;

import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.ListView;

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
    private View container;
    private View fabRefresh;
    private SummaryModel model;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        this.container = inflater.inflate(R.layout.summary_fragment, container, false);
        act = (MainActivity) getActivity();
        act.setTitle(getResources().getString(R.string.rss));
        initViews();
        setViews();
        initModel();
        restoreState(savedInstanceState);
        return this.container;
    }

    @Override
    public void onPause() {
        super.onPause();
        model.removeObservers(act);
    }

    @Override
    public void onResume() {
        super.onResume();
        model.addObserver(act, this);
    }

    @Override
    public boolean onBackPressed() {
        if (model.inProgress) {
            model.cancel = true;
            return false;
        }
        return true;
    }

    private void initModel() {
        model = ViewModelProviders.of(act).get(SummaryModel.class);
        if (model.inProgress)
            act.status.setLoad(true);
    }

    @Override
    public void onChanged(@Nullable Data data) {
        if (!model.inProgress)
            return;
        if (data.getBoolean(Const.LIST, false)) {
            openList(false);
            return;
        }
        if (data.getBoolean(Const.FINISH, false)) {
            finishLoad(data.getString(Const.ERROR));
            return;
        }
        if (data.getInt(Const.DIALOG, 0) != LoaderModel.DIALOG_MSG)
            return;
        String link = data.getString(Const.MSG);
        for (int i = 0; i < adSummary.getCount(); i++) {
            if (adSummary.getItem(i).getLink().equals(link)) {
                lvSummary.smoothScrollToPosition(i);
                View item = lvSummary.getChildAt(i);
                if (item == null)
                    break;
                item.startAnimation(AnimationUtils.loadAnimation(act, R.anim.blink));
                break;
            }
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

    private void initViews() {
        fabRefresh = container.findViewById(R.id.fabRefresh);
        lvSummary = (ListView) container.findViewById(R.id.lvList);
        adSummary = new ListAdapter(act);
        lvSummary.setAdapter(adSummary);
        act.fab = fabRefresh;
    }

    private void setViews() {
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
                        model.cancel = true;
                    else
                        act.status.setLoad(false);
                } else if (act.status.onClick())
                    fabRefresh.setVisibility(View.VISIBLE);
                else if (act.status.isTime())
                    startLoad();
            }
        });
        lvSummary.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int pos, long l) {
                BrowserActivity.openReader(act, adSummary.getItem(pos).getLink(), null);
            }
        });
        lvSummary.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                    if (!act.status.startMin())
                        act.startAnimMin();
                } else if (motionEvent.getAction() == MotionEvent.ACTION_UP
                        || motionEvent.getAction() == MotionEvent.ACTION_CANCEL) {
                    if (!act.status.startMax())
                        act.startAnimMax();
                }
                return false;
            }
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

    private void startLoad() {
        if (ProgressHelper.isBusy())
            return;
        act.status.setError(null);
        fabRefresh.setVisibility(View.GONE);
        act.status.setLoad(true);
        model.startLoad();
    }

    private void finishLoad(String error) {
        model.finish();
        act.updateNew();
        if (error != null) {
            act.status.setError(error);
            return;
        }
        fabRefresh.setVisibility(View.VISIBLE);
        act.status.setLoad(false);
    }
}
