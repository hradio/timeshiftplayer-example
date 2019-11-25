package eu.hradio.timeshiftsample;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.util.Linkify;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import org.omri.radio.Radio;
import org.omri.radio.RadioErrorCode;
import org.omri.radio.RadioStatus;
import org.omri.radio.RadioStatusListener;
import org.omri.radio.impl.RadioImpl;
import org.omri.radio.impl.TunerIpShoutcast;
import org.omri.radioservice.RadioService;
import org.omri.radioservice.RadioServiceDab;
import org.omri.radioservice.RadioServiceType;
import org.omri.radioservice.metadata.Textual;
import org.omri.radioservice.metadata.TextualDabDynamicLabel;
import org.omri.radioservice.metadata.TextualDabDynamicLabelPlusItem;
import org.omri.radioservice.metadata.TextualType;
import org.omri.radioservice.metadata.Visual;
import org.omri.tuner.Tuner;
import org.omri.tuner.TunerListener;
import org.omri.tuner.TunerStatus;
import org.omri.tuner.ReceptionQuality;
import org.omri.tuner.TunerType;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import eu.hradio.timeshiftplayer.SkipItem;
import eu.hradio.timeshiftplayer.TimeshiftListener;
import eu.hradio.timeshiftplayer.TimeshiftPlayer;

import static eu.hradio.timeshiftsample.BuildConfig.DEBUG;

public class MainActivity extends AppCompatActivity implements RadioStatusListener, TimeshiftListener, RetainedFragment.RetainedStateCallback, SkipItemAdapter.SkipitemClickListener, RadioServiceAdapter.RadioServiceClickListener {

    private final String TAG = "Timeshifter";

    private ImageView mSlsView = null;
    private TextView mDlsView = null;
    private ImageButton mScanButton = null;

	private ImageView mSignalStatView = null;

	private RetainedFragment mRetainedFragment;
	static final String RETAINEDFRAGMENT_TAG = "RETAINEDFRAMENT";

    private List<RadioService> mServiceList = new ArrayList<RadioService>();

	private ProgressDialog mEnsembleScanProgress;

	private ImageButton mTimeshiftPauseButton = null;
	private TextView mTimeshiftProgressView = null;
	private SeekBar mTimeshiftSeekBar = null;

	private RadioService mRunningSrv = null;
	private Visual mLastVis = null;
	private Textual mLastText = null;

	@Override
	public void onNewTimeshiftPlayer(TimeshiftPlayer player) {
		Log.d(TAG, "onNewTimeshiftPlayer");
		player.addListener(this);
		if(mSkipItemAdapter != null) {
			player.addListener(mSkipItemAdapter);
			if(mSkipItemAdapter != null) {
				mSkipItemAdapter.clear();
				mSkipItemAdapter.addAllSkipItems(player.getSkipItems());
			}
		}
	}

	/*
     * A listener for tuner messages.
     */
	private boolean mIpScanDone = false;
    private TunerListener mTunerListener = new TunerListener() {

        @Override
        public void tunerStatusChanged(final Tuner tuner, TunerStatus tunerStatus) {
            Log.d(TAG, "tunerStateChanged to: " + tunerStatus.toString());

            boolean allTunersInit = false;

            switch (tunerStatus) {
				case TUNER_STATUS_INITIALIZED: {
					Log.d(TAG, "Tuner " + tuner + " initialized");

					for(Tuner iniTuner : Radio.getInstance().getAvailableTuners()) {
						if(iniTuner.getTunerStatus() != TunerStatus.TUNER_STATUS_INITIALIZED) {
							allTunersInit = false;
						}
						if(iniTuner.getTunerStatus() == TunerStatus.TUNER_STATUS_INITIALIZED) {
							allTunersInit = true;
						}
					}

					break;
                }
                default: {
                    break;
                }
            }

            if(allTunersInit) {
	            mServiceList = Radio.getInstance().getRadioServices();
	            if(DEBUG)Log.d(TAG, "All tuners initialized, got " + mServiceList.size() + " services");
	            for(int i = 0; i < mServiceList.size(); i++) {
	            	RadioService iSrv = mServiceList.get(i);
	            	for(int j = i+1; j < mServiceList.size()-i; j++) {
	            		RadioService jSrv = mServiceList.get(j);
	            		if(iSrv.equalsRadioService(jSrv)) {
	            			Log.d(TAG, "Found EqualSrv: " + iSrv.getRadioServiceType().toString() + "_" + iSrv.getServiceLabel() + " : " + jSrv.getRadioServiceType().toString() + "_" + jSrv.getServiceLabel());
			            }
		            }
	            }
	            if(mRadioServiceAdapter != null) {
		            runOnUiThread(new Runnable() {
			            @Override
			            public void run() {
			            	mRadioServiceAdapter.updateServiceList(mServiceList);
				            if(mScanButton != null) {
				            	for(Tuner initTuner : Radio.getInstance().getAvailableTuners()) {
						            if (initTuner.getTunerType() == TunerType.TUNER_TYPE_DAB) {
							            mScanButton.setEnabled(true);
							            mSignalStatView.setVisibility(View.VISIBLE);
							            mScanButton.setVisibility(View.VISIBLE);

							            if(mMenu != null) {
								            final MenuItem scanItem = mMenu.findItem(R.id.action_scan);
								            if(scanItem != null) {
									            scanItem.setEnabled(true);
									            scanItem.setVisible(true);
								            }
							            }
						            }
					            }
				            }
			            }
		            });

		            if(false) {
		            //if(!mIpScanDone) {
		            	Timer ipscanTimer = new Timer();
		            	ipscanTimer.schedule(new TimerTask() {
				            @Override
				            public void run() {
					            if(DEBUG)Log.d(TAG, "IpServiceScan with: " + Radio.getInstance().getRadioServices().size() + " services");
					            for(Tuner scanTuner : Radio.getInstance().getAvailableTuners()) {
						            if(scanTuner instanceof TunerIpShoutcast) {
							            mIpScanDone = true;
							            scanTuner.startRadioServiceScan();
							            break;
						            }
					            }
				            }
			            }, 10000);
		            }
	            }
            }
        }

        private int mScanSrvCnt = 0;
		@Override
		public void tunerScanStarted(Tuner tuner) {
			Log.d(TAG, "ServiceScan started!");
			mScanSrvCnt = 0;
			if(tuner.getTunerType() != TunerType.TUNER_TYPE_IP_SHOUTCAST) {
				if (mScanButton != null) {
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							mScanButton.setEnabled(false);
							if(mMenu != null) {
								final MenuItem scanItem = mMenu.findItem(R.id.action_scan);
								if(scanItem != null) {
									scanItem.setEnabled(false);
								}
							}
							clearMetadataViews();
							mEnsembleScanProgress.show();
						}
					});
				}
			}
		}

        @Override
        public void tunerScanProgress(Tuner tuner, final int percentScanned) {
            Log.d(TAG, "tunerScanProgress: " + percentScanned + "%");

			if(mEnsembleScanProgress.isShowing()) {
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						if(mScanSrvCnt > 0) {
							mEnsembleScanProgress.setMessage("Progress: " + percentScanned + "%\n" +
									"Found Services: " + mScanSrvCnt);
						} else {
							mEnsembleScanProgress.setMessage("Progress: " + percentScanned + "%");
						}
					}
				});
			}
		}

		@Override
		public void tunerScanFinished(Tuner tuner) {
			Log.d(TAG, "ServiceScan finished!");

			for(Tuner chkTun : Radio.getInstance().getAvailableTuners()) {
				if(chkTun.getTunerStatus() == TunerStatus.TUNER_STATUS_SCANNING) {
					if(DEBUG)Log.d(TAG, "Tuner " + chkTun + " is still scanning....");
					return;
				}
			}
			if(mEnsembleScanProgress.isShowing()) {
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						mEnsembleScanProgress.setMessage("Progress: 0%");
						mEnsembleScanProgress.dismiss();
					}
				});
			}

			mServiceList = Radio.getInstance().getRadioServices();
			if(mRadioServiceAdapter != null) {
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						mRadioServiceAdapter.updateServiceList(mServiceList);
					}
				});
			}

			if(mScanButton != null) {
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						mScanButton.setEnabled(true);
					}
				});
			}

			if(mMenu != null) {
				final MenuItem scanItem = mMenu.findItem(R.id.action_scan);
				if(scanItem != null) {
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							scanItem.setVisible(true);
							scanItem.setEnabled(true);
						}
					});
				}
			}

			List<RadioService> singleTunerServices = tuner.getRadioServices();
			for(RadioService service : singleTunerServices) {
				switch (service.getRadioServiceType()) {
					case RADIOSERVICE_TYPE_DAB: {
						Log.d(TAG, "Found DAB " + ( ((RadioServiceDab)service).isProgrammeService() ? "Programme" : "Data" ) + " Service: " + ((RadioServiceDab)service).getServiceLabel());
						break;
					}
					default: {
						break;
					}
				}
			}
		}

        @Override
        public void radioServiceStarted(Tuner tuner, final RadioService radioService) {
            Log.d(TAG, "radioServiceStarted: " + radioService.getRadioServiceType().toString() + " : " + radioService.getServiceLabel());

            if(mRunningSrv != null) {
            	Radio.getInstance().stopRadioService(mRunningSrv);
            }

            mRunningSrv = radioService;

            if(mRadioServiceAdapter != null) {
            	runOnUiThread(new Runnable() {
		            @Override
		            public void run() {
			            mRadioServiceAdapter.setCurrentRadioService(radioService);
		            }
	            });

            }

            if(getSupportActionBar() != null) {
            	runOnUiThread(new Runnable() {
		            @Override
		            public void run() {
			            getSupportActionBar().setTitle(radioService.getServiceLabel());
			            if(!radioService.getLogos().isEmpty()) {
				            for(Visual logo : radioService.getLogos()) {
					            if(logo.getVisualWidth() >= 32 && logo.getVisualHeight() == logo.getVisualWidth()) {
						            Drawable image = new BitmapDrawable(getResources(), BitmapFactory.decodeByteArray(logo.getVisualData(), 0, logo.getVisualData().length));
						            getSupportActionBar().setIcon(image);
						            break;
					            }
				            }
			            }
		            }
	            });
            }
		}

        @Override
        public void radioServiceStopped(Tuner tuner, RadioService radioService) {
            Log.d(TAG, "radioServiceStopped: " + radioService.getRadioServiceType().toString() + " : " + radioService.getServiceLabel());

	        if(getSupportActionBar() != null) {
	        	runOnUiThread(new Runnable() {
			        @Override
			        public void run() {
				        getSupportActionBar().setTitle("");
				        getSupportActionBar().setIcon(null);
			        }
		        });
	        }

			if(mSkipItemAdapter != null) {
				if(DEBUG)Log.d(TAG, "Clearing adapter...");
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						mSkipItemAdapter.clear();
					}
				});
			}
		}

        @Override
        public void tunerReceptionStatistics(final Tuner tuner, final boolean locked, final ReceptionQuality quality){
            //Log.d(TAG, "tunerReceptionStatistics: Locked: " + locked + " Quality: " + quality.toString());
			if(mSignalStatView != null) {
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						if(locked) {
							int drawableRsrc = R.drawable.outline_signal_cellular_off_white_48;
							switch (quality) {
								case NO_SIGNAL: {
									drawableRsrc = R.drawable.outline_signal_cellular_off_white_48;
									break;
								}
								case BAD: {
									drawableRsrc = R.drawable.outline_signal_cellular_0_bar_white_48;
									break;
								}
								case POOR: {
									drawableRsrc = R.drawable.outline_signal_cellular_1_bar_white_48;
									break;
								}
								case OKAY: {
									drawableRsrc = R.drawable.outline_signal_cellular_2_bar_white_48;
									break;
								}
								case GOOD: {
									drawableRsrc = R.drawable.outline_signal_cellular_3_bar_white_48;
									break;
								}
								case BEST: {
									drawableRsrc = R.drawable.outline_signal_cellular_4_bar_white_48;
									break;
								}
							}

							mSignalStatView.setImageResource(drawableRsrc);
						} else {
							mSignalStatView.setImageResource(R.drawable.outline_signal_cellular_off_white_36);
						}
					}
				});
			}
        }

        @Override
        public void tunerRawData(Tuner tuner, byte[] data) {
            //Currently this is not called
	        //Do something useful with this later
        }

		@Override
		public void tunerScanServiceFound(Tuner tuner, RadioService radioService) {
			//Do something useful with this e.g. update scan status with found services
			if(radioService.getRadioServiceType() == RadioServiceType.RADIOSERVICE_TYPE_DAB) {
				if(((RadioServiceDab)radioService).isProgrammeService()) {
					++mScanSrvCnt;
				}
			}
		}
	};

    /* A listener for dynamic attachable tuners e.g. for USB tuner hardware */
	@Override
	public void tunerAttached(Tuner tuner) {
		Log.d(TAG, "################# Tuner Attached: " + tuner.getTunerType().toString());

		tuner.subscribe(mTunerListener);
		if(mRetainedFragment != null) {
			tuner.subscribe(mRetainedFragment);
		}

		if(tuner.getTunerStatus() == TunerStatus.TUNER_STATUS_NOT_INITIALIZED) {
			tuner.initializeTuner();

			if(tuner.getTunerStatus() == TunerStatus.TUNER_STATUS_NOT_INITIALIZED) {
				tuner.initializeTuner();

				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						mSignalStatView.setVisibility(View.VISIBLE);
						mScanButton.setEnabled(true);
						mScanButton.setVisibility(View.VISIBLE);

						if(mMenu != null) {
							MenuItem scanItem = mMenu.findItem(R.id.action_scan);
							if(scanItem != null) {
								scanItem.setVisible(false);
							}
						}
					}
				});
			}
		}
	}

	@Override
	public void tunerDetached(Tuner tuner) {
		Log.d(TAG, "################# Tuner Detached: " + tuner.getTunerType().toString());

		tuner.unsubscribe(mTunerListener);
		if(mRetainedFragment != null) {
			tuner.unsubscribe(mRetainedFragment);
		}

		boolean nomoreUsbTuner = true;
		for(Tuner tunerDev : Radio.getInstance().getAvailableTuners()) {
			if(tunerDev.getTunerType() == TunerType.TUNER_TYPE_DAB) {
				nomoreUsbTuner = false;
				break;
			}
		}

		Log.d(TAG, "################# Tuner Detached noMoreUsb: " + nomoreUsbTuner);
		if(nomoreUsbTuner) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					mSignalStatView.setVisibility(View.GONE);
					//mScanButton.setEnabled(false);
					//mScanButton.setVisibility(View.INVISIBLE);
					if(mMenu != null) {
						MenuItem scanItem = mMenu.findItem(R.id.action_scan);
						if(scanItem != null) {
							scanItem.setVisible(false);
						}
					}
				}
			});
		}
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);

		//Immersive fullscreen
		/*
		if (hasFocus) {
			getWindow().getDecorView().setSystemUiVisibility(
					View.SYSTEM_UI_FLAG_LAYOUT_STABLE
							| View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
							| View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
							| View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
							| View.SYSTEM_UI_FLAG_FULLSCREEN
							| View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
		}
		*/
	}

	private boolean mSeekFromUser = false;
	private RecyclerView mSkipItemRecyclerView = null;
	private SkipItemAdapter mSkipItemAdapter = null;

	private RadioServiceAdapter mRadioServiceAdapter = null;
	private RecyclerView mRadioServiceRecyclerView = null;

	private boolean mDrawerHintShown = false;
	private DrawerLayout mDrawerLayout;

	final static String NOTIFICATION_INTENT_ID = "notification_intent";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

	    if(BuildConfig.DEBUG)Log.d(TAG, "onCreate was called");
	    int orientation = getResources().getConfiguration().orientation;
	    if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
		    if(DEBUG)Log.d(TAG, "SetOrientation ORIENTATION_LANDSCAPE");
		    setContentView(R.layout.content_main_con);
	    } else {
		    if(DEBUG)Log.d(TAG, "SetOrientation ORIENTATION_PORTRAIT");
		    setContentView(R.layout.content_main_con);
	    }

	    Toolbar mToolbar = findViewById(R.id.radio_toolbar);
	    setSupportActionBar(mToolbar);

	    if (savedInstanceState != null) {
		    mDrawerHintShown = savedInstanceState.getBoolean("drawerhint_shown", false);
	    }

	    mDrawerLayout = findViewById(R.id.drawer_layout);
	    if(mDrawerLayout != null) {
		    if (!mDrawerHintShown) {
			    mDrawerLayout.openDrawer(Gravity.START);
			    mDrawerHintShown = true;
		    }
	    }

		mTimeshiftProgressView = findViewById(R.id.radio_timeshift_seekprog);
	    if(mTimeshiftProgressView != null) {
		    mTimeshiftProgressView.setText("00 : 00");
	    }

		mTimeshiftSeekBar = findViewById(R.id.radio_timeshift_seekbar);
	    mTimeshiftSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				if (fromUser) {
					if(DEBUG)Log.d(TAG, "SeekUser: " + progress);
					mSeekFromUser = true;
					mSkipItemAdapter.onSeek(seekBar.getProgress());
				}
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {

			}

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
				if(DEBUG)Log.d(TAG, "SeekBar: " + seekBar.getProgress());
				mSeekFromUser = false;
				clearMetadataViews();
				if(mRetainedFragment != null && mRetainedFragment.getTimeshiftPlayer() != null) {
					mRetainedFragment.getTimeshiftPlayer().seek(seekBar.getProgress() * 1000);
					mSkipItemAdapter.onSeek(seekBar.getProgress());
				}
			}
		});

	    /* Seeking 10 (click) or 30 (longClick) backward */
	    ImageButton minus5Button = findViewById(R.id.radio_ts_minus_five);
	    minus5Button.setOnClickListener(new View.OnClickListener() {
		    @Override
		    public void onClick(View v) {
			    if (mTimeshiftSeekBar.getProgress() > 10) {
				    if (mRetainedFragment != null && mRetainedFragment.getTimeshiftPlayer() != null) {
					    mRetainedFragment.getTimeshiftPlayer().seek((mTimeshiftSeekBar.getProgress() - 10) * 1000);
					    mSkipItemAdapter.onSeek(mTimeshiftSeekBar.getProgress() - 2);
				    }
			    }
		    }
	    });
	    minus5Button.setOnLongClickListener(new View.OnLongClickListener() {
		    @Override
		    public boolean onLongClick(View v) {
			    if (mTimeshiftSeekBar.getProgress() > 30) {
				    if (mRetainedFragment != null && mRetainedFragment.getTimeshiftPlayer() != null) {
					    mRetainedFragment.getTimeshiftPlayer().seek((mTimeshiftSeekBar.getProgress() - 30) * 1000);
					    mSkipItemAdapter.onSeek(mTimeshiftSeekBar.getProgress() - 30);
				    }
			    }

			    return true;
		    }
	    });

	    /* Seeking 10 (click) or 30 (longClick) forward */
	    ImageButton plus5Button = findViewById(R.id.radio_ts_plus_five);
	    plus5Button.setOnClickListener(new View.OnClickListener() {
		    @Override
		    public void onClick(View v) {
			    if ((mTimeshiftSeekBar.getMax() - mTimeshiftSeekBar.getProgress()) > 10) {
				    if (mRetainedFragment != null && mRetainedFragment.getTimeshiftPlayer() != null) {
					    mRetainedFragment.getTimeshiftPlayer().seek((mTimeshiftSeekBar.getProgress() + 10) * 1000);
					    mSkipItemAdapter.onSeek(mTimeshiftSeekBar.getProgress() + 1);
				    }
			    }
		    }
	    });
	    plus5Button.setOnLongClickListener(new View.OnLongClickListener() {
		    @Override
		    public boolean onLongClick(View v) {
			    if ((mTimeshiftSeekBar.getMax() - mTimeshiftSeekBar.getProgress()) > 30) {
				    if (mRetainedFragment != null && mRetainedFragment.getTimeshiftPlayer() != null) {
					    mRetainedFragment.getTimeshiftPlayer().seek((mTimeshiftSeekBar.getProgress() + 30) * 1000);
					    mSkipItemAdapter.onSeek(mTimeshiftSeekBar.getProgress() + 30);
				    }
			    }

			    return true;
		    }
	    });

	    mTimeshiftPauseButton = findViewById(R.id.timeshiftpause_button);
	    mTimeshiftPauseButton.setOnClickListener(new View.OnClickListener() {
		    @Override
		    public void onClick(View view) {
			    if (mRetainedFragment != null && mRetainedFragment.getTimeshiftPlayer() != null) {
				    mRetainedFragment.getTimeshiftPlayer().pause(!mRetainedFragment.getTimeshiftPlayer().isPaused());
			    }
		    }
	    });

        /* Getting the Views for DLS and SLS */
        mSlsView = (ImageView)findViewById(R.id.radio_metadata_visual_imageview);
	    Animatable animatable = (Animatable)mSlsView.getDrawable();
	    animatable.start();

        mDlsView = (TextView)findViewById(R.id.radio_metadata_textual_textview);

        mSignalStatView = (ImageView) findViewById(R.id.radio_signal_imageview);

        /* Adding the Fragment for the servilist to the layout */
	    FragmentManager fragmentManager = getSupportFragmentManager();
	    FragmentTransaction transaction = fragmentManager.beginTransaction();

	    mSkipItemRecyclerView = findViewById(R.id.skiplist_recycler_view);

	    final LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
	    linearLayoutManager.setStackFromEnd(true);
	    mSkipItemRecyclerView.setLayoutManager(linearLayoutManager);

	    mSkipItemAdapter = new SkipItemAdapter(this);
	    mSkipItemAdapter.addSkipitemClickListener(this);
	    mSkipItemRecyclerView.setAdapter(mSkipItemAdapter);

	    mRadioServiceRecyclerView = findViewById(R.id.servicelist_recycler_view);
	    final LinearLayoutManager srvLinearLayoutManager = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
	    mRadioServiceRecyclerView.setLayoutManager(srvLinearLayoutManager);

	    mRadioServiceAdapter = new RadioServiceAdapter();
	    mRadioServiceAdapter.addRadioServiceClickListener(this);
	    mRadioServiceRecyclerView.setAdapter(mRadioServiceAdapter);

	    Intent notiIntent = new Intent(this, MainActivity.class);
	    notiIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
	    PendingIntent pendIntent = PendingIntent.getActivity(this, 0, notiIntent, 0);

        mRetainedFragment = (RetainedFragment)fragmentManager.findFragmentByTag(RETAINEDFRAGMENT_TAG);
        if(mRetainedFragment == null) {
        	mRetainedFragment = new RetainedFragment();
        	Bundle argsBundle = new Bundle();
        	argsBundle.putParcelable(NOTIFICATION_INTENT_ID, pendIntent);
        	mRetainedFragment.setArguments(argsBundle);
	        transaction.add(mRetainedFragment, RETAINEDFRAGMENT_TAG);
        } else {
	        Log.d(TAG, "RetainedFragment already attached!");
	        if(mRetainedFragment.getTimeshiftPlayer() != null) {
	        	for(SkipItem skItem : mRetainedFragment.getTimeshiftPlayer().getSkipItems()) {
			        mSkipItemAdapter.skipItemAdded(skItem);
		        }
		        mSkipItemAdapter.onSeek((int)(mRetainedFragment.getTimeshiftPlayer().getCurrentPosition() / 1000));
	        	mRetainedFragment.getTimeshiftPlayer().addListener(mSkipItemAdapter);
	        }
        }

        transaction.commit();
	    mRetainedFragment.setNotificationIntent(pendIntent);

        /* Getting the scan button and attaching a onClickListener to do something when it's clicked */
	    mScanButton = findViewById(R.id.radio_service_scan_button);
	    mScanButton.setEnabled(true);
	    mScanButton.setVisibility(View.VISIBLE);
	    mScanButton.setOnClickListener(new View.OnClickListener() {
		    @Override
		    public void onClick(View v) {
			    startServiceScan();
		    }
	    });

	    mEnsembleScanProgress = new ProgressDialog(this);
		mEnsembleScanProgress.setCancelable(true);
		mEnsembleScanProgress.setTitle("Scanning Ensembles");
		mEnsembleScanProgress.setOnDismissListener(new DialogInterface.OnDismissListener() {
			@Override
			public void onDismiss(DialogInterface dialogInterface) {
				for(Tuner tun : Radio.getInstance().getAvailableTuners()) {
					tun.stopRadioServiceScan();
				}
			}
		});

		/*
         * Ok, here is the real entry point for the RadioAPI
         * Ask the API for its status and handle it.
         * It will be in the state 'STATUS_RADIO_SUSPENDED' when it is not initialized. You have to initialize it, optionally with the Android App Context,
         * allowing you e.g. to persist the scanned services to the private App data directory without explicitly asking the WRITE_INTERNAL_ or WRITE_EXTERNAL_STORAGE
         * permission in your app manifest.
         * Be aware that this is a blocking call. If your Radio takes a long time to initialize you should do this in a separate thread or AsyncTask.
        */
        RadioStatus stat = Radio.getInstance().getRadioStatus();
        switch (stat) {
            case STATUS_RADIO_SUSPENDED: {
                RadioErrorCode initCode = Radio.getInstance().initialize(this);
                if(initCode == RadioErrorCode.ERROR_INIT_OK) {
                    Log.d(TAG, "Radio successfully initialized!");
                }
                break;
            }
            case STATUS_RADIO_RUNNING: {
                Log.d(TAG, "Great, the Radio is already running.");
                break;
            }
            default: {
                break;
            }
        }

		Radio.getInstance().registerRadioStatusListener(this);

        mServiceList.clear();
        mServiceList.addAll(Radio.getInstance().getRadioServices());
	    mRadioServiceAdapter.updateServiceList(mServiceList);

        /* The Radio is running, now we are getting the available Tuners.
         * Before we initialize a Tuner we should subscribe a TunerListener to it to get callbacks on Tuner state changes.
         */
        List<Tuner> tunerList = Radio.getInstance().getAvailableTuners();
        Log.d(TAG, "Found " + tunerList.size() + " Tuners!");
        for(Tuner tuner : tunerList) {
            Log.d(TAG, "TunerType: " + tuner.getTunerType().toString());
            Log.d(TAG, "TunerStatus: " + tuner.getTunerStatus().toString());

            tuner.subscribe(mTunerListener);
	        if(mRetainedFragment != null) {
		        tuner.subscribe(mRetainedFragment);
	        }

            if(tuner.getTunerStatus() == TunerStatus.TUNER_STATUS_NOT_INITIALIZED) {
                tuner.initializeTuner();
            }
        }
    }

    private void startServiceScan() {
	    Bundle scanoptionsBundle = new Bundle();
	    scanoptionsBundle.putBoolean(RadioImpl.SERVICE_SEARCH_OPT_USE_HRADIO, true);

	    AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(MainActivity.this);

	    List<Tuner> dabTuners = Radio.getInstance().getAvailableTuners(TunerType.TUNER_TYPE_DAB);
	    if(dabTuners.size() > 0) {
		    alertDialogBuilder.setMessage("Search only DAB services?");
		    alertDialogBuilder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
			    @Override
			    public void onClick(DialogInterface dialog, int which) {
				    clearMetadataViews();
				    for (Tuner tuner : Radio.getInstance().getAvailableTuners()) {
					    if (tuner.getTunerType() == TunerType.TUNER_TYPE_DAB) {
						    if (mRetainedFragment != null && mRetainedFragment.getTimeshiftPlayer() != null) {
							    mRetainedFragment.getTimeshiftPlayer().stop(true);
						    }

						    scanoptionsBundle.putBoolean(RadioImpl.SERVICE_SEARCH_OPT_DELETE_SERVICES, false);
						    scanoptionsBundle.putBoolean(RadioImpl.SERVICE_SEARCH_OPT_HYBRID_SCAN, true);
						    tuner.startRadioServiceScan(scanoptionsBundle);
						    break;
					    }
				    }
			    }
		    });
		    alertDialogBuilder.setNegativeButton("No", new DialogInterface.OnClickListener() {
			    @Override
			    public void onClick(DialogInterface dialog, int which) {
				    clearMetadataViews();
				    for (Tuner tuner : Radio.getInstance().getAvailableTuners()) {
					    if (tuner.getTunerType() == TunerType.TUNER_TYPE_DAB) {
						    if (mRetainedFragment != null && mRetainedFragment.getTimeshiftPlayer() != null) {
							    mRetainedFragment.getTimeshiftPlayer().stop(true);
						    }

						    scanoptionsBundle.putBoolean(RadioImpl.SERVICE_SEARCH_OPT_DELETE_SERVICES, false);
						    tuner.startRadioServiceScan(scanoptionsBundle);
						    break;
					    }
				    }

				    List<Tuner> ediTuners = Radio.getInstance().getAvailableTuners(TunerType.TUNER_TYPE_IP_EDI);
				    if (!ediTuners.isEmpty()) {
					    //scanoptionsBundle.putString("genres", "Rock");
					    scanoptionsBundle.putString("name", "*");
					    ediTuners.get(0).startRadioServiceScan(scanoptionsBundle);
				    }
			    }
		    });
	    } else {
		    alertDialogBuilder.setMessage("Search EDI services");
		    alertDialogBuilder.setPositiveButton("Okay", new DialogInterface.OnClickListener() {
			    @Override
			    public void onClick(DialogInterface dialog, int which) {
				    clearMetadataViews();

				    if (mRetainedFragment != null && mRetainedFragment.getTimeshiftPlayer() != null) {
					    mRetainedFragment.getTimeshiftPlayer().stop(true);
				    }

				    List<Tuner> ediTuners = Radio.getInstance().getAvailableTuners(TunerType.TUNER_TYPE_IP_EDI);
				    if (!ediTuners.isEmpty()) {
					    scanoptionsBundle.putBoolean(RadioImpl.SERVICE_SEARCH_OPT_DELETE_SERVICES, true);
					    scanoptionsBundle.putBoolean(RadioImpl.SERVICE_SEARCH_OPT_USE_HRADIO, true);
					    //scanoptionsBundle.putString("genres", "Rock");
					    scanoptionsBundle.putString("name", "*");
					    scanoptionsBundle.putString("bearers.mimeType", "*edi");
					    ediTuners.get(0).startRadioServiceScan(scanoptionsBundle);
				    }
			    }
		    });
	    }

	    AlertDialog alertDialog = alertDialogBuilder.create();
	    alertDialog.show();
    }

    private Menu mMenu = null;
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);

        mMenu = menu;
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_scan) {
        	startServiceScan();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy was called, isFinishing: " + isFinishing());

        /*
         * Unsubscribe your listeners
         * You may also save your last SLS and DLS and display it again after screen orientation change.
         */
	    if(isFinishing()) {
		    for (Tuner tuner : Radio.getInstance().getAvailableTuners()) {
			    tuner.unsubscribe(mTunerListener);
			    tuner.stopRadioService();
			    if(mRetainedFragment != null) {
				    tuner.unsubscribe(mRetainedFragment);
			    }
		    }

		    Radio.getInstance().unregisterRadioStatusListener(this);
		    Radio.getInstance().deInitialize();
	    }

    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

	    Log.d(TAG, "onSaveInstanceState was called: isFinishing: " + isFinishing() + ", isChangingConfigurations: " + isChangingConfigurations());

	    if(mLastVis != null) {
	    	outState.putSerializable("last_visual", (Serializable)mLastVis);
	    }
	    if(mLastText != null) {
	    	outState.putSerializable("last_text", (Serializable)mLastText);
	    }

	    outState.putInt("signalstat_visibility", mSignalStatView.getVisibility());
	    outState.putInt("scanbutton_visibility", mScanButton.getVisibility());

	    outState.putBoolean("drawerhint_shown", mDrawerHintShown);
    }

	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);

		Log.d(TAG, "onRestoreInstanceState was called!");

		if(mRetainedFragment != null && mRetainedFragment.getTimeshiftPlayer() != null) {
			//mServicelistFragment.serviceStarted(mRetainedFragment.getTimeshiftPlayer().getRadioService());
			mRadioServiceAdapter.setCurrentRadioService(mRetainedFragment.getTimeshiftPlayer().getRadioService());
			mRetainedFragment.getTimeshiftPlayer().addListener(mSkipItemAdapter);
			mRetainedFragment.getTimeshiftPlayer().addListener(this);
		}

		mLastVis = (Visual)savedInstanceState.getSerializable("last_visual");
		mLastText = (Textual)savedInstanceState.getSerializable("last_text");

		if(mLastVis != null) {
			visual(mLastVis);
		}
		if(mLastText != null) {
			textual(mLastText);
		}

		mSignalStatView.setVisibility(savedInstanceState.getInt("signalstat_visibility"));
		//mScanButton.setVisibility(savedInstanceState.getInt("scanbutton_visibility"));

	}

	@Override
	protected void onResume() {
		super.onResume();

		Log.d(TAG, "onResume was called!");

		if(mRetainedFragment != null && mRetainedFragment.getTimeshiftPlayer() != null) {
			mRadioServiceAdapter.setCurrentRadioService(mRetainedFragment.getTimeshiftPlayer().getRadioService());
			mRetainedFragment.getTimeshiftPlayer().addListener(mSkipItemAdapter);
			mRetainedFragment.getTimeshiftPlayer().addListener(this);
		}

		Radio.getInstance().registerRadioStatusListener(this);
		//Maybe resume to the last state of the radio
		//Radio.getInstance().resume();
	}

	private void clearMetadataViews() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
            	mLastVis = null;
            	mLastText = null;
                mDlsView.setText("");
                mSlsView.setImageResource(R.drawable.hradio_logo_anim_rotate);
	            Animatable animatable = (Animatable)mSlsView.getDrawable();
	            animatable.start();
            }
        });
    }

    /* TimshiftListener */
	@Override
	public void progress(final long curPos, final long totalDuration) {
		if(!mSeekFromUser) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					mTimeshiftSeekBar.setMax((int) totalDuration);
					mTimeshiftSeekBar.setProgress((int) curPos);

					StringBuilder posBuilder = new StringBuilder();
					StringBuilder totBuilder = new StringBuilder();

					if (curPos > 3600 || totalDuration > 3600) {
						posBuilder.append(String.format(Locale.getDefault(), "%02d", (curPos / 3600)));
						posBuilder.append(":");
						totBuilder.append(String.format(Locale.getDefault(), "%02d", (totalDuration / 3600)));
						totBuilder.append(":");
					}

					posBuilder.append(String.format(Locale.getDefault(), "%02d", ((curPos / 60) % 60)));
					posBuilder.append(":");
					posBuilder.append(String.format(Locale.getDefault(), "%02d", (curPos % 60)));

					totBuilder.append(String.format(Locale.getDefault(), "%02d", ((totalDuration / 60) % 60)));
					totBuilder.append(":");
					totBuilder.append(String.format(Locale.getDefault(), "%02d", (totalDuration % 60)));

					if (mTimeshiftProgressView != null) {
						mTimeshiftProgressView.setText(posBuilder.toString() + " : " + totBuilder.toString());
					}
				}
			});
		}
	}

	@Override
	public void sbtRealTime(long realTimePosix, long streamTimePosix, long curPos, long totalDuration) {
		Log.d(TAG, "SBT Realtime curPos: " + curPos + ", Total: " + totalDuration);
		if(!mSeekFromUser) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					mTimeshiftSeekBar.setMax((int) totalDuration);
					mTimeshiftSeekBar.setProgress((int) curPos);

					StringBuilder progressBuilder = new StringBuilder();

					SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss");

					progressBuilder.append(format.format(new Date(streamTimePosix)));
					progressBuilder.append(" : ");
					progressBuilder.append(format.format(new Date(realTimePosix)));

					if (mTimeshiftProgressView != null) {
						mTimeshiftProgressView.setText(progressBuilder.toString());
					}
				}
			});
		}
	}

	@Override
	public void started() {
		if(mTimeshiftPauseButton != null) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					mTimeshiftPauseButton.setImageResource(R.drawable.outline_pause_circle_outline_white_48);
				}
			});
		}
	}

	@Override
	public void paused() {
		if(mTimeshiftPauseButton != null) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					mTimeshiftPauseButton.setImageResource(R.drawable.outline_play_circle_filled_white_white_48);
				}
			});
		}
	}

	@Override
	public void stopped() {

	}

	@Override
	public void textual(final Textual timeshiftLabel) {
		mLastText = timeshiftLabel;
		if(mDlsView != null) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					if(timeshiftLabel.getType() == TextualType.METADATA_TEXTUAL_TYPE_DAB_DLS) {
						TextualDabDynamicLabel dls = (TextualDabDynamicLabel)timeshiftLabel;
						StringBuilder dlPlusBuilder = new StringBuilder();
						dlPlusBuilder.append(timeshiftLabel.getText());
						dlPlusBuilder.append("\n");
						if(dls.hasTags()) {
							for(TextualDabDynamicLabelPlusItem item : dls.getDlPlusItems()) {
								dlPlusBuilder.append(item.getDlPlusContentCategory());
								dlPlusBuilder.append(": ");
								dlPlusBuilder.append(item.getDlPlusContentText());
								dlPlusBuilder.append("\n");
							}

							dlPlusBuilder.append("ItemRunning: ");
							dlPlusBuilder.append(dls.itemRunning());
							dlPlusBuilder.append("\n");
							dlPlusBuilder.append("ItemToggle: ");
							dlPlusBuilder.append(dls.itemToggled());
						}

						mDlsView.setText(dlPlusBuilder.toString());
						Linkify.addLinks(mDlsView, Linkify.ALL);
					} else {
						mDlsView.setText(timeshiftLabel.getText());
					}
				}
			});
		}
	}

	@Override
	public void visual(final Visual timeshiftVis) {
		mLastVis = timeshiftVis;
		if(mSlsView != null) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					Log.d(TAG, "New Visual Received: " + timeshiftVis.getVisualType().toString() + " with DataSize of: " + timeshiftVis.getVisualData().length);
					Bitmap sls = BitmapFactory.decodeByteArray(timeshiftVis.getVisualData(), 0, timeshiftVis.getVisualData().length);
					if(sls != null) {
						Log.d(TAG, "Bitmap: " + sls.getWidth() + ":" + sls.getHeight());
						mSlsView.setImageBitmap(sls);
					} else {
						Log.e(TAG, "SLS Image is null!");
					}
				}
			});
		}
	}

	@Override
	public void skipItemAdded(final SkipItem skipItem) {
		if(DEBUG)Log.d(TAG, "SkipItem Added: " + skipItem.getSkipTextual().getText() + " : " + skipItem.getSkipPoint() + " : ListSize: " + mRetainedFragment.getTimeshiftPlayer().getSkipItems().size());
		if(mSkipItemRecyclerView != null) {
			if(mSkipItemRecyclerView.getVisibility() == View.INVISIBLE) {
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						if(DEBUG)Log.d(TAG, "Setting SkipItem Added Adapter visible");
						mSkipItemRecyclerView.setVisibility(View.VISIBLE);
					}
				});
			}
		}
	}

	@Override
	public void skipItemRemoved(SkipItem skipItem) {

	}

	@Override
	public void onSkipItemClicked(SkipItem clickedItem) {
		if(mRetainedFragment != null && mRetainedFragment.getTimeshiftPlayer() != null && clickedItem != null) {
			mRetainedFragment.getTimeshiftPlayer().skipTo(clickedItem);
		}
	}

	/* RadioServiceClickListener
	 * ServicelistListener to handle onClick events from our servicelist.
	 * When a servicelist entry was clicked this listener is called with the RadioService which was clicked.
	 * We first clear our SLS and DLS views and tell the Radio to start clicked service.
	 */
	@Override
	public void onRadioServiceClicked(RadioService service) {
		Log.d(TAG, "Service Selected!");

		if(mRunningSrv != null) {
			if(!mRunningSrv.equalsRadioService(service)) {
				clearMetadataViews();
			}
		}

		Radio.getInstance().startRadioService(service);

		if(mDrawerLayout != null) {
			if(mDrawerLayout.isDrawerOpen(Gravity.START)) {
				mDrawerLayout.closeDrawer(Gravity.START);
			}
		}
	}

	/* Just an example on how to delete with an internal deleteRadioService method  */
	@Override
	public void onRadioServiceLongClicked(RadioService service) {
		if(service != null) {
			Log.d(TAG, "Service longClicked, deleting");
			boolean delSuccess = ((RadioImpl) Radio.getInstance()).deleteRadioService(service);
			if(delSuccess && mRadioServiceAdapter != null) {
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						mRadioServiceAdapter.serviceRemoved(service);
					}
				});
			}
		}
	}
}
