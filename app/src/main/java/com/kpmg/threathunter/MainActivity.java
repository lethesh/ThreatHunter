
package com.kpmg.threathunter;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

public class MainActivity extends Activity
{
    public final static String TAG = "com.kpmg.threathunter";//$NON-NLS-1$

    public static final String ACTION_SEND_LOG = "com.kpmg.threathunter.intent.action.SEND_LOG";//$NON-NLS-1$
    public static final String EXTRA_SEND_INTENT_ACTION = "com.kpmg.threathunter.intent.extra.SEND_INTENT_ACTION";//$NON-NLS-1$
    public static final String EXTRA_DATA = "com.kpmg.threathunter.intent.extra.DATA";//$NON-NLS-1$
    public static final String EXTRA_ADDITIONAL_INFO = "com.kpmg.threathunter.intent.extra.ADDITIONAL_INFO";//$NON-NLS-1$
    public static final String EXTRA_SHOW_UI = "com.kpmg.threathunter.intent.extra.SHOW_UI";//$NON-NLS-1$
    public static final String EXTRA_FILTER_SPECS = "com.kpmg.threathunter.intent.extra.FILTER_SPECS";//$NON-NLS-1$
    public static final String EXTRA_FORMAT = "com.kpmg.threathunter.intent.extra.FORMAT";//$NON-NLS-1$
    public static final String EXTRA_BUFFER = "com.kpmg.threathunter.intent.extra.BUFFER";//$NON-NLS-1$

    final int MAX_LOG_MESSAGE_LENGTH = 100000;

    private AlertDialog mMainDialog;
    private Intent mSendIntent;
    private CollectLogTask mCollectLogTask;
    private ProgressDialog mProgressDialog;
    private String mAdditonalInfo;
    private boolean mShowUi;
    private String[] mFilterSpecs;
    private String mFormat;
    private String mBuffer;


    @Override
    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);

       collectLog();

    }

    @SuppressWarnings("unchecked")
    void collectLog(){
        /*Usage: logcat [options] [filterspecs]
        options include:
          -s              Set default filter to silent.
                          Like specifying filterspec '*:s'
          -f <filename>   Log to file. Default to stdout
          -r [<kbytes>]   Rotate log every kbytes. (16 if unspecified). Requires -f
          -n <count>      Sets max number of rotated logs to <count>, default 4
          -v <format>     Sets the log print format, where <format> is one of:

                          brief process tag thread raw time threadtime long

          -c              clear (flush) the entire log and exit
          -d              dump the log and then exit (don't block)
          -g              get the size of the log's ring buffer and exit
          -b <buffer>     request alternate ring buffer
                          ('main' (default), 'radio', 'events')
          -B              output the log in binary
        filterspecs are a series of
          <tag>[:priority]

        where <tag> is a log component tag (or * for all) and priority is:
          V    Verbose
          D    Debug
          I    Info
          W    Warn
          E    Error
          F    Fatal
          S    Silent (supress all output)

        '*' means '*:d' and <tag> by itself means <tag>:v

        If not specified on the commandline, filterspec is set from ANDROID_LOG_TAGS.
        If no filterspec is found, filter defaults to '*:I'

        If not specified with -v, format is set from ANDROID_PRINTF_LOG
        or defaults to "brief"*/

        ArrayList<String> list = new ArrayList<String>();
        String LINE_SEPARATOR = System.getProperty("line.separator");

        if (mFormat != null){
            list.add("-v");
            list.add(mFormat);
        }

        if (mBuffer != null){
            list.add("-b");
            list.add(mBuffer);
        }

        if (mFilterSpecs != null){
            for (String filterSpec : mFilterSpecs){
                list.add(filterSpec);
            }
        }

        mCollectLogTask = (CollectLogTask) new CollectLogTask().execute(list);
    }

    @SuppressLint("StaticFieldLeak")
    private class CollectLogTask extends AsyncTask<ArrayList<String>, Void, StringBuilder>{
        @Override
        protected void onPreExecute(){
            showProgressDialog(getString(R.string.acquiring_log_progress_dialog_message));
        }

        @Override
        protected StringBuilder doInBackground(ArrayList<String>... params){
            final StringBuilder log = new StringBuilder();
            String LINE_SEPARATOR = System.getProperty("line.separator");
            try{
                ArrayList<String> commandLine = new ArrayList<>();
                commandLine.add("logcat");//$NON-NLS-1$
                commandLine.add("-d");//$NON-NLS-1$
                ArrayList<String> arguments = ((params != null) && (params.length > 0)) ? params[0] : null;
                if (null != arguments){
                    commandLine.addAll(arguments);
                }

                Process process = Runtime.getRuntime().exec(commandLine.toArray(new String[0]));
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));

                String line;
                while ((line = bufferedReader.readLine()) != null){
                    log.append(line);
                    log.append(LINE_SEPARATOR);
                }
            }
            catch (IOException e){
                Log.e("Lethesh", "CollectLogTask.doInBackground failed", e);//$NON-NLS-1$
            }

            return log;
        }

        @Override
        protected void onPostExecute(StringBuilder log){
            String LINE_SEPARATOR = System.getProperty("line.separator");
                if (mAdditonalInfo != null){
                    log.insert(0, LINE_SEPARATOR);
                    log.insert(0, mAdditonalInfo);
                }

                 else{
                 showErrorDialog(getString(R.string.failed_to_get_log_message));
                 }
        }
    }

    void showErrorDialog(String errorMessage){
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.app_name))
                .setMessage(errorMessage)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener(){
                    public void onClick(DialogInterface dialog, int whichButton){
                        finish();
                    }
                })
                .show();
    }

    void dismissMainDialog(){
        if (null != mMainDialog && mMainDialog.isShowing()){
            mMainDialog.dismiss();
            mMainDialog = null;
        }
    }

    void showProgressDialog(String message){
        mProgressDialog = new ProgressDialog(this);
        mProgressDialog.setIndeterminate(true);
        mProgressDialog.setMessage(message);
        mProgressDialog.show();
    }

    private static String getVersionNumber(Context context)
    {
        String version = "?";
        try
        {
            PackageInfo packagInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            version = packagInfo.versionName;
        }
        catch (PackageManager.NameNotFoundException e){};

        return version;
    }

    private String getFormattedKernelVersion()
    {
        String procVersionStr;

        try {
            BufferedReader reader = new BufferedReader(new FileReader("/proc/version"), 256);
            try {
                procVersionStr = reader.readLine();
            } finally {
                reader.close();
            }

            final String PROC_VERSION_REGEX =
                    "\\w+\\s+" + /* ignore: Linux */
                            "\\w+\\s+" + /* ignore: version */
                            "([^\\s]+)\\s+" + /* group 1: 2.6.22-omap1 */
                            "\\(([^\\s@]+(?:@[^\\s.]+)?)[^)]*\\)\\s+" + /* group 2: (xxxxxx@xxxxx.constant) */
                            "\\([^)]+\\)\\s+" + /* ignore: (gcc ..) */
                            "([^\\s]+)\\s+" + /* group 3: #26 */
                            "(?:PREEMPT\\s+)?" + /* ignore: PREEMPT (optional) */
                            "(.+)"; /* group 4: date */

            Pattern p = Pattern.compile(PROC_VERSION_REGEX);
            Matcher m = p.matcher(procVersionStr);

            if (!m.matches()) {
                Log.e(TAG, "Regex did not match on /proc/version: " + procVersionStr);
                return "Unavailable";
            } else if (m.groupCount() < 4) {
                Log.e(TAG, "Regex match on /proc/version only returned " + m.groupCount()
                        + " groups");
                return "Unavailable";
            } else {
                return (new StringBuilder(m.group(1)).append("\n").append(
                        m.group(2)).append(" ").append(m.group(3)).append("\n")
                        .append(m.group(4))).toString();
            }
        } catch (IOException e) {
            Log.e(TAG,
                    "IO Exception when getting kernel version for Device Info screen",
                    e);

            return "Unavailable";
        }
    }
}


//might be helpful if above code doesn't work http://android-delight.blogspot.com/2016/06/how-to-write-app-logcat-to-sd-card-in.html