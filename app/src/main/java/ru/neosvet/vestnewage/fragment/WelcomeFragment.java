package ru.neosvet.vestnewage.fragment;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import ru.neosvet.utils.Const;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.list.ListAdapter;
import ru.neosvet.vestnewage.list.ListItem;

public class WelcomeFragment extends BottomSheetDialogFragment {
    public interface ItemClicker {
        void onItemClick(String link);
    }

    private ItemClicker clicker;
    private ListAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.welcome_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ListView lvBottom = view.findViewById(R.id.lvBottom);
        adapter = new ListAdapter(requireContext());
        lvBottom.setAdapter(adapter);
        lvBottom.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, final int pos, long l) {
                if (clicker != null)
                    clicker.onItemClick(adapter.getItem(pos).getLink());
            }
        });
        fillInList();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof ItemClicker)
            clicker = (ItemClicker) context;
    }

    private void fillInList() {
        Bundle args = getArguments();

        if (args.getBoolean(Const.ADS)) {
            adapter.addItem(new ListItem(getResources().getString(R.string.new_dev_ads), Const.ADS));
        }

        if (args.getBoolean(Const.PAGE)) {
            String[] title = args.getStringArray(Const.TITLE);
            String[] link = args.getStringArray(Const.LINK);
            for (int i = 0; i < title.length; i++) {
                ListItem item = new ListItem(title[i], link[i]);
                item.setDes(getResources().getString(R.string.updated_page));
                adapter.addItem(item);
            }
        }

        if (args.getBoolean(Const.TIME)) {
            int timeDiff = args.getInt(Const.TIMEDIFF);
            ListItem item = new ListItem(getResources().getString(R.string.sync_time));
            if (timeDiff == 0)
                item.setDes(getResources().getString(R.string.matches));
            else
                item.setDes(String.format(getResources().getString(R.string.time_deviation),
                        timeDiff));
            adapter.addItem(item);
            adapter.notifyDataSetChanged();
        }
    }
}
