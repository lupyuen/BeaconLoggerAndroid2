package org.altbeacon.beaconreference;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.util.Log;
import android.view.View;
import android.widget.EditText;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.MonitorNotifier;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;

import java.util.Collection;
import java.util.Hashtable;

/**
 * 
 * @author dyoung
 * @author Matt Tyler
 */
public class MonitoringActivity extends Activity
    implements BeaconConsumer ////
{
	protected static final String TAG = "MonitoringActivity";
	private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Log.d(TAG, "onCreate");
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_monitoring);
		verifyBluetooth();
        logToDisplay("Application just launched");

		////  TODO
		beaconManager.bind(this);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android M Permission check
            if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("This app needs location access");
                builder.setMessage("Please grant location access so this app can detect beacons in the background.");
                builder.setPositiveButton(android.R.string.ok, null);
                builder.setOnDismissListener(new DialogInterface.OnDismissListener() {

                    @TargetApi(23)
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                                PERMISSION_REQUEST_COARSE_LOCATION);
                    }

                });
                builder.show();
            }
        }
	}

	@Override
	public void onRequestPermissionsResult(int requestCode,
										   String permissions[], int[] grantResults) {
		switch (requestCode) {
			case PERMISSION_REQUEST_COARSE_LOCATION: {
				if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
					Log.d(TAG, "coarse location permission granted");
				} else {
					final AlertDialog.Builder builder = new AlertDialog.Builder(this);
					builder.setTitle("Functionality limited");
					builder.setMessage("Since location access has not been granted, this app will not be able to discover beacons when in the background.");
					builder.setPositiveButton(android.R.string.ok, null);
					builder.setOnDismissListener(new DialogInterface.OnDismissListener() {

						@Override
						public void onDismiss(DialogInterface dialog) {
						}

					});
					builder.show();
				}
				return;
			}
		}
	}

	public void onRangingClicked(View view) {
		Intent myIntent = new Intent(this, RangingActivity.class);
		this.startActivity(myIntent);
	}

    @Override
    public void onResume() {
        super.onResume();
        ((BeaconReferenceApplication) this.getApplicationContext()).setMonitoringActivity(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        ((BeaconReferenceApplication) this.getApplicationContext()).setMonitoringActivity(null);
    }

	private void verifyBluetooth() {

		try {
			if (!BeaconManager.getInstanceForApplication(this).checkAvailability()) {
                Log.e(TAG, "No Bluetooth access???");
				final AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setTitle("Bluetooth not enabled");			
				builder.setMessage("Please enable bluetooth in settings and restart this application.");
				builder.setPositiveButton(android.R.string.ok, null);
				builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
					@Override
					public void onDismiss(DialogInterface dialog) {
						finish();
			            System.exit(0);					
					}					
				});
				builder.show();
			}
		}
		catch (RuntimeException e) {
			final AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle("Bluetooth LE not available");			
			builder.setMessage("Sorry, this device does not support Bluetooth LE.");
			builder.setPositiveButton(android.R.string.ok, null);
			builder.setOnDismissListener(new DialogInterface.OnDismissListener() {

				@Override
				public void onDismiss(DialogInterface dialog) {
					finish();
		            System.exit(0);					
				}
				
			});
			builder.show();
			
		}
		
	}	

    public void logToDisplay(final String line) {
    	runOnUiThread(new Runnable() {
    	    public void run() {
    	    	EditText editText = (EditText)MonitoringActivity.this
    					.findViewById(R.id.monitoringText);
       	    	editText.append(line+"\n");            	    	    		
    	    }
    	});
    }

	//  Lup Yuen: Added code:

	private BeaconManager beaconManager = BeaconManager.getInstanceForApplication(this);

	@Override
	public void onBeaconServiceConnect() {
		beaconManager.setRangeNotifier(new RangeNotifier() {
			@Override
			public void didRangeBeaconsInRegion(Collection<Beacon> beacons, Region region) {
				if (beacons.size() > 0) {
					//EditText editText = (EditText)RangingActivity.this.findViewById(R.id.rangingText);
					Beacon firstBeacon = beacons.iterator().next();
					logToDisplay("The first beacon " + firstBeacon.toString() + " is about " + firstBeacon.getDistance() + " meters away.");
				}
			}

		});

		beaconManager.setMonitorNotifier(new MonitorNotifier() {
			@Override
			public void didEnterRegion(Region region) {
				final String[] regionInfo = getRegionInfo(region); final String beaconregion = regionInfo[0]; final String beaconuuid = regionInfo[1]; final String beaconmajor = regionInfo[2]; final String beaconminor = regionInfo[3];
				final Logger req = Logger.startLog(TAG + "_didEnterRegion2", new Hashtable<String, Object>() {{ put("beaconregion", beaconregion); put("beaconuuid", beaconuuid); put("beaconmajor", beaconmajor); put("beaconminor", beaconminor); }});
				////  TODO: MainApplication.beaconController.didEnterRegion(region);
			}

			@Override
			public void didExitRegion(Region region) {
				final String[] regionInfo = getRegionInfo(region); final String beaconregion = regionInfo[0]; final String beaconuuid = regionInfo[1]; final String beaconmajor = regionInfo[2]; final String beaconminor = regionInfo[3];
				final Logger req = Logger.startLog(TAG + "_didExitRegion2", new Hashtable<String, Object>() {{ put("beaconregion", beaconregion); put("beaconuuid", beaconuuid); put("beaconmajor", beaconmajor); put("beaconminor", beaconminor); }});
				////  TODO: MainApplication.beaconController.didExitRegion(region);
			}

			@Override
			public void didDetermineStateForRegion(int state, Region region) {
				final Logger req = Logger.startLog(TAG + "_didDetermineStateForRegion2");
				////  TODO: MainApplication.beaconController.didDetermineStateForRegion(state, region);
			}
		});
	}


	public String[] getRegionInfo(Region region) {
		//  Return the region ID, UUID, major, minor for the region.
		String beaconregion = ""; String beaconuuid = ""; String beaconmajor = ""; String beaconminor = "";
		try {
			if (region != null) {
				if (region.getUniqueId() != null) beaconregion = region.getUniqueId();
				if (region.getId1() != null) beaconuuid = region.getId1().toHexString();
				if (region.getId2() != null) beaconmajor = region.getId2().toString();
				if (region.getId3() != null) beaconminor = region.getId3().toString();
			}
		}
		catch (Exception ex) {
			Log.e(TAG, "getRegionInfo: " + ex.toString());
		}
		return new String[] { beaconregion, beaconuuid, beaconmajor, beaconminor };
	}
}
