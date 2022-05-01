package ru.neosvet.vestnewage.fragment;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import ru.neosvet.utils.Const;
import ru.neosvet.utils.Lib;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.activity.MainActivity;
import ru.neosvet.vestnewage.list.HelpAdapter;

public class HelpFragment extends Fragment {
    private final int COUNT = 7, FEEDBACK = 1, FEEDBACK_COUNT = 4,
            WRITE_TO_DEV = 1, LINK_ON_APP = 2, LINK_ON_SITE = 3, CHANGELOG = 4;
    private boolean feedback = false;
    private MainActivity act;
    private HelpAdapter adHelp;
    private boolean[] mHelp = null;

    public static HelpFragment newInstance(int section) {
        HelpFragment fragment = new HelpFragment();
        Bundle args = new Bundle();
        args.putInt(Const.TAB, section);
        fragment.setArguments(args);
        return fragment;
    }

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
        return inflater.inflate(R.layout.help_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        act.setTitle(getString(R.string.help));
        initList(view);
        restoreState(savedInstanceState);
    }

    private void restoreState(Bundle state) {
        if (state == null) {
            mHelp = new boolean[COUNT];
            if (getArguments() != null) {
                int section = getArguments().getInt(Const.TAB);
                mHelp[section] = true;
            }
        } else {
            mHelp = state.getBooleanArray(Const.PANEL);
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
        outState.putBooleanArray(Const.PANEL, mHelp);
        super.onSaveInstanceState(outState);
    }

    private void initList(View container) {
        adHelp = new HelpAdapter();
        ListView lvHelp = container.findViewById(R.id.lvHelp);
        lvHelp.setAdapter(adHelp);
        lvHelp.setOnItemClickListener((adapterView, view, pos, l) -> {
            int i = pos;
            if (feedback && pos > FEEDBACK) {
                if (pos <= FEEDBACK + FEEDBACK_COUNT) {
                    onClickButton(pos - FEEDBACK);
                    return;
                } else
                    i -= FEEDBACK_COUNT;
            }
            if (pos == FEEDBACK)
                turnFeedback();
            else if (adHelp.getItem(pos).hasLink()) {
                mHelp[i] = false;
                adHelp.getItem(pos).clear();
            } else {
                mHelp[i] = true;
                adHelp.getItem(pos).addLink("");
            }
            adHelp.notifyDataSetChanged();
        });
    }

    private void onClickButton(int index) {
        switch (index) {
            case WRITE_TO_DEV:
                var msg = getString(R.string.srv_info)
                        + adHelp.getItem(FEEDBACK + CHANGELOG).getDes();
                Lib.openInApps(Const.mailto + msg, null);
                break;
            case LINK_ON_APP:
                Intent sendIntent = new Intent(Intent.ACTION_SEND);
                sendIntent.setType("text/plain");
                sendIntent.putExtra(Intent.EXTRA_TEXT, getString(R.string.title_app)
                        + getString(R.string.url_on_app));
                startActivity(sendIntent);
                break;
            case LINK_ON_SITE:
                Lib.openInApps("http://neosvet.ucoz.ru/vna/", null);
                break;
            default: //CHANGELOG
                Lib.openInApps("http://neosvet.ucoz.ru/vna/changelog.html", null);
        }
    }

    private void turnFeedback() {
        feedback = !feedback;
        if (feedback) {
            adHelp.insertItem(FEEDBACK + WRITE_TO_DEV, getString(R.string.write_to_dev), R.drawable.gm);
            adHelp.insertItem(FEEDBACK + LINK_ON_APP, getString(R.string.link_on_app), R.drawable.play_store);
            adHelp.insertItem(FEEDBACK + LINK_ON_SITE, getString(R.string.page_app), R.drawable.www);
            adHelp.insertItem(FEEDBACK + CHANGELOG, getString(R.string.changelog), 0);
            try {
                String des = String.format(getString(R.string.format_info),
                        act.getPackageManager().getPackageInfo(act.getPackageName(), 0).versionName,
                        act.getPackageManager().getPackageInfo(act.getPackageName(), 0).versionCode,
                        Build.VERSION.RELEASE,
                        Build.VERSION.SDK_INT);
                adHelp.getItem(FEEDBACK + CHANGELOG).setDes(des);
                adHelp.getItem(FEEDBACK + CHANGELOG).addLink("");
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            for (int i = 0; i < FEEDBACK_COUNT; i++)
                adHelp.removeItem(FEEDBACK + 1);
        }
    }
}
