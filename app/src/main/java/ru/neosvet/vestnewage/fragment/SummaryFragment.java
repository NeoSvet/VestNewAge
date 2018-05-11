package ru.neosvet.vestnewage.fragment;

import android.app.Fragment;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.ListView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

import ru.neosvet.ui.CustomDialog;
import ru.neosvet.ui.ListAdapter;
import ru.neosvet.ui.ListItem;
import ru.neosvet.utils.Const;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.activity.BrowserActivity;
import ru.neosvet.vestnewage.activity.MainActivity;
import ru.neosvet.vestnewage.task.SummaryTask;

public class SummaryFragment extends Fragment {
    public static final String RSS = "/rss";

    private ListView lvSummary;
    private ListAdapter adSummary;
    private MainActivity act;
    private Animation anMin, anMax;
    private View container;
    private View fabRefresh;
    private SummaryTask task = null;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        this.container = inflater.inflate(R.layout.summary_fragment, container, false);
        act = (MainActivity) getActivity();
        act.setTitle(getResources().getString(R.string.rss));
        initViews();
        setViews();
        restoreActivityState(savedInstanceState);
        return this.container;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putSerializable(Const.TASK, task);
        super.onSaveInstanceState(outState);
    }

    private void restoreActivityState(Bundle state) {
        if (state != null) {
            task = (SummaryTask) state.getSerializable(Const.TASK);
            if (task != null) {
                if (task.getStatus() == AsyncTask.Status.RUNNING) {
                    act.status.setLoad(true);
                    task.setFrm(this);
                } else task = null;
            }
        }
        File f = new File(act.getFilesDir() + RSS);
        if (f.exists()) {
            if (act.status.checkTime(f.lastModified()))
                fabRefresh.setVisibility(View.GONE);
            else
                fabRefresh.setVisibility(View.VISIBLE);
            createList(true);
        } else {
            startLoad(false);
        }
    }

    private void initViews() {
        fabRefresh = container.findViewById(R.id.fabRefresh);
        lvSummary = (ListView) container.findViewById(R.id.lvSummary);
        adSummary = new ListAdapter(act);
        lvSummary.setAdapter(adSummary);
        anMin = AnimationUtils.loadAnimation(act, R.anim.minimize);
        anMin.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                fabRefresh.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
        anMax = AnimationUtils.loadAnimation(act, R.anim.maximize);
    }

    private void setViews() {
        fabRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startRefresh();
            }
        });
        fabRefresh.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                showRefreshAlert();
                return false;
            }
        });
        act.status.setClick(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (act.status.onClick())
                    fabRefresh.setVisibility(View.VISIBLE);
                else if (act.status.isTime())
                    startRefresh();
            }
        });
        lvSummary.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int pos, long l) {
                String link = adSummary.getItem(pos).getLink();
                BrowserActivity.openReader(act, link, null);
            }
        });
        lvSummary.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                    if (!act.status.startMin())
                        fabRefresh.startAnimation(anMin);
                } else if (motionEvent.getAction() == MotionEvent.ACTION_UP
                        || motionEvent.getAction() == MotionEvent.ACTION_CANCEL) {
                    if (!act.status.startMax()) {
                        fabRefresh.setVisibility(View.VISIBLE);
                        fabRefresh.startAnimation(anMax);
                    }
                }
                return false;
            }
        });
    }

    private void startRefresh() {
        int value = act.getRefMode(MainActivity.SUMMARY_REFMODE);
        if (value == Const.NULL)
            showRefreshAlert();
        else
            startLoad(value == Const.TRUE);
    }

    private void showRefreshAlert() {
        final CustomDialog dialog = new CustomDialog(act);
        dialog.setTitle(getResources().getString(R.string.renewal));
        dialog.setMessage(getResources().getString(R.string.refresh_alert));
        dialog.setLeftButton(getResources().getString(R.string.no), new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                act.setRefMode(MainActivity.SUMMARY_REFMODE, Const.FALSE);
                startLoad(false);
                dialog.dismiss();
            }
        });
        dialog.setRightButton(getResources().getString(R.string.yes), new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                act.setRefMode(MainActivity.SUMMARY_REFMODE, Const.TRUE);
                startLoad(true);
                dialog.dismiss();
            }
        });
        dialog.show(null);
    }

    private void createList(boolean boolLoad) {
        try {
            adSummary.clear();
            BufferedReader br = new BufferedReader(new FileReader(act.getFilesDir() + RSS));
            String t, p;
            int i = 0;
            long now = System.currentTimeMillis();
            while ((t = br.readLine()) != null) {
                adSummary.addItem(new ListItem(t, br.readLine()));
                t = br.readLine();
                p = br.readLine();
                adSummary.getItem(i).setDes(
                        act.lib.getDiffDate(now, Long.parseLong(p))
                                + getResources().getString(R.string.back)
                                + Const.N + t);
                i++;
            }
            br.close();
            adSummary.notifyDataSetChanged();
            lvSummary.smoothScrollToPosition(0);
        } catch (Exception e) {
            e.printStackTrace();
            if (boolLoad)
                startLoad(false);
        }
    }

    private void startLoad(boolean load_new) {
        if (load_new) {
            act.startLoadIt(R.id.nav_rss);
            return;
        }
        act.status.setCrash(false);
        fabRefresh.setVisibility(View.GONE);
        task = new SummaryTask(this);
        task.execute();
        act.status.setLoad(true);
    }

    public void finishLoad(Boolean suc) {
        task = null;
        if (suc) {
            fabRefresh.setVisibility(View.VISIBLE);
            act.status.setLoad(false);
            createList(false);
        } else {
            act.status.setCrash(true);
        }
    }
}
