package org.altbeacon.beaconreference;

import android.location.Location;
import android.location.LocationListener;
import android.os.Bundle;
import android.util.Log;

////  TODO: import com.appkaki.makanpoints.MainApplication;
////  TODO: import com.appkaki.makanpoints.activity.MainActivity;
////  TODO: import com.firebase.client.Firebase;
////  TODO: import com.parse.ParseInstallation;
////  TODO: import com.parse.ParseUser;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;

/**
 * Created by Luppy on 25/3/15.
 */
public class Logger {
    //  Helper class for logging and generating server requests.

    public static String version = null;
    public static String token = null;  //  Security token for authenticating requests to fnbserver.
    public static String togo_status = null;
    public static String installationId = null;
    public static String user = null;
    public static String userName = null;
    public static String userFullname = null;
    public static String userEmail = null;
    public static String userPhone = null;
    public static Double deviceLatitude = -1.0;
    public static Double deviceLongitude = -1.0;
    public static Double deviceLatitudeCoarse = -1.0;  //  Use coarse location until fine location is available.
    public static Double deviceLongitudeCoarse = -1.0;

    public Date starttime = null;
    public String param = null;
    public String outlet = null;
    public String magentoOrderId = null;
    public String posOrderId = null;
    public String action = "(None)";

    public final static LocationListener locationListener = new LocationListener() {
        public void onLocationChanged(Location location) {
            deviceLongitude = location.getLongitude();
            deviceLatitude = location.getLatitude();
        }
        public void onStatusChanged(String s, int i, Bundle b) { }
        public void onProviderEnabled(String s) { }
        public void onProviderDisabled(String s) { }
    };

    public Logger()
    {
        if (installationId == null) {
            ////  TODO: installationId = ParseInstallation.getCurrentInstallation().getInstallationId();
            ////  Change to device ID.
            installationId = "BeaconLoggerAndroid2";
        }
    }

    public Logger copyOutlet()
    {
        //  Return a copy with the outlet set.
        Logger logger = new Logger();
        logger.outlet = outlet;
        return logger;
    }

    public HashMap<String, String> createPara(HashMap<String, String> body)  {
        //  Create the parameters for a server call, adding device and user info.
        HashMap<String, String> para = new HashMap<String, String>();
        if (body != null) {
            para = body;
        }
        para.put("starttime", new Date().toString());
        para.put("timestamp", new Date().toString());
        para.put("devicetype", System.getProperty("http.agent"));
        //  TODO: para.put("deviceuuid"] = UIDevice.currentDevice().identifierForVendor.UUIDString
        //  TODO: Generate a UUID as installation ID if push notification is disabled.
        para.put("deviceinstallationid", installationId);
        if (token != null) para.put("token", token);
        if (version != null) para.put("clientversion", version);

        //  Use coarse location until fine location is available.
        if (deviceLatitude >= 0) para.put("devicelat", deviceLatitude.toString());
        else if (deviceLatitudeCoarse >= 0) para.put("devicelat", deviceLatitudeCoarse.toString());
        if (deviceLongitude >= 0) para.put("devicelong", deviceLongitude.toString());
        else if (deviceLongitudeCoarse >= 0) para.put("devicelong", deviceLongitudeCoarse.toString());

        ////  TODO: if (MainApplication.screenWidth > 0) para.put("devicewidth", MainApplication.screenWidth + "");
        ////  TODO: if (MainApplication.screenHeight > 0) para.put("deviceheight", MainApplication.screenHeight + "");

        /*  TODO: Populate these from SEED login.
        ParseUser user = ParseUser.getCurrentUser();
        if (user != null)
        {
            if (user.getEmail() != null) {
                userEmail = user.getEmail();
                para.put("useremail", userEmail);
            }
            if (user.getUsername() != null) {
                userName = user.getUsername();
                para.put("username", userName);
            }
            if (user.get("phone") != null) {
                userPhone = user.get("phone").toString();
                para.put("userphone", userPhone);
            }
            if (user.get("fullname") != null) {
                userFullname = user.get("fullname").toString();
                para.put("userfullname", userFullname);
            }
        }
        */
        return para;
    }

    public void success(String action0, String result, Hashtable<String, Object> body)
    {
        //  Log a successful action.
        if (action0 != null) action = action0;
        if (body == null) body = new Hashtable<String, Object>();
        body.put("status", "success");
        body.put("result", result);
        log(action, body);
    }

    public static Logger startSuccess(String action0, String result, Hashtable<String, Object> body) {
        Logger logger = new Logger();
        logger.success(action0, result, body);
        return logger;
    }

    public void success() { success(null, "", null); }
    public void success(String result) { success(null, result, null); }
    public void success(Hashtable<String, Object> body) { success(null, "", body); }
    public void success(String result, Hashtable<String, Object> body) { success(null, result, body); }
    public static Logger startSuccess(String action0) { return startSuccess(action0, "", null); }
    public static Logger startSuccess(String action0, String result) { return startSuccess(action0, result, null); }

    public void error(String action0, Exception ex, Hashtable<String, Object> body)
    {
        //  Log an error action.
        if (action0 != null) action = action0;
        if (body == null) body = new Hashtable<String, Object>();
        body.put("status", "error");
        body.put("exception", ex.toString());
        StringWriter sw = new StringWriter();
        try {
            //  Add the stack trace.
            ex.printStackTrace(new PrintWriter(sw));
            body.put("exceptionstacktrace", sw.toString());
        }
        catch (Exception ex2) {}
        log(action, body);
        ex.printStackTrace();
    }

    public static Logger startError(String action0, Exception ex, Hashtable<String, Object> body) {
        Logger logger = new Logger();
        logger.error(action0, ex, body);
        return logger;
    }

    public void error(Exception ex) { error(null, ex, null); }
    public void error(Exception ex, Hashtable<String, Object> body) { error(null, ex, body); }
    public static Logger startError(String action0, Exception ex) { return startError(action0, ex, null); }

    public static Logger startLog(String action0, Hashtable<String, Object> body)
    {
        Logger logger = new Logger();
        logger.log(action0, body);
        return logger;
    }

    public static Logger startLog(String action0)
    {
        return startLog(action0, null);
    }

    public void log(Hashtable<String, Object> body)
    {
        log(null, body);
    }

    public void log(String action0, Hashtable<String, Object> body)
    {
        //  Write the log to Firebase.
        try
        {
            if (body == null) body = new Hashtable<String, Object>();
            if (action0 != null) action = action0;
            Logger logger = this;
            {
                if (logger.installationId != null) body.put("deviceinstallationid", logger.installationId);
                if (logger.version != null) body.put("clientversion", logger.version);
                if (logger.user != null) body.put("user", logger.user);
                if (logger.param != null) body.put("params", logger.param);
                if (logger.outlet != null) body.put("outlet", logger.outlet);
                if (logger.userName != null) body.put("username", logger.userName);
                if (logger.userFullname != null) body.put("userfullname", logger.userFullname);
                if (logger.userEmail != null) body.put("useremail", logger.userEmail);
                if (logger.userPhone != null) body.put("userphone", logger.userPhone);
                if (logger.magentoOrderId != null) body.put("magentoorderid", logger.magentoOrderId);
                if (logger.posOrderId != null) body.put("posorderid", logger.posOrderId);

                if (logger.starttime == null) logger.starttime = new Date();
                else body.put("duration", ((new Date()).getTime() - logger.starttime.getTime()) / 1000.0);
            }
            StringBuilder output = new StringBuilder(action + ": ");
            boolean firstKey = true;
            Enumeration<String> enumKey = body.keys();
            while(enumKey.hasMoreElements()) {
                String key = enumKey.nextElement();
                Object value = body.get(key);
                if (value == null) {
                    value = "null";
                    body.put(key, value);
                }
                if (firstKey) firstKey = false;
                else output.append(", ");
                output.append(key + ": " + value);
            }
            if (body.containsKey("exception"))
                Log.e(action, output.toString());
            else
                Log.d(action, output.toString());
            body.put("source", "beaconloggerandroid");
            body.put("action", action);
            body.put("timestamp", new Date());
            body.put("starttime", logger.starttime);
            body.put("devicetype", System.getProperty("http.agent"));
            ////  TODO: if (MainApplication.screenWidth > 0) body.put("devicewidth", MainApplication.screenWidth);
            ////  TODO: if (MainApplication.screenHeight > 0) body.put("deviceheight", MainApplication.screenHeight);
            //  Use coarse location until fine location is available.
            if (deviceLatitude >= 0) body.put("devicelat", deviceLatitude.toString());
            else if (deviceLatitudeCoarse >= 0) body.put("devicelat", deviceLatitudeCoarse.toString());
            if (deviceLongitude >= 0) body.put("devicelong", deviceLongitude.toString());
            else if (deviceLongitudeCoarse >= 0) body.put("devicelong", deviceLongitudeCoarse.toString());

            logTask(body);
        }
        catch (Exception ex)
        {
            Log.e(action, "Can't log to Firebase: " + ex.toString());
        }
    }

    static void logTask(Object body0)
    {
        try
        {
            Hashtable<String, Object> body = (Hashtable<String, Object>) body0;
            String url = "https://popping-fire-8815.firebaseio.com/fnb/logclient/";
            //  url ls fnb/logclient/source/deviceinstallationid/action/YYYY-MM-DD/HH/MM
            //TimeZone tz = TimeZone.getTimeZone("UTC");
            DateFormat df = new SimpleDateFormat("yyyy-MM-dd'/'HH'/'mm");
            //df.setTimeZone(tz);
            String dateTime = df.format(new Date());

            url = url + "makanpointsandroid" + "/";
            url = url + (Logger.installationId == null ? "0" : Logger.installationId) + "/";
            url = url + (body.containsKey("action") ? body.get("action") : "0") + "/";
            url = url + dateTime;

            ////  TODO: final Firebase myFirebaseRef = new Firebase(url);
            try
            {
                ////  TODO: myFirebaseRef.push().setValue(body);
            }
            catch (Exception ex)
            {
                //  Sometime can't serialise the complex XML result.  We try without the result.
                body.put("result", null);
                ////  TODO: myFirebaseRef.push().setValue(body);
            }
        }
        catch (Exception ex)
        {
            Log.e("Log", "Can't write to Firebase log: " + ex.toString());
        }
    }
}
