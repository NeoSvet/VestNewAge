package ru.neosvet.vestnewage;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

import ru.neosvet.ui.ListAdapter;
import ru.neosvet.ui.ListItem;
import ru.neosvet.utils.Lib;
import ru.neosvet.utils.SummaryTask;

public class SummaryFragment extends Fragment {
    public static final String RSS = "/rss";
    private boolean boolLoad = false;

    public void setLoad(boolean boolLoad) {
        this.boolLoad = boolLoad;
    }

    private ListView lvSummary;
    private ListAdapter adSummary;
    private MainActivity act;
    private View container;
    private View fabRefresh;
    private SummaryTask task = null;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        this.container = inflater.inflate(R.layout.fragment_summary, container, false);
        act = (MainActivity) getActivity();
        act.setTitle(getResources().getString(R.string.rss));
        initViews();
        restoreActivityState(savedInstanceState);
        return this.container;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putSerializable(Lib.TASK, task);
        super.onSaveInstanceState(outState);
    }

    private void restoreActivityState(Bundle state) {
        if (state != null) {
            task = (SummaryTask) state.getSerializable(Lib.TASK);
            if (task != null) {
                act.status.setLoad(true);
                task.setFrm(this);
            }
        }
        File f = new File(act.getFilesDir() + RSS);
        if (f.exists() && !boolLoad) {
            act.status.checkTime(f.lastModified());
            createList(true);
        } else {
            boolLoad = false;
            startLoad();
        }
    }

    private void initViews() {
        fabRefresh = container.findViewById(R.id.fabRefresh);
        fabRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startLoad();
            }
        });
        act.status.setClick(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (act.status.onClick())
                    fabRefresh.setVisibility(View.VISIBLE);
                else if (act.status.isTime())
                    startLoad();
            }
        });

        lvSummary = (ListView) container.findViewById(R.id.lvSummary);
        adSummary = new ListAdapter(act);
        lvSummary.setAdapter(adSummary);
        lvSummary.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int pos, long l) {
                String link = adSummary.getItem(pos).getLink();
                boolean b = false;
                if (link.indexOf(BrowserActivity.ARTICLE) == Lib.LINK.length()) {
                    link = link.substring(BrowserActivity.ARTICLE.length());
                    b = true;
                }
                BrowserActivity.openActivity(act, link, b);
            }
        });
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
                                + Lib.N + t);
                i++;
            }
            br.close();
            adSummary.notifyDataSetChanged();
            lvSummary.smoothScrollToPosition(0);
        } catch (Exception e) {
            e.printStackTrace();
            if (boolLoad)
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

    public void finishLoad(Boolean result) {
        task = null;
        if (result) {
            fabRefresh.setVisibility(View.VISIBLE);
            act.status.setLoad(false);
            createList(false);
        } else {
            act.status.setCrash(true);
        }
    }
}
