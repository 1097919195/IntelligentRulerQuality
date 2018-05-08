package cc.lotuscard.broadcast;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.widget.Toast;

import cc.lotuscard.app.AppApplication;

/**
 * Created by Administrator on 2018/4/2 0002.
 */

public class StartingUpBroadcast extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        PackageManager pm = context.getPackageManager();
        Intent it = new Intent();
        it = pm.getLaunchIntentForPackage("com.lotuscard.rulerQuality");
        if (null != it) {
            // FIXME: 2018/5/4 0004
//            context.startActivity(it);
        }
    }
}
