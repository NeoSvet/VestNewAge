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
import java.util.List;

import ru.neosvet.utils.Const;
import ru.neosvet.utils.DataBase;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.activity.BrowserActivity;
import ru.neosvet.vestnewage.activity.MainActivity;
import ru.neosvet.vestnewage.helpers.DateHelper;
import ru.neosvet.vestnewage.helpers.UnreadHelper;
import ru.neosvet.vestnewage.list.ListAdapter;
import ru.neosvet.vestnewage.list.ListItem;
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
    private DateHelper dateNow;

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
            act.setFrSummary(this);
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
            if (act.status.checkTime(f.lastModified() / DateHelper.SEC_IN_MILLS))
                fabRefresh.setVisibility(View.GONE);
            else
                fabRefresh.setVisibility(View.VISIBLE);
            openList(true, false);
        } else {
            startLoad();
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
                startLoad();
            }
        });
        act.status.setClick(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!act.status.isStop()) {
                    if (task != null)
                        task.cancel(false);
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
                String s = adSummary.getItem(pos).getLink();
                BrowserActivity.openReader(act, s, null);
                s = adSummary.getItem(pos).getDes();
                if (s.contains(getResources().getString(R.string.new_item))) {
                    s = s.substring(getResources().getString(R.string.new_item).length() + 1);
                    adSummary.getItem(pos).setDes(s);
                }
                adSummary.notifyDataSetChanged();
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

    public void openList(boolean loadIfNeed, boolean addOnlyExists) {
        try {
            adSummary.clear();
            dateNow = DateHelper.initNow(act);
            BufferedReader br = new BufferedReader(new FileReader(act.getFilesDir() + RSS));
            String title, des, time, link, name;
            int i = 0;
            DataBase dataBase = null;
            UnreadHelper unread = new UnreadHelper(act);
            List<String> links = unread.getList();
            unread.close();
            while ((title = br.readLine()) != null) {
                link = br.readLine();
                des = br.readLine();
                time = br.readLine();
                if (addOnlyExists && !link.contains(":")) {
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
                adSummary.getItem(i).setDes(prepareDes(des, time,
                        links.contains(link.replace(Const.HTML, ""))));
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
        task = new SummaryTask(this);
        task.execute();
        act.status.setLoad(true);
    }

    public void finishLoad(Boolean suc) {
        task = null;
        if (suc) {
            fabRefresh.setVisibility(View.VISIBLE);
            act.status.setLoad(false);
        } else {
            act.status.setCrash(true);
        }
    }

    public void blinkItem(String[] item) {
        dateNow = DateHelper.initNow(act);
        adSummary.insertItem(0, new ListItem(item[0], item[1]));
        adSummary.getItem(0).setDes(prepareDes(item[2], item[3], true));
        adSummary.setAnimation(true);
        adSummary.notifyDataSetChanged();
    }

    private String prepareDes(String des, String time, boolean isNewItem) {
        return (isNewItem ? getResources().getString(R.string.new_item) + Const.N : "") +
                dateNow.getDiffDate(Long.parseLong(time))
                + getResources().getString(R.string.back)
                + Const.N + des;
    }

    public boolean onBackPressed() {
        if (task != null) {
            task.cancel(false);
            return false;
        }
        return true;
    }
}
