package com.eggheadgames.siren;

import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Created by yuriy on 09.03.2016.
 */
public class Siren {
    /**JSON format should be the following
     * {
     *     "com.example.app": {
     *         "minVersionCode": 7,
     *         "minVersionName": "1.0.0.0"
     *     }
     * }
     */

    private static Siren ourInstance = new Siren();
    private ISirenListener sirenListener;
    private Context context;
    private WeakReference<Activity> activityRef;

    /**
     * Determines alert type during version code verification
     */
    private SirenAlertType versionCodeUpdateAlertType = SirenAlertType.OPTION;

    /**
     * Determines the type of alert that should be shown for major version updates: A.b.c
     */
    private SirenAlertType majorUpdateAlertType = SirenAlertType.OPTION;

    /**
     * Determines the type of alert that should be shown for minor version updates: a.B.c
     */
    private SirenAlertType minorUpdateAlertType  = SirenAlertType.OPTION;

    /**
     Determines the type of alert that should be shown for minor patch updates: a.b.C
     */
    private SirenAlertType patchUpdateAlertType = SirenAlertType.OPTION;

    /**
     Determines the type of alert that should be shown for revision updates: a.b.c.D
     */
    private SirenAlertType revisionUpdateAlertType = SirenAlertType.OPTION;

    /**
     * @param context - you should use an Application context here in order to not cause memory leaks
     * @return
     */
    public static Siren getInstance(Context context) {
        ourInstance.context = context;
        return ourInstance;
    }

    private Siren() {
    }

    public void checkVersion(Activity activity, SirenVersionCheckType versionCheckType, String appDescriptionUrl) {

        activityRef = new WeakReference<Activity>(activity);

        if (TextUtils.isEmpty(appDescriptionUrl)) {
            Log.e(getClass().getSimpleName(), "Please make sure your set correct path to app version description document");
            return;
        }

        if (versionCheckType == SirenVersionCheckType.IMMEDIATELY) {
            performVersionCheck(appDescriptionUrl);
        } else if (versionCheckType.getValue() <= SirenHelper.getDaysSinceLastCheck(context)) {
            performVersionCheck(appDescriptionUrl);
        }
    }

    private void performVersionCheck(String appDescriptionUrl) {
        new LoadJsonTask().execute(appDescriptionUrl);
    }

    protected void handleVerificationResults(String json) {
        try {
            JSONObject rootJson = new JSONObject(json);

            if (!rootJson.isNull(SirenHelper.getPackageName(context))) {
                JSONObject appJson = rootJson.getJSONObject(SirenHelper.getPackageName(context));

                //version name have higher priority
                if (checkVersionName(appJson)) {
                    return;
                }

                checkVersionCode(appJson);

            } else {
                throw new JSONException("field not found");
            }

        } catch (JSONException e) {
            e.printStackTrace();
            if (sirenListener != null) {
                sirenListener.onError(e);
            }
        }
    }

    private boolean checkVersionName(JSONObject appJson) throws JSONException{
        if (!appJson.isNull(Constants.JSON_MIN_VERSION_NAME)) {

            //save last successful verification date
            SirenHelper.setLastVerificationDate(context);

            String minVersionName = appJson.getString(Constants.JSON_MIN_VERSION_NAME);
            String currentVersionName = SirenHelper.getVersionName(context);

            SirenAlertType alertType = null;
            if (!TextUtils.isEmpty(minVersionName) && !TextUtils.isEmpty(currentVersionName)
                    && !SirenHelper.isVersionSkippedByUser(context, minVersionName)) {
                String[] minVersionNumbers = minVersionName.split("\\.");
                String[] currentVersionNumbers = currentVersionName.split("\\.");
                if (minVersionNumbers != null && currentVersionNumbers != null
                        && minVersionNumbers.length == currentVersionNumbers.length) {
                    if (minVersionNumbers.length > 0 && SirenHelper.isGreater(minVersionNumbers[0], currentVersionNumbers[0])) {
                        alertType = majorUpdateAlertType;
                    } else if (minVersionNumbers.length > 1 && SirenHelper.isGreater(minVersionNumbers[1], currentVersionNumbers[1])) {
                        alertType = minorUpdateAlertType;
                    } else if (minVersionNumbers.length > 2 && SirenHelper.isGreater(minVersionNumbers[2], currentVersionNumbers[2])) {
                        alertType = patchUpdateAlertType;
                    } else if (minVersionNumbers.length > 3 && SirenHelper.isGreater(minVersionNumbers[3], currentVersionNumbers[3])) {
                        alertType = revisionUpdateAlertType;
                    }

                    if (alertType != null) {
                        showAlert(minVersionName, alertType);
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private boolean checkVersionCode(JSONObject appJson) throws JSONException{
        if (!appJson.isNull(Constants.JSON_MIN_VERSION_CODE)) {
            int minAppVersionCode = appJson.getInt(Constants.JSON_MIN_VERSION_CODE);

            //save last successful verification date
            SirenHelper.setLastVerificationDate(context);

            if (SirenHelper.getVersionCode(context) < minAppVersionCode
                    && !SirenHelper.isVersionSkippedByUser(context, String.valueOf(minAppVersionCode))) {
                showAlert(String.valueOf(minAppVersionCode), versionCodeUpdateAlertType);
                return true;
            }
        }
        return false;
    }

    private void showAlert(String appVersion, SirenAlertType alertType) {
        if (versionCodeUpdateAlertType == SirenAlertType.NONE) {
            if (sirenListener != null) {
                sirenListener.onDetectNewVersionWithoutAlert(SirenHelper.getAlertMessage(context, appVersion));
            }
        } else {
            new SirenAlertWrapper(activityRef.get(), sirenListener, alertType, appVersion).show();
        }
    }

    public void setMajorUpdateAlertType(SirenAlertType majorUpdateAlertType) {
        this.majorUpdateAlertType = majorUpdateAlertType;
    }

    public void setMinorUpdateAlertType(SirenAlertType minorUpdateAlertType) {
        this.minorUpdateAlertType = minorUpdateAlertType;
    }

    public void setPatchUpdateAlertType(SirenAlertType patchUpdateAlertType) {
        this.patchUpdateAlertType = patchUpdateAlertType;
    }

    public void setRevisionUpdateAlertType(SirenAlertType revisionUpdateAlertType) {
        this.revisionUpdateAlertType = revisionUpdateAlertType;
    }

    public void setSirenListener(ISirenListener sirenListener) {
        this.sirenListener = sirenListener;
    }

    public void setVersionCodeUpdateAlertType(SirenAlertType versionCodeUpdateAlertType) {
        this.versionCodeUpdateAlertType = versionCodeUpdateAlertType;
    }

    public static class LoadJsonTask extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... params) {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(params[0]);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setUseCaches(false);
                connection.setAllowUserInteraction(false);
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.connect();
                int status = connection.getResponseCode();

                switch (status) {
                    case 200:
                    case 201:
                        BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) {
                            if (isCancelled()) {
                                br.close();
                                connection.disconnect();
                                return null;
                            }
                            sb.append(line+"\n");
                        }
                        br.close();
                        return sb.toString();
                }

            } catch (IOException ex) {
                ex.printStackTrace();
                if (Siren.ourInstance.sirenListener != null) {
                    Siren.ourInstance.sirenListener.onError(ex);
                }

            } finally {
                if (connection != null) {
                    try {
                        connection.disconnect();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        if (Siren.ourInstance.sirenListener != null) {
                            Siren.ourInstance.sirenListener.onError(ex);
                        }
                    }
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            if (!TextUtils.isEmpty(result)) {
                Siren.ourInstance.handleVerificationResults(result);
            } else {
                if (Siren.ourInstance.sirenListener != null) {
                    Siren.ourInstance.sirenListener.onError(new NullPointerException());
                }
            }
        }
    }
}