package org.altbeacon.beaconreference;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.util.Log;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.Identifier;
import org.altbeacon.beacon.MonitorNotifier;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;
import org.altbeacon.beacon.startup.BootstrapNotifier;
import org.altbeacon.beacon.startup.RegionBootstrap;

import java.text.DecimalFormat;
import java.util.*;

/**
 * Created by Luppy on 26/4/15.
 */
public class BeaconController implements BeaconConsumer, BootstrapNotifier {
    //  This class manages the monitoring and ranging of beacons.
    protected static final String TAG = "BeaconController";
    static final HashSet<String> defaultBeacons = new HashSet<String>() {{ add("b9407f30-f5f8-466e-aff9-25556b57fe6d"); }};
    static final String beaconIdentifierPrefix = "com.appkaki.makanpoints";
    static int beaconIdentifierIndex = 0;
    static HashSet<String> allBeacons = new HashSet<>();
    static Hashtable<String, Region> allRegions = new Hashtable<>();
    static HashSet<String> activeRegions = new HashSet<>();  //  Regions that the user is currently in.
    static HashSet<String> activeBeacons = new HashSet<>();  //  All the beacons that have been detected.
    static Hashtable<String, Double> activeBeaconsDistance = new Hashtable<>();  //  The min distances of all beacons detected.
    static boolean haveDetectedBeaconsSinceBoot = false;
    static ArrayList<Region> allRegions2 = new ArrayList<>();

    private BeaconManager beaconManager = null;
    public static MonitoringActivity activity = null;
    private Application application = null;
    static RegionBootstrap regionBootstrap = null;

    public BeaconController(Application application0) {
        application = application0;
        Logger req = Logger.startLog(TAG + "_BeaconController");
        try {
            beaconManager = BeaconManager.getInstanceForApplication(application);
            beaconManager.bind(this);

            // By default the AndroidBeaconLibrary will only find AltBeacons.  If you wish to make it
            // find a different type of beacon, you must specify the byte layout for that beacon's
            // advertisement with a line like below.  The example shows how to find a beacon with the
            // same byte layout as AltBeacon but with a beaconTypeCode of 0xaabb.  To find the proper
            // layout expression for other beacon types, do a web search for "setBeaconLayout"
            // including the quotes.
            // beaconManager.getBeaconParsers().add(new BeaconParser().
            //        setBeaconLayout("m:2-3=aabb,i:4-19,i:20-21,i:22-23,p:24-24,d:25-25"));

            //  For Apple iBeacon specification.  Note the final "d" field.
            beaconManager.getBeaconParsers().clear();
            beaconManager.getBeaconParsers().add(new LoggingBeaconParser().setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24,d:25-25"));

            //  Wake up the app when a beacon is seen.
            registerBeacons();

            // If you wish to test beacon detection in the Android Emulator, you can use code like this:
            // BeaconManager.setBeaconSimulator(new TimedBeaconSimulator() );
            // ((TimedBeaconSimulator) BeaconManager.getBeaconSimulator()).createTimedSimulatedBeacons();
        }
        catch (Exception ex) {
        }
    }

    void registerBeacons() {
        //  Register beacons to be detected.
        Logger req = Logger.startLog(TAG + "_registerBeacons");
        if (regionBootstrap != null) {
            req.success("Already registered, skipping");
            return;
        }
        //  Register the default beacons.
        for (String beaconuuid: defaultBeacons) {
            registerBeacon(beaconuuid);
        }
        ////  TODO: Get the beacons from the server.  Comma-separated string.
        ////final String beacons = config.getString("monitorBeacons");
        final String beacons = "8492e75f-4fd6-469d-b132-043fe94921d8";  //  Estimote simulator.
        if (beacons != null && beacons.length() > 0) {
            req.log(TAG + "_registerBeacons", new Hashtable<String, Object>() {{ put("beacons", beacons); }});
            for (String beaconuuid: beacons.split(",")) {
                registerBeacon(beaconuuid);
            }
        }
        /*
            Applications that implement the library’s RegionBootstrap class will automatically restart in the b
            ackground to look for beacons as soon as possible. This restart is guaranteed to happen after reboot or
            after connecting/disconnecting the device to a charger. (The latter guarantee is implemented in library
            version 1.1.4 or higher.) Since users must typically charge their devices once per day, this means that
            applications implementing the RegionBootstrap will be looking for beacons each day the device is powered,
            and will typically continue to do so unless the user does a force stop of the app on that day.

            IMPORTANT NOTE: The RegionBootstrap class registers an internal MonitorNotifier with the BeaconManager.
            If you use the RegionBootstrap, your application must not manually register a second MonitorNotifier,
            otherwise it will unregister the one configured by the RegionBootstrap, effectively disabling it. When
            using the RegionBootstrap, any custom monitoring code must therefore be placed in the callback methods in
            the BootstrapNotifier implementation passed to the RegionBootstrap.
         */
        regionBootstrap = new RegionBootstrap(this, allRegions2);
    }

    void registerBeacon(final String beaconuuid) {
        //  Register the beacon for region monitoring and bootstrap.
        Logger req = Logger.startLog(TAG + "_registerBeacon", new Hashtable<String, Object>() {{ put("beaconuuid", beaconuuid); }});
        try {
            if (allBeacons.contains(beaconuuid)) {
                req.success("Already registered, skipping " + beaconuuid, new Hashtable<String, Object>() {{ put("beaconuuid", beaconuuid); }});
                return;
            }
            final String beaconregion = beaconIdentifierPrefix + beaconIdentifierIndex;
            beaconIdentifierIndex++;
            Region region = new Region(beaconregion, Identifier.parse(beaconuuid), null, null);
            allBeacons.add(beaconuuid);
            allRegions.put(beaconregion, region);
            allRegions2.add(region);
            req.success("Registered " + beaconuuid + " with region " + beaconregion, new Hashtable<String, Object>() {{ put("beaconuuid", beaconuuid); put("beaconregion", beaconregion); }});
        }
        catch (Exception ex) {
            req.error(ex, new Hashtable<String, Object>() {{ put("beaconuuid", beaconuuid); }});
        }
    }

    @Override
    public void didEnterRegion(final Region region) {
        // In this example, this class sends a notification to the user whenever a Beacon
        // matching a Region (defined above) are first seen.
        final String[] regionInfo = getRegionInfo(region); final String beaconregion = regionInfo[0]; final String beaconuuid = regionInfo[1]; final String beaconmajor = regionInfo[2]; final String beaconminor = regionInfo[3];
        final Logger req = Logger.startLog(TAG + "_didEnterRegion", new Hashtable<String, Object>() {{ put("beaconregion", beaconregion); put("beaconuuid", beaconuuid); put("beaconmajor", beaconmajor); put("beaconminor", beaconminor); }});
        try {
            //  Range all regions briefly.
            //  TODO: Don't range because LoggingBeaconParser will log all beacons.
            ////rangeBeaconsBriefly();

            // The very first time since boot that we detect an beacon, we launch the MainActivity
            // Important:  make sure to add android:launchMode="singleInstance" in the manifest
            // to keep multiple copies of this activity from getting created if the user has
            // already manually launched the app.
            Intent intent = new Intent(application, MonitoringActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            application.startActivity(intent);
            req.success("Launched MainActivity");

            /*  Original code:
            if (!haveDetectedBeaconsSinceBoot) {
                haveDetectedBeaconsSinceBoot = true;
                // The very first time since boot that we detect an beacon, we launch the MainActivity
                // Important:  make sure to add android:launchMode="singleInstance" in the manifest
                // to keep multiple copies of this activity from getting created if the user has
                // already manually launched the app.
                Intent intent = new Intent(application, MonitoringActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                application.startActivity(intent);
                req.success("Launched MainActivity");
            }
            else {
                if (activity != null) {
                    // If the Monitoring Activity is visible, we log info about the beacons we have
                    // seen on its display
                    activity.didEnterRegion(region);
                } else {
                    // If we have already seen beacons before, but the monitoring activity is not in
                    // the foreground, we send a notification to the user on subsequent detections.
                    //Log.d(TAG, "Sending notification.");
                    //sendNotification();
                }
            }
            */
            activeRegions.add(beaconregion);
            final StringBuilder activeRegionsStr = new StringBuilder();
            for (String s: activeRegions)  { activeRegionsStr.append(s + ","); }
            req.success(new Hashtable<String, Object>() {{ put("activeRegions", activeRegionsStr.toString()); put("beaconregion", beaconregion); put("beaconuuid", beaconuuid); put("beaconmajor", beaconmajor); put("beaconminor", beaconminor); }});
        }
        catch (Exception ex) {
            req.error(ex, new Hashtable<String, Object>() {{ put("beaconregion", beaconregion); put("beaconuuid", beaconuuid); put("beaconmajor", beaconmajor); put("beaconminor", beaconminor); }});
        }
    }

    @Override
    public void didExitRegion(Region region) {
        final String[] regionInfo = getRegionInfo(region); final String beaconregion = regionInfo[0]; final String beaconuuid = regionInfo[1]; final String beaconmajor = regionInfo[2]; final String beaconminor = regionInfo[3];
        final Logger req = Logger.startLog(TAG + "_didExitRegion", new Hashtable<String, Object>() {{ put("beaconregion", beaconregion); put("beaconuuid", beaconuuid); put("beaconmajor", beaconmajor); put("beaconminor", beaconminor); }});
        try {
            //  Range all regions briefly.
            //  TODO: Don't range because LoggingBeaconParser will log all beacons.
            ////rangeBeaconsBriefly();
            if (activeRegions.contains(beaconregion)) activeRegions.remove(beaconregion);
            final StringBuilder activeRegionsStr = new StringBuilder();
            for (String s: activeRegions)  { activeRegionsStr.append(s + ","); }
            if (activity != null) {
                // If the Monitoring Activity is visible, we log info about the beacons we have
                // seen on its display
                activity.didExitRegion(region);
            }
            req.success(new Hashtable<String, Object>() {{ put("activeRegions", activeRegionsStr.toString()); put("beaconregion", beaconregion); put("beaconuuid", beaconuuid); put("beaconmajor", beaconmajor); put("beaconminor", beaconminor); }});
        }
        catch (Exception ex) {
            req.error(ex, new Hashtable<String, Object>() {{ put("beaconregion", beaconregion); put("beaconuuid", beaconuuid); put("beaconmajor", beaconmajor); put("beaconminor", beaconminor); }});
        }
    }

    @Override
    public void didDetermineStateForRegion(final int state, Region region) {
        final String[] regionInfo = getRegionInfo(region); final String beaconregion = regionInfo[0]; final String beaconuuid = regionInfo[1]; final String beaconmajor = regionInfo[2]; final String beaconminor = regionInfo[3];
        final Logger req = Logger.startLog(TAG + "didDetermineStateForRegion", new Hashtable<String, Object>() {{ put("state", state); put("beaconregion", beaconregion); put("beaconuuid", beaconuuid); put("beaconmajor", beaconmajor); put("beaconminor", beaconminor); }});
        try {
            req.success(new Hashtable<String, Object>() {{ put("state", state); put("beaconregion", beaconregion); put("beaconuuid", beaconuuid); put("beaconmajor", beaconmajor); put("beaconminor", beaconminor); }});
        }
        catch (Exception ex) {
            req.error(ex, new Hashtable<String, Object>() {{ put("state", state); put("beaconregion", beaconregion); put("beaconuuid", beaconuuid); put("beaconmajor", beaconmajor); put("beaconminor", beaconminor); }});
        }
    }

    @Override
    public void onBeaconServiceConnect() {
        final Logger req = Logger.startLog(TAG + "_onBeaconServiceConnect");
        try {
            //  RegionBootstrap doesn't allow us to register another monitor notifier, so this code is commented out.
            req.success();
        }
        catch (Exception ex) {
            req.error(ex);
        }
    }

    public static String[] getRegionInfo(Region region) {
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

    @Override
    public Context getApplicationContext() {
        //if (activity != null) return activity.getApplicationContext();
        if (application != null) return application.getApplicationContext();
        Logger.startError(TAG + "getApplicationContext", new Exception("Application is null"));
        return null;
    }

    @Override
    public void unbindService(ServiceConnection serviceConnection) {
        //if (activity != null) { activity.unbindService(serviceConnection); return; }
        if (application != null) { application.unbindService(serviceConnection); return; }
        Logger.startError(TAG + "unbindService", new Exception("Application is null"));
    }

    @Override
    public boolean bindService(Intent intent, ServiceConnection serviceConnection, int i) {
        //if (activity != null) return activity.bindService(intent, serviceConnection, i);
        if (application != null) return application.bindService(intent, serviceConnection, i);
        Logger.startError(TAG + "bindService", new Exception("Application is null"));
        return false;
    }

}
