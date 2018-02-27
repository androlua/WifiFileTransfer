package com.czy.wififiletransfer.service;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.text.TextUtils;
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
 * 时间：2018/2/26 17:06
 * 描述：发送文件
 * GitHub主页：https://github.com/leavesC
 * 简书主页：https://www.jianshu.com/u/9df45b87cfdf
 */
public class FileSenderService extends IntentService {

    private Socket socket;

    private OutputStream outputStream;

    private ObjectOutputStream objectOutputStream;

    private InputStream inputStream;

    private OnSendProgressChangListener progressChangListener;

    private static final String ACTION_START_SEND = "com.czy.wififiletransfer.service.action.startSend";

    private static final String EXTRA_PARAM_FILE_TRANSFER = "com.czy.wififiletransfer.service.extra.FileTransfer";

    private static final String EXTRA_PARAM_IP_ADDRESS = "com.czy.wififiletransfer.service.extra.IpAddress";

    private static final String TAG = "FileSenderService";

    public interface OnSendProgressChangListener {

        /**
         * 如果待发送的文件还没计算MD5码，则在开始计算MD5码时回调
         */
        void onStartComputeMD5();

        /**
         * 当传输进度发生变化时回调
         *
         * @param fileTransfer  待发送的文件模型
         * @param progress      文件传输进度
         * @param speed         文件传输速率
         * @param remainingTime 预估的剩余完成时间
         */
        void onProgressChanged(FileTransfer fileTransfer, int progress, double speed, long remainingTime);

        /**
         * 当文件传输成功时回调
         *
         * @param fileTransfer FileTransfer
         */
        void onTransferSucceed(FileTransfer fileTransfer);

        /**
         * 当文件传输失败时回调
         *
         * @param fileTransfer FileTransfer
         * @param e            Exception
         */
        void onTransferFailed(FileTransfer fileTransfer, Exception e);

    }

    public FileSenderService() {
        super("FileSenderService");
    }

    public class MyBinder extends Binder {
        public FileSenderService getService() {
            return FileSenderService.this;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new FileSenderService.MyBinder();
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null && ACTION_START_SEND.equals(intent.getAction())) {
            clean();
            FileTransfer fileTransfer = (FileTransfer) intent.getSerializableExtra(EXTRA_PARAM_FILE_TRANSFER);
            String ipAddress = intent.getStringExtra(EXTRA_PARAM_IP_ADDRESS);
            if (fileTransfer == null || TextUtils.isEmpty(ipAddress)) {
                return;
            }
            if (TextUtils.isEmpty(fileTransfer.getMd5())) {
                Logger.e(TAG, "MD5码为空，开始计算文件的MD5码");
                if (progressChangListener != null) {
                    progressChangListener.onStartComputeMD5();
                }
                fileTransfer.setMd5(Md5Util.getMd5(new File(fileTransfer.getFilePath())));
                Log.e(TAG, "计算结束，文件的MD5码值是：" + fileTransfer.getMd5());
            } else {
                Logger.e(TAG, "MD5码不为空，无需再次计算，MD5码为：" + fileTransfer.getMd5());
            }
            try {
                socket = new Socket();
                socket.bind(null);
                socket.connect((new InetSocketAddress(ipAddress, Constants.PORT)), 20000);
                outputStream = socket.getOutputStream();
                objectOutputStream = new ObjectOutputStream(outputStream);
                objectOutputStream.writeObject(fileTransfer);
                inputStream = new FileInputStream(new File(fileTransfer.getFilePath()));
                byte buf[] = new byte[512];
                int len;
                //文件大小
                long fileSize = fileTransfer.getFileLength();
                //当前的传输进度
                int progress;
                //总的已传输字节数
                long total = 0;
                //缓存-当次更新进度时的时间
                long tempTime = System.currentTimeMillis();
                //缓存-当次更新进度时已传输的总字节数
                long tempTotal = 0;
                //传输速率（Kb/s）
                double speed;
                //预估的剩余完成时间（秒）
                long remainingTime;
                while ((len = inputStream.read(buf)) != -1) {
                    outputStream.write(buf, 0, len);
                    total += len;
                    long time = System.currentTimeMillis() - tempTime;
                    //每一秒更新一次传输速率和传输进度
                    if (time > 1000) {
                        //当前的传输进度
                        progress = (int) (total * 100 / fileSize);
                        Logger.e(TAG, "---------------------------");
                        Logger.e(TAG, "传输进度（%）: " + progress);
                        Logger.e(TAG, "时间变化（秒）：" + time / 1000.0);
                        Logger.e(TAG, "字节变化：" + (total - tempTotal));
                        //计算传输速率，字节转Kb，毫秒转秒
                        speed = ((total - tempTotal) / 1024.0 / (time / 1000.0));
                        //预估的剩余完成时间
                        remainingTime = (long) ((fileSize - total) / 1024.0 / speed);
                        Logger.e(TAG, "传输速率（Kb/秒）：" + speed);
                        Logger.e(TAG, "预估的剩余完成时间（秒）：" + remainingTime);
                        //缓存-当次更新进度时已传输的总字节数
                        tempTotal = total;
                        //缓存-当次更新进度时的时间
                        tempTime = System.currentTimeMillis();
                        if (progressChangListener != null) {
                            progressChangListener.onProgressChanged(fileTransfer, progress, speed, remainingTime);
                        }
                    }
                }
                if (progressChangListener != null) {
                    Log.e(TAG, "文件发送成功");
                    //因为上面在计算文件传输进度时因为小数点问题可能不会显示到100%，所以此处手动将之设为100%
                    progressChangListener.onProgressChanged(fileTransfer, 100, 0, 0);
                    progressChangListener.onTransferSucceed(fileTransfer);
                }
            } catch (Exception e) {
                Log.e(TAG, "文件发送异常 Exception: " + e.getMessage());
                if (progressChangListener != null) {
                    progressChangListener.onTransferFailed(fileTransfer, e);
                }
            } finally {
                clean();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.e(TAG, "onDestroy");
    }

    private void clean() {
        if (socket != null) {
            try {
                socket.close();
                socket = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (outputStream != null) {
            try {
                outputStream.close();
                outputStream = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (objectOutputStream != null) {
            try {
                objectOutputStream.close();
                objectOutputStream = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (inputStream != null) {
            try {
                inputStream.close();
                inputStream = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void startActionTransfer(Context context, FileTransfer fileTransfer, String ipAddress) {
        Intent intent = new Intent(context, FileSenderService.class);
        intent.setAction(ACTION_START_SEND);
        intent.putExtra(EXTRA_PARAM_FILE_TRANSFER, fileTransfer);
        intent.putExtra(EXTRA_PARAM_IP_ADDRESS, ipAddress);
        context.startService(intent);
    }

    public void setProgressChangListener(OnSendProgressChangListener progressChangListener) {
        this.progressChangListener = progressChangListener;
    }

}
