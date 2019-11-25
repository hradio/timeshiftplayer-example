package eu.hradio.timeshiftsample;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.Fragment;
import android.util.Log;

import org.omri.radio.Radio;
import org.omri.radioservice.RadioService;
import org.omri.radioservice.RadioServiceType;
import org.omri.radioservice.metadata.Textual;
import org.omri.radioservice.metadata.TextualDabDynamicLabel;
import org.omri.radioservice.metadata.TextualDabDynamicLabelPlusItem;
import org.omri.radioservice.metadata.TextualType;
import org.omri.radioservice.metadata.Visual;
import org.omri.tuner.ReceptionQuality;
import org.omri.tuner.Tuner;
import org.omri.tuner.TunerListener;
import org.omri.tuner.TunerStatus;

import java.io.IOException;

import eu.hradio.core.audiotrackservice.AudiotrackService;
import eu.hradio.timeshiftplayer.SkipItem;
import eu.hradio.timeshiftplayer.TimeshiftListener;
import eu.hradio.timeshiftplayer.TimeshiftPlayer;
import eu.hradio.timeshiftplayer.TimeshiftPlayerFactory;

import static eu.hradio.timeshiftsample.BuildConfig.DEBUG;

public class RetainedFragment extends Fragment implements TunerListener, TimeshiftListener {

	private static final String TAG = "RetainedFragment";

	private TimeshiftPlayer mTimeshiftPlayer = null;

	private RadioService mRunningSrv = null;

	private boolean mAudiotrackServiceBound = false;
	private transient AudiotrackService.AudioTrackBinder mAudiotrackService = null;

	private PendingIntent mNotificationIntent = null;

	private ServiceConnection mSrvCon = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			if(BuildConfig.DEBUG)Log.d(TAG, "onServiceConnected AudioTrackService");

			if(service instanceof AudiotrackService.AudioTrackBinder) {
				mAudiotrackServiceBound = true;
				mAudiotrackService = (AudiotrackService.AudioTrackBinder) service;

				if(mNotificationIntent != null) {
					mAudiotrackService.getNotification().setContentIntent(mNotificationIntent);
				}
			}
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			if(BuildConfig.DEBUG)Log.d(TAG, "onServiceDisconnected AudioTrackService");

			mAudiotrackServiceBound = false;
			mAudiotrackService = null;
		}
	};

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if(BuildConfig.DEBUG) Log.d(TAG, "onCreate");

		setRetainInstance(true);

		Bundle argsbundle = getArguments();
		if(argsbundle != null) {
			mNotificationIntent = argsbundle.getParcelable(MainActivity.NOTIFICATION_INTENT_ID);
		}
	}

	private RetainedStateCallback mRetCb = null;
	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
		if(BuildConfig.DEBUG) Log.d(TAG, "onAttach");

		mRetCb = (RetainedStateCallback)context;
		if(mTimeshiftPlayer != null) {
			mRetCb.onNewTimeshiftPlayer(mTimeshiftPlayer);
		}

		if(getActivity() != null) {
			Intent aTrackIntent = new Intent(getActivity(), eu.hradio.core.audiotrackservice.AudiotrackService.class);
			getActivity().startService(aTrackIntent);
			getActivity().bindService(aTrackIntent, mSrvCon, 0);
		}
	}

	@Override
	public void onDetach() {
		super.onDetach();
		if(BuildConfig.DEBUG) Log.d(TAG, "onDetach,  isRemoving: " + isRemoving() + " : isStateSaved: " + isStateSaved());

		if(mAudiotrackServiceBound && mAudiotrackService != null) {
			if(getActivity() != null) {
				getActivity().unbindService(mSrvCon);
			}
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if(BuildConfig.DEBUG) Log.d(TAG, "onDestroy, isRemoving: " + isRemoving() + ", isStateSaved: " + isStateSaved() + ", Activity: " + (getActivity() != null ? "okay" : "null"));

		if(mTimeshiftPlayer != null) {
			mTimeshiftPlayer.stop(true);
			mTimeshiftPlayer.removeAudioDataListener(mAudiotrackService.getAudioDataListener());
		}

		if(mAudiotrackServiceBound && mAudiotrackService != null) {
			if(BuildConfig.DEBUG)Log.d(TAG, "Dismissing notification, Activity: " + (getActivity() != null ? "okay" : "null"));
			mAudiotrackService.getNotification().dismissNotification();
			if(getActivity() != null) {
				getActivity().unbindService(mSrvCon);
				getActivity().stopService(new Intent(getActivity(), eu.hradio.core.audiotrackservice.AudiotrackService.class));

				//set here because onDetach will be called before onServiceDisconnected() from ServiceConnection
				mAudiotrackServiceBound = false;
			}
		}
	}

	TimeshiftPlayer getTimeshiftPlayer() {
		return mTimeshiftPlayer;
	}

	void setNotificationIntent(PendingIntent intent) {
		if(mAudiotrackServiceBound && mAudiotrackService != null) {
			mAudiotrackService.getNotification().setContentIntent(intent);
		}
	}

	void setNotificatinLargeIcon(Bitmap largeIcon) {
		if(mAudiotrackServiceBound && mAudiotrackService != null) {
			mAudiotrackService.getNotification().setLargeIcon(largeIcon);
		}
	}

	void shutdown() {
		if(BuildConfig.DEBUG)Log.d(TAG, "Shutting down");

		if(mTimeshiftPlayer != null) {
			mTimeshiftPlayer.stop(true);
			mTimeshiftPlayer.removeAudioDataListener(mAudiotrackService.getAudioDataListener());
		}

		if(BuildConfig.DEBUG)Log.d(TAG, "Dismissing notification, Activity: " + (getActivity() != null ? "okay" : "null"));
		mAudiotrackService.getNotification().dismissNotification();
		if(getActivity() != null) {
			getActivity().unbindService(mSrvCon);
			getActivity().stopService(new Intent(getActivity(), eu.hradio.core.audiotrackservice.AudiotrackService.class));
		}
	}

	/**/
	@Override
	public void radioServiceStarted(Tuner tuner, RadioService radioService) {
		if(BuildConfig.DEBUG) Log.d(TAG, "radioServiceStarted: " + radioService.getServiceLabel());


		if(mRunningSrv != null) {
			/*
			if(mRunningSrv.equalsRadioService(radioService)) {
				if(DEBUG)Log.d(TAG, "Same service started, switching Service: " + mRunningSrv.getServiceLabel() + " from " + mRunningSrv.getRadioServiceType().toString() + " to " + radioService.getRadioServiceType().toString());
				if(mTimeshiftPlayer != null) {
					if(DEBUG)Log.d(TAG, "TimeshiftPlayer already running....");
					((TimeshiftPlayerPcmAu)mTimeshiftPlayer).setNewService(radioService);
					Radio.getInstance().stopRadioService(mRunningSrv);
					mRunningSrv = radioService;
					return;
				}
			}
			 */

			Radio.getInstance().stopRadioService(mRunningSrv);
		}

		mRunningSrv = radioService;

		if(mTimeshiftPlayer != null) {
			mTimeshiftPlayer.removeListener(this);
			mTimeshiftPlayer.stop(true);

			if(mAudiotrackServiceBound) {
				if(mAudiotrackService != null) {
					mTimeshiftPlayer.removeAudioDataListener(mAudiotrackService.getAudioDataListener());
				}
			}
			mTimeshiftPlayer = null;
		}

		try {
			//For ShoutCast IP services a PCM TimeshiftPlayer is needed
			if(radioService.getRadioServiceType() == RadioServiceType.RADIOSERVICE_TYPE_IP) {
				mTimeshiftPlayer = TimeshiftPlayerFactory.createPcmPlayer(getActivity(), radioService);
			} else {
				mTimeshiftPlayer = TimeshiftPlayerFactory.create(getActivity(), radioService);
			}

			if(mTimeshiftPlayer != null) {
				mTimeshiftPlayer.addListener(this);
				mRetCb.onNewTimeshiftPlayer(mTimeshiftPlayer);
				mTimeshiftPlayer.setPlayWhenReady();
				if(mAudiotrackServiceBound && mAudiotrackService != null) {
					mTimeshiftPlayer.addAudioDataListener(mAudiotrackService.getAudioDataListener());
					Bitmap logoBmp = null;
					if(!radioService.getLogos().isEmpty()) {
						for(Visual logo : radioService.getLogos()) {
							if(logo.getVisualHeight() > 32 && logo.getVisualHeight() == logo.getVisualHeight()) {
								if(DEBUG)Log.d(TAG, "Setting NotificationIcon with: " + logo.getVisualWidth() + "x" + logo.getVisualHeight());
								logoBmp = BitmapFactory.decodeByteArray(logo.getVisualData(), 0, logo.getVisualData().length);
								break;
							}
						}
					}
					mAudiotrackService.getNotification().setLargeIcon(logoBmp);
					mAudiotrackService.getNotification().setNotificationText("");
					mAudiotrackService.getNotification().setNotificationTitle(radioService.getServiceLabel());
				}
			}
		} catch(IOException ioE) {
			ioE.printStackTrace();
		}
	}

	@Override
	public void radioServiceStopped(Tuner tuner, RadioService radioService) {
		if(BuildConfig.DEBUG) Log.d(TAG, "radioServiceStopped: " + radioService.getServiceLabel());

		if(mAudiotrackServiceBound && mAudiotrackService != null) {
			//
		}
	}

	@Override
	public void tunerStatusChanged(Tuner tuner, TunerStatus tunerStatus) {

	}

	@Override
	public void tunerScanStarted(Tuner tuner) {

	}

	@Override
	public void tunerScanProgress(Tuner tuner, int i) {

	}

	@Override
	public void tunerScanFinished(Tuner tuner) {

	}

	@Override
	public void tunerScanServiceFound(Tuner tuner, RadioService radioService) {

	}

	@Override
	public void tunerReceptionStatistics(Tuner tuner, boolean b, ReceptionQuality receptionQuality) {

	}

	@Override
	public void tunerRawData(Tuner tuner, byte[] bytes) {

	}

	/* TimeshiftListener impl*/
	@Override
	public void progress(long l, long l1) {

	}

	@Override
	public void sbtRealTime(long l, long l1, long l2, long l3) {

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
		if(textual.getType() == TextualType.METADATA_TEXTUAL_TYPE_DAB_DLS && ((TextualDabDynamicLabel)textual).hasTags()) {
			TextualDabDynamicLabel dl = (TextualDabDynamicLabel)textual;
			String itemArtist = null;
			String itemTitle = null;
			for(TextualDabDynamicLabelPlusItem dlItem : dl.getDlPlusItems()) {
				switch (dlItem.getDynamicLabelPlusContentType()) {
					case ITEM_TITLE:
						itemTitle = dlItem.getDlPlusContentText();
						break;
					case ITEM_ARTIST:
						itemArtist = dlItem.getDlPlusContentText();
						break;
				}
			}

			if(itemArtist != null && itemTitle != null) {
				mAudiotrackService.getNotification().setNotificationText(itemArtist + "\n" + itemTitle);
			}
		}
	}

	@Override
	public void visual(Visual visual) {

	}

	@Override
	public void skipItemAdded(SkipItem skipItem) {

	}

	@Override
	public void skipItemRemoved(SkipItem skipItem) {

	}

	/**/
	public interface RetainedStateCallback {

		void onNewTimeshiftPlayer(TimeshiftPlayer player);
	}
}
