package com.czy.wififiletransfer;

import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.webkit.MimeTypeMap;

import com.czy.wififiletransfer.common.Constants;
import com.czy.wififiletransfer.manager.ApManager;
import com.czy.wififiletransfer.model.FileTransfer;
import com.czy.wififiletransfer.service.FileReceiverService;

import java.io.File;
import java.util.Locale;

/**
 * 作者：chenZY
 * 时间：2018/2/24 11:27
 * 描述：接收文件-开启Ap热点
 * GitHub主页：https://github.com/leavesC
 * 简书主页：https://www.jianshu.com/u/9df45b87cfdf
 */
public class ReceiveFileActivity extends BaseActivity {

    private FileReceiverService mFileReceiverService;

    private ProgressDialog progressDialog;

    private ServiceConnection serviceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            FileReceiverService.MyBinder binder = (FileReceiverService.MyBinder) service;
            mFileReceiverService = binder.getService();
            mFileReceiverService.setProgressChangListener(progressChangListener);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mFileReceiverService = null;
            bindService(FileReceiverService.class, serviceConnection);
        }
    };

    private FileReceiverService.OnReceiveProgressChangListener progressChangListener = new FileReceiverService.OnReceiveProgressChangListener() {

        private FileTransfer originFileTransfer;

        @Override
        public void onProgressChanged(final FileTransfer fileTransfer, final int progress, final double speed, final long remainingTime) {
            this.originFileTransfer = fileTransfer;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (!isFinishingOrDestroyed()) {
                        progressDialog.setTitle("正在接收的文件： " + new File(originFileTransfer.getFilePath()).getName());
                        progressDialog.setMessage("原始文件的MD5码是：" + originFileTransfer.getMd5()
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
        public void onStartComputeMD5() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (!isFinishingOrDestroyed()) {
                        progressDialog.setTitle("传输结束，正在计算本地文件的MD5码以校验文件完整性");
                        progressDialog.setMessage("原始文件的MD5码是：" + originFileTransfer.getMd5());
                        progressDialog.setCancelable(false);
                        progressDialog.show();
                    }
                }
            });
        }

        @Override
        public void onTransferFinished(final FileTransfer fileTransfer) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (!isFinishingOrDestroyed()) {
                        if (fileTransfer == null || TextUtils.isEmpty(fileTransfer.getMd5())) {
                            progressDialog.setTitle("传输失败");
                            progressDialog.setMessage(null);
                        } else {
                            progressDialog.setTitle("传输成功");
                            progressDialog.setMessage("原始文件的MD5码是：" + originFileTransfer.getMd5()
                                    + "\n" + "本地文件的MD5码是：" + fileTransfer.getMd5()
                                    + "\n" + "文件位置：" + fileTransfer.getFilePath());
                        }
                        progressDialog.setCancelable(true);
                        progressDialog.show();
                    }
                }
            });
        }
    };

    protected static final String TAG = "ReceiveFileActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receive_file);
        ApManager.saveApCache(this);
        initView();
        bindService(FileReceiverService.class, serviceConnection);
        ApManager.openAp(this, Constants.AP_SSID, Constants.AP_PASSWORD);
        FileReceiverService.startActionTransfer(this);
    }

    private void initView() {
        setTitle("接收文件");
        progressDialog = new ProgressDialog(this);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setCancelable(false);
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.setTitle("正在接收文件");
        progressDialog.setMax(100);
    }

    public void openAp(View view) {
//        boolean flag = ApManager.isApOn(this);
//        boolean flag = ApManager.openAp(this, Constants.AP_SSID, Constants.AP_PASSWORD);
//        Logger.e(TAG, "开启Wifi热点：" + flag);
//        showToast("开启Wifi热点：" + flag);
//        if (flag) {
//            FileReceiverService.startActionTransfer(this);
//        }
    }

    public void closeAp(View view) {
        ApManager.closeAp(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mFileReceiverService != null) {
            mFileReceiverService.setProgressChangListener(null);
            unbindService(serviceConnection);
        }
    }

    private void openFile(String filePath) {
        String ext = filePath.substring(filePath.lastIndexOf('.')).toLowerCase(Locale.US);
        try {
            MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
            String mime = mimeTypeMap.getMimeTypeFromExtension(ext.substring(1));
            mime = TextUtils.isEmpty(mime) ? "" : mime;
            Intent intent = new Intent();
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setAction(android.content.Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.fromFile(new File(filePath)), mime);
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "文件打开异常：" + e.getMessage());
            showToast("文件打开异常：" + e.getMessage());
        }
    }

}
