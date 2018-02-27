package com.czy.wififiletransfer;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.czy.wififiletransfer.common.Constants;
import com.czy.wififiletransfer.manager.WifiLManager;
import com.czy.wififiletransfer.model.FileTransfer;
import com.czy.wififiletransfer.task.FileSenderTask;
import com.czy.wififiletransfer.util.Logger;

import java.io.File;

/**
 * 作者：chenZY
 * 时间：2018/2/24 11:27
 * 描述：发送文件-连接Wifi热点
 * GitHub主页：https://github.com/leavesC
 * 简书主页：https://www.jianshu.com/u/9df45b87cfdf
 */
public class SendFileActivity extends BaseActivity {

    public static final String TAG = "SendFileActivity";

    private static final int CODE_CHOOSE_FILE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_send_file);
        setTitle("发送文件");
    }

    public void connectWifi(View view) {
        Logger.e(TAG, "连接Wifi： " + WifiLManager.connectWifi(this, Constants.AP_SSID, Constants.AP_PASSWORD));
    }

    public void disconnectWifi(View view) {
        WifiLManager.disconnectWifi(this);
    }

    public void chooseFile(View view) {
        if (!Constants.AP_SSID.equals(WifiLManager.getConnectedSSID(this))) {
            showToast("当前连接的Wifi并非文件接收端开启的Ap热点，请重试");
            return;
        }
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
                        new FileSenderTask(this, fileTransfer).execute(WifiLManager.getHotspotIpAddress(this));
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
