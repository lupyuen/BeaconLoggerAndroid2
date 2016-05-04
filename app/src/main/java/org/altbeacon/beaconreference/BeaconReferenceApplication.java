package org.altbeacon.beaconreference;

import android.app.Activity;
import android.app.Application;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.Identifier;
import org.altbeacon.beacon.MonitorNotifier;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;
import org.altbeacon.beacon.powersave.BackgroundPowerSaver;
import org.altbeacon.beacon.startup.RegionBootstrap;
import org.altbeacon.beacon.startup.BootstrapNotifier;

import java.text.DecimalFormat;
import java.util.Collection;
import java.util.HashSet;
import java.util.Hashtable;

public class BeaconReferenceApplication extends Application implements BootstrapNotifier {
    private static final String TAG = "BeaconReferenceApp";
    private RegionBootstrap regionBootstrap;
    private BackgroundPowerSaver backgroundPowerSaver;
    private boolean haveDetectedBeaconsSinceBoot = false;
    private MonitoringActivity monitoringActivity = null;

    //  Added by Lup Yuen.
    static final HashSet<String> defaultBeacons = new HashSet<String>() {{ add("b9407f30-f5f8-466e-aff9-25556b57fe6d"); }};  //  Estimote.
    static final String beaconIdentifierPrefix = "com.appkaki.makanpoints";
    static int beaconIdentifierIndex = 0;
    static HashSet<String> allBeacons = new HashSet<String>();
    static Hashtable<String, Region> allRegions = new Hashtable<>();
    static Hashtable<String, RegionBootstrap> allRegionBootstraps = new Hashtable<>();
    static HashSet<String> activeRegions = new HashSet<>();  //  Regions that the user is currently in.
    static HashSet<String> activeBeacons = new HashSet<>();  //  All the beacons that have been detected.
    static Hashtable<String, Double> activeBeaconsDistance = new Hashtable<>();  //  The min distances of all beacons detected.

    private BeaconManager beaconManager = null;
    private Activity activity = null;
    private Application application = null;

    void registerBeacons() {
        //  Register beacons to be detected.
        //  Register the default beacons.
        for (String beaconuuid: defaultBeacons) {
            registerBeacon(beaconuuid);
        }
        ////  TODO: Get the beacons from the server.  Comma-separated string.
        ////final String beacons = config.getString("monitorBeacons");
        final String beacons = "8492e75f-4fd6-469d-b132-043fe94921d8";  //  Estimote simulator.
        if (beacons != null && beacons.length() > 0) {
            Logger req = Logger.startLog(TAG + "_registerBeacons", new Hashtable<String, Object>() {{ put("beacons", beacons); }});
            for (String beaconuuid: beacons.split(",")) {
                registerBeacon(beaconuuid);
            }
        }
    }

    void registerBeacon(final String beaconuuid) {
        //  Register the beacon for region monitoring and bootstrap.
        Logger req = Logger.startLog(TAG + "_registerBeacon", new Hashtable<String, Object>() {{ put("beaconuuid", beaconuuid); }});
        try {
            if (allBeacons.contains(beaconuuid)) {
                ////req.success("Already registered, skipping " + beaconuuid, new Hashtable<String, Object>() {{ put("beaconuuid", beaconuuid); }});
                return;
            }
            final String beaconregion = beaconIdentifierPrefix + beaconIdentifierIndex;
            beaconIdentifierIndex++;
            Region region = new Region(beaconregion, Identifier.parse(beaconuuid), null, null);
            RegionBootstrap regionBootstrap = new RegionBootstrap(this, region);
            allBeacons.add(beaconuuid);
            allRegions.put(beaconregion, region);
            allRegionBootstraps.put(beaconregion, regionBootstrap);
            req.success("Registered " + beaconuuid + " with region " + beaconregion, new Hashtable<String, Object>() {{ put("beaconuuid", beaconuuid); put("beaconregion", beaconregion); }});
        }
        catch (Exception ex) {
            req.error(ex, new Hashtable<String, Object>() {{ put("beaconuuid", beaconuuid); }});
        }
    }


    //////////////////////////////////////////////////////////////


    //  This is the original demo code.
    public void onCreate() {
        super.onCreate();
        beaconManager = org.altbeacon.beacon.BeaconManager.getInstanceForApplication(this);

        // By default the AndroidBeaconLibrary will only find AltBeacons.  If you wish to make it
        // find a different type of beacon, you must specify the byte layout for that beacon's
        // advertisement with a line like below.  The example shows how to find a beacon with the
        // same byte layout as AltBeacon but with a beaconTypeCode of 0xaabb.  To find the proper
        // layout expression for other beacon types, do a web search for "setBeaconLayout"
        // including the quotes.
        //
        //beaconManager.getBeaconParsers().clear();
        //beaconManager.getBeaconParsers().add(new BeaconParser().
        //        setBeaconLayout("m:2-3=beac,i:4-19,i:20-21,i:22-23,p:24-24,d:25-25"));

        //  For Apple iBeacon specification.  Note the final "d" field.
        beaconManager.getBeaconParsers().clear();
        beaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24,d:25-25"));

        Log.i(TAG, "setting up background monitoring for beacons and power saving");
        // wake up the app when a beacon is seen
        registerBeacons();
        /*
        Region region = new Region("backgroundRegion",
                null, null, null);
        regionBootstrap = new RegionBootstrap(this, region);
        */

        // simply constructing this class and holding a reference to it in your custom Application
        // class will automatically cause the BeaconLibrary to save battery whenever the application
        // is not visible.  This reduces bluetooth power usage by about 60%
        ////  TODO: Why does this crash?
        backgroundPowerSaver = new BackgroundPowerSaver(this);

        // If you wish to test beacon detection in the Android Emulator, you can use code like this:
        // BeaconManager.setBeaconSimulator(new TimedBeaconSimulator() );
        // ((TimedBeaconSimulator) BeaconManager.getBeaconSimulator()).createTimedSimulatedBeacons();
    }

    @Override
    public void didEnterRegion(Region arg0) {
        // In this example, this class sends a notification to the user whenever a Beacon
        // matching a Region (defined above) are first seen.
        Log.i(TAG, "did enter region.");
        if (!haveDetectedBeaconsSinceBoot) {
            Log.i(TAG, "auto launching MainActivity");

            // The very first time since boot that we detect an beacon, we launch the
            // MainActivity
            Intent intent = new Intent(this, MonitoringActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            // Important:  make sure to add android:launchMode="singleInstance" in the manifest
            // to keep multiple copies of this activity from getting created if the user has
            // already manually launched the app.
            this.startActivity(intent);
            haveDetectedBeaconsSinceBoot = true;
        } else {
            if (monitoringActivity != null) {
                // If the Monitoring Activity is visible, we log info about the beacons we have
                // seen on its display
                monitoringActivity.logToDisplay("I see a beacon again" );
            } else {
                // If we have already seen beacons before, but the monitoring activity is not in
                // the foreground, we send a notification to the user on subsequent detections.
                Log.i(TAG, "Sending notification.");
                sendNotification();
            }
        }


    }

    @Override
    public void didExitRegion(Region region) {
        if (monitoringActivity != null) {
            monitoringActivity.logToDisplay("I no longer see a beacon.");
        }
    }

    @Override
    public void didDetermineStateForRegion(int state, Region region) {
        if (monitoringActivity != null) {
            monitoringActivity.logToDisplay("I have just switched from seeing/not seeing beacons: " + state);
        }
    }

    private void sendNotification() {
        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this)
                        .setContentTitle("Beacon Reference Application")
                        .setContentText("An beacon is nearby.")
                        .setSmallIcon(R.drawable.ic_launcher);

        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        stackBuilder.addNextIntent(new Intent(this, MonitoringActivity.class));
        PendingIntent resultPendingIntent =
                stackBuilder.getPendingIntent(
                        0,
                        PendingIntent.FLAG_UPDATE_CURRENT
                );
        builder.setContentIntent(resultPendingIntent);
        NotificationManager notificationManager =
                (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(1, builder.build());
    }

    public void setMonitoringActivity(MonitoringActivity activity) {
        this.monitoringActivity = activity;
    }

}