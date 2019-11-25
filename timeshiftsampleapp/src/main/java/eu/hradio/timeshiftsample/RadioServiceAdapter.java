package eu.hradio.timeshiftsample;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.omri.radioservice.RadioService;
import org.omri.radioservice.RadioServiceDab;
import org.omri.radioservice.RadioServiceType;
import org.omri.radioservice.metadata.Visual;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class RadioServiceAdapter extends RecyclerView.Adapter<RadioServiceAdapter.RadioServiceViewHolder> {

	private final static String TAG = "RadioServiceAdapter";
	private List<RadioService> mRadioServicelist = new ArrayList<>();

	private RadioService mCurrentItem = null;

	private ConcurrentHashMap<View, ServiceViewFillerTask> mViewtasks = new ConcurrentHashMap<>();

	@NonNull
	@Override
	public RadioServiceAdapter.RadioServiceViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int viewType) {
		if(BuildConfig.DEBUG) Log.d(TAG, "onCreateViewHolder with ViewType: " + viewGroup);

		View view = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.radioservice_adapter_row_layout, null);
		return new RadioServiceAdapter.RadioServiceViewHolder(view, mServiceClickListener);
	}

	@Override
	public void onBindViewHolder(@NonNull RadioServiceAdapter.RadioServiceViewHolder serviceViewHolder, int position) {
		if(BuildConfig.DEBUG) Log.d(TAG, "onBindViewHolder at pos: " + position);

		serviceViewHolder.serviceLabelView.setVisibility(View.INVISIBLE);
		serviceViewHolder.serviceLogoView.setVisibility(View.INVISIBLE);
		serviceViewHolder.ensFreqView.setVisibility(View.INVISIBLE);
		serviceViewHolder.ensIdView.setVisibility(View.INVISIBLE);
		serviceViewHolder.ensLabelView.setVisibility(View.INVISIBLE);
		serviceViewHolder.srvidView.setVisibility(View.INVISIBLE);

		RadioService curSrv = mRadioServicelist.get(position);

		if(serviceViewHolder.radioServiceViewPosition != position) {
			if(mViewtasks.get(serviceViewHolder.itemView) != null) {
				if(BuildConfig.DEBUG)Log.d(TAG, "Cancelling old running task");
				mViewtasks.get(serviceViewHolder.itemView).cancel(true);
				mViewtasks.remove(serviceViewHolder.itemView);
			}
		}

		serviceViewHolder.setItemPosition(position);

		ServiceViewFillerTask fillTask = new ServiceViewFillerTask(mRadioServicelist.get(position), position, curSrv.equals(mCurrentItem));
		mViewtasks.put(serviceViewHolder.itemView, fillTask);
		fillTask.execute(serviceViewHolder);
	}

	@Override
	public int getItemCount() {
		return mRadioServicelist.size();
	}

	/* Viewolder */
	static class RadioServiceViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {

		private final RelativeLayout serviceBaseLayout;
		private final ImageView serviceLogoView;
		private final TextView serviceLabelView;
		private final TextView ensLabelView;
		private final TextView srvidView;
		private final TextView ensIdView;
		private final TextView ensFreqView;

		private final RadioServiceAdapter.RadioServiceViewHolder.RadioServiceViewOnClickListener mClickListener;
		private int radioServiceViewPosition;

		private RadioServiceViewHolder(View radioServiceView, RadioServiceAdapter.RadioServiceViewHolder.RadioServiceViewOnClickListener listener) {
			super(radioServiceView);

			mClickListener = listener;

			serviceBaseLayout = radioServiceView.findViewById(R.id.radioservice_base_layout);
			serviceLogoView = radioServiceView.findViewById(R.id.radioservice_adapter_icon);
			serviceLabelView = radioServiceView.findViewById(R.id.radioservice_adapter_servicelabel);

			ensLabelView = radioServiceView.findViewById(R.id.radioservice_adapter_ensembleLabel);
			srvidView = radioServiceView.findViewById(R.id.radioservice_adapter_serviceId);
			ensIdView = radioServiceView.findViewById(R.id.radioservice_adapter_ensembleId);
			ensFreqView = radioServiceView.findViewById(R.id.radioservice_adapter_ensembleFreq);

			radioServiceView.setOnClickListener(this);
			radioServiceView.setOnLongClickListener(this);
		}

		void setItemPosition(int pos) {
			radioServiceViewPosition = pos;
		}

		@Override
		public void onClick(View v) {
			if(BuildConfig.DEBUG)Log.d(TAG, "Item clicked at pos: " + radioServiceViewPosition);
			if(mClickListener != null) {
				mClickListener.onRadioServiceViewClicked(radioServiceViewPosition);
			}
		}

		@Override
		public boolean onLongClick(View v) {
			if(BuildConfig.DEBUG)Log.d(TAG, "Item longClicked at pos: " + radioServiceViewPosition);
			if(mClickListener != null) {
				mClickListener.onRadioServiceViewLongClicked(radioServiceViewPosition);
			}

			return true;
		}

		public static interface RadioServiceViewOnClickListener {

			void onRadioServiceViewClicked(int position);

			void onRadioServiceViewLongClicked(int position);
		}
	}

	/* List updates */
	void updateServiceList(final List<RadioService> radioServices) {
		Log.d(TAG, "Updating servicelist with " + radioServices.size() + " Services");

		mRadioServicelist.clear();
		for(RadioService srv : radioServices) {
			if(srv.getRadioServiceType() == RadioServiceType.RADIOSERVICE_TYPE_DAB || srv.getRadioServiceType() == RadioServiceType.RADIOSERVICE_TYPE_EDI) {
				if(((RadioServiceDab)srv).isProgrammeService()) {
					mRadioServicelist.add(srv);
				}
			}
			if(srv.getRadioServiceType() == RadioServiceType.RADIOSERVICE_TYPE_IP) {
				mRadioServicelist.add(srv);
			}
		}

		notifyDataSetChanged();
	}

	void setCurrentRadioService(RadioService service) {
		if(service != null) {
			if(mCurrentItem != null) {
				int curIdx = mRadioServicelist.indexOf(mCurrentItem);
				if(curIdx > -1) {
					RadioService unselectedItem = mRadioServicelist.get(mRadioServicelist.indexOf(mCurrentItem));
					mCurrentItem = null;
					notifyItemChanged(mRadioServicelist.indexOf(unselectedItem));
				}
			}

			mCurrentItem = service;
			notifyItemChanged(mRadioServicelist.indexOf(mCurrentItem));
		}
	}

	void serviceRemoved(RadioService deletedSrv) {
		int delSrvIdx = mRadioServicelist.indexOf(deletedSrv);
		if(delSrvIdx > -1) {
			mRadioServicelist.remove(delSrvIdx);
			notifyItemRemoved(delSrvIdx);
			notifyDataSetChanged();
		}
	}

	private List<RadioServiceClickListener> mSrvClickListeners = new ArrayList<>();
	void addRadioServiceClickListener(RadioServiceClickListener listener) {
		if(!mSrvClickListeners.contains(listener)) {
			mSrvClickListeners.add(listener);
		}
	}

	void removeRadioServiceClickListener(RadioServiceClickListener listener) {
		mSrvClickListeners.remove(listener);
	}

	interface RadioServiceClickListener {

		void onRadioServiceClicked(RadioService service);

		void onRadioServiceLongClicked(RadioService service);
	}

	/**/
	private static class ServiceViewFillerTask extends AsyncTask<RadioServiceAdapter.RadioServiceViewHolder, Void, Bitmap> {

		private final RadioService mRadioService;
		private final int mPosition;
		private final boolean mIsCurrentItem;

		private RadioServiceViewHolder mServiceViewHolder;

		private ServiceViewFillerTask(RadioService item, int position, boolean isCurrentItem) {
			this.mRadioService = item;
			this.mPosition = position;
			this.mIsCurrentItem = isCurrentItem;
		}

		@Override
		protected Bitmap doInBackground(RadioServiceViewHolder... radioServiceViewHolders) {
			mServiceViewHolder = radioServiceViewHolders[0];

			Visual logo = null;
			for(Visual logoVisual : mRadioService.getLogos()) {
				if(logoVisual.getVisualHeight() >= 32 && logoVisual.getVisualWidth() == logoVisual.getVisualHeight()) {
					logo = logoVisual;
				}
				if(logoVisual.getVisualHeight() > 32 && logoVisual.getVisualWidth() == logoVisual.getVisualHeight()) {
					logo = logoVisual;
					break;
				}
			}

			Bitmap logoBmp = null;
			if(logo != null) {
				logoBmp = BitmapFactory.decodeByteArray(logo.getVisualData(), 0, logo.getVisualData().length);
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
		protected void onPostExecute(Bitmap logoBitmap) {
			super.onPostExecute(logoBitmap);

			if (mServiceViewHolder.radioServiceViewPosition == mPosition) {
				if(mIsCurrentItem) {
					mServiceViewHolder.serviceBaseLayout.setBackgroundResource(R.drawable.list_bg_selected);
				} else {
					mServiceViewHolder.serviceBaseLayout.setBackgroundResource(R.drawable.list_bg);
				}

				switch (mRadioService.getRadioServiceType()) {
					case RADIOSERVICE_TYPE_DAB:
						mServiceViewHolder.srvidView.setText("DAB");
						break;
					case RADIOSERVICE_TYPE_EDI:
						mServiceViewHolder.srvidView.setText("EDI");
						break;
					case RADIOSERVICE_TYPE_IP:
						mServiceViewHolder.srvidView.setText("ShoutCast");
						break;
					default:
						mServiceViewHolder.srvidView.setText("Unknown");
						break;
				}

				if(logoBitmap != null) {
					mServiceViewHolder.serviceLogoView.setImageBitmap(logoBitmap);
				} else {
					mServiceViewHolder.serviceLogoView.setImageResource(R.mipmap.hradio);
				}
				mServiceViewHolder.serviceLabelView.setText(mRadioService.getServiceLabel());
				if(mRadioService.getRadioServiceType() == RadioServiceType.RADIOSERVICE_TYPE_DAB || mRadioService.getRadioServiceType() == RadioServiceType.RADIOSERVICE_TYPE_EDI) {
					mServiceViewHolder.ensLabelView.setText(((RadioServiceDab) mRadioService).getEnsembleLabel());
					mServiceViewHolder.ensIdView.setText("0x" + Integer.toHexString(((RadioServiceDab) mRadioService).getEnsembleId()));

					StringBuilder srvDetailsBuilder = new StringBuilder();
					srvDetailsBuilder.append("SId: 0x").append(Integer.toHexString(((RadioServiceDab) mRadioService).getServiceId()).toUpperCase());
					srvDetailsBuilder.append(", ");
					srvDetailsBuilder.append("EId: 0x").append(Integer.toHexString(((RadioServiceDab) mRadioService).getEnsembleId()).toUpperCase());
					srvDetailsBuilder.append(", ");
					srvDetailsBuilder.append("ECC: 0x").append(Integer.toHexString(((RadioServiceDab) mRadioService).getEnsembleEcc()).toUpperCase());

					mServiceViewHolder.ensIdView.setText(srvDetailsBuilder.toString());

					mServiceViewHolder.ensFreqView.setText("Frequency: " + ((RadioServiceDab) mRadioService).getEnsembleFrequency());
				} else if(mRadioService.getRadioServiceType() == RadioServiceType.RADIOSERVICE_TYPE_IP) {
					mServiceViewHolder.ensLabelView.setText("HTTP");
					mServiceViewHolder.ensIdView.setText("");

					mServiceViewHolder.ensFreqView.setText("");
				}

				mServiceViewHolder.srvidView.setVisibility(View.VISIBLE);
				mServiceViewHolder.serviceLogoView.setVisibility(View.VISIBLE);
				mServiceViewHolder.serviceLabelView.setVisibility(View.VISIBLE);
				mServiceViewHolder.ensLabelView.setVisibility(View.VISIBLE);
				mServiceViewHolder.ensIdView.setVisibility(View.VISIBLE);
				mServiceViewHolder.ensFreqView.setVisibility(View.VISIBLE);
			}
		}
	}

	/* ViewHolder Clicklistener */
	private RadioServiceAdapter.RadioServiceViewHolder.RadioServiceViewOnClickListener mServiceClickListener = new RadioServiceAdapter.RadioServiceViewHolder.RadioServiceViewOnClickListener() {

		@Override
		public void onRadioServiceViewClicked(int position) {
			if(BuildConfig.DEBUG)Log.d(TAG, "onRadioServiceViewClicked: " + position);

			if(mRadioServicelist.size() > position) {
				RadioService clickedSrv = mRadioServicelist.get(position);
				for(RadioServiceClickListener cb : mSrvClickListeners) {
					cb.onRadioServiceClicked(clickedSrv);
				}
			}
		}

		@Override
		public void onRadioServiceViewLongClicked(int position) {
			if(BuildConfig.DEBUG)Log.d(TAG, "onRadioServiceViewLongClicked: " + position);

			if(mRadioServicelist.size() > position) {
				RadioService clickedSrv = mRadioServicelist.get(position);
				for(RadioServiceClickListener cb : mSrvClickListeners) {
					cb.onRadioServiceLongClicked(clickedSrv);
				}
			}
		}
	};
}
