package com.boardgamegeek.ui;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.client.HttpClient;

import android.app.ListActivity;
import android.app.SearchManager;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.boardgamegeek.BggApplication;
import com.boardgamegeek.R;
import com.boardgamegeek.Utility;
import com.boardgamegeek.io.RemoteExecutor;
import com.boardgamegeek.io.RemoteSearchHandler;
import com.boardgamegeek.io.SearchResult;
import com.boardgamegeek.io.XmlHandler.HandlerException;
import com.boardgamegeek.provider.BggContract.Games;
import com.boardgamegeek.util.HttpUtils;
import com.boardgamegeek.util.UIUtils;

public class SearchResultsActivity extends ListActivity {
	private final String TAG = "SearchResultsActivity";

	private List<SearchResult> mSearchResults = new ArrayList<SearchResult>();
	private BoardGameAdapter mAdapter;
	private TextView mSearchTextView;
	private String mSearchText;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_searchresults);

		UIUtils.setTitle(this);
		UIUtils.allowTypeToSearch(this);

		mAdapter = new BoardGameAdapter();
		mSearchTextView = (TextView) findViewById(R.id.search_text);

		parseIntent(getIntent());
	}

	@Override
	public void onNewIntent(Intent intent) {
		parseIntent(intent);
	}

	@Override
	public void setTitle(CharSequence title) {
		UIUtils.setTitle(this, title);
	}

	public void onHomeClick(View v) {
		UIUtils.resetToHome(this);
	}

	public void onSearchClick(View v) {
		onSearchRequested();
	}

	public void onListItemClick(ListView l, View v, int position, long id) {
		SearchResult game = (SearchResult) mAdapter.getItem(position);
		viewBoardGame(game.Id);
	}

	private void parseIntent(Intent intent) {
		if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
			mSearchText = intent.getExtras().getString(SearchManager.QUERY);
			if (TextUtils.isEmpty(mSearchText)) {
				showError("Search performed with no search text");
			} else {
				mSearchResults.clear();
				setListAdapter(null);
				String message = String.format(getResources().getString(R.string.search_searching), mSearchText);
				mSearchTextView.setText(message);
				UIUtils.showListMessage(this, R.string.search_message, false);
				SearchTask task = new SearchTask();
				task.execute();
			}
		} else if (Intent.ACTION_VIEW.equals(intent.getAction())) {
			String data = intent.getDataString();
			if (TextUtils.isEmpty(data)) {
				showError("Trying to view an unspecified game.");
			} else {
				Uri uri = Uri.parse(data);
				viewBoardGame(Utility.parseInt(uri.getLastPathSegment(), 0));
				finish();
			}
		} else {
			showError("Received bad intent action: " + intent.getAction());
		}
	}

	private void showError(String message) {
		Log.w(TAG, message);
		Toast.makeText(this, message, Toast.LENGTH_LONG).show();
		UIUtils.showListMessage(this, "Error");
	}

	private void viewBoardGame(int gameId) {
		final Uri gameUri = Games.buildGameUri(gameId);
		startActivity(new Intent(Intent.ACTION_VIEW, gameUri));
	}

	private class SearchTask extends AsyncTask<Void, Void, RemoteSearchHandler> {

		private HttpClient mHttpClient;
		private RemoteExecutor mExecutor;
		private RemoteSearchHandler mHandler = new RemoteSearchHandler();

		@Override
		protected void onPreExecute() {
			mSearchResults.clear();
			mHttpClient = HttpUtils.createHttpClient(SearchResultsActivity.this, true);
			mExecutor = new RemoteExecutor(mHttpClient, null);
		}

		@Override
		protected RemoteSearchHandler doInBackground(Void... params) {
			if (BggApplication.getInstance().getExactSearch()) {
				executeGet(true);
				if (mHandler.isBggDown() || mHandler.getCount() > 0) {
					return mHandler;
				}
			}
			executeGet(false);
			return mHandler;
		}

		@Override
		protected void onPostExecute(RemoteSearchHandler result) {
			int count = result.getCount();
			if (result.isBggDown()) {
				UIUtils.showListMessage(SearchResultsActivity.this, R.string.bgg_down);
			} else if (count == 0) {
				String message = String.format(getResources().getString(R.string.search_no_results), mSearchText);
				mSearchTextView.setText(message);
				UIUtils.showListMessage(SearchResultsActivity.this, R.string.search_no_results_details);
			} else if (count == 1) {
				if (BggApplication.getInstance().getSkipResults()) {
					viewBoardGame(result.getResults().get(0).Id);
					finish();
				}
			} else {
				mSearchResults = result.getResults();
				String message = String.format(getResources().getString(R.string.search_results),
						mSearchResults.size(), mSearchText);
				mSearchTextView.setText(message);
				mAdapter = new BoardGameAdapter();
				setListAdapter(mAdapter);
			}
		}

		private void executeGet(boolean useExact) {
			String url = constructUrl(useExact);
			try {
				mExecutor.executeGet(url, mHandler);
			} catch (HandlerException e) {
				Log.e(TAG, e.toString());
			}
		}

		private String constructUrl(boolean useExact) {
			// http://boardgamegeek.com/xmlapi2/search?query=puerto+rico
			String queryUrl = Utility.siteUrl + "xmlapi/search?search=" + URLEncoder.encode(mSearchText);
			if (useExact) {
				queryUrl += "&exact=1";
			}
			Log.d(TAG, "Query: " + queryUrl);
			return queryUrl;
		}
	}

	class BoardGameAdapter extends ArrayAdapter<SearchResult> {
		private LayoutInflater mInflater;

		BoardGameAdapter() {
			super(SearchResultsActivity.this, R.layout.row_search, mSearchResults);
			mInflater = getLayoutInflater();
		}

		public View getView(int position, View convertView, ViewGroup parent) {
			ViewHolder holder;
			if (convertView == null) {
				convertView = mInflater.inflate(R.layout.row_search, parent, false);
				holder = new ViewHolder(convertView);
				convertView.setTag(holder);
			} else {
				holder = (ViewHolder) convertView.getTag();
			}

			SearchResult game = mSearchResults.get(position);
			if (game != null) {
				holder.name.setText(game.Name);
				if (!game.IsNamePrimary) {
					holder.name.setTypeface(holder.name.getTypeface(), 2);
				}
				if (game.YearPublished > 0) {
					holder.year.setText("" + game.YearPublished);
				}
				holder.gameId.setText(String.format(getResources().getString(R.string.id_list_text), game.Id));
			}

			return convertView;
		}
	}

	static class ViewHolder {
		TextView name;
		TextView year;
		TextView gameId;

		public ViewHolder(View view) {
			name = (TextView) view.findViewById(R.id.name);
			year = (TextView) view.findViewById(R.id.year);
			gameId = (TextView) view.findViewById(R.id.gameId);
		}
	}
}
