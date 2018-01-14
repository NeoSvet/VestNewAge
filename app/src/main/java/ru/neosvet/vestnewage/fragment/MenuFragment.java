package ru.neosvet.vestnewage.fragment;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

import ru.neosvet.ui.MenuAdapter;
import ru.neosvet.utils.Lib;
import ru.neosvet.vestnewage.activity.MainActivity;
import ru.neosvet.vestnewage.R;

public class MenuFragment extends Fragment {
    private static final String SELECT = "select";
    private final int[] mMenu = new int[]{R.id.nav_rss, R.id.nav_main, R.id.nav_calendar,
            R.id.nav_book, R.id.nav_search, R.id.nav_marker, R.id.nav_journal,
            R.id.nav_cabinet, R.id.nav_settings, R.id.nav_help};
    private MainActivity act;
    private View container;
    private MenuAdapter adMenu;
    private int iSelect = 2;

    public void setSelect(int id) {
        for (int i = 0; i < mMenu.length; i++) {
            if (id == mMenu[i]) {
                iSelect = i;
                break;
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        this.container = inflater.inflate(R.layout.menu_fragment, container, false);
        act = (MainActivity) getActivity();

        this.container.findViewById(R.id.ivHeadMenu).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                act.lib.openInApps(Lib.SITE.substring(0, Lib.SITE.length() - 1), null);
//                startActivity(Intent.createChooser(act.lib.openInApps(Lib.SITE),
//                        getResources().getString(R.string.open)));
            }
        });

        ListView lvMenu = (ListView) this.container.findViewById(R.id.lvMenu);
        adMenu = new MenuAdapter(act);
        lvMenu.setAdapter(adMenu);
        int[] mImage = new int[]{R.drawable.download, R.drawable.rss, R.drawable.main,
                R.drawable.calendar, R.drawable.book, R.drawable.search, R.drawable.marker,
                R.drawable.journal, R.drawable.cabinet, R.drawable.settings, R.drawable.help};
        int[] mTitle = new int[]{R.string.download_title, R.string.rss, R.string.main,
                R.string.calendar, R.string.book, R.string.search, R.string.markers,
                R.string.journal, R.string.cabinet, R.string.settings, R.string.help};
        for (int i = 0; i < mImage.length; i++) {
            adMenu.addItem(mImage[i], act.getResources().getString(mTitle[i]));
        }

        lvMenu.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int pos, long l) {
                if (pos == 0) {
                    act.showDownloadMenu();
                    return;
                }
                if (adMenu.getItem(pos).isSelect()) return;
                iSelect = pos - 1;
                for (int i = 0; i < adMenu.getCount(); i++) {
                    adMenu.getItem(i).setSelect(false);
                }
                adMenu.getItem(pos).setSelect(true);
                adMenu.notifyDataSetChanged();
                act.setFragment(mMenu[iSelect]);
            }
        });
        if (savedInstanceState != null) {
            iSelect = savedInstanceState.getInt(SELECT);
        }
        adMenu.getItem(iSelect + 1).setSelect(true);
        adMenu.notifyDataSetChanged();

        return this.container;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putInt(SELECT, iSelect);
        super.onSaveInstanceState(outState);
    }
}
