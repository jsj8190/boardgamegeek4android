package com.boardgamegeek.ui;

import java.util.ArrayList;
import java.util.List;

import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.View;
import android.widget.ListView;

import com.boardgamegeek.R;
import com.boardgamegeek.model.ForumThread;
import com.boardgamegeek.util.ForumsUtils;
import com.boardgamegeek.util.HttpUtils;
import com.boardgamegeek.util.UIUtils;

public class GeneralForumActivity extends ListActivity {
	private final String TAG = "ForumActivity";

	public static final String KEY_FORUM_ID = "FORUM_ID";
	public static final String KEY_GAME_NAME = "GAME_NAME";
	public static final String KEY_THUMBNAIL_URL = "THUMBNAIL_URL";
	public static final String KEY_FORUM_TITLE = "FORUM_NAME";
	public static final String KEY_NUM_THREADS = "NUM_THREADS";
	public static final String KEY_THREADS = "THREADS";

	private List<ForumThread> mForumThreads = new ArrayList<ForumThread>();

	private String mForumId;
	private String mForumName;
	private String mNumThreads;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_forum);
		UIUtils.setTitle(this, mForumName);
		findViewById(R.id.game_thumbnail).setClickable(false);
		findViewById(R.id.forum_game_header).setVisibility(View.GONE);
		findViewById(R.id.forum_header_divider).setVisibility(View.GONE);

		if (savedInstanceState == null) {
			final Intent intent = getIntent();
			mForumId = intent.getExtras().getString(KEY_FORUM_ID);
			mForumName = intent.getExtras().getString(KEY_FORUM_TITLE);
			mNumThreads = intent.getExtras().getString(KEY_NUM_THREADS);
		} else {
			mForumId = savedInstanceState.getString(KEY_FORUM_ID);
			mForumName = savedInstanceState.getString(KEY_FORUM_TITLE);
			mNumThreads = savedInstanceState.getString(KEY_NUM_THREADS);
			mForumThreads = savedInstanceState.getParcelableArrayList(KEY_THREADS);
		}

		if (mForumThreads == null || mForumThreads.size() == 0) {
			ForumsUtils.ForumTask task =
				new ForumsUtils.ForumTask(this, mForumThreads, HttpUtils.constructForumUrl(mForumId), this.getTitle().toString(), TAG);
			task.execute();
		} else {
			setListAdapter(new ForumsUtils.ForumAdapter(this, mForumThreads));
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putString(KEY_FORUM_ID, mForumId);
		outState.putString(KEY_FORUM_TITLE, mForumName);
		outState.putString(KEY_NUM_THREADS, mNumThreads);
		outState.putParcelableArrayList(KEY_THREADS, (ArrayList<? extends Parcelable>) mForumThreads);
	}

	public void onHomeClick(View v) {
		UIUtils.goHome(this);
	}

	public void onSearchClick(View v) {
		onSearchRequested();
	}

	@Override
	protected void onListItemClick(ListView listView, View convertView, int position, long id) {
		ForumsUtils.ForumViewHolder holder = (ForumsUtils.ForumViewHolder) convertView.getTag();
		if (holder != null) {
			Intent forumsIntent = new Intent(this, GeneralThreadActivity.class);
			forumsIntent.putExtra(ThreadActivity.KEY_THREAD_ID, holder.threadId);
			forumsIntent.putExtra(ThreadActivity.KEY_THREAD_SUBJECT, holder.subject.getText());
			this.startActivity(forumsIntent);
		}
	}
}
