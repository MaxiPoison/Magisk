package com.topjohnwu.magisk.asyncs;

import android.app.Activity;

import com.topjohnwu.crypto.ByteArrayStream;
import com.topjohnwu.magisk.MagiskManager;
import com.topjohnwu.magisk.utils.Shell;
import com.topjohnwu.magisk.utils.WebService;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Proxy;
import java.net.HttpURLConnection;

import dalvik.system.DexClassLoader;

public class CheckSafetyNet extends ParallelTask<Void, Void, Exception> {

    public static final int SNET_VER = 3;

    private static final String SNET_URL = "https://www.dropbox.com/s/jg2yhcrn3l9fckc/snet.apk?dl=1";
    private static final String PKG = "com.topjohnwu.snet";

    private File dexPath;
    private DexClassLoader loader;

    public CheckSafetyNet(Activity activity) {
        super(activity);
        dexPath = new File(activity.getCacheDir().getParent() + "/snet", "snet.apk");
    }

    @Override
    protected void onPreExecute() {
        MagiskManager mm = MagiskManager.get();
        if (mm.snet_version != CheckSafetyNet.SNET_VER) {
            Shell.sh("rm -rf " + dexPath.getParent());
        }
        mm.snet_version = CheckSafetyNet.SNET_VER;
        mm.prefs.edit().putInt("snet_version", CheckSafetyNet.SNET_VER).apply();
    }

    @Override
    protected Exception doInBackground(Void... voids) {
        try {
            if (!dexPath.exists()) {
                HttpURLConnection conn = WebService.request(SNET_URL, null);
                ByteArrayStream bas = new ByteArrayStream();
                bas.readFrom(conn.getInputStream());
                conn.disconnect();
                dexPath.getParentFile().mkdir();
                try (OutputStream out = new BufferedOutputStream(new FileOutputStream(dexPath))) {
                    bas.writeTo(out);
                    out.flush();
                }
            }
            loader = new DexClassLoader(dexPath.toString(), dexPath.getParent(),
                    null, ClassLoader.getSystemClassLoader());
        } catch (Exception e) {
            return e;
        }
        return null;
    }

    @Override
    protected void onPostExecute(Exception err) {
        MagiskManager mm = MagiskManager.get();
        try {
            if (err != null) throw err;
            Class<?> helperClazz = loader.loadClass(PKG + ".SafetyNetHelper");
            Class<?> callbackClazz = loader.loadClass(PKG + ".SafetyNetCallback");
            Object helper = helperClazz.getConstructors()[0].newInstance(
                    getActivity(), dexPath.getPath(), Proxy.newProxyInstance(
                            loader, new Class[] { callbackClazz }, (proxy, method, args) -> {
                                mm.safetyNetDone.publish(false, args[0]);
                                return null;
                            }));
            helperClazz.getMethod("attest").invoke(helper);
        } catch (Exception e) {
            e.printStackTrace();
            mm.safetyNetDone.publish(false, -1);
        }
        super.onPostExecute(err);
    }
}
