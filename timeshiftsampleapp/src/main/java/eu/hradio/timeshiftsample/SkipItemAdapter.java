package eu.hradio.timeshiftsample;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.util.LongSparseArray;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.omri.radioservice.metadata.Textual;
import org.omri.radioservice.metadata.TextualDabDynamicLabel;
import org.omri.radioservice.metadata.TextualDabDynamicLabelPlusItem;
import org.omri.radioservice.metadata.TextualType;
import org.omri.radioservice.metadata.Visual;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

import eu.hradio.timeshiftplayer.SkipItem;
import eu.hradio.timeshiftplayer.TimeshiftListener;

public class SkipItemAdapter extends RecyclerView.Adapter<SkipItemAdapter.RecycleSkipViewHolder> implements TimeshiftListener {

	private final static String TAG = "SkipItemAdapter";

	private Context mContext;

	private LongSparseArray<SkipItem> mSkipSparse = new LongSparseArray<>();

	private RecyclerView mRecyclerRoot = null;

	private boolean mUserScroll = false;
	private Timer mScrollbackTimer = null;

	private List<SkipitemClickListener> mItemClickListeners = new ArrayList<>();

	private ConcurrentHashMap<View, SkipViewFillerTask> mViewtasks = new ConcurrentHashMap<>();

	SkipItemAdapter(Context appContext) {
		mContext = appContext;
	}

	private RecyclerView.OnScrollListener mScrollListener = new RecyclerView.OnScrollListener() {
		@Override
		public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
			super.onScrollStateChanged(recyclerView, newState);

			switch (newState) {
				case RecyclerView.SCROLL_STATE_IDLE:
					if(mCurItem != null && mUserScroll) {
						if (mScrollbackTimer != null) {
							mScrollbackTimer.cancel();
						}

						mScrollbackTimer = new Timer();
						mScrollbackTimer.schedule(new TimerTask() {
							@Override
							public void run() {
								if(BuildConfig.DEBUG)Log.d(TAG, "Executing scrollBackTimer");
								mUserScroll = false;
								updateList(mCurItem);
							}
						}, 5000);
					}

					break;
				case RecyclerView.SCROLL_STATE_DRAGGING:
					//if(BuildConfig.DEBUG)Log.d(TAG, "onScrollStateChanged: " + newState + " : " + "SCROLL_STATE_DRAGGING");
					break;
				case RecyclerView.SCROLL_STATE_SETTLING:
					//if(BuildConfig.DEBUG)Log.d(TAG, "onScrollStateChanged: " + newState + " : " + "SCROLL_STATE_SETTLING");
					break;
			}

		}

		@Override
		public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
			super.onScrolled(recyclerView, dx, dy);
		}
	};

	@Override
	public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
		super.onAttachedToRecyclerView(recyclerView);
		if(BuildConfig.DEBUG)Log.d(TAG, "onAttachedToRecyclerView");

		mRecyclerRoot = recyclerView;
		mRecyclerRoot.addOnScrollListener(mScrollListener);
		mRecyclerRoot.setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				//if(BuildConfig.DEBUG)Log.d(TAG, "onTouchEvent");
				mUserScroll = true;
				return false;
			}
		});
	}

	@Override
	public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
		super.onDetachedFromRecyclerView(recyclerView);
		//if(BuildConfig.DEBUG)Log.d(TAG, "onDetachedFromRecyclerView");

		recyclerView.removeOnScrollListener(mScrollListener);
		mRecyclerRoot = null;
	}

	@Override
	public void onViewDetachedFromWindow(@NonNull RecycleSkipViewHolder holder) {
		super.onViewDetachedFromWindow(holder);
		//if(BuildConfig.DEBUG)Log.d(TAG, "onViewDetachedFromWindow");
	}

	@NonNull
	@Override
	public RecycleSkipViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int viewType) {
		if(BuildConfig.DEBUG) Log.d(TAG, "onCreateViewHolder with ViewType: " + viewGroup);

		View view = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.skipitem_adapter_row_layout_con_alt, null);
		return new RecycleSkipViewHolder(view, mSkipClickListener);
	}

	@Override
	public void onBindViewHolder(@NonNull RecycleSkipViewHolder skipViewHolder, int position) {
		if(BuildConfig.DEBUG) Log.d(TAG, "onBindViewHolder at pos: " + position);

		skipViewHolder.setItemPosition(position);

		skipViewHolder.skipLoadingProgress.setVisibility(View.VISIBLE);
		skipViewHolder.skipItemSls.setVisibility(View.INVISIBLE);
		skipViewHolder.skipItemtext.setVisibility(View.INVISIBLE);
		skipViewHolder.skipItemRelPos.setVisibility(View.INVISIBLE);

		SkipItem visibleSkipItem = mSkipSparse.valueAt(position);
		boolean isCurrentItem = false;
		if(visibleSkipItem.equals(mCurItem)) {
			skipViewHolder.skipItemBaseLayout.setBackgroundResource(R.drawable.list_bg_selected);
			isCurrentItem = true;
		} else {
			skipViewHolder.skipItemBaseLayout.setBackgroundResource(R.drawable.list_bg);
		}

		if(skipViewHolder.skipViewPosition != position) {
			if(mViewtasks.get(skipViewHolder.itemView) != null) {
				if(BuildConfig.DEBUG)Log.d(TAG, "Cancelling old running task");
				mViewtasks.get(skipViewHolder.itemView).cancel(true);
				mViewtasks.remove(skipViewHolder.itemView);
			}
		}

		SkipViewFillerTask viewTask = new SkipViewFillerTask(visibleSkipItem, position, isCurrentItem);
		mViewtasks.put(skipViewHolder.itemView, viewTask);
		viewTask.execute(skipViewHolder);
	}

	@Override
	public int getItemCount() {
		if(BuildConfig.DEBUG)Log.d(TAG, "getItemCount: " + mSkipSparse.size());
		return mSkipSparse.size();
	}

	private RecycleSkipViewHolder.SkipViewOnClickListener mSkipClickListener = new RecycleSkipViewHolder.SkipViewOnClickListener() {
		@Override
		public void onSkipViewClicked(int position) {
			if(BuildConfig.DEBUG)Log.d(TAG, "onSkipViewClicked: " + position);
			SkipItem clickedItem = mSkipSparse.valueAt(position);
			if(clickedItem != null) {
				for (SkipitemClickListener listener : mItemClickListeners) {
					listener.onSkipItemClicked(clickedItem);
				}
			}
		}
	};

	private void updateList(final SkipItem curItem) {
		if(curItem != null) {
			Log.d(TAG, "NewCurItem: " + curItem.getRelativeTimepoint());

			((Activity)mContext).runOnUiThread(new Runnable() {
				@Override
				public void run() {
					if(mCurItem != null) {
						int lastItemPos = mSkipSparse.indexOfValue(mCurItem);
						mCurItem = null;
						notifyItemChanged(lastItemPos);
					}

					mCurItem = curItem;

					int itemPos = mSkipSparse.indexOfValue(mCurItem);
					notifyItemChanged(itemPos);

					if(mRecyclerRoot != null) {
						mRecyclerRoot.smoothScrollToPosition(itemPos);
					}
				}
			});
		} else {
			if(mCurItem != null) {
				int lastItemPos = mSkipSparse.indexOfValue(mCurItem);
				mCurItem = null;
				notifyItemChanged(lastItemPos);
			}
			if(mRecyclerRoot != null) {
				mRecyclerRoot.smoothScrollToPosition(0);
			}
		}
	}

	void onSeek(int newPos) {
		if(BuildConfig.DEBUG)Log.d(TAG, "SkipSeek newPos: " + newPos);
		if(mSkipSparse.size() > 0) {
			if (mSkipSparse.size() > 1) {
				int i = 0;
				for (i = 0; i < mSkipSparse.size() - 1; i++) {
					long skipPosStart = mSkipSparse.keyAt(i);
					long skipPosEnd = mSkipSparse.keyAt(i + 1);

					if (newPos < skipPosStart) {
						if (BuildConfig.DEBUG) Log.d(TAG, "SkipSeek Item zero");

						updateList(null);
						return;
					}

					if (BuildConfig.DEBUG)
						Log.d(TAG, "SkipSeek NewPos: " + newPos + ", Start: " + skipPosStart + ", End: " + skipPosEnd);
					if (newPos >= skipPosStart && newPos < skipPosEnd) {
						SkipItem curItem = mSkipSparse.valueAt(i);

						if (!curItem.equals(mCurItem)) {
							if (BuildConfig.DEBUG)
								Log.d(TAG, "SkipSeek setting SkipItem: " + (curItem.getRelativeTimepoint() / 1000));
							updateList(curItem);
						} else {
							if (BuildConfig.DEBUG) Log.d(TAG, "SkipSeek same Item in between");
						}
						return;
					}
				}

				SkipItem curItem = mSkipSparse.valueAt(i);
				if (!curItem.equals(mCurItem)) {
					if(BuildConfig.DEBUG)Log.d(TAG, "SkipSeek setting last SkipItem: " + (curItem.getRelativeTimepoint() / 1000));
					updateList(curItem);
				} else {
					if (BuildConfig.DEBUG) Log.d(TAG, "SkipSeek same Item last");
				}

				return;
			}
		}

		updateList(mSkipSparse.valueAt(0));
	}

	synchronized void clear() {
		if(BuildConfig.DEBUG)Log.d(TAG, "Clearing adapter");
		if(mNotifyTimer != null) {
			mNotifyTimer.cancel();
		}

		for(ConcurrentHashMap.Entry entry : mViewtasks.entrySet()) {
			if(BuildConfig.DEBUG)Log.d(TAG, "Cancelling remaining fillertasks");
			((SkipViewFillerTask)entry.getValue()).cancel(true);
		}
		mViewtasks.clear();

		mSkipSparse.clear();
		mCurItem = null;

		((Activity)mContext).runOnUiThread(new Runnable() {
			@Override
			public void run() {
				notifyDataSetChanged();
			}
		});
	}
	/**/
	static class RecycleSkipViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
		private final android.support.constraint.ConstraintLayout skipItemBaseLayout;
		private final ImageView skipItemSls;
		private final TextView skipItemtext;
		private final TextView skipItemRelPos;
		private final ProgressBar skipLoadingProgress;
		private final SkipViewOnClickListener mClickListener;
		private int skipViewPosition;

		private RecycleSkipViewHolder(View skipView, SkipViewOnClickListener listener) {
			super(skipView);

			mClickListener = listener;

			skipItemBaseLayout = skipView.findViewById(R.id.skipitem_base_layout);
			skipLoadingProgress = skipView.findViewById(R.id.skipitem_adapter_loadprogress);
			skipItemSls = skipView.findViewById(R.id.skipitem_adapter_icon);
			skipItemtext = skipView.findViewById(R.id.skipitem_adapter_text);
			skipItemRelPos = skipView.findViewById(R.id.skipitem_adapter_reltimepos);

			skipView.setOnClickListener(this);
		}

		void setItemPosition(int pos) {
			skipViewPosition = pos;
		}

		@Override
		public void onClick(View v) {
			if(BuildConfig.DEBUG)Log.d(TAG, "Item clicked at pos: " + skipViewPosition);
			if(mClickListener != null) {
				mClickListener.onSkipViewClicked(skipViewPosition);
			}
		}

		public static interface SkipViewOnClickListener {

			void onSkipViewClicked(int position);
		}
	}

	/**/
	private SkipItem mCurItem = null;
	@Override
	public void progress(long curPos, long totalDuration) {
		Log.d(TAG, "progress: " + curPos);
		final SkipItem curItem = mSkipSparse.get(curPos);
		if(BuildConfig.DEBUG) {
			if(curItem != null) {
				Log.d(TAG, "NewCurItem: " + curItem.getRelativeTimepoint());

				((Activity)mContext).runOnUiThread(new Runnable() {
					@Override
					public void run() {
						if(mCurItem != null) {
							int lastItemPos = mSkipSparse.indexOfValue(mCurItem);
							mCurItem = null;
							notifyItemChanged(lastItemPos);
						}

						mCurItem = curItem;

						int itemPos = mSkipSparse.indexOfValue(mCurItem);
						notifyItemChanged(itemPos);

						if(mRecyclerRoot != null) {
							mRecyclerRoot.smoothScrollToPosition(itemPos);
						}
					}
				});
			}
		}
	}

	private void setCurrentItem(final SkipItem item) {
		if(item != null) {
			if (mContext != null) {
				((Activity) mContext).runOnUiThread(new Runnable() {
					@Override
					public void run() {
						if (mCurItem != null) {
							int lastItemPos = mSkipSparse.indexOfValue(mCurItem);
							mCurItem = null;
							notifyItemChanged(lastItemPos);
						}

						mCurItem = item;

						int itemPos = mSkipSparse.indexOfValue(mCurItem);
						notifyItemChanged(itemPos);

						if (mRecyclerRoot != null) {
							mRecyclerRoot.smoothScrollToPosition(itemPos);
						}
					}
				});
			}
		}
	}

	@Override
	public void sbtRealTime(long realTimePosix, long streamTimePosix, long curPos, long totalDuration) {
		if(mSkipSparse.size() == 0) {
			return;
		}

		final int sparseIdxMax = mSkipSparse.size()-1;
		if(streamTimePosix >= mSkipSparse.valueAt(sparseIdxMax).getSbtRealTime()) {
			final SkipItem foundItem = mSkipSparse.valueAt(sparseIdxMax);

			if(mCurItem != null && mCurItem.equals(foundItem)) {
				return;
			}

			setCurrentItem(foundItem);

			return;
		} else if(streamTimePosix >= mSkipSparse.valueAt(sparseIdxMax-1).getSbtRealTime() && streamTimePosix < mSkipSparse.valueAt(sparseIdxMax).getSbtRealTime()) {
			final SkipItem foundItem = mSkipSparse.valueAt(sparseIdxMax-1);

			if(mCurItem != null && mCurItem.equals(foundItem)) {
				return;
			}

			setCurrentItem(foundItem);

			return;
		}

		for(int i = 0; i < sparseIdxMax; i++) {
			if(streamTimePosix >= mSkipSparse.valueAt(i).getSbtRealTime() && streamTimePosix < mSkipSparse.valueAt(i+1).getSbtRealTime()) {

				final SkipItem foundItem = mSkipSparse.valueAt(i);

				if(mCurItem != null && mCurItem.equals(foundItem)) {
					return;
				}

				if(BuildConfig.DEBUG)Log.d(TAG, "SBT found curItem at Idx " + i + " from " + sparseIdxMax + " : " + mSkipSparse.valueAt(i).getSkipTextual().getText());

				setCurrentItem(foundItem);

				break;
			}
		}
	}

	@Override
	public void started() {

	}

	@Override
	public void paused() {

	}

	@Override
	public void stopped() {

	}

	@Override
	public void textual(Textual textual) {

	}

	@Override
	public void visual(Visual visual) {

	}

	public synchronized void addAllSkipItems(List<SkipItem> skipItems) {
		for(SkipItem skipItem : skipItems) {
			int sparseKey;
			if(skipItem.getSbtRealTime() == 0) {
				sparseKey = mSkipSparse.indexOfKey(skipItem.getRelativeTimepoint() / 1000);
			} else {
				sparseKey = mSkipSparse.indexOfKey(skipItem.getSbtRealTime());
			}

			if(sparseKey < 0) {
				if(BuildConfig.DEBUG)Log.d(TAG, "SBTItem appending new at: " + skipItem.getSbtRealTime() + " : " + skipItem.getSkipTextual().getText() + " Vis: " + (skipItem.getSkipVisual() != null ? "okay" : "null"));
				if(skipItem.getSbtRealTime() == 0) {
					mSkipSparse.append(skipItem.getRelativeTimepoint() / 1000, skipItem);
				} else {
					mSkipSparse.append(skipItem.getSbtRealTime(), skipItem);
				}
			}
		}

		((Activity) mContext).runOnUiThread(new Runnable() {
			@Override
			public void run() {
				notifyDataSetChanged();
			}
		});
	}

	//timer for stress relief
	private Timer mNotifyTimer = null;
	@Override
	public synchronized void skipItemAdded(SkipItem skipItem) {
		if(BuildConfig.DEBUG)Log.d(TAG, "SkipItem '" + skipItem.getSkipTextual().getText() + "' Added skipItemAdded at: " + (skipItem.getRelativeTimepoint()/1000));

		int sparseKey;
		if(skipItem.getSbtRealTime() == 0) {
			sparseKey = mSkipSparse.indexOfKey(skipItem.getRelativeTimepoint() / 1000);
		} else {
			sparseKey = mSkipSparse.indexOfKey(skipItem.getSbtRealTime());
		}
		if(sparseKey < 0) {
			if(BuildConfig.DEBUG)Log.d(TAG, "SBTItem appending new at: " + skipItem.getSbtRealTime() + " : " + skipItem.getSkipTextual().getText() + " Vis: " + (skipItem.getSkipVisual() != null ? "okay" : "null"));
			if(skipItem.getSbtRealTime() == 0) {
				mSkipSparse.append(skipItem.getRelativeTimepoint() / 1000, skipItem);
			} else {
				mSkipSparse.append(skipItem.getSbtRealTime(), skipItem);
			}

			if(mNotifyTimer != null) {
				mNotifyTimer.cancel();
				mNotifyTimer = new Timer();
				mNotifyTimer.schedule(new TimerTask() {
					@Override
					public void run() {
						((Activity) mContext).runOnUiThread(new Runnable() {
							@Override
							public void run() {
								//may lead to weird behaviour on too frequent updates
								//notifyItemInserted(mSkipSparse.size() - 1);
								notifyDataSetChanged();
							}
						});
					}
				}, 500);
			}
		} else {
			if(BuildConfig.DEBUG)Log.d(TAG, "SBTItem updating at: " + skipItem.getSbtRealTime() + " : " + skipItem.getSkipTextual().getText() + " Vis: " + (skipItem.getSkipVisual() != null ? "okay" : "null"));
			mSkipSparse.remove(sparseKey);
			mSkipSparse.setValueAt(sparseKey, skipItem);
			((Activity) mContext).runOnUiThread(new Runnable() {
				@Override
				public void run() {
					notifyItemChanged(sparseKey);
				}
			});
		}
	}

	@Override
	public synchronized void skipItemRemoved(SkipItem skipItem) {
		if(BuildConfig.DEBUG)Log.d(TAG, "SBTItem removing item: " + skipItem.getSkipTextual().getText() + " : " + new Date(skipItem.getSbtRealTime()).toString());
		int sparseKey = mSkipSparse.indexOfKey(skipItem.getSbtRealTime());
		if(sparseKey >= 0) {
			if(BuildConfig.DEBUG)Log.d(TAG, "SBTItem removing idx: " + sparseKey);
			if(mCurItem != null) {
				if (sparseKey == mSkipSparse.indexOfValue(mCurItem)) {
					mCurItem = null;
				}
			}

			if(BuildConfig.DEBUG)Log.d(TAG, "SBTItem remove size prev: " + mSkipSparse.size());
			mSkipSparse.removeAt(sparseKey);
			if(BuildConfig.DEBUG)Log.d(TAG, "SBTItem remove size afte: " + mSkipSparse.size());
			((Activity) mContext).runOnUiThread(new Runnable() {
				@Override
				public void run() {
					notifyItemRemoved(sparseKey);
				}
			});
		}
	}

	/**/
	void addSkipitemClickListener(SkipitemClickListener listener) {
		if(!mItemClickListeners.contains(listener)) {
			mItemClickListeners.add(listener);
		}
	}

	void removeSkipitemClickListener(SkipitemClickListener listener) {
		mItemClickListeners.remove(listener);
	}

	interface SkipitemClickListener {

		void onSkipItemClicked(SkipItem clickedItem);
	}

	/**/
	private static class SkipViewFillerTask extends AsyncTask<RecycleSkipViewHolder, Void, Bitmap> {

		private final SkipItem mSkipItem;
		private final int position;
		private final boolean mIsCurrentItem;

		private RecycleSkipViewHolder skipViewHolder;

		private String mSkipRelPos;
		private String mSkipText;

		private SkipViewFillerTask(SkipItem item, int position, boolean isCurrentItem) {
			this.mSkipItem = item;
			this.position = position;
			this.mIsCurrentItem = isCurrentItem;
		}

		@Override
		protected Bitmap doInBackground(RecycleSkipViewHolder... recycleSkipViewHolders) {
			skipViewHolder = recycleSkipViewHolders[0];

			StringBuilder skipPos = new StringBuilder();
			if(mSkipItem.getSbtRealTime() == 0) {
				long relPos = mSkipItem.getRelativeTimepoint() / 1000;
				if (relPos > 3600) {
					skipPos.append(String.format(Locale.getDefault(), "%02d", (relPos / 3600)));
					skipPos.append(":");
				}

				skipPos.append(String.format(Locale.getDefault(), "%02d", ((relPos / 60) % 60)));
				skipPos.append(":");
				skipPos.append(String.format(Locale.getDefault(), "%02d", (relPos % 60)));
			} else {
				SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss");
				skipPos.append(format.format(new Date(mSkipItem.getSbtRealTime())));
			}

			mSkipRelPos = skipPos.toString();
			Log.d("SkipItem", "SkipPos: " + skipPos.toString() + " : " + (mSkipItem.getRelativeTimepoint()/1000));


			StringBuilder skipTextBuilder = new StringBuilder();
			if(mSkipItem.getSkipTextual() != null) {
				if (mSkipItem.getSkipTextual().getType() == TextualType.METADATA_TEXTUAL_TYPE_DAB_DLS) {
					TextualDabDynamicLabel dlP = (TextualDabDynamicLabel) mSkipItem.getSkipTextual();
					if (dlP.hasTags()) {
						String artist = null;
						String title = null;
						String email = null;
						String phone = null;
						for (TextualDabDynamicLabelPlusItem dlpItem : dlP.getDlPlusItems()) {
							switch (dlpItem.getDynamicLabelPlusContentType()) {
								case ITEM_ARTIST: {
									artist = dlpItem.getDlPlusContentText();
									break;
								}
								case ITEM_TITLE: {
									title = dlpItem.getDlPlusContentText();
									break;
								}
								case EMAIL_HOTLINE:
								case EMAIL_OTHER:
								case EMAIL_STUDIO: {
									email = dlpItem.getDlPlusContentText();
									break;
								}
								case PHONE_HOTLINE:
								case PHONE_OTHER:
								case PHONE_STUDIO: {
									phone = dlpItem.getDlPlusContentText();
									break;
								}
							}
						}

						if (artist != null) {
							skipTextBuilder.append(artist);
							if (title != null) {
								skipTextBuilder.append("\n");
								skipTextBuilder.append(title);
							}
						} else if (email != null) {
							skipTextBuilder.append("Email:");
							skipTextBuilder.append("\n");
							skipTextBuilder.append(email);
						} else if (phone != null) {
							skipTextBuilder.append("Phone:");
							skipTextBuilder.append("\n");
							skipTextBuilder.append(phone);
						} else {
							skipTextBuilder.append(dlP.getText());
						}
					} else {
						skipTextBuilder.append(dlP.getText());
					}
				}
			}

			mSkipText = skipTextBuilder.toString();

			Bitmap logoBmp = null;

			Visual itemVis = mSkipItem.getSkipVisual();
			if(itemVis != null) {
				logoBmp = BitmapFactory.decodeByteArray(itemVis.getVisualData(), 0, itemVis.getVisualData().length);
			}

			return logoBmp;
		}

		@Override
		protected void onCancelled() {
			super.onCancelled();
			if(BuildConfig.DEBUG)Log.d(TAG, "onCancelled");
		}

		@Override
		protected void onCancelled(Bitmap bitmap) {
			super.onCancelled(bitmap);
			if(BuildConfig.DEBUG)Log.d(TAG, "onCancelled result");
		}

		@Override
		protected void onPostExecute(Bitmap result) {
			super.onPostExecute(result);
			//make sure that the holder isn't already recycled
			if (skipViewHolder.skipViewPosition == position) {
				if(mIsCurrentItem) {
					if(BuildConfig.DEBUG) Log.d(TAG, "SkipSelected filler isCurrrent: " + mSkipItem.getRelativeTimepoint());

					skipViewHolder.skipItemBaseLayout.setBackgroundResource(R.drawable.list_bg_selected);
				} else {
					skipViewHolder.skipItemBaseLayout.setBackgroundResource(R.drawable.list_bg);
				}

				if(result != null) {
					skipViewHolder.skipItemSls.setVisibility(View.VISIBLE);
					skipViewHolder.skipItemSls.setImageBitmap(result);
				}

				skipViewHolder.skipItemRelPos.setText(mSkipRelPos);
				skipViewHolder.skipItemtext.setText(mSkipText);

				skipViewHolder.skipLoadingProgress.setVisibility(View.INVISIBLE);

				skipViewHolder.skipItemtext.setVisibility(View.VISIBLE);
				skipViewHolder.skipItemRelPos.setVisibility(View.VISIBLE);
			}
		}
	}
}
