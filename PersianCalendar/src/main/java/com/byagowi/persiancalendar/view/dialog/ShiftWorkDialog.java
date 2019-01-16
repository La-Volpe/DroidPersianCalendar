package com.byagowi.persiancalendar.view.dialog;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;

import com.byagowi.persiancalendar.R;
import com.byagowi.persiancalendar.databinding.ShiftWorkItemBinding;
import com.byagowi.persiancalendar.databinding.ShiftWorkSettingsBinding;
import com.byagowi.persiancalendar.di.dependencies.AppDependency;
import com.byagowi.persiancalendar.di.dependencies.CalendarFragmentDependency;
import com.byagowi.persiancalendar.di.dependencies.MainActivityDependency;
import com.byagowi.persiancalendar.entity.FormattedIntEntity;
import com.byagowi.persiancalendar.entity.ShiftWorkRecord;
import com.byagowi.persiancalendar.util.CalendarUtils;
import com.byagowi.persiancalendar.util.Utils;
import com.byagowi.persiancalendar.view.activity.MainActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import dagger.android.support.DaggerAppCompatDialogFragment;

import static com.byagowi.persiancalendar.Constants.PREF_SHIFT_WORK_RECURS;
import static com.byagowi.persiancalendar.Constants.PREF_SHIFT_WORK_SETTING;
import static com.byagowi.persiancalendar.Constants.PREF_SHIFT_WORK_STARTING_JDN;

public class ShiftWorkDialog extends DaggerAppCompatDialogFragment {
    private static String BUNDLE_KEY = "jdn";
    @Inject
    AppDependency appDependency;
    @Inject
    MainActivityDependency mainActivityDependency;
    @Inject
    CalendarFragmentDependency calendarFragmentDependency;

    public static ShiftWorkDialog newInstance(long jdn) {
        Bundle args = new Bundle();
        args.putLong(BUNDLE_KEY, jdn);

        ShiftWorkDialog fragment = new ShiftWorkDialog();
        fragment.setArguments(args);
        return fragment;
    }

    private long jdn = -1;
    private long selectedJdn = -1;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Bundle args = getArguments();
        MainActivity mainActivity = mainActivityDependency.getMainActivity();

        Utils.applyAppLanguage(mainActivity);
        Utils.updateStoredPreference(mainActivity);

        selectedJdn = args == null ? -1 : args.getLong(BUNDLE_KEY, -1);
        if (selectedJdn == -1) selectedJdn = CalendarUtils.getTodayJdn();

        jdn = Utils.getShiftWorkStartingJdn();
        if (jdn == -1) jdn = selectedJdn;

        Context context = getContext();
        ShiftWorkSettingsBinding binding = ShiftWorkSettingsBinding.inflate(
                LayoutInflater.from(getContext()), null, false);
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(context));

        List<ShiftWorkRecord> shiftWorks = Utils.getShiftWorks();
        if (shiftWorks.size() == 0)
            shiftWorks = Collections.singletonList(new ShiftWorkRecord("d", 0));
        ShiftWorkItemAdapter shiftWorkItemAdapter = new ShiftWorkItemAdapter(shiftWorks, binding);
        binding.recyclerView.setAdapter(shiftWorkItemAdapter);

        binding.description.setText(String.format(getString(R.string.shift_work_starting_date),
                CalendarUtils.formatDate(
                        CalendarUtils.getDateFromJdnOfCalendar(Utils.getMainCalendar(), jdn))));

        binding.resetLink.setOnClickListener(v -> {
            jdn = selectedJdn;
            binding.description.setText(String.format(getString(R.string.shift_work_starting_date),
                    CalendarUtils.formatDate(
                            CalendarUtils.getDateFromJdnOfCalendar(Utils.getMainCalendar(), jdn))));
            shiftWorkItemAdapter.reset();
        });
        binding.recurs.setChecked(Utils.getShiftWorkRecurs());

        return new AlertDialog.Builder(mainActivity)
                .setView(binding.getRoot())
                .setTitle(null)
                .setPositiveButton(R.string.accept, (dialogInterface, i) -> {
                    StringBuilder result = new StringBuilder();
                    boolean first = true;
                    for (ShiftWorkRecord record : shiftWorkItemAdapter.getRows()) {
                        if (record.length == 0) continue;

                        if (first) first = false;
                        else result.append(",");
                        result.append(record.type);
                        result.append("=");
                        result.append(record.length);
                    }

                    SharedPreferences.Editor edit = appDependency.getSharedPreferences().edit();
                    edit.putLong(PREF_SHIFT_WORK_STARTING_JDN, result.length() == 0 ? -1 : jdn);
                    edit.putString(PREF_SHIFT_WORK_SETTING, result.toString());
                    edit.putBoolean(PREF_SHIFT_WORK_RECURS, binding.recurs.isChecked());
                    edit.apply();

                    calendarFragmentDependency.getCalendarFragment().afterShiftWorkChange();
                    mainActivity.restartActivity();
                })
                .setCancelable(true)
                .setNegativeButton(R.string.cancel, null)
                .create();
    }

    private class ShiftWorkItemAdapter extends RecyclerView.Adapter<ShiftWorkDialog.ShiftWorkItemAdapter.ViewHolder> {
        List<String> mShiftWorkKeys;
        private List<ShiftWorkRecord> mRows = new ArrayList<>();
        final private ShiftWorkSettingsBinding mBinding;

        ShiftWorkItemAdapter(List<ShiftWorkRecord> initialItems, ShiftWorkSettingsBinding binding) {
            mRows.addAll(initialItems);
            mShiftWorkKeys = Arrays.asList(getResources().getStringArray(R.array.shift_work_keys));
            mBinding = binding;
            updateShiftWorkResult();
        }

        List<ShiftWorkRecord> getRows() {
            return mRows;
        }


        private void updateShiftWorkResult() {
            StringBuilder result = new StringBuilder();
            boolean first = true;
            for (ShiftWorkRecord record : mRows) {
                if (record.length == 0) continue;

                if (first) first = false;
                else result.append(Utils.getSpacedComma());
                result.append(String.format(getString(R.string.shift_work_record_title),
                        Utils.formatNumber(record.length), Utils.getShiftWorkTitles().get(record.type)));
            }

            mBinding.result.setText(result.toString());
            mBinding.result.setVisibility(result.length() == 0 ? View.GONE : View.VISIBLE);
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ShiftWorkItemBinding binding = ShiftWorkItemBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);

            return new ViewHolder(binding);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.bind(position);
        }

        @Override
        public int getItemCount() {
            return mRows.size() + 1;
        }

        void reset() {
            mRows.clear();
            mRows.add(new ShiftWorkRecord("d", 0));
            notifyDataSetChanged();
            updateShiftWorkResult();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ShiftWorkItemBinding mBinding;
            int mPosition;

            ViewHolder(@NonNull ShiftWorkItemBinding binding) {
                super(binding.getRoot());
                mBinding = binding;
                Context context = binding.getRoot().getContext();

                List<FormattedIntEntity> days = new ArrayList<>();
                for (int i = 0; i <= 7; ++i) {
                    days.add(new FormattedIntEntity(i, Utils.formatNumber(i)));
                }
                binding.lengthSpinner.setAdapter(new ArrayAdapter<>(context,
                        android.R.layout.simple_spinner_dropdown_item, days));

                binding.typeSpinner.setAdapter(new ArrayAdapter<>(context,
                        android.R.layout.simple_spinner_dropdown_item,
                        getResources().getStringArray(R.array.shift_work)));

                binding.remove.setOnClickListener(v -> {
                    mRows.remove(mPosition);
                    notifyDataSetChanged();
                    updateShiftWorkResult();
                });

                binding.lengthSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        mRows.set(mPosition, new ShiftWorkRecord(
                                mRows.get(mPosition).type, position));
                        updateShiftWorkResult();
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                    }
                });

                binding.typeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        mRows.set(mPosition, new ShiftWorkRecord(
                                mShiftWorkKeys.get(position), mRows.get(mPosition).length));
                        updateShiftWorkResult();
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                    }
                });

                binding.addButton.setOnClickListener((v -> {
                    mRows.add(new ShiftWorkRecord("r", 0));
                    notifyDataSetChanged();
                    updateShiftWorkResult();
                }));
            }

            public void bind(int position) {
                if (position < mRows.size()) {
                    ShiftWorkRecord shiftWorkRecord = mRows.get(position);
                    mPosition = position;
                    mBinding.rowNumber.setText(String.format("%s:", Utils.formatNumber(position + 1)));
                    mBinding.lengthSpinner.setSelection(shiftWorkRecord.length);
                    mBinding.typeSpinner.setSelection(mShiftWorkKeys.indexOf(shiftWorkRecord.type));
                    mBinding.detail.setVisibility(View.VISIBLE);
                    mBinding.addButton.setVisibility(View.GONE);
                } else {
                    mBinding.detail.setVisibility(View.GONE);
                    mBinding.addButton.setVisibility(mRows.size() < 20 ? View.VISIBLE : View.GONE);
                }
            }
        }
    }
}
