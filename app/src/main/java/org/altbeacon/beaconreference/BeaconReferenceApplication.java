package org.altbeacon.beaconreference;

import android.app.Activity;
import android.app.Application;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.provider.Settings;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.firebase.client.Firebase;

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

public class BeaconReferenceApplication extends Application
{
    private static final String TAG = "BeaconReferenceApp";
    public static BackgroundPowerSaver backgroundPowerSaver;
    public static boolean deviceSupportsBluetooth = true;
    public static BeaconController beaconController = null;

    public void onCreate() {
        super.onCreate();

        //  This is needed for any code using BeaconController:

        //  Set the device ID to be logged.
        if (Logger.installationId == null) {
            Logger.installationId = Settings.Secure.getString(getApplicationContext().getContentResolver(),
                    Settings.Secure.ANDROID_ID);
        }

        //  Initialise Firebase.
        Firebase.setAndroidContext(this);

        //  Do the beacon management here.
        if (deviceSupportsBluetooth) {
            beaconController = new BeaconController(this);
            /*
            How do I customize the background scan rate?
            You may alter the default background scan period and the time between scans using the methods on the
            BeaconManager class. Doing this is easy, but be careful. The longer you wait between scans, the longer
            it will take to detect a beacon. And the more reduce the length of the scan, the more likely it is that
            you might miss an advertisement from an beacon. We recommend not reducing the scan period to be less than
            1.1 seconds, since many beacons only transmit at a frequency of 1 Hz. But keep in mind that the radio may
            miss a single beacon advertisement, which is why we make the default background scan period 10 seconds to
            make extra, extra sure that any transmitting beacons get detected. Below is an example of a rather extreme
            battery savings configuration:

            // set the duration of the scan to be 1.1 seconds
            beaconManager.setBackgroundScanPeriod(1100l);
            // set the time between each scan to be 1 hour (3600 seconds)
            beaconManager.setBackgroundBetweenScanPeriod(3600000l);
             */
            // simply constructing this class and holding a reference to it in your custom Application
            // class will automatically cause the BeaconLibrary to save battery whenever the application
            // is not visible.  This reduces bluetooth power usage by about 60%
            //backgroundPowerSaver = new BackgroundPowerSaver(this);
            //Log.i(TAG, "Power Saver is on: " + backgroundPowerSaver.toString());
        }
    }

}