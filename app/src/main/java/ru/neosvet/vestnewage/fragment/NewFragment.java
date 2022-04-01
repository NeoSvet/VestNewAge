package ru.neosvet.vestnewage.fragment;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.io.File;
import java.util.List;

import ru.neosvet.utils.Const;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.activity.BrowserActivity;
import ru.neosvet.vestnewage.activity.MainActivity;
import ru.neosvet.vestnewage.helpers.DevadsHelper;
import ru.neosvet.vestnewage.helpers.NotificationHelper;
import ru.neosvet.vestnewage.helpers.UnreadHelper;
import ru.neosvet.vestnewage.list.ListAdapter;
import ru.neosvet.vestnewage.list.ListItem;

public class NewFragment extends Fragment {
    private ListAdapter adNew;
    private MainActivity act;
    private View fabClear, tvEmptyNew;
    private DevadsHelper ads;

    @Override
    public void onAttach(@NonNull Context context) {
        act = (MainActivity) getActivity();
        super.onAttach(context);
    }

    @Override
    public void onDestroyView() {
        act = null;
        super.onDestroyView();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.new_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ads = new DevadsHelper(act);
        act.setTitle(getString(R.string.new_section));
        tvEmptyNew = view.findViewById(R.id.tvEmptyNew);
        fabClear = view.findViewById(R.id.fabClear);
        initClear();
        initList(view.findViewById(R.id.lvList));
        restoreState(savedInstanceState);
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
    public void onStop() {
        super.onStop();
        ads.close();
    }

    @Override
    public void onResume() {
        super.onResume();
        adNew.clear();
        ads.reopen();
        loadList();
        act.updateNew();
    }

    private void initClear() {
        act.fab = fabClear;
        fabClear.setOnClickListener(view -> {
            try {
                UnreadHelper unread = new UnreadHelper();
                unread.clearList();
                unread.setBadge(ads.getUnreadCount());
                adNew.clear();
                adNew.notifyDataSetChanged();
                tvEmptyNew.setVisibility(View.VISIBLE);
                fabClear.setVisibility(View.GONE);
                act.updateNew();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initList(ListView lvNew) {
        adNew = new ListAdapter(act);
        lvNew.setAdapter(adNew);
        lvNew.setOnItemClickListener((adapterView, view, pos, l) -> {
            if (act.checkBusy()) return;
            if (adNew.getItem(pos).getTitle().contains(getString(R.string.ad))) {
                ads.setIndex(pos);
                showAd(pos);
            } else if (!adNew.getItem(pos).getLink().equals("")) {
                BrowserActivity.openReader(adNew.getItem(pos).getLink(), null);
            }
        });
        lvNew.setOnTouchListener((view, motionEvent) -> {
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

    private void loadList() {
        NotificationHelper notifHelper = new NotificationHelper();
        notifHelper.cancel(NotificationHelper.NOTIF_SUMMARY);
        try {
            ads.loadList(adNew, true);
            String t, s;
            int n;
            UnreadHelper unread = new UnreadHelper();
            unread.setBadge(ads.getUnreadCount());
            if (unread.lastModified() > 0) {
                List<String> links = unread.getList();
                for (int i = links.size() - 1; i > -1; i--) {
                    s = links.get(i);
                    t = s.substring(s.lastIndexOf(File.separator) + 1);
                    if (t.contains("_")) {
                        n = t.indexOf("_");
                        t = t.substring(0, n) + " (" + t.substring(n + 1) + ")";
                    }
                    if (s.contains(Const.POEMS))
                        t = getString(R.string.katren) + " " + getString(R.string.from) + " " + t;
                    if (s.contains("#")) {
                        t = t.replace("#", " (") + ")";
                        adNew.addItem(new ListItem(t, s.replace("#", Const.HTML + "#")));
                    } else
                        adNew.addItem(new ListItem(t, s + Const.HTML));
                }
                links.clear();
            }
            if (ads.getIndex() > -1)
                showAd(ads.getIndex());
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (adNew.getCount() == 0) {
            tvEmptyNew.setVisibility(View.VISIBLE);
            fabClear.setVisibility(View.GONE);
        }
    }

    private void showAd(int index) {
        String t = adNew.getItem(index).getTitle();
        ads.showAd(t.substring(t.indexOf(" ") + 1),
                adNew.getItem(index).getLink(),
                adNew.getItem(index).getHead(0));
        act.updateNew();
    }
}
