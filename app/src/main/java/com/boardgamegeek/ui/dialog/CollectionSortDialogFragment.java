package com.boardgamegeek.ui.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RadioGroup.OnCheckedChangeListener;

import com.boardgamegeek.R;
import com.boardgamegeek.sorter.CollectionSorterFactory;
import com.boardgamegeek.util.StringUtils;

import java.util.List;

import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.InjectViews;
import hugo.weaving.DebugLog;
import timber.log.Timber;

public class CollectionSortDialogFragment extends DialogFragment implements OnCheckedChangeListener {
	public interface Listener {
		void onSortSelected(int sortType);
	}

	private ViewGroup root;
	private Listener listener;
	private int selectedType;
	@SuppressWarnings("unused") @InjectView(R.id.radio_group) RadioGroup radioGroup;
	@SuppressWarnings("unused") @InjectViews({
		R.id.menu_collection_sort_name,
		R.id.menu_collection_sort_rank,
		R.id.menu_collection_sort_geek_rating,
		R.id.menu_collection_sort_rating,
		R.id.menu_collection_sort_myrating,
		R.id.menu_collection_sort_last_viewed,
		R.id.menu_collection_sort_wishlist_priority,
		R.id.menu_collection_sort_plays,
		R.id.menu_collection_sort_published,
		R.id.menu_collection_sort_playtime,
		R.id.menu_collection_sort_age,
		R.id.menu_collection_sort_weight,
		R.id.menu_collection_sort_acquisition_date,
	}) List<RadioButton> radioButtons;

	@DebugLog
	public CollectionSortDialogFragment() {
	}

	@DebugLog
	public static CollectionSortDialogFragment newInstance(@Nullable ViewGroup root, @Nullable Listener listener) {
		final CollectionSortDialogFragment fragment = new CollectionSortDialogFragment();
		fragment.initialize(root, listener);
		return fragment;
	}

	@DebugLog
	private void initialize(@Nullable ViewGroup root, @Nullable Listener listener) {
		this.root = root;
		this.listener = listener;
	}

	@DebugLog
	@Override
	@NonNull
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		LayoutInflater layoutInflater = LayoutInflater.from(getActivity());
		View rootView = layoutInflater.inflate(R.layout.dialog_collection_sort, root, false);

		ButterKnife.inject(this, rootView);
		setChecked();
		radioGroup.setOnCheckedChangeListener(this);

		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity()).setView(rootView);
		builder.setTitle(R.string.title_sort);
		return builder.create();
	}

	@DebugLog
	@Override
	public void onCheckedChanged(RadioGroup group, int checkedId) {
		int sortType = getTypeFromView(group.findViewById(checkedId));
		Timber.d("Sort by " + sortType);
		if (listener != null) {
			listener.onSortSelected(sortType);
		}
		dismiss();
	}

	@DebugLog
	public void setSelection(int type) {
		selectedType = type;
		setChecked();
	}

	@DebugLog
	private void setChecked() {
		if (radioButtons != null) {
			for (RadioButton radioButton : radioButtons) {
				int type = getTypeFromView(radioButton);
				if (type == selectedType) {
					radioGroup.setOnCheckedChangeListener(null);
					radioButton.setChecked(true);
					radioGroup.setOnCheckedChangeListener(this);
					return;
				}
			}
		}
	}

	@DebugLog
	private int getTypeFromView(View view) {
		return StringUtils.parseInt(view.getTag().toString(), CollectionSorterFactory.TYPE_UNKNOWN);
	}
}
