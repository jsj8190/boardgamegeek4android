package com.boardgamegeek.service;

import android.accounts.Account;
import android.content.Context;
import android.content.SyncResult;
import android.database.Cursor;
import android.support.annotation.NonNull;
import android.support.v4.util.ArrayMap;

import com.boardgamegeek.R;
import com.boardgamegeek.io.BggService;
import com.boardgamegeek.io.CollectionRequest;
import com.boardgamegeek.io.CollectionResponse;
import com.boardgamegeek.model.persister.CollectionPersister;
import com.boardgamegeek.provider.BggContract.Collection;
import com.boardgamegeek.service.model.GameList;

import timber.log.Timber;

/**
 * Syncs collection items that have not yet been updated completely with stats and private info (in batches of 25).
 */
public class SyncCollectionUnupdated extends SyncTask {
	private static final int GAME_PER_FETCH = 25;

	public SyncCollectionUnupdated(Context context, BggService service) {
		super(context, service);
	}

	@Override
	public int getSyncType() {
		return SyncService.FLAG_SYNC_COLLECTION_DOWNLOAD;
	}

	@Override
	public void execute(@NonNull Account account, @NonNull SyncResult syncResult) {
		Timber.i("Syncing unupdated collection list...");
		try {
			int numberOfFetches = 0;
			CollectionPersister persister = new CollectionPersister.Builder(context)
				.includePrivateInfo()
				.includeStats()
				.build();
			ArrayMap<String, String> options = new ArrayMap<>();
			options.put(BggService.COLLECTION_QUERY_KEY_SHOW_PRIVATE, "1");
			options.put(BggService.COLLECTION_QUERY_KEY_STATS, "1");

			do {
				if (isCancelled()) break;

				numberOfFetches++;
				GameList gameIds = queryGames();
				if (gameIds.getSize() > 0) {
					String detail = context.getString(R.string.sync_notification_collection_update_games, gameIds.getSize(), gameIds.getDescription());
					if (numberOfFetches > 1) {
						detail = context.getString(R.string.sync_notification_page_suffix, detail, numberOfFetches);
					}
					updateProgressNotification(detail);
					Timber.i("...found %,d games to update [%s]", gameIds.getSize(), gameIds.getDescription());

					options.put(BggService.COLLECTION_QUERY_KEY_ID, gameIds.getIds());
					options.remove(BggService.COLLECTION_QUERY_KEY_SUBTYPE);
					boolean success = requestAndPersist(account.name, persister, options, syncResult);

					if (!success) {
						Timber.i("...unsuccessful sync; breaking out of fetch loop");
						break;
					}

					options.put(BggService.COLLECTION_QUERY_KEY_SUBTYPE, BggService.THING_SUBTYPE_BOARDGAME_ACCESSORY);
					success = requestAndPersist(account.name, persister, options, syncResult);

					if (!success) {
						Timber.i("...unsuccessful sync; breaking out of fetch loop");
						break;
					}
				} else {
					Timber.i("...no more unupdated collection items");
					break;
				}
			} while (numberOfFetches < 100);
		} finally {
			Timber.i("...complete!");
		}
	}

	public GameList queryGames() {
		GameList list = new GameList(GAME_PER_FETCH);
		Cursor cursor = context.getContentResolver().query(Collection.CONTENT_URI,
			new String[] { Collection.GAME_ID, Collection.GAME_NAME },
			"(collection." + Collection.UPDATED + "=0 OR collection." + Collection.UPDATED + " IS NULL) AND " + Collection.COLLECTION_ID + " IS NOT NULL",
			null,
			"collection." + Collection.UPDATED_LIST + " DESC LIMIT " + GAME_PER_FETCH);
		try {
			while (cursor != null && cursor.moveToNext()) {
				list.addGame(cursor.getInt(0), cursor.getString(1));
			}
		} finally {
			if (cursor != null) cursor.close();
		}
		return list;
	}


	private boolean requestAndPersist(String username, @NonNull CollectionPersister persister, ArrayMap<String, String> options, @NonNull SyncResult syncResult) {
		Timber.i("..requesting collection items with options %s", options);
		CollectionResponse response = new CollectionRequest(service, username, options).execute();
		if (response.hasError()) {
			showError(response.getError());
			return false;
		} else if (response.getNumberOfItems() > 0) {
			int count = persister.save(response.getItems()).getRecordCount();
			syncResult.stats.numUpdates += response.getNumberOfItems();
			Timber.i("...saved %,d records for %,d collection items", count, response.getNumberOfItems());
			return true;
		} else {
			Timber.i("...no collection items found for these games");
			return true;
		}
	}

	@Override
	public int getNotificationSummaryMessageId() {
		return R.string.sync_notification_collection_unupdated;
	}
}
