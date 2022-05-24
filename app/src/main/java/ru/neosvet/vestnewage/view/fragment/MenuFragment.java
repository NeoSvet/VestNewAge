package ru.neosvet.vestnewage.view.fragment;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.network.NeoClient;
import ru.neosvet.vestnewage.utils.Const;
import ru.neosvet.vestnewage.utils.Lib;
import ru.neosvet.vestnewage.utils.ScreenUtils;
import ru.neosvet.vestnewage.view.activity.MainActivity;
import ru.neosvet.vestnewage.view.list.MenuAdapter;

public class MenuFragment extends Fragment {
    private final int MAX = 12;
    private final int[] mMenu = new int[]{R.id.nav_new, R.id.nav_rss, R.id.nav_site, R.id.nav_calendar,
            R.id.nav_book, R.id.nav_search, R.id.nav_marker, R.id.nav_journal,
            R.id.nav_cabinet, R.id.nav_settings, R.id.nav_help};
    private MainActivity act;
    private ListView lvMenu;
    private MenuAdapter adMenu;
    private int iSelect = 3;
    private boolean isFullScreen = false;

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
        return inflater.inflate(R.layout.menu_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initView(view);
        restoreState(savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();
        setNew(act.getNewId());
    }

    private void initView(View container) {
        lvMenu = container.findViewById(R.id.lvMenu);

        if (ScreenUtils.isTabletLand()) {
            container.findViewById(R.id.ivHeadMenu).setOnClickListener(view -> {
                Lib.openInApps(NeoClient.SITE.substring(0, NeoClient.SITE.length() - 1), null);
//                startActivity(Intent.createChooser(Lib.openInApps(NeoClient.SITE),
//                        getString(R.string.open)));
            });
            if (MainActivity.isCountInMenu)
                act.setProm(container.findViewById(R.id.tvPromTimeInMenu));
            initList(0);
        } else {
            act.setTitle(getString(R.string.app_name));
            container.findViewById(R.id.ivHeadMenu).setVisibility(View.GONE);
            isFullScreen = true;
            initList(1);
        }
    }


    private void restoreState(Bundle state) {
        if (!isFullScreen) {
            if (state != null) {
                act.setFrMenu(this);
                iSelect = state.getInt(Const.SELECT);
            }
            adMenu.getItem(getPos(iSelect)).setSelect(true);
        }
        adMenu.notifyDataSetChanged();
    }

    private void initList(int i) {
        adMenu = new MenuAdapter();
        lvMenu.setAdapter(adMenu);
        int[] mImage = new int[]{R.drawable.ic_download, R.drawable.ic_0, R.drawable.ic_rss, R.drawable.ic_main,
                R.drawable.ic_calendar, R.drawable.ic_book, R.drawable.ic_search, R.drawable.ic_marker,
                R.drawable.ic_journal, R.drawable.ic_cabinet, R.drawable.ic_settings, R.drawable.ic_help};
        int[] mTitle = new int[]{R.string.download_title, R.string.new_section, R.string.rss,
                R.string.news, R.string.calendar, R.string.book, R.string.search, R.string.markers,
                R.string.journal, R.string.cabinet, R.string.settings, R.string.help};
        for (; i < mImage.length; i++) {
            adMenu.addItem(mImage[i], act.getString(mTitle[i]));
        }

        lvMenu.setOnItemClickListener((adapterView, view, pos, l) -> {
            if (pos == 0 && !isFullScreen) {
                act.showDownloadMenu();
                return;
            }
            if (adMenu.getItem(pos).isSelect()) return;
            iSelect = pos - (isFullScreen ? 0 : 1);
            act.setFragment(mMenu[iSelect], false);
            if (isFullScreen) return;
            for (int i1 = getPos(0); i1 < adMenu.getCount(); i1++) {
                adMenu.getItem(i1).setSelect(false);
            }
            adMenu.getItem(pos).setSelect(true);
            adMenu.notifyDataSetChanged();
        });
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putInt(Const.SELECT, iSelect);
        super.onSaveInstanceState(outState);
    }

    public void setSelect(int id) {
        for (int i = 0; i < mMenu.length; i++) {
            if (id == mMenu[i]) {
                if (adMenu != null)
                    adMenu.getItem(getPos(iSelect)).setSelect(false);
                iSelect = i;
                break;
            }
        }
        if (adMenu != null) {
            adMenu.getItem(getPos(iSelect)).setSelect(true);
            adMenu.notifyDataSetChanged();
        }
    }

    private int getPos(int i) {
        if (adMenu.getCount() == MAX)
            return i + 1;
        else
            return i;
    }

    public void setNew(int newId) {
        if (adMenu != null) {
            adMenu.changeIcon(newId, getPos(0));
            adMenu.notifyDataSetChanged();
        }
    }
}