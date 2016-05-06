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
        }
    }

}