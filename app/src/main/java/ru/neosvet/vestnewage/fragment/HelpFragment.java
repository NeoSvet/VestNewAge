package ru.neosvet.vestnewage.fragment;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

import ru.neosvet.ui.HelpAdapter;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.activity.MainActivity;

public class HelpFragment extends Fragment {
    private final String PANELS = "panels";
    private final int COUNT = 8, FEEDBACK = 1;
    private int FEEDBACK_COUNT = 3;
    private boolean feedback = false;
    private MainActivity act;
    private View container;
    private HelpAdapter adHelp;
    private boolean[] mHelp = null;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        this.container = inflater.inflate(R.layout.help_fragment, container, false);
        act = (MainActivity) getActivity();
        act.setTitle(getResources().getString(R.string.help));
        initList();
        restoreActivityState(savedInstanceState);
        return this.container;
    }

    public void setOpenHelp(int i) {
        mHelp = new boolean[COUNT];
        mHelp[i] = true;
    }

    private void restoreActivityState(Bundle state) {
        if (state == null) {
            if (mHelp == null)
                mHelp = new boolean[COUNT];
        } else {
            mHelp = state.getBooleanArray(PANELS);
            if (mHelp[FEEDBACK])
                turnFeedback();
            adHelp.notifyDataSetChanged();
        }
        for (int i = 0; i < COUNT; i++) {
            adHelp.addItem(getResources().getStringArray(R.array.help_title)[i],
                    getResources().getStringArray(R.array.help_content)[i]);
            if (mHelp[i])
                adHelp.getItem(i).addLink("");
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putBooleanArray(PANELS, mHelp);
        super.onSaveInstanceState(outState);
    }

    private void initList() {
        adHelp = new HelpAdapter(act);
        ListView lvHelp = (ListView) container.findViewById(R.id.lvHelp);
        lvHelp.setAdapter(adHelp);
        lvHelp.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int pos, long l) {
                int i = pos;
                if (feedback && pos > FEEDBACK) {
                    if (pos <= FEEDBACK + FEEDBACK_COUNT) {
                        onClickButton(pos - FEEDBACK - 1);
                        return;
                    } else
                        i -= FEEDBACK_COUNT;
                }
                if (pos == FEEDBACK)
                    turnFeedback();
                else if (adHelp.getItem(pos).getCount() == 0) {
                    mHelp[i] = true;
                    adHelp.getItem(pos).addLink("");
                } else {
                    mHelp[i] = false;
                    adHelp.getItem(pos).clear();
                }
                adHelp.notifyDataSetChanged();
            }
        });
    }

    private void onClickButton(int index) {
        switch (index) {
            case 0: //write_to_dev
                act.lib.openInApps("mailto:neosvet333@gmail.com?subject=Приложение «Весть Нового Века»", null);
                break;
            case 1: //link_on_app
                act.lib.copyAddress(getResources().getString(R.string.url_on_app));
                break;
            case 2: //changelog
                act.lib.openInApps("http://neosvet.ucoz.ru/vna/changelog.html", null);
                break;
            default: //version
        }
    }

    private void turnFeedback() {
        feedback = !feedback;
        if (feedback) {
            adHelp.insertItem(FEEDBACK + 1, getResources().getString(R.string.write_to_dev), R.drawable.gm);
            adHelp.insertItem(FEEDBACK + 2, getResources().getString(R.string.link_on_app), R.drawable.play_store);
            adHelp.insertItem(FEEDBACK + 3, getResources().getString(R.string.changelog), R.drawable.journal);
            try {
                String title = getResources().getString(R.string.current_version) +
                        act.getPackageManager().getPackageInfo(act.getPackageName(), 0).versionName;
                adHelp.insertItem(FEEDBACK + 4, title, 0);
                FEEDBACK_COUNT = 4;
            } catch (Exception e) {
                FEEDBACK_COUNT = 3;
                e.printStackTrace();
            }
        } else {
            for (int i = 0; i < FEEDBACK_COUNT; i++)
                adHelp.removeItem(FEEDBACK + 1);
        }
    }
}
