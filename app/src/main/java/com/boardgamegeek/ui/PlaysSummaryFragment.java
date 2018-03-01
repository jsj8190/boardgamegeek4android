package com.boardgamegeek.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v4.widget.SwipeRefreshLayout.OnRefreshListener;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.TextView;

import com.boardgamegeek.R;
import com.boardgamegeek.auth.AccountUtils;
import com.boardgamegeek.events.PlaySelectedEvent;
import com.boardgamegeek.events.SyncCompleteEvent;
import com.boardgamegeek.events.SyncEvent;
import com.boardgamegeek.pref.SyncPrefs;
import com.boardgamegeek.provider.BggContract;
import com.boardgamegeek.provider.BggContract.PlayPlayers;
import com.boardgamegeek.provider.BggContract.PlayerColors;
import com.boardgamegeek.provider.BggContract.Plays;
import com.boardgamegeek.service.SyncService;
import com.boardgamegeek.sorter.LocationsSorter;
import com.boardgamegeek.sorter.LocationsSorterFactory;
import com.boardgamegeek.sorter.PlayersSorter;
import com.boardgamegeek.sorter.PlayersSorterFactory;
import com.boardgamegeek.sorter.PlaysSorter;
import com.boardgamegeek.sorter.PlaysSorterFactory;
import com.boardgamegeek.ui.model.Location;
import com.boardgamegeek.ui.model.PlayModel;
import com.boardgamegeek.ui.model.Player;
import com.boardgamegeek.ui.model.PlayerColor;
import com.boardgamegeek.util.ColorUtils;
import com.boardgamegeek.util.PreferencesUtils;
import com.boardgamegeek.util.PresentationUtils;
import com.boardgamegeek.util.SelectionBuilder;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.Unbinder;
import hugo.weaving.DebugLog;

public class PlaysSummaryFragment extends Fragment implements LoaderCallbacks<Cursor>, OnRefreshListener, OnSharedPreferenceChangeListener {
	private static final int PLAYS_TOKEN = 1;
	private static final int PLAY_COUNT_TOKEN = 2;
	private static final int PLAYERS_TOKEN = 3;
	private static final int LOCATIONS_TOKEN = 4;
	private static final int COLORS_TOKEN = 5;
	private static final int PLAYS_IN_PROGRESS_TOKEN = 6;

	private static final int NUMBER_OF_PLAYS_SHOWN = 5;
	private static final int NUMBER_OF_PLAYERS_SHOWN = 5;
	private static final int NUMBER_OF_LOCATIONS_SHOWN = 5;

	private boolean isRefreshing;

	private Unbinder unbinder;
	@BindView(R.id.swipe_refresh) SwipeRefreshLayout swipeRefreshLayout;
	@BindView(R.id.card_sync) View syncCard;
	@BindView(R.id.card_plays) View playsCard;
	@BindView(R.id.plays_subtitle_in_progress) TextView playsInProgressSubtitle;
	@BindView(R.id.plays_in_progress_container) LinearLayout playsInProgressContainer;
	@BindView(R.id.plays_subtitle_recent) TextView recentPlaysSubtitle;
	@BindView(R.id.plays_container) LinearLayout recentPlaysContainer;
	@BindView(R.id.more_plays_button) Button morePlaysButton;
	@BindView(R.id.card_players) View playersCard;
	@BindView(R.id.players_container) LinearLayout playersContainer;
	@BindView(R.id.card_locations) View locationsCard;
	@BindView(R.id.locations_container) LinearLayout locationsContainer;
	@BindView(R.id.more_players_button) Button morePlayersButton;
	@BindView(R.id.more_locations_button) Button moreLocationsButton;
	@BindView(R.id.card_colors) View colorsCard;
	@BindView(R.id.color_container) LinearLayout colorContainer;
	@BindView(R.id.h_index) TextView hIndexView;
	@BindView(R.id.sync_status) TextView syncStatusView;

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_plays_summary, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		unbinder = ButterKnife.bind(this, view);

		swipeRefreshLayout.setOnRefreshListener(this);
		swipeRefreshLayout.setColorSchemeResources(PresentationUtils.getColorSchemeResources());

		hIndexView.setText(PresentationUtils.getText(getActivity(), R.string.game_h_index_prefix, PreferencesUtils.getGameHIndex(getActivity())));
	}

	@Override
	public void onResume() {
		super.onResume();
		bindStatusMessage();
		bindSyncCard();
		SyncPrefs.getPrefs(getContext()).registerOnSharedPreferenceChangeListener(this);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		getLoaderManager().restartLoader(PLAYS_TOKEN, null, this);
		getLoaderManager().restartLoader(PLAY_COUNT_TOKEN, null, this);
		getLoaderManager().restartLoader(PLAYERS_TOKEN, null, this);
		getLoaderManager().restartLoader(LOCATIONS_TOKEN, null, this);
		if (!TextUtils.isEmpty(AccountUtils.getUsername(getActivity()))) {
			getLoaderManager().restartLoader(COLORS_TOKEN, null, this);
		}
		getLoaderManager().restartLoader(PLAYS_IN_PROGRESS_TOKEN, null, this);
	}

	@DebugLog
	@Override
	public void onStart() {
		super.onStart();
		EventBus.getDefault().register(this);
	}

	@Override
	public void onPause() {
		super.onPause();
		SyncPrefs.getPrefs(getContext()).unregisterOnSharedPreferenceChangeListener(this);
	}

	@DebugLog
	@Override
	public void onStop() {
		super.onStop();
		EventBus.getDefault().unregister(this);
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		unbinder.unbind();
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		bindStatusMessage();
	}

	@NonNull
	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle args) {
		CursorLoader loader = new CursorLoader(getContext());
		switch (id) {
			case PLAYS_IN_PROGRESS_TOKEN:
				PlaysSorter playsSorter = PlaysSorterFactory.create(getContext(), PlayersSorterFactory.TYPE_DEFAULT);
				loader = new CursorLoader(getContext(),
					Plays.CONTENT_URI.buildUpon().appendQueryParameter(BggContract.QUERY_KEY_LIMIT, String.valueOf(NUMBER_OF_PLAYS_SHOWN)).build(),
					PlayModel.getProjection(),
					Plays.DIRTY_TIMESTAMP + ">0",
					null,
					playsSorter == null ? null : playsSorter.getOrderByClause());
				break;
			case PLAYS_TOKEN:
				playsSorter = PlaysSorterFactory.create(getContext(), PlayersSorterFactory.TYPE_DEFAULT);
				loader = new CursorLoader(getContext(),
					Plays.CONTENT_URI.buildUpon().appendQueryParameter(BggContract.QUERY_KEY_LIMIT, String.valueOf(NUMBER_OF_PLAYS_SHOWN)).build(),
					PlayModel.getProjection(),
					SelectionBuilder.whereZeroOrNull(Plays.DIRTY_TIMESTAMP) + " AND " + SelectionBuilder.whereZeroOrNull(Plays.DELETE_TIMESTAMP),
					null,
					playsSorter == null ? null : playsSorter.getOrderByClause());
				break;
			case PLAY_COUNT_TOKEN:
				loader = new CursorLoader(getContext(),
					Plays.CONTENT_SIMPLE_URI,
					new String[] { Plays.SUM_QUANTITY },
					SelectionBuilder.whereZeroOrNull(Plays.DIRTY_TIMESTAMP),
					null,
					null);
				break;
			case PLAYERS_TOKEN:
				PlayersSorter playersSorter = PlayersSorterFactory.create(getContext(), PlayersSorterFactory.TYPE_QUANTITY);
				loader = new CursorLoader(getContext(),
					Plays.buildPlayersByUniquePlayerUri()
						.buildUpon()
						.appendQueryParameter(BggContract.QUERY_KEY_LIMIT, String.valueOf(NUMBER_OF_PLAYERS_SHOWN))
						.build(),
					Player.PROJECTION,
					PlayPlayers.USER_NAME + "!=?",
					new String[] { AccountUtils.getUsername(getActivity()) },
					playersSorter.getOrderByClause());
				break;
			case LOCATIONS_TOKEN:
				LocationsSorter locationsSorter = LocationsSorterFactory.create(getContext(), LocationsSorterFactory.TYPE_QUANTITY);
				loader = new CursorLoader(getContext(),
					Plays.buildLocationsUri()
						.buildUpon()
						.appendQueryParameter(BggContract.QUERY_KEY_LIMIT, String.valueOf(NUMBER_OF_LOCATIONS_SHOWN))
						.build(),
					Location.PROJECTION,
					SelectionBuilder.whereNotNullOrEmpty(Plays.LOCATION),
					null,
					locationsSorter.getOrderByClause());
				break;
			case COLORS_TOKEN:
				String username = AccountUtils.getUsername(getActivity());
				if (!TextUtils.isEmpty(username)) {
					loader = new CursorLoader(getContext(),
						PlayerColors.buildUserUri(username),
						PlayerColor.PROJECTION,
						null, null, null);
				}
				break;
		}
		return loader;
	}

	@Override
	public void onLoadFinished(@NonNull Loader<Cursor> loader, Cursor cursor) {
		if (getActivity() == null) return;

		switch (loader.getId()) {
			case PLAYS_IN_PROGRESS_TOKEN:
				onPlaysInProgressQueryComplete(cursor);
				break;
			case PLAYS_TOKEN:
				onPlaysQueryComplete(cursor);
				break;
			case PLAY_COUNT_TOKEN:
				onPlayCountQueryComplete(cursor);
				break;
			case PLAYERS_TOKEN:
				onPlayersQueryComplete(cursor);
				break;
			case LOCATIONS_TOKEN:
				onLocationsQueryComplete(cursor);
				break;
			case COLORS_TOKEN:
				onColorsQueryComplete(cursor);
				break;
			default:
				cursor.close();
				break;
		}
	}

	private void bindStatusMessage() {
		long oldestDate = SyncPrefs.getPlaysOldestTimestamp(getContext());
		long newestDate = SyncPrefs.getPlaysNewestTimestamp(getContext());
		if (oldestDate == 0 && newestDate == 0) {
			syncStatusView.setText(R.string.plays_sync_status_none);
		} else if (oldestDate == 0) {
			syncStatusView.setText(String.format(getString(R.string.plays_sync_status_new),
				DateUtils.formatDateTime(getContext(), newestDate, DateUtils.FORMAT_SHOW_DATE)));
		} else if (newestDate == 0) {
			syncStatusView.setText(String.format(getString(R.string.plays_sync_status_old),
				DateUtils.formatDateTime(getContext(), oldestDate, DateUtils.FORMAT_SHOW_DATE)));
		} else {
			syncStatusView.setText(String.format(getString(R.string.plays_sync_status_range),
				DateUtils.formatDateTime(getContext(), oldestDate, DateUtils.FORMAT_SHOW_DATE),
				DateUtils.formatDateTime(getContext(), newestDate, DateUtils.FORMAT_SHOW_DATE)));
		}
	}

	private void bindSyncCard() {
		syncCard.setVisibility(PreferencesUtils.getSyncPlays(getContext()) ||
			PreferencesUtils.getSyncPlaysTimestamp(getContext()) > 0 ?
			View.GONE :
			View.VISIBLE);
	}

	private void onPlaysInProgressQueryComplete(Cursor cursor) {
		int numberOfPlaysInProgress = cursor == null ? 0 : cursor.getCount();
		final int visibility = numberOfPlaysInProgress == 0 ? View.GONE : View.VISIBLE;
		playsInProgressSubtitle.setVisibility(visibility);
		playsInProgressContainer.setVisibility(visibility);
		recentPlaysSubtitle.setVisibility(visibility);

		playsInProgressContainer.removeAllViews();
		if (numberOfPlaysInProgress > 0) {
			while (cursor.moveToNext()) {
				addPlayToContainer(cursor, playsInProgressContainer);
			}
			playsCard.setVisibility(View.VISIBLE);
		}
	}

	private void onPlaysQueryComplete(Cursor cursor) {
		if (cursor == null) return;

		recentPlaysContainer.removeAllViews();
		if (cursor.moveToFirst()) {
			do {
				addPlayToContainer(cursor, recentPlaysContainer);
			} while (cursor.moveToNext());
			playsCard.setVisibility(View.VISIBLE);
			recentPlaysContainer.setVisibility(View.VISIBLE);
		}
	}

	private void addPlayToContainer(Cursor cursor, LinearLayout container) {
		long internalId = cursor.getLong(cursor.getColumnIndex(Plays._ID));
		PlayModel play = PlayModel.fromCursor(cursor, getContext());
		View view = createRow(container, play.getName(), PresentationUtils.describePlayDetails(getActivity(), play.getDate(), play.getLocation(), play.getQuantity(), play.getLength(), play.getPlayerCount()));

		view.setTag(R.id.id, internalId);
		view.setTag(R.id.game_info_id, play.getGameId());
		view.setTag(R.id.game_name, play.getName());
		view.setTag(R.id.thumbnail, play.getThumbnailUrl());
		view.setTag(R.id.account_image, play.getImageUrl());

		view.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				EventBus.getDefault().postSticky(new PlaySelectedEvent(
					(long) v.getTag(R.id.id),
					(int) v.getTag(R.id.game_info_id),
					(String) v.getTag(R.id.game_name),
					(String) v.getTag(R.id.thumbnail),
					(String) v.getTag(R.id.account_image)));
			}
		});
	}

	private void onPlayCountQueryComplete(Cursor cursor) {
		morePlaysButton.setVisibility(View.VISIBLE);
		morePlaysButton.setText(R.string.more);
		if (cursor == null) return;
		if (cursor.moveToFirst()) {
			int morePlaysCount = cursor.getInt(0) - NUMBER_OF_PLAYS_SHOWN;
			if (morePlaysCount > 0) {
				morePlaysButton.setText(String.format(getString(R.string.more_suffix), morePlaysCount));
			}
		}
	}

	private void onPlayersQueryComplete(Cursor cursor) {
		if (cursor == null) return;

		playersContainer.removeAllViews();
		while (cursor.moveToNext()) {
			Player player = Player.fromCursor(cursor);

			playersCard.setVisibility(View.VISIBLE);
			View view = createRowWithPlayCount(playersContainer, PresentationUtils.describePlayer(player.getName(), player.getUsername()), player.getPlayCount());

			view.setTag(R.id.name, player.getName());
			view.setTag(R.id.username, player.getUsername());

			view.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					BuddyActivity.start(getContext(),
						(String) v.getTag(R.id.username),
						(String) v.getTag(R.id.name));
				}
			});
		}
	}

	private void onLocationsQueryComplete(Cursor cursor) {
		if (cursor == null) return;

		moreLocationsButton.setVisibility(View.VISIBLE);
		locationsContainer.removeAllViews();
		while (cursor.moveToNext()) {
			Location location = Location.fromCursor(cursor);

			locationsCard.setVisibility(View.VISIBLE);
			View view = createRowWithPlayCount(locationsContainer, location.getName(), location.getPlayCount());

			view.setTag(R.id.name, location.getName());

			view.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					LocationActivity.start(getContext(), (String) v.getTag(R.id.name));
				}
			});
		}
	}

	private View createRow(LinearLayout container, String title, String text) {
		View view = LayoutInflater.from(getContext()).inflate(R.layout.row_player_summary, container, false);
		container.addView(view);
		((TextView) view.findViewById(android.R.id.title)).setText(title);
		((TextView) view.findViewById(android.R.id.text1)).setText(text);
		return view;
	}

	private View createRowWithPlayCount(LinearLayout container, String title, int playCount) {
		return createRow(container, title, getResources().getQuantityString(R.plurals.plays_suffix, playCount, playCount));
	}

	private void onColorsQueryComplete(Cursor cursor) {
		if (cursor == null) return;

		colorContainer.removeAllViews();
		if (cursor.getCount() > 0) {
			while (cursor.moveToNext()) {
				ImageView view = createViewToBeColored();
				PlayerColor color = PlayerColor.fromCursor(cursor);
				ColorUtils.setColorViewValue(view, ColorUtils.parseColor(color.getColor()));
				colorContainer.addView(view);
			}
			colorsCard.setVisibility(View.VISIBLE);
		} else {
			colorsCard.setVisibility(View.GONE);

		}
	}

	private ImageView createViewToBeColored() {
		ImageView view = new ImageView(getActivity());
		int size = getResources().getDimensionPixelSize(R.dimen.color_circle_diameter_small);
		int margin = getResources().getDimensionPixelSize(R.dimen.color_circle_diameter_small_margin);
		LayoutParams lp = new LayoutParams(size, size);
		lp.setMargins(margin, margin, margin, margin);
		view.setLayoutParams(lp);
		return view;
	}

	@Override
	public void onLoaderReset(@NonNull Loader<Cursor> loader) {
	}

	@OnClick(R.id.sync)
	public void onSyncClick() {
		PreferencesUtils.setSyncPlays(getContext());
		SyncService.sync(getActivity(), SyncService.FLAG_SYNC_PLAYS);
		PreferencesUtils.setSyncPlaysTimestamp(getContext());
		bindSyncCard();
	}

	@OnClick(R.id.sync_cancel)
	public void onSyncCancelClick() {
		PreferencesUtils.setSyncPlaysTimestamp(getContext());
		bindSyncCard();
	}

	@OnClick(R.id.more_plays_button)
	public void onPlaysClick() {
		startActivity(new Intent(getActivity(), PlaysActivity.class));
	}

	@OnClick(R.id.more_players_button)
	public void onPlayersClick() {
		startActivity(new Intent(getActivity(), PlayersActivity.class));
	}

	@OnClick(R.id.more_locations_button)
	public void onLocationsClick() {
		startActivity(new Intent(getActivity(), LocationsActivity.class));
	}

	@OnClick(R.id.edit_colors_button)
	public void onColorsClick() {
		PlayerColorsActivity.start(getContext(), AccountUtils.getUsername(getActivity()), null);
	}

	@OnClick(R.id.more_play_stats_button)
	public void onStatsClick() {
		startActivity(new Intent(getActivity(), PlayStatsActivity.class));
	}

	@Override
	public void onRefresh() {
		if (!isRefreshing) {
			SyncService.sync(getActivity(), SyncService.FLAG_SYNC_PLAYS);
		} else {
			updateRefreshStatus(false);
		}
	}

	@SuppressWarnings("unused")
	@DebugLog
	@Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
	public void onEvent(@NonNull SyncEvent event) {
		if (((event.getType() & SyncService.FLAG_SYNC_PLAYS_DOWNLOAD) == SyncService.FLAG_SYNC_PLAYS_DOWNLOAD) ||
			((event.getType() & SyncService.FLAG_SYNC_PLAYS_UPLOAD) == SyncService.FLAG_SYNC_PLAYS_UPLOAD)) {
			updateRefreshStatus(true);
		}
	}

	@SuppressWarnings({ "unused", "UnusedParameters" })
	@DebugLog
	@Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
	public void onEvent(SyncCompleteEvent event) {
		updateRefreshStatus(false);
	}

	@DebugLog
	private void updateRefreshStatus(boolean value) {
		this.isRefreshing = value;
		if (swipeRefreshLayout != null) {
			swipeRefreshLayout.post(new Runnable() {
				@Override
				public void run() {
					if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(isRefreshing);
				}
			});
		}
	}
}
