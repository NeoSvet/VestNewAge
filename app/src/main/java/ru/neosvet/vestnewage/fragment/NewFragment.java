package ru.neosvet.vestnewage.fragment;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

import java.io.File;
import java.util.List;

import ru.neosvet.utils.AdsUtils;
import ru.neosvet.utils.Const;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.activity.BrowserActivity;
import ru.neosvet.vestnewage.activity.MainActivity;
import ru.neosvet.vestnewage.helpers.NotificationHelper;
import ru.neosvet.vestnewage.helpers.UnreadHelper;
import ru.neosvet.vestnewage.list.ListAdapter;
import ru.neosvet.vestnewage.list.ListItem;

public class NewFragment extends Fragment {
    private ListAdapter adNew;
    private MainActivity act;
    private View container, fabClear, tvEmptyNew;
    private AdsUtils ads;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        this.container = inflater.inflate(R.layout.new_fragment, container, false);
        act = (MainActivity) getActivity();
        ads = new AdsUtils(act);
        act.setTitle(getResources().getString(R.string.new_section));
        tvEmptyNew = this.container.findViewById(R.id.tvEmptyNew);
        initClear();
        initList();
        restoreState(savedInstanceState);
        return this.container;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putInt(Const.ADS, ads.getIndex());
        super.onSaveInstanceState(outState);
    }

    private void restoreState(Bundle state) {
        if (state != null) {
            int index_ads = state.getInt(Const.ADS);
            if (index_ads == -1) return;
            ads.setIndex(index_ads);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        adNew.clear();
        loadList();
        act.updateNew();
    }

    private void initClear() {
        fabClear = container.findViewById(R.id.fabClear);
        act.fab = fabClear;
        fabClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    UnreadHelper unread = new UnreadHelper(act);
                    unread.clearList();
                    unread.setBadge();
                    unread.close();
                    adNew.clear();
                    adNew.notifyDataSetChanged();
                    tvEmptyNew.setVisibility(View.VISIBLE);
                    fabClear.setVisibility(View.GONE);
                    act.updateNew();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void initList() {
        ListView lvNew = container.findViewById(R.id.lvList);
        adNew = new ListAdapter(act);
        lvNew.setAdapter(adNew);
        lvNew.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int pos, long l) {
                if (act.checkBusy()) return;
                if (adNew.getItem(pos).getTitle().contains(getResources().getString(R.string.ad))) {
                    ads.setIndex(pos);
                    ads.showAd(adNew.getItem(pos).getLink(), adNew.getItem(pos).getHead(0));
                } else if (!adNew.getItem(pos).getLink().equals("")) {
                    BrowserActivity.openReader(act, adNew.getItem(pos).getLink(), null);
                }
            }
        });
        lvNew.setOnTouchListener(new View.OnTouchListener() {
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

    private void loadList() {
        NotificationHelper notifHelper = new NotificationHelper(act);
        notifHelper.cancel(NotificationHelper.NOTIF_SUMMARY);
        try {
            String t, s;
            int n;
            UnreadHelper unread = new UnreadHelper(act);
            unread.setBadge();
            long time = unread.lastModified();
            if (time > 0) {
                List<String> links = unread.getList();
                unread.close();
                for (int i = links.size() - 1; i > -1; i--) {
                    s = links.get(i);
                    t = s.substring(s.lastIndexOf(File.separator) + 1);
                    if (t.contains("_")) {
                        n = t.indexOf("_");
                        t = t.substring(0, n) + " (" + t.substring(n + 1) + ")";
                    }
                    if (s.contains(Const.POEMS))
                        t = getResources().getString(R.string.katren) + " " + getResources().getString(R.string.from) + " " + t;
                    if (s.contains("#")) {
                        t = t.replace("#", " (") + ")";
                        adNew.addItem(new ListItem(t, s.replace("#", Const.HTML + "#")));
                    } else
                        adNew.addItem(new ListItem(t, s + Const.HTML));
                }
                links.clear();
            }
            if (time < ads.getTime()) {
                ads.loadList(adNew, true);
                if (ads.getIndex() > -1)
                    ads.showAd(adNew.getItem(ads.getIndex()).getLink(), adNew.getItem(ads.getIndex()).getHead(0));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (adNew.getCount() == 0) {
            tvEmptyNew.setVisibility(View.VISIBLE);
            fabClear.setVisibility(View.GONE);
        }
    }
}
