package com.czy.wififiletransfer;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import com.czy.wififiletransfer.common.Constants;
import com.czy.wififiletransfer.manager.WifiLManager;
import com.czy.wififiletransfer.model.FileTransfer;
import com.czy.wififiletransfer.service.FileSenderService;
import com.czy.wififiletransfer.util.Logger;

import java.io.File;
import java.util.List;

/**
 * 作者：chenZY
 * 时间：2018/2/26 18:14
 * 描述：
 * GitHub主页：https://github.com/leavesC
 * 简书主页：https://www.jianshu.com/u/9df45b87cfdf
 */
public class SendFile2Activity extends BaseActivity {

    public static final String TAG = "SendFile2Activity";

    private static final int CODE_CHOOSE_FILE = 100;

    private FileSenderService fileSenderService;

    private ProgressDialog progressDialog;

    private ProgressDialog loadingDialog;

    private FileTransfer mFileTransfer;

    private FileSenderService.OnSendProgressChangListener progressChangListener = new FileSenderService.OnSendProgressChangListener() {
        @Override
        public void onStartComputeMD5() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (!isFinishingOrDestroyed()) {
                        progressDialog.setTitle("发送文件");
                        progressDialog.setMessage("正在计算文件的MD5码");
                        progressDialog.setProgress(0);
                        progressDialog.setCancelable(false);
                        progressDialog.show();
                    }
                }
            });
        }

        @Override
        public void onProgressChanged(final FileTransfer fileTransfer, final int progress, final double speed, final long remainingTime) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (!isFinishingOrDestroyed()) {
                        progressDialog.setTitle("正在发送的文件： " + new File(fileTransfer.getFilePath()).getName());
                        progressDialog.setMessage("文件的MD5码：" + fileTransfer.getMd5()
                                + "\n" + "传输速率：" + (int) speed + " Kb/s"
                                + "\n" + "预估的剩余完成时间：" + remainingTime + " 秒");
                        progressDialog.setProgress(progress);
                        progressDialog.setCancelable(true);
                        progressDialog.show();
                    }
                }
            });
        }

        @Override
        public void onTransferSucceed(FileTransfer fileTransfer) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (!isFinishingOrDestroyed()) {
                        progressDialog.setTitle("文件发送成功");
                        progressDialog.setMessage(null);
                        progressDialog.setCancelable(true);
                        progressDialog.show();
                    }
                }
            });
        }

        @Override
        public void onTransferFailed(FileTransfer fileTransfer, final Exception e) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (!isFinishingOrDestroyed()) {
                        progressDialog.setTitle("文件发送失败");
                        progressDialog.setMessage("异常信息： " + e.getMessage());
                        progressDialog.setCancelable(true);
                        progressDialog.show();
                    }
                }
            });
        }
    };

    private ServiceConnection serviceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            FileSenderService.MyBinder binder = (FileSenderService.MyBinder) service;
            fileSenderService = binder.getService();
            fileSenderService.setProgressChangListener(progressChangListener);
            Log.e(TAG, "onServiceConnected");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            fileSenderService = null;
            bindService(FileSenderService.class, serviceConnection);
            Log.e(TAG, "onServiceDisconnected");
        }
    };

    //启动Wifi扫描
    private static final int CODE_START_SCAN = 10;

    //获取Wifi扫描结果
    private static final int CODE_GET_SCAN_RESULT = 20;

    //Wifi扫描最大尝试次数
    private static final int MAX_TRY_COUNT = 10;

    //当前Wifi扫描次数
    private int tryCount = 0;

    @SuppressLint("HandlerLeak")
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case CODE_START_SCAN: {
                    tryCount++;
                    //判断是否已连接到文件接收端的Ap热点
                    if (Constants.AP_SSID.equals(WifiLManager.getConnectedSSID(SendFile2Activity.this))) {
                        tryCount = 0;
                        loadingDialog.cancel();
                        showToast("已连接到文件接收端的Ap热点");
                        if (mFileTransfer != null) {
                            showToast("开始发送文件");
                            FileSenderService.startActionTransfer(SendFile2Activity.this, mFileTransfer, WifiLManager.getHotspotIpAddress(SendFile2Activity.this));
                            mFileTransfer = null;
                        }
                        return;
                    }
                    if (tryCount > MAX_TRY_COUNT) {
                        tryCount = 0;
                        showToast("已达到最大搜索次数，停止Wifi扫描");
                        loadingDialog.setMessage("已达到最大尝试连接，停止Wifi扫描");
                        loadingDialog.setCancelable(true);
                        loadingDialog.show();
                        return;
                    }
//                    if (!WifiLManager.isWifiEnabled(SendFile2Activity.this)) {
//                        tryCount = 0;
//                        loadingDialog.setMessage("Wifi不可用");
//                        loadingDialog.setCancelable(true);
//                        loadingDialog.show();
//                        return;
//                    }
                    loadingDialog.setMessage("当前还未连接至文件接收端开启的Ap热点，正在搜索附近的Wifi热点"
                            + "\n" + "当前搜索次数：" + tryCount);
                    loadingDialog.setCancelable(false);
                    loadingDialog.show();
                    WifiLManager.startWifiScan(SendFile2Activity.this);
                    //四秒后检查Wifi扫描结果中是否有需要的热点信息
                    mHandler.sendEmptyMessageDelayed(CODE_GET_SCAN_RESULT, 4000);
                    break;
                }
                case CODE_GET_SCAN_RESULT: {
                    List<ScanResult> scanResultList = WifiLManager.getScanResults(SendFile2Activity.this);
                    if (scanResultList == null || scanResultList.size() == 0) {
                        //重新启动扫描
                        mHandler.sendEmptyMessage(CODE_START_SCAN);
                    } else {
                        for (ScanResult scanResult : scanResultList) {
                            if (Constants.AP_SSID.equals(scanResult.SSID)) {
                                WifiLManager.connectWifi(SendFile2Activity.this, Constants.AP_SSID, Constants.AP_PASSWORD);
                                //三秒后再重新启动扫描
                                mHandler.sendEmptyMessageDelayed(CODE_START_SCAN, 3000);
                                return;
                            }
                        }
                        //重新启动扫描
                        mHandler.sendEmptyMessage(CODE_START_SCAN);
                    }
                    break;
                }
            }
        }
    };

    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (TextUtils.isEmpty(action)) {
                return;
            }
            Log.e(TAG, "action: " + action);
            switch (action) {
                case WifiManager.SCAN_RESULTS_AVAILABLE_ACTION: {
//                    List<ScanResult> scanResultList = WifiLManager.getScanResults(SendFile2Activity.this);
//                    Log.e(TAG, "----------------------------------");
//                    if (scanResultList != null) {
//                        for (ScanResult scanResult : scanResultList) {
//                            Log.e(TAG, "scanResult :" + scanResult);
//                        }
//                    }
//                    Log.e(TAG, "----------------------------------");
                    break;
                }
                case WifiManager.WIFI_STATE_CHANGED_ACTION: {
                    int wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, 0);
                    switch (wifiState) {
                        case WifiManager.WIFI_STATE_ENABLED:
                            Log.e(TAG, "Wifi已启用");
                            break;
                        case WifiManager.WIFI_STATE_DISABLED:
                            Log.e(TAG, "Wifi已关闭");
                            break;
                    }
                    break;
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_send_file);
        initView();
        bindService(FileSenderService.class, serviceConnection);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        intentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        registerReceiver(broadcastReceiver, intentFilter);
    }

    private void initView() {
        setTitle("发送文件");
        progressDialog = new ProgressDialog(this);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setCancelable(false);
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.setTitle("发送文件");
        progressDialog.setMax(100);
        progressDialog.setIndeterminate(false);
        loadingDialog = new ProgressDialog(this);
        loadingDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        loadingDialog.setCancelable(false);
        loadingDialog.setCanceledOnTouchOutside(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mHandler.removeCallbacksAndMessages(null);
        if (fileSenderService != null) {
            fileSenderService.setProgressChangListener(null);
            unbindService(serviceConnection);
        }
        unregisterReceiver(broadcastReceiver);
    }

    public void startWifiScan(View view) {
        WifiLManager.startWifiScan(this);
    }

    public void getScanResults(View view) {
        List<ScanResult> scanResultList = WifiLManager.getScanResults(this);
        if (scanResultList == null) {
            Log.e(TAG, "scanResultList==null");
            return;
        }
        for (ScanResult scanResult : scanResultList) {
            Log.e(TAG, "scanResult: " + scanResult);
        }
    }

    public void connectWifi(View view) {
        Logger.e(TAG, "连接Wifi： " + WifiLManager.connectWifi(this, Constants.AP_SSID, Constants.AP_PASSWORD));
    }

    public void disconnectWifi(View view) {
        WifiLManager.disconnectWifi(this);
    }

    public void chooseFile(View view) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, CODE_CHOOSE_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == CODE_CHOOSE_FILE && resultCode == RESULT_OK) {
            Uri uri = data.getData();
            if (uri != null) {
                String path = getPath(this, uri);
                if (path != null) {
                    File file = new File(path);
                    if (file.exists()) {
                        FileTransfer fileTransfer = new FileTransfer(file.getPath(), file.length());
                        Log.e(TAG, "待发送的文件：" + fileTransfer);
                        mFileTransfer = fileTransfer;
                        if (!Constants.AP_SSID.equals(WifiLManager.getConnectedSSID(this))) {
                            Log.e(TAG, "当前还未连接至文件接收端开启的Ap热点，正在搜索附近的Wifi热点");
                            if (WifiLManager.isWifiEnabled(this)) {
                                Log.e(TAG, "isWifiEnabled");
                                mHandler.sendEmptyMessage(CODE_START_SCAN);
                            } else {
                                Log.e(TAG, "! isWifiEnabled");
                                mHandler.sendEmptyMessageDelayed(CODE_START_SCAN, 1000);
                            }
                        } else {
                            showToast("开始发送文件");
                            FileSenderService.startActionTransfer(this, fileTransfer, WifiLManager.getHotspotIpAddress(this));
                        }
                    }
                }
            }
        }
    }

    private String getPath(Context context, Uri uri) {
        if ("content".equalsIgnoreCase(uri.getScheme())) {
            Cursor cursor = context.getContentResolver().query(uri, new String[]{"_data"}, null, null, null);
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    String data = cursor.getString(cursor.getColumnIndex("_data"));
                    cursor.close();
                    return data;
                }
            }
        } else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }
        return null;
    }

}
