package cc.lotuscard.crashapp;

import android.os.Environment;
import android.util.Log;

import java.io.File;

/**
 * Created by asus-pc on 2017/11/30.
 */

public class AppCrashHandler extends AppCrashLog {


    private static AppCrashHandler mCrashHandler = null;

    private AppCrashHandler(){}

    public static AppCrashHandler getInstance() {

        if(mCrashHandler == null) {
            mCrashHandler = new AppCrashHandler();
        }
        return mCrashHandler;
    }
    @Override
    public void initParams() {
        Log.e("************", "initParams");
        AppCrashLog.CACHE_LOG = Environment.getExternalStorageDirectory().getAbsolutePath()+ File.separator+"zhangjialin/log";

    }

    @Override
    public void sendCrashLogToServer(File file) {

        Log.e("************", "sendCrashLogToServer");
    }

//    //在手机sdcard路径下新建名为 zhangjialin 的文件夹
//    public void newPackage(){
//        AppCrashLog.CACHE_LOG = Environment.getExternalStorageDirectory()+"/zhangjialin/log";
//        File file = new File(AppCrashLog.CACHE_LOG);
//        AlertDialog.Builder build = new AlertDialog.Builder(AppApplication.getAppContext());
//        if(file.exists()){
//            build.setMessage("文件夹已存在").show();
//        }else{
//            file.mkdirs();//mkdirs可以建立多级文件夹
//            build.setMessage("新建成功").show();
//        }
//    }

}
