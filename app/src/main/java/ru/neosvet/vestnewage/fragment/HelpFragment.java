package ru.neosvet.vestnewage.fragment;

import android.app.Fragment;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

import ru.neosvet.utils.Const;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.activity.MainActivity;
import ru.neosvet.vestnewage.list.HelpAdapter;

public class HelpFragment extends Fragment {
    private final int COUNT = 8, FEEDBACK = 1, FEEDBACK_COUNT = 5,
            WRITE_TO_DEV = 1, LINK_ON_APP = 2, LINK_ON_YM = 3, LINK_ON_QW = 4, CHANGELOG = 5;
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
        restoreState(savedInstanceState);
        return this.container;
    }

    public void setOpenHelp(int i) {
        mHelp = new boolean[COUNT];
        mHelp[i] = true;
    }

    private void restoreState(Bundle state) {
        if (state == null) {
            if (mHelp == null)
                mHelp = new boolean[COUNT];
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
                        onClickButton(pos - FEEDBACK);
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
            case WRITE_TO_DEV:
                act.lib.openInApps("mailto:neosvet333@gmail.com?subject=Приложение «Весть Нового Века»&body="
                        + getResources().getString(R.string.srv_info)
                        + adHelp.getItem(FEEDBACK + CHANGELOG).getDes(), null);
                break;
            case LINK_ON_APP:
                Intent sendIntent = new Intent(Intent.ACTION_SEND);
                sendIntent.setType("text/plain");
                sendIntent.putExtra(Intent.EXTRA_TEXT, getResources().getString(R.string.url_on_app));
                startActivity(sendIntent);
                break;
            case LINK_ON_YM:
                act.lib.openInApps("https://money.yandex.ru/to/410012986244848", null);
                break;
            case LINK_ON_QW:
                act.lib.openInApps("https://qiwi.com/n/neosvet", null);
                break;
            default: //CHANGELOG
                act.lib.openInApps("http://neosvet.ucoz.ru/vna/changelog.html", null);
        }
    }

    private void turnFeedback() {
        feedback = !feedback;
        if (feedback) {
            adHelp.insertItem(FEEDBACK + WRITE_TO_DEV, getResources().getString(R.string.write_to_dev), R.drawable.gm);
            adHelp.insertItem(FEEDBACK + LINK_ON_APP, getResources().getString(R.string.link_on_app), R.drawable.play_store);
            adHelp.insertItem(FEEDBACK + LINK_ON_YM, getResources().getString(R.string.support_ym), R.drawable.ymoney);
            adHelp.insertItem(FEEDBACK + LINK_ON_QW, getResources().getString(R.string.support_qiwi), R.drawable.qiwi);
            adHelp.insertItem(FEEDBACK + CHANGELOG, getResources().getString(R.string.changelog), 0);
            try {
                StringBuilder des = new StringBuilder(getResources().getString(R.string.app_version));
                des.append(act.getPackageManager().getPackageInfo(act.getPackageName(), 0).versionName);
                des.append(" (");
                des.append(act.getPackageManager().getPackageInfo(act.getPackageName(), 0).versionCode);
                des.append(")\n");
                des.append(getResources().getString(R.string.system_version));
                des.append(Build.VERSION.RELEASE);
                des.append(" (");
                des.append(Build.VERSION.SDK_INT);
                des.append(")");
                adHelp.getItem(FEEDBACK + CHANGELOG).setDes(des.toString());
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
