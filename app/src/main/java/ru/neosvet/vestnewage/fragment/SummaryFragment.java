package ru.neosvet.vestnewage.fragment;

import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.work.Data;
import androidx.work.WorkInfo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.List;

import ru.neosvet.ui.MultiWindowSupport;
import ru.neosvet.utils.BackFragment;
import ru.neosvet.utils.Const;
import ru.neosvet.utils.DataBase;
import ru.neosvet.utils.Lib;
import ru.neosvet.utils.ProgressModel;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.activity.BrowserActivity;
import ru.neosvet.vestnewage.activity.MainActivity;
import ru.neosvet.vestnewage.helpers.DateHelper;
import ru.neosvet.vestnewage.list.ListAdapter;
import ru.neosvet.vestnewage.list.ListItem;
import ru.neosvet.vestnewage.model.SummaryModel;

public class SummaryFragment extends BackFragment {
    private ListView lvSummary;
    private ListAdapter adSummary;
    private MainActivity act;
    private Animation anMin, anMax;
    private View container;
    private View fabRefresh;
    private TextView tvNew;
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
        model = ViewModelProviders.of(act).get(SummaryModel.class);
        model.getProgress().observe(act, new Observer<Data>() {
            @Override
            public void onChanged(@Nullable Data data) {
                String link = data.getString(Const.LINK);
                for (int i = 0; i < adSummary.getCount(); i++) {
                    if (adSummary.getItem(i).equals(link)) {
                        View item = (View) lvSummary.getItemAtPosition(i);
                        item.startAnimation(AnimationUtils.loadAnimation(act, R.anim.blink));
                    }
                }
            }
        });
        model.getState().observe(act, new Observer<List<WorkInfo>>() {
            @Override
            public void onChanged(@Nullable List<WorkInfo> workInfos) {
                String tag;
                for (int i = 0; i < workInfos.size(); i++) {
                    tag = ProgressModel.getFirstTag(workInfos.get(i).getTags());
                    if (tag.equals(SummaryModel.TAG) && workInfos.get(i).getState().isFinished())
                        finishLoad(workInfos.get(i).getState().equals(WorkInfo.State.SUCCEEDED));
                    if (workInfos.get(i).getState().equals(WorkInfo.State.FAILED))
                        Lib.showToast(act, workInfos.get(i).getOutputData().getString(Const.ERROR));
                }
            }
        });
        if (model.inProgress)
            act.status.setLoad(true);
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
        } else if (!model.inProgress)
            startLoad();
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
        tvNew = (TextView) container.findViewById(R.id.tvNew);
        fabRefresh = container.findViewById(R.id.fabRefresh);
        lvSummary = (ListView) container.findViewById(R.id.lvList);
        adSummary = new ListAdapter(act);
        lvSummary.setAdapter(adSummary);
        anMin = AnimationUtils.loadAnimation(act, R.anim.minimize);
        anMin.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                tvNew.setVisibility(View.GONE);
                fabRefresh.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
        anMax = AnimationUtils.loadAnimation(act, R.anim.maximize);
    }

    private void setViews() {
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
                    if (!act.status.startMin()) {
                        fabRefresh.startAnimation(anMin);
                        if (act.k_new > 0)
                            tvNew.startAnimation(anMin);
                    }
                } else if (motionEvent.getAction() == MotionEvent.ACTION_UP
                        || motionEvent.getAction() == MotionEvent.ACTION_CANCEL) {
                    if (!act.status.startMax()) {
                        fabRefresh.setVisibility(View.VISIBLE);
                        fabRefresh.startAnimation(anMax);
                        if (act.k_new > 0) {
                            tvNew.setVisibility(View.VISIBLE);
                            tvNew.startAnimation(anMax);
                        }
                    }
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
            String title, des, time, link, name;
            int i = 0;
            DataBase dataBase = null;
            while ((title = br.readLine()) != null) {
                link = br.readLine();
                des = br.readLine();
                time = br.readLine();
                if (!link.contains(":")) {
                    name = DataBase.getDatePage(link);
                    if (dataBase == null || !dataBase.getName().equals(name)) {
                        if (dataBase != null)
                            dataBase.close();
                        dataBase = new DataBase(act, name);
                    }
                    if (!dataBase.existsPage(link))
                        continue;
                }
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
        act.status.setCrash(false);
        fabRefresh.setVisibility(View.GONE);
        act.status.setLoad(true);
        model.startLoad();
    }

    private void finishLoad(Boolean suc) {
        model.finish();
        if (suc) {
            fabRefresh.setVisibility(View.VISIBLE);
            act.status.setLoad(false);
        } else {
            act.status.setCrash(true);
        }
        openList(false);
    }
}
