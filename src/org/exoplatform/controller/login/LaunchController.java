package org.exoplatform.controller.login;

import greendroid.util.Config;

import java.util.ArrayList;

import org.exoplatform.model.ServerObjInfo;
import org.exoplatform.singleton.AccountSetting;
import org.exoplatform.singleton.LocalizationHelper;
import org.exoplatform.singleton.ServerSettingHelper;
import org.exoplatform.singleton.SocialDetailHelper;
import org.exoplatform.utils.ExoConstants;
import org.exoplatform.utils.ImageDownloader;
import org.exoplatform.utils.ServerConfigurationUtils;
import org.exoplatform.utils.SettingUtils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.os.Environment;
import android.util.Log;

public class LaunchController {
  private SharedPreferences        sharedPreference;

  private Context                  context;

  private ArrayList<ServerObjInfo> _arrDefaulServerList;

  private ArrayList<ServerObjInfo> _arrDeletedServerList;

  private ArrayList<ServerObjInfo> _arrServerList;

  public LaunchController(Context c, SharedPreferences prefs) {
    sharedPreference = prefs;
    context = c;
    getLaunchInfo();
    getServerInfo();
    SocialDetailHelper.getInstance().imageDownloader = new ImageDownloader(c.getApplicationContext());
  }

  private void getLaunchInfo() {
    if (sharedPreference == null) {
      sharedPreference = context.getSharedPreferences(ExoConstants.EXO_PREFERENCE, 0);
    }
    LocalizationHelper.getInstance().setSharePrefs(sharedPreference);
    String strLocalize;
    strLocalize = sharedPreference.getString(ExoConstants.EXO_PRF_LOCALIZE,
                                             ExoConstants.EXO_PRF_LOCALIZE);
    /*
     * check if localize file name is null or not assigned then default locale
     * is English
     */

    if (strLocalize == null || strLocalize.equalsIgnoreCase(ExoConstants.EXO_PRF_LOCALIZE))
      strLocalize = ExoConstants.ENGLISH_LOCALIZATION;
    Configuration config = new Configuration();
    SettingUtils.setLocalization(context, config, strLocalize);

    AccountSetting.getInstance()
                  .setUsername(sharedPreference.getString(ExoConstants.EXO_PRF_USERNAME, ""));
    AccountSetting.getInstance()
                  .setPassword(sharedPreference.getString(ExoConstants.EXO_PRF_PASSWORD, ""));
    AccountSetting.getInstance()
                  .setDomainIndex(sharedPreference.getString(ExoConstants.EXO_PRF_DOMAIN_INDEX,
                                                             "-1"));
    AccountSetting.getInstance()
                  .setDomainName(sharedPreference.getString(ExoConstants.EXO_PRF_DOMAIN, ""));
  }

  private void getServerInfo() {

    _arrServerList = new ArrayList<ServerObjInfo>();

    String appVer = "";
    String oldVer = ServerConfigurationUtils.getAppVersion(context);
    try {
      appVer = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
      ServerSettingHelper.getInstance().setApplicationVersion(appVer);
    } catch (NameNotFoundException e) {
      if (Config.GD_ERROR_LOGS_ENABLED)
        Log.e("NameNotFoundException", "Get package information is error!");
    }

    if (!(Environment.getExternalStorageState().equalsIgnoreCase(Environment.MEDIA_MOUNTED))) {
      _arrDefaulServerList = ServerConfigurationUtils.getDefaultServerList(context);
      if (_arrDefaulServerList.size() > 0)
        _arrServerList.addAll(_arrDefaulServerList);
    } else {
      ArrayList<ServerObjInfo> defaultServerList = ServerConfigurationUtils.getServerListWithFileName("DefaultServerList.xml");

      _arrDeletedServerList = ServerConfigurationUtils.getServerListWithFileName("DeletedDefaultServerList.xml");
      if (appVer.compareToIgnoreCase(oldVer) > 0) {

        ArrayList<ServerObjInfo> deletedDefaultServers = _arrDeletedServerList;

        ArrayList<ServerObjInfo> tmp = new ArrayList<ServerObjInfo>();
        if (deletedDefaultServers == null)
          tmp = defaultServerList;
        else {
          for (int i = 0; i < defaultServerList.size(); i++) {
            ServerObjInfo newServerObj = defaultServerList.get(i);
            boolean isDeleted = false;
            for (int j = 0; j < deletedDefaultServers.size(); j++) {
              ServerObjInfo deletedServerObj = deletedDefaultServers.get(i);
              if (newServerObj._strServerName.equalsIgnoreCase(deletedServerObj._strServerName)
                  && newServerObj._strServerUrl.equalsIgnoreCase(deletedServerObj._strServerUrl)) {
                isDeleted = true;
                break;
              }
            }
            if (!isDeleted)
              tmp.add(newServerObj);
          }
        }

        ServerConfigurationUtils.createXmlDataWithServerList(tmp, "DefaultServerList.xml", appVer);
      }

      _arrDefaulServerList = ServerConfigurationUtils.getServerListWithFileName("DefaultServerList.xml");

      if (_arrDefaulServerList.size() > 0)
        _arrServerList.addAll(_arrDefaulServerList);
    }

    ServerSettingHelper.getInstance().setServerInfoList(_arrServerList);
  }

}
