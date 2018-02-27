package com.czy.wififiletransfer;

import android.content.Context;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.text.format.Formatter;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.czy.wififiletransfer.manager.ApManager;

import java.io.File;
import java.util.List;

/**
 * 作者：叶应是叶
 * 时间：2018/2/27 20:10
 * 描述：GitHub主页：https://github.com/leavesC
 * 简书主页：https://www.jianshu.com/u/9df45b87cfdf
 */
public class MainActivity extends BaseActivity {

    private static final String TAG = "MainActivity";

    private TextView tv_test;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tv_test = findViewById(R.id.tv_test);
    }

    public void sendFile(View view) {
        startActivity(SendFile2Activity.class);
    }

    public void receiveFile(View view) {
        startActivity(ReceiveFileActivity.class);
    }

    public void getWifiConfiguration(View view) {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        List<WifiConfiguration> wifiConfigurationList = wifiManager == null ? null : wifiManager.getConfiguredNetworks();
        if (wifiConfigurationList != null && wifiConfigurationList.size() > 0) {
            for (WifiConfiguration wifiConfiguration : wifiConfigurationList) {
                Log.e(TAG, wifiConfiguration.toString());
            }
        }
        tv_test.setText(null);
        test(this);
        test2(this);
        String[] params = ApManager.getApSSIDAndPwd(this);
        if (params != null && params[0] != null) {
            Log.e(TAG, "开启的Ap热点的名称: " + params[0]);
            Log.e(TAG, "开启的Ap热点的密码: " + params[1]);
            tv_test.append("\n");
            tv_test.append("开启的Ap热点的名称: " + params[0]);
            tv_test.append("\n");
            tv_test.append("开启的Ap热点的密码: " + params[1]);
        } else {
            Log.e(TAG, "无Ap热点信息");
            tv_test.append("\n");
            tv_test.append("无Ap热点信息");
        }
    }

    //获取内置存储空间的容量
    public void test(Context context) {
        File file = Environment.getExternalStorageDirectory();
        //总容量
        long totalSpace = file.getTotalSpace();
        //剩余容量
        long usableSpace = file.getUsableSpace();
        //格式化
        String usableSpaceStr = Formatter.formatFileSize(context, usableSpace);
        String totalSpaceStr = Formatter.formatFileSize(context, totalSpace);
        Log.e(TAG, "内置存储空间-剩余空间: " + usableSpace + " " + usableSpaceStr);
        Log.e(TAG, "内置存储空间-总的空间: " + totalSpace + " " + totalSpaceStr);
        tv_test.append("\n");
        tv_test.append("内置存储空间-" + file.getPath());
        tv_test.append("\n");
        tv_test.append("内置存储空间-剩余空间: " + usableSpace + " " + usableSpaceStr);
        tv_test.append("\n");
        tv_test.append("内置存储空间-总的空间: " + totalSpace + " " + totalSpaceStr);
    }

    //获取外置存储空间的容量
    public void test2(Context context) {
        File file = new File("/storage/sdcard1");
        //总容量
        long totalSpace = file.getTotalSpace();
        //剩余容量
        long usableSpace = file.getUsableSpace();
        String usableSpaceStr = Formatter.formatFileSize(context, usableSpace);
        String totalSpaceStr = Formatter.formatFileSize(context, totalSpace);
        Log.e(TAG, "外置存储空间-剩余空间: " + usableSpace + " " + usableSpaceStr);
        Log.e(TAG, "外置存储空间-总的空间: " + totalSpace + " " + totalSpaceStr);
        tv_test.append("\n");
        tv_test.append("外置存储空间-" + file.getPath());
        tv_test.append("\n");
        tv_test.append("外置存储空间-剩余空间: " + usableSpace + " " + usableSpaceStr);
        tv_test.append("\n");
        tv_test.append("外置存储空间-总的空间: " + totalSpace + " " + totalSpaceStr);
    }

}
