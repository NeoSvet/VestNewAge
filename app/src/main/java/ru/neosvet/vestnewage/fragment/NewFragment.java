package ru.neosvet.vestnewage.fragment;

import android.app.Fragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.List;

import ru.neosvet.ui.dialogs.CustomDialog;
import ru.neosvet.utils.Const;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.activity.BrowserActivity;
import ru.neosvet.vestnewage.activity.MainActivity;
import ru.neosvet.vestnewage.helpers.UnreadHelper;
import ru.neosvet.vestnewage.list.ListAdapter;
import ru.neosvet.vestnewage.list.ListItem;

public class NewFragment extends Fragment {
    private CustomDialog alert;
    private ListAdapter adNew;
    private MainActivity act;
    private int index_ads = -1;
    private View container, fabClear, tvEmptyNew;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        this.container = inflater.inflate(R.layout.new_fragment, container, false);
        act = (MainActivity) getActivity();
        act.setTitle(getResources().getString(R.string.new_section));
        tvEmptyNew = this.container.findViewById(R.id.tvEmptyNew);
        initClear();
        initList();
        restoreActivityState(savedInstanceState);
        return this.container;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putInt(Const.ADS, index_ads);
        super.onSaveInstanceState(outState);
    }

    private void restoreActivityState(Bundle state) {
        if (state != null) {
            index_ads = state.getInt(Const.ADS);
            showAd(adNew.getItem(index_ads).getLink(), adNew.getItem(index_ads).getHead(0));
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
        fabClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    UnreadHelper unread = new UnreadHelper(act);
                    unread.clearList();
                    unread.setBadge();
                    unread.close();
                    File file = new File(act.getFilesDir() + File.separator + Const.ADS);
                    if (file.exists()) {
                        BufferedReader br = new BufferedReader(new FileReader(file));
                        String t = br.readLine(); // читаем время последней загрузки объявлений
                        br.close();
                        BufferedWriter bw = new BufferedWriter(new FileWriter(file));
                        bw.write(t); // затираем файл объявлений, оставляем лишь время загрузки
                        bw.close();
                    }
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
        ListView lvNew = (ListView) container.findViewById(R.id.lvList);
        adNew = new ListAdapter(act);
        lvNew.setAdapter(adNew);
        lvNew.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int pos, long l) {
                BrowserActivity.openReader(act, adNew.getItem(pos).getLink(), null);
            }
        });
        lvNew.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int pos, long l) {
                if (adNew.getItem(pos).getTitle().contains(getResources().getString(R.string.ad))) {
                    String link = adNew.getItem(pos).getLink();
                    String des = adNew.getItem(pos).getHead(0);
                    if (des.equals("")) // only link
                        act.lib.openInApps(link, null);
                    else {
                        index_ads = pos;
                        showAd(link, des);
                    }
                } else if (!adNew.getItem(pos).getLink().equals("")) {
                    BrowserActivity.openReader(act, adNew.getItem(pos).getLink(), null);
                }
            }
        });
    }

    private void loadList() {
        try {
            String t, s;
            int n;
            UnreadHelper unread = new UnreadHelper(act);
            unread.setBadge();
            long time = unread.lastModified();
            if (time > 0) {
                List<String> links = unread.getList();
                unread.close();
                for (int i = 0; i < links.size(); i++) {
                    s = links.get(i);
                    t = s.substring(s.lastIndexOf(File.separator) + 1);
                    if (t.contains("_")) {
                        n = t.indexOf("_");
                        t = t.substring(0, n) + " (" + t.substring(n + 1) + ")";
                    }
                    if (s.contains(Const.POEMS))
                        t = getResources().getString(R.string.katren) + " " +
                                getResources().getString(R.string.from) + " " + t;
                    adNew.addItem(new ListItem(t, s + Const.HTML));
                }
                links.clear();
            }
            File file = new File(act.getFilesDir() + File.separator + Const.ADS);
            if (file.exists()) {
                BufferedReader br = new BufferedReader(new FileReader(file));
                br.readLine(); //time
                final String end = "<e>";
                while ((s = br.readLine()) != null) {
                    if (s.contains("<t>")) {
                        adNew.insertItem(0, new ListItem(
                                getResources().getString(R.string.ad)
                                        + ": " + s.substring(3)));
                    } else if (s.contains("<u>")) {
                        n = Integer.parseInt(s.substring(3));
                        if (n > act.getPackageManager().getPackageInfo(act.getPackageName(), 0).versionCode) {
                            adNew.insertItem(0, new ListItem(
                                    getResources().getString(R.string.ad) + ": " +
                                            getResources().getString(R.string.access_new_version)));
                            adNew.getItem(0).addLink(
                                    getResources().getString(R.string.url_on_app));
                        } else {
                            while (!s.equals(end))
                                s = br.readLine();
                        }
                    } else if (s.contains("<d>")) {
                        t = s.substring(3);
                        s = br.readLine();
                        while (!s.equals(end)) {
                            t += Const.N + s;
                            s = br.readLine();
                        }
                        adNew.getItem(0).addHead(t);
                    } else if (s.contains("<l>")) {
                        adNew.getItem(0).addLink(s.substring(3));
                    }
                }
                br.close();
            }
            adNew.notifyDataSetChanged();
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (adNew.getCount() == 0) {
            tvEmptyNew.setVisibility(View.VISIBLE);
            fabClear.setVisibility(View.GONE);
        }
    }

    private void showAd(final String link, String des) {
        alert = new CustomDialog(act);
        alert.setTitle(getResources().getString(R.string.ad));
        alert.setMessage(des);

        if (link.equals("")) { // only des
            alert.setRightButton(getResources().getString(android.R.string.ok), new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    alert.dismiss();
                }
            });
        } else {
            alert.setRightButton(getResources().getString(R.string.open_link), new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    act.lib.openInApps(link, null);
                    alert.dismiss();
                }
            });
        }

        alert.show(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialogInterface) {
                index_ads = -1;
            }
        });
    }
}
