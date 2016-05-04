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
import java.util.Collection;
import java.util.HashSet;
import java.util.Hashtable;

/**
 * Created by Luppy on 26/4/15.
 */
public class BeaconController implements BeaconConsumer, BootstrapNotifier {
    //  Manages the ranging and detection of beacons.
    protected static final String TAG = "BeaconController";
    static final HashSet<String> defaultBeacons = new HashSet<String>() {{ add("b9407f30-f5f8-466e-aff9-25556b57fe6d"); }};
    static final String beaconIdentifierPrefix = "com.appkaki.makanpoints";
    static int beaconIdentifierIndex = 0;
    static HashSet<String> allBeacons = new HashSet<String>();
    static Hashtable<String, Region> allRegions = new Hashtable<>();
    static Hashtable<String, RegionBootstrap> allRegionBootstraps = new Hashtable<>();
    static HashSet<String> activeRegions = new HashSet<>();  //  Regions that the user is currently in.
    static HashSet<String> activeBeacons = new HashSet<>();  //  All the beacons that have been detected.
    static Hashtable<String, Double> activeBeaconsDistance = new Hashtable<>();  //  The min distances of all beacons detected.
    static boolean haveDetectedBeaconsSinceBoot = false;

    private BeaconManager beaconManager = null;
    public static MonitoringActivity activity = null;
    private Application application = null;

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
            beaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24,d:25-25"));

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
                req.success("Already registered, skipping " + beaconuuid, new Hashtable<String, Object>() {{ put("beaconuuid", beaconuuid); }});
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

    //  Range all beacons for a short while then stop.
    public void rangeBeaconsBriefly() {
        final Logger req = Logger.startLog(TAG + "_rangeBeaconsBriefly");
        startRangingBeacons();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(10 * 1000);
                } catch (InterruptedException e) {
                }
                final String beacons = BeaconReferenceApplication.beaconController.stopRangingBeacons();
                //  Report to activity about the beacons detected.
                if (activity != null) {
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            activity.didDetectBeacons(beacons);
                        }
                    });
                }
                req.success();
            }
        }).start();
    }

    public void startRangingBeacons() {
        //  Monitor and range all the beacons that we know.
        Logger req = Logger.startLog(TAG + "_startRangingBeacons");
        try {
            activeBeacons = new HashSet<>();
            activeBeaconsDistance = new Hashtable<>();
            int count = 0;
            for (Region region: BeaconController.allRegions.values()) {
                beaconManager.startRangingBeaconsInRegion(region);
                count++;

                final String[] regionInfo = getRegionInfo(region); final String beaconregion = regionInfo[0]; final String beaconuuid = regionInfo[1]; final String beaconmajor = regionInfo[2]; final String beaconminor = regionInfo[3];
                req.log(new Hashtable<String, Object>() {{ put("result", "Started ranging"); put("beaconregion", beaconregion); put("beaconuuid", beaconuuid); put("beaconmajor", beaconmajor); put("beaconminor", beaconminor); }});
            }
            req.success("Started ranging " + count + " regions");
        }
        catch (Exception ex) {
            req.error(ex);
        }
    }

    public String stopRangingBeacons() {
        //  Stop ranging beacons and return the beacons found.
        StringBuilder result = new StringBuilder();
        Logger req = Logger.startLog(TAG + "_stopRangingBeacons");
        try {
            int count = 0;
            for (Region region: BeaconController.allRegions.values()) {
                beaconManager.stopRangingBeaconsInRegion(region);
                count++;
                final String[] regionInfo = getRegionInfo(region); final String beaconregion = regionInfo[0]; final String beaconuuid = regionInfo[1]; final String beaconmajor = regionInfo[2]; final String beaconminor = regionInfo[3];
                req.log(new Hashtable<String, Object>() {{ put("result", "Stopped ranging"); put("beaconregion", beaconregion); put("beaconuuid", beaconuuid); put("beaconmajor", beaconmajor); put("beaconminor", beaconminor); }});
            }
            req.success("Stopped ranging " + count + " regions");
            for (String beaconid: activeBeacons) {
                //  Append the beacon ID and distance.
                double dist = -1;
                if (activeBeaconsDistance.containsKey(beaconid))
                    dist = activeBeaconsDistance.get(beaconid);
                if (result.length() > 0) result.append("|");
                result.append(beaconid + "," + new DecimalFormat("0.0").format(dist));
            }
            req.success(result.toString());
        }
        catch (Exception ex) {
            req.error(ex);
        }
        return result.toString();
    }

    @Override
    public void didEnterRegion(final Region region) {
        // In this example, this class sends a notification to the user whenever a Beacon
        // matching a Region (defined above) are first seen.
        final String[] regionInfo = getRegionInfo(region); final String beaconregion = regionInfo[0]; final String beaconuuid = regionInfo[1]; final String beaconmajor = regionInfo[2]; final String beaconminor = regionInfo[3];
        final Logger req = Logger.startLog(TAG + "_didEnterRegion", new Hashtable<String, Object>() {{ put("beaconregion", beaconregion); put("beaconuuid", beaconuuid); put("beaconmajor", beaconmajor); put("beaconminor", beaconminor); }});
        try {
            //  Range all regions briefly.
            rangeBeaconsBriefly();
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
            rangeBeaconsBriefly();
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

    public void processBeaconsInRange(final Collection<Beacon> beacons, Region region) {
        //  Process the beacons that have been ranged.
        if (beacons.size() == 0) return;
        final Logger req = Logger.startLog(TAG + "_processBeaconsInRange", new Hashtable<String, Object>() {{ put("beaconCount", beacons.size()); }});
        for (Beacon beacon: beacons) {
            final String[] beaconInfo = getBeaconInfo(beacon);
            final String beaconuuid = beaconInfo[0];
            final String beaconmajor = beaconInfo[1];
            final String beaconminor = beaconInfo[2];
            final String beacondistance = beaconInfo[3];
            final String beaconrssi = beaconInfo[4];
            String beaconid = beaconuuid + "," + beaconmajor + "," + beaconminor;
            activeBeacons.add(beaconid);
            //  Remember the max distance for the beacon.
            if (!activeBeaconsDistance.containsKey(beaconid)) {
                activeBeaconsDistance.put(beaconid, Double.parseDouble(beacondistance));
            }
            else {
                Double dist = activeBeaconsDistance.get(beaconid);
                if (Double.parseDouble(beacondistance) < dist)
                    activeBeaconsDistance.put(beaconid, Double.parseDouble(beacondistance));
            }
            req.log(new Hashtable<String, Object>() {{
                put("beaconuuid", beaconuuid);
                put("beaconmajor", beaconmajor);
                put("beaconminor", beaconminor);
                put("beacondistance", beacondistance);
                put("beaconrssi", beaconrssi);
            }});
        }
        req.success(new Hashtable<String, Object>() {{
            put("beaconCount", beacons.size());
        }});
    }

    @Override
    public void onBeaconServiceConnect() {
        final Logger req = Logger.startLog(TAG + "_onBeaconServiceConnect");
        try {
            beaconManager.setRangeNotifier(new RangeNotifier() {
                @Override
                public void didRangeBeaconsInRegion(Collection<Beacon> beacons, Region region) {
                    if (beacons.size() == 0) return;
                    final Logger req = Logger.startLog(TAG + "_didRangeBeaconsInRegion");
                    BeaconController.this.processBeaconsInRange(beacons, region);
                }
            });
            beaconManager.setMonitorNotifier(new MonitorNotifier() {
                @Override
                public void didEnterRegion(Region region) {
                    final String[] regionInfo = getRegionInfo(region); final String beaconregion = regionInfo[0]; final String beaconuuid = regionInfo[1]; final String beaconmajor = regionInfo[2]; final String beaconminor = regionInfo[3];
                    final Logger req = Logger.startLog(TAG + "_didEnterRegion2", new Hashtable<String, Object>() {{ put("beaconregion", beaconregion); put("beaconuuid", beaconuuid); put("beaconmajor", beaconmajor); put("beaconminor", beaconminor); }});
                    BeaconReferenceApplication.beaconController.didEnterRegion(region);
                }

                @Override
                public void didExitRegion(Region region) {
                    final String[] regionInfo = getRegionInfo(region); final String beaconregion = regionInfo[0]; final String beaconuuid = regionInfo[1]; final String beaconmajor = regionInfo[2]; final String beaconminor = regionInfo[3];
                    final Logger req = Logger.startLog(TAG + "_didExitRegion2", new Hashtable<String, Object>() {{ put("beaconregion", beaconregion); put("beaconuuid", beaconuuid); put("beaconmajor", beaconmajor); put("beaconminor", beaconminor); }});
                    BeaconReferenceApplication.beaconController.didExitRegion(region);
                }

                @Override
                public void didDetermineStateForRegion(int state, Region region) {
                    final Logger req = Logger.startLog(TAG + "_didDetermineStateForRegion2");
                    BeaconReferenceApplication.beaconController.didDetermineStateForRegion(state, region);
                }
            });
            req.success();
        }
        catch (Exception ex) {
            req.error(ex);
        }
    }

    public static String[] getBeaconInfo(Beacon beacon) {
        //  Return the region ID, UUID, major, minor for the region.
        String beaconuuid = ""; String beaconmajor = ""; String beaconminor = "";  String beacondistance = "";
        String beaconrssi = "";
        try {
            if (beacon != null) {
                if (beacon.getId1() != null) beaconuuid = beacon.getId1().toHexString();
                if (beacon.getId2() != null) beaconmajor = beacon.getId2().toString();
                if (beacon.getId3() != null) beaconminor = beacon.getId3().toString();
                beacondistance = new DecimalFormat("0.0").format(beacon.getDistance());
                beaconrssi = new DecimalFormat("0.0").format(beacon.getRssi());
            }
        }
        catch (Exception ex) {
            Log.e(TAG, "getBeaconInfo: " + ex.toString());
        }
        return new String[] { beaconuuid, beaconmajor, beaconminor, beacondistance, beaconrssi };
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

    public void setActivity(MonitoringActivity activity0) {
        //  Called by MainApplication to set the activity.
        activity = activity0;
    }

    public void destroy() {
        beaconManager.unbind(this);
    }

    public void setBackgroundMode(final boolean mode) {
        //  This method notifies the beacon service that the application is either moving to background mode or foreground mode. When in background mode, BluetoothLE scans to look for beacons are executed less frequently in order to save battery life. The specific scan rates for background and foreground operation are set by the defaults below, but may be customized. When ranging in the background, the time between updates will be much less frequent than in the foreground. Updates will come every time interval equal to the sum total of the BackgroundScanPeriod and the BackgroundBetweenScanPeriod.
        Logger req = Logger.startLog(TAG + "_setBackgroundMode", new Hashtable<String, Object>() {{ put("mode", mode); }});
        beaconManager.setBackgroundMode(mode);
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
