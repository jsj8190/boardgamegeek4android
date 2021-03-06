package com.boardgamegeek.ui;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBar;
import android.text.TextUtils;
import android.view.MenuItem;

import com.boardgamegeek.provider.BggContract.Games;
import com.crashlytics.android.answers.Answers;
import com.crashlytics.android.answers.ContentViewEvent;

public class GameForumsActivity extends SimpleSinglePaneActivity {
	private static final String KEY_GAME_NAME = "GAME_NAME";

	private int gameId;
	private String gameName;

	public static void start(Context context, Uri gameUri, String gameName) {
		Intent starter = createIntent(context, gameUri, gameName);
		context.startActivity(starter);
	}

	public static void startUp(Context context, Uri gameUri, String gameName) {
		Intent starter = createIntent(context, gameUri, gameName);
		starter.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		context.startActivity(starter);
	}

	@NonNull
	private static Intent createIntent(Context context, Uri gameUri, String gameName) {
		Intent intent = new Intent(context, GameForumsActivity.class);
		intent.setData(gameUri);
		intent.putExtra(KEY_GAME_NAME, gameName);
		return intent;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (!TextUtils.isEmpty(gameName)) {
			ActionBar actionBar = getSupportActionBar();
			if (actionBar != null) {
				actionBar.setSubtitle(gameName);
			}
		}

		if (savedInstanceState == null) {
			Answers.getInstance().logContentView(new ContentViewEvent()
				.putContentType("GameForums")
				.putContentId(String.valueOf(gameId))
				.putContentName(gameName));
		}
	}

	@Override
	protected void readIntent(Intent intent) {
		gameId = Games.getGameId(intent.getData());
		gameName = intent.getStringExtra(KEY_GAME_NAME);
	}

	@Override
	protected Fragment onCreatePane(Intent intent) {
		return ForumsFragment.newInstance(gameId, gameName);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				GameActivity.startUp(this, gameId, gameName);
				finish();
				return true;
		}
		return super.onOptionsItemSelected(item);
	}
}
