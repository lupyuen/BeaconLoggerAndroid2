package org.altbeacon.beaconreference;

import android.bluetooth.BluetoothDevice;
import android.util.Log;
import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconParser;

import java.text.DecimalFormat;
import java.util.Hashtable;

/**
 * Created by Luppy on 6/5/16.
 */
public class LoggingBeaconParser extends BeaconParser {
    //  This class is called whenever a beacon is detected.  We log the beacon to Firebase.
    //  This is meant as a more effective way to monitor beacons in the background, because the default beacon
    //  monitoring doesn't work well if there are too many beacons.

    protected static final String TAG = "LoggingBeaconParser";

    public LoggingBeaconParser() {}

    public LoggingBeaconParser(String identifier) {
        super(identifier);
    }

    @Override
    protected Beacon fromScanData(byte[] bytesToProcess, final int rssi, BluetoothDevice device, Beacon beacon) {
        final Beacon resultBeacon = super.fromScanData(bytesToProcess, rssi, device, beacon);
        try {
            Log.i(TAG, resultBeacon == null ? "NULL" : resultBeacon.toString());
            if (resultBeacon == null) return null;
            final Logger req = Logger.startLog(TAG + "_fromScanData", new Hashtable<String, Object>() {{
                put("beaconuuid", resultBeacon.getId1() == null ? "NULL" : resultBeacon.getId1());
                put("beaconmajor", resultBeacon.getId2() == null ? "NULL" : resultBeacon.getId2());
                put("beaconminor", resultBeacon.getId3() == null ? "NULL" : resultBeacon.getId3());
                put("beacondistance", new DecimalFormat("0.0").format(resultBeacon.getDistance()));
                put("beaconrssi", rssi);
                put("beacontypecode", resultBeacon.getBeaconTypeCode());
                put("beaconaddress", resultBeacon.getBluetoothAddress() == null ? "NULL" : resultBeacon.getBluetoothAddress());
                put("beaconname", resultBeacon.getBluetoothName() == null ? "NULL" : resultBeacon.getBluetoothName());
                put("beaconmanufacturer", resultBeacon.getManufacturer());
                put("beaconserviceuuid", resultBeacon.getServiceUuid());
                put("beacontxpower", resultBeacon.getTxPower());
            }});
        }
        catch (Exception ex) {
            Log.e(TAG, ex.toString());
        }
        return resultBeacon;
    }

    @Override
    public Beacon fromScanData(byte[] scanData, int rssi, BluetoothDevice device) {
        Beacon resultBeacon = super.fromScanData(scanData, rssi, device);
        Log.i(TAG + "2", resultBeacon == null ? "NULL" : resultBeacon.toString());
        return resultBeacon;
    }
}
