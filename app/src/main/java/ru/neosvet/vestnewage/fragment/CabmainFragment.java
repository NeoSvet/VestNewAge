package ru.neosvet.vestnewage.fragment;

import android.app.Service;
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.work.Data;

import ru.neosvet.ui.SoftKeyboard;
import ru.neosvet.utils.BackFragment;
import ru.neosvet.utils.Const;
import ru.neosvet.utils.Lib;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.activity.CabpageActivity;
import ru.neosvet.vestnewage.activity.MainActivity;
import ru.neosvet.vestnewage.helpers.ProgressHelper;
import ru.neosvet.vestnewage.list.ListAdapter;
import ru.neosvet.vestnewage.list.ListItem;
import ru.neosvet.vestnewage.model.CabModel;
import ru.neosvet.vestnewage.workers.CabWorker;

public class CabmainFragment extends BackFragment implements Observer<Data> {
    private MainActivity act;
    private ListAdapter adMain;
    private SoftKeyboard softKeyboard;
    private CheckBox cbRemEmail, cbRemPassword;
    private TextView tvError;
    private EditText etEmail, etPassword;
    private View container, fabEnter, fabExit, pMain;
    private byte mode_list = 0;
    private CabModel model;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        this.container = inflater.inflate(R.layout.cabmain_fragment, container, false);
        act = (MainActivity) getActivity();
        initViews();
        setViews();
        restoreState(savedInstanceState);
        initModel();
        return this.container;
    }

    @Override
    public void onPause() {
        super.onPause();
        ProgressHelper.removeObservers(act);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (ProgressHelper.isBusy())
            ProgressHelper.addObserver(act, this);
    }

    @Override
    public boolean onBackPressed() {
        if (ProgressHelper.isBusy()) {
            ProgressHelper.cancelled();
            return false;
        }
        if (mode_list == CabModel.LOGIN)
            return true;
        else {
            mode_list--;
            if (mode_list == CabModel.LOGIN) {
                pMain.setVisibility(View.VISIBLE);
                fabEnter.setVisibility(View.VISIBLE);
                fabExit.setVisibility(View.GONE);
                adMain.clear();
                loginList();
            } else { //CabModel.CABINET
                mode_list = CabModel.LOGIN;
                initLoad();
                act.status.startText();
                model.login(etEmail.getText().toString(), etPassword.getText().toString());
            }
            return false;
        }
    }

    private void initModel() {
        model = ViewModelProviders.of(act).get(CabModel.class);
        if (ProgressHelper.isBusy())
            initLoad();
    }

    @Override
    public void onChanged(@Nullable Data result) {
        if (!ProgressHelper.isBusy())
            return;
        if (result.getBoolean(Const.START, false)) {
            act.status.loadText();
            return;
        }
        if (!result.getBoolean(Const.FINISH, false))
            return;
        ProgressHelper.setBusy(false);
        ProgressHelper.removeObservers(act);
        act.status.setLoad(false);
        String error = result.getString(Const.ERROR);
        if (error != null) {
            act.status.setError(error);
            return;
        }
        switch (result.getInt(Const.MODE, 0)) {
            case CabWorker.SELECTED_WORD:
                int select = result.getInt(Const.SELECT, -1);
                if (select == -1)
                    initCabinet(getResources().getString(R.string.selected_status),
                            result.getString(Const.DESCTRIPTION));
                else
                    initCabinet(getResources().getString(R.string.selected_status),
                            adMain.getItem(select).getTitle());
                break;
            case CabWorker.NO_SELECTED:
                initCabinet(getResources().getString(R.string.send_status),
                        getResources().getString(R.string.select_status));
                break;
            case CabWorker.WORD_LIST:
                initWordList(result.getStringArray(Const.DESCTRIPTION));
                break;
            case CabWorker.TIMEOUT:
                initCabinet(getResources().getString(R.string.send_status),
                        result.getString(Const.DESCTRIPTION));
                break;
            case CabWorker.ERROR:
                tvError.setText(result.getString(Const.DESCTRIPTION));
                tvError.setVisibility(View.VISIBLE);
                break;
        }
        pMain.setVisibility(View.GONE);
        fabEnter.setVisibility(View.GONE);
        fabExit.setVisibility(View.VISIBLE);
    }

    private void restoreState(Bundle state) {
        if (state == null) {
            SharedPreferences pref = act.getSharedPreferences(this.getClass().getSimpleName(), Context.MODE_PRIVATE);
            String s = pref.getString(Const.EMAIL, "");
            if (s.length() > 0) {
                cbRemEmail.setChecked(true);
                etEmail.setText(s);
                s = pref.getString(Const.PASSWORD, "");
                if (s.length() > 0) {
                    cbRemPassword.setChecked(true);
                    etPassword.setText(uncriptPassword(s));
                }
            }
            loginList();
        } else {
            act.setCurFragment(this);
            mode_list = state.getByte(Const.PANEL);
            if (mode_list > CabModel.LOGIN) {
                pMain.setVisibility(View.GONE);
                fabEnter.setVisibility(View.GONE);
                fabExit.setVisibility(View.VISIBLE);
            }
            String d;
            for (String t : state.getStringArray(Const.LINK)) {
                if (t.contains("#")) {
                    d = t.substring(t.indexOf("#") + 1);
                    t = t.substring(0, t.indexOf("#"));
                    adMain.addItem(new ListItem(t), d);
                } else
                    adMain.addItem(new ListItem(t));
            }
        }
        adMain.notifyDataSetChanged();
        softKeyboard.closeSoftKeyboard();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putByte(Const.PANEL, mode_list);
        String[] m = new String[adMain.getCount()];
        String d;
        for (int i = 0; i < adMain.getCount(); i++) {
            d = adMain.getItem(i).getDes();
            m[i] = adMain.getItem(i).getTitle() + (d == null ? "" : "#" + d);
        }
        outState.putStringArray(Const.LINK, m);
        super.onSaveInstanceState(outState);
    }

    private void initViews() {
        act.setTitle(getResources().getString(R.string.cabinet));
        pMain = container.findViewById(R.id.pMain);
        fabEnter = container.findViewById(R.id.fabEnter);
        fabExit = container.findViewById(R.id.fabExit);
        tvError = (TextView) container.findViewById(R.id.tvError);
        etEmail = (EditText) container.findViewById(R.id.etEmail);
        etPassword = (EditText) container.findViewById(R.id.etPassword);
        cbRemEmail = (CheckBox) container.findViewById(R.id.cbRemEmail);
        cbRemPassword = (CheckBox) container.findViewById(R.id.cbRemPassword);
        InputMethodManager im = (InputMethodManager) act.getSystemService(Service.INPUT_METHOD_SERVICE);
        LinearLayout mainLayout = (LinearLayout) container.findViewById(R.id.content_cabmain);
        softKeyboard = new SoftKeyboard(mainLayout, im);
        ListView lvList = (ListView) container.findViewById(R.id.lvList);
        adMain = new ListAdapter(act);
        lvList.setAdapter(adMain);
        lvList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int pos, long l) {
                if (act.checkBusy()) return;
                if (mode_list == CabModel.LOGIN) {
                    String s;
                    switch (pos) {
                        case 0: //восстановить доступ
                            s = "sendpass.html";
                            break;
                        case 1: //зарегистрироваться
                            s = "register.html";
                            break;
                        case 2: //о регистрации
                            s = "reginfo.html";
                            break;
                        case 3: //стастистика регистраций
                            s = "regstat.html";
                            break;
                        default: //(4) стастистика слов
                            s = "trans.html";
                            break;
                    }
                    CabpageActivity.openPage(act, s);
                } else if (mode_list == CabModel.CABINET) {
                    switch (pos) {
                        case 0: //передача ощущений
                            if (adMain.getItem(pos).getDes().equals(
                                    getResources().getString(R.string.select_status))) {
                                initLoad();
                                act.status.startText();
                                model.getListWord();
                            } else
                                Lib.showToast(act, getResources().getString(R.string.send_unlivable));
                            break;
                        case 1: //анкета
                            CabpageActivity.openPage(act, "edinenie/anketa.html");
                            break;
                        case 2: //единомышленники
                            CabpageActivity.openPage(act, "edinenie/edinomyshlenniki.html");
                            break;
                        default:
                            break;
                    }
                } else if (mode_list == CabModel.WORDS) {
                    initLoad();
                    act.status.startText();
                    model.selectWord(pos);
                }
            }
        });
    }

    private void initLoad() {
        ProgressHelper.addObserver(act, this);
        act.status.setLoad(true);
    }

    private void loginList() {
        tvError.setVisibility(View.GONE);
        for (int i = 0; i < getResources().getStringArray(R.array.cabinet_main).length; i += 2) {
            adMain.addItem(new ListItem(getResources().getStringArray(R.array.cabinet_main)[i]),
                    getResources().getStringArray(R.array.cabinet_main)[i + 1]);
        }
        adMain.notifyDataSetChanged();
    }

    private void setViews() {
        TextWatcher textWatcher = new TextWatcher() {
            public void afterTextChanged(Editable s) {
                boolean ready = false;
                if (etEmail.length() > 5 && etPassword.length() > 5) {
                    ready = (etEmail.getText().toString().contains("@")
                            && etEmail.getText().toString().contains("."));
                }
                if (ready) { //ready to login
                    fabEnter.setVisibility(View.VISIBLE);
                } else {
                    fabEnter.setVisibility(View.GONE);
                }
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        };
        etEmail.addTextChangedListener(textWatcher);
        etPassword.addTextChangedListener(textWatcher);

        etPassword.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View view, int keyCode, KeyEvent keyEvent) {
                if ((keyEvent.getAction() == KeyEvent.ACTION_DOWN && keyEvent.getKeyCode() == KeyEvent.KEYCODE_ENTER)
                        || keyCode == EditorInfo.IME_ACTION_GO) {
                    if (fabEnter.getVisibility() == View.VISIBLE)
                        subLogin();
                    return true;
                }
                return false;
            }
        });
        cbRemEmail.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean check) {
                cbRemPassword.setEnabled(check);
                if (!check)
                    cbRemPassword.setChecked(false);
            }
        });
        fabEnter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                subLogin();
            }
        });
        fabExit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mode_list = CabModel.LOGIN;
                CabModel.cookie = null;
                CabModel.email = "";
                adMain.clear();
                loginList();
                fabEnter.setVisibility(View.VISIBLE);
                fabExit.setVisibility(View.GONE);
                pMain.setVisibility(View.VISIBLE);
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (!cbRemPassword.isChecked()) {
            SharedPreferences pref = act.getSharedPreferences(this.getClass().getSimpleName(), Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = pref.edit();
            editor.putString(Const.PASSWORD, "");
            if (!cbRemEmail.isChecked()) {
                editor.putString(Const.EMAIL, "");
            }
            editor.commit();
        }
    }

    private String criptPassword(String password) {
        char[] c = password.toCharArray();
        password = "";
        for (int a = 0; a < c.length; a++) {
            password += Character.toString((char) ((Character.codePointAt(c, a) - a - 1)));
        }
        return password;
    }

    private String uncriptPassword(String password) {
        try {
            char[] c = password.toCharArray();
            password = "";
            for (int a = 0; a < c.length; a++) {
                password += Character.toString((char) ((Character.codePointAt(c, a) + a + 1)));
            }
            return password;
        } catch (Exception e) {
            return "";
        }
    }

    private void subLogin() {
        softKeyboard.closeSoftKeyboard();
        if (ProgressHelper.isBusy())
            return;
        if (adMain.getCount() == 1) {
            adMain.clear();
            loginList();
        }
        if (cbRemEmail.isChecked()) {
            SharedPreferences pref = act.getSharedPreferences(this.getClass().getSimpleName(), Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = pref.edit();
            editor.putString(Const.EMAIL, etEmail.getText().toString());
            if (cbRemPassword.isChecked()) {
                editor.putString(Const.PASSWORD, criptPassword(etPassword.getText().toString()));
            }
            editor.commit();
        }
        initLoad();
        act.status.startText();
        model.login(etEmail.getText().toString(), etPassword.getText().toString());
    }

    private void initWordList(String[] words) {
        mode_list = CabModel.WORDS;
        adMain.clear();
        for (int i = 0; i < words.length; i++) {
            adMain.addItem(new ListItem(words[i]));
        }
        adMain.notifyDataSetChanged();
    }

    private void initCabinet(String title, String des) {
        mode_list = CabModel.CABINET;
        adMain.clear();
        adMain.addItem(new ListItem(title));
        adMain.getItem(0).setDes(des);
        for (int i = 0; i < getResources().getStringArray(R.array.cabinet_enter).length; i++) {
            adMain.addItem(new ListItem(getResources().getStringArray(R.array.cabinet_enter)[i]));
        }
        adMain.getItem(adMain.getCount() - 1).setDes(getResources().getString(R.string.cabinet_tip));
        adMain.notifyDataSetChanged();
    }
}
