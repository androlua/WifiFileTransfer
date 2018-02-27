package com.czy.wififiletransfer.task;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import com.czy.wififiletransfer.common.Constants;
import com.czy.wififiletransfer.model.FileTransfer;
import com.czy.wififiletransfer.util.Logger;
import com.czy.wififiletransfer.util.Md5Util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * 作者：chenZY
 * 时间：2018/2/24 10:21
 * 描述：用于发送文件
 * GitHub主页：https://github.com/leavesC
 * 简书主页：https://www.jianshu.com/u/9df45b87cfdf
 */
public class FileSenderTask extends AsyncTask<String, Double, Boolean> {

    private ProgressDialog progressDialog;

    private FileTransfer fileTransfer;

    private static final String TAG = "FileSenderTask";

    public FileSenderTask(Context context, FileTransfer fileTransfer) {
        this.fileTransfer = fileTransfer;
        progressDialog = new ProgressDialog(context);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setCancelable(false);
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.setTitle("发送文件：" + fileTransfer.getFilePath());
        progressDialog.setMax(100);
    }

    @Override
    protected void onPreExecute() {
        progressDialog.show();
    }

    @Override
    protected Boolean doInBackground(String... strings) {
        Logger.e(TAG, "开始计算文件的MD5码");
        fileTransfer.setMd5(Md5Util.getMd5(new File(fileTransfer.getFilePath())));
        Log.e(TAG, "计算结束，文件的MD5码值是：" + fileTransfer.getMd5());
        Socket socket = null;
        OutputStream outputStream = null;
        ObjectOutputStream objectOutputStream = null;
        InputStream inputStream = null;
        try {
            socket = new Socket();
            socket.bind(null);
            socket.connect((new InetSocketAddress(strings[0], Constants.PORT)), 20000);
            outputStream = socket.getOutputStream();
            objectOutputStream = new ObjectOutputStream(outputStream);
            objectOutputStream.writeObject(fileTransfer);
            inputStream = new FileInputStream(new File(fileTransfer.getFilePath()));
            byte buf[] = new byte[512];
            int len;
            //文件大小
            long fileSize = fileTransfer.getFileLength();
            //当前的传输进度
            double progress;
            //总的已传输字节数
            long total = 0;
            //缓存-当次更新进度时的时间
            long tempTime = System.currentTimeMillis();
            //缓存-当次更新进度时已传输的总字节数
            long tempTotal = 0;
            //传输速率（Kb/s）
            double speed;
            //预估的剩余完成时间（秒）
            double remainingTime;
            while ((len = inputStream.read(buf)) != -1) {
                outputStream.write(buf, 0, len);
                total += len;
                long time = System.currentTimeMillis() - tempTime;
                //每一秒更新一次传输速率和传输进度
                if (time > 1000) {
                    //当前的传输进度
                    progress = total * 100 / fileSize;
                    Logger.e(TAG, "---------------------------");
                    Logger.e(TAG, "传输进度（%）: " + progress);
                    Logger.e(TAG, "时间变化（秒）：" + time / 1000.0);
                    Logger.e(TAG, "字节变化：" + (total - tempTotal));
                    //计算传输速率，字节转Kb，毫秒转秒
                    speed = ((total - tempTotal) / 1024.0 / (time / 1000.0));
                    //预估的剩余完成时间
                    remainingTime = (fileSize - total) / 1024.0 / speed;
                    publishProgress(progress, speed, remainingTime);
                    Logger.e(TAG, "传输速率（Kb/秒）：" + speed);
                    Logger.e(TAG, "预估的剩余完成时间（秒）：" + remainingTime);
                    //缓存-当次更新进度时已传输的总字节数
                    tempTotal = total;
                    //缓存-当次更新进度时的时间
                    tempTime = System.currentTimeMillis();
                }
            }
            outputStream.close();
            objectOutputStream.close();
            inputStream.close();
            socket.close();
            outputStream = null;
            objectOutputStream = null;
            inputStream = null;
            socket = null;
            Log.e(TAG, "文件发送成功");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "文件发送异常 Exception: " + e.getMessage());
            return false;
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (objectOutputStream != null) {
                try {
                    objectOutputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (socket != null) {
                try {
                    socket.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    protected void onProgressUpdate(Double... values) {
        progressDialog.setProgress(values[0].intValue());
        progressDialog.setTitle("传输速率：" + values[1].intValue() + "Kb/s" + "\n"
                + "预计剩余完成时间：" + values[2].longValue() + "秒");
    }

    @Override
    protected void onPostExecute(Boolean aBoolean) {
        progressDialog.cancel();
        Log.e(TAG, "onPostExecute: " + aBoolean);
    }

}
