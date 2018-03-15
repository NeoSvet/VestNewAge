package ru.neosvet.vestnewage.fragment;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

import ru.neosvet.ui.MenuAdapter;
import ru.neosvet.utils.Const;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.activity.MainActivity;

public class MenuFragment extends Fragment {
    private static final String SELECT = "select";
    private final int[] mMenu = new int[]{R.id.nav_rss, R.id.nav_main, R.id.nav_calendar,
            R.id.nav_book, R.id.nav_search, R.id.nav_marker, R.id.nav_journal,
            R.id.nav_cabinet, R.id.nav_settings, R.id.nav_help};
    private MainActivity act;
    private View container;
    private MenuAdapter adMenu;
    private int iSelect = 2;
    private boolean isFullScreen = false;

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

        int i;
        if (getResources().getInteger(R.integer.screen_mode) ==
                getResources().getInteger(R.integer.screen_tablet_land)) {
            this.container.findViewById(R.id.ivHeadMenu).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    act.lib.openInApps(Const.SITE.substring(0, Const.SITE.length() - 1), null);
//                startActivity(Intent.createChooser(act.lib.openInApps(Const.SITE),
//                        getResources().getString(R.string.open)));
                }
            });
            if (MainActivity.isCountInMenu)
                act.setProm(this.container.findViewById(R.id.tvPromTimeInMenu));
            i = 0;
        } else {
            act.setTitle(getResources().getString(R.string.app_name));
            this.container.findViewById(R.id.ivHeadMenu).setVisibility(View.GONE);
            this.container.findViewById(R.id.divMenu).setVisibility(View.GONE);
            isFullScreen = true;
            i = 1;
        }

        ListView lvMenu = (ListView) this.container.findViewById(R.id.lvMenu);
        adMenu = new MenuAdapter(act);
        lvMenu.setAdapter(adMenu);
        int[] mImage = new int[]{R.drawable.download, R.drawable.rss, R.drawable.main,
                R.drawable.calendar, R.drawable.book, R.drawable.search, R.drawable.marker,
                R.drawable.journal, R.drawable.cabinet, R.drawable.settings, R.drawable.help};
        int[] mTitle = new int[]{R.string.download_title, R.string.rss, R.string.main,
                R.string.calendar, R.string.book, R.string.search, R.string.markers,
                R.string.journal, R.string.cabinet, R.string.settings, R.string.help};
        for (; i < mImage.length; i++) {
            adMenu.addItem(mImage[i], act.getResources().getString(mTitle[i]));
        }

        lvMenu.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int pos, long l) {
                if (pos == 0 && !isFullScreen) {
                    act.showDownloadMenu();
                    return;
                }
                if (adMenu.getItem(pos).isSelect()) return;
                iSelect = pos - (isFullScreen ? 0 : 1);
                act.setFragment(mMenu[iSelect]);
                if (isFullScreen) return;
                for (int i = 0; i < adMenu.getCount(); i++) {
                    adMenu.getItem(i).setSelect(false);
                }
                adMenu.getItem(pos).setSelect(true);
                adMenu.notifyDataSetChanged();
            }
        });
        if (!isFullScreen) {
            if (savedInstanceState != null)
                iSelect = savedInstanceState.getInt(SELECT);
            adMenu.getItem(iSelect + 1).setSelect(true);
        }
        adMenu.notifyDataSetChanged();

        return this.container;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putInt(SELECT, iSelect);
        super.onSaveInstanceState(outState);
    }
}
