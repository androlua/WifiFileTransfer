package com.czy.wififiletransfer.service;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Environment;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import com.czy.wififiletransfer.common.Constants;
import com.czy.wififiletransfer.model.FileTransfer;
import com.czy.wififiletransfer.util.Logger;
import com.czy.wififiletransfer.util.Md5Util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * 作者：chenZY
 * 时间：2018/2/24 9:45
 * 描述：用于接收文件
 * GitHub主页：https://github.com/leavesC
 * 简书主页：https://www.jianshu.com/u/9df45b87cfdf
 */
public class FileReceiverService extends IntentService {

    private static final String ACTION_START_RECEIVE = "com.czy.wififiletransfer.service.action.startReceive";

    private static final String TAG = "FileReceiverService";

    public interface OnReceiveProgressChangListener {

        /**
         * 当传输进度发生变化时回调
         *
         * @param fileTransfer  文件发送方传来的文件模型
         * @param progress      文件传输进度
         * @param speed         文件传输速率
         * @param remainingTime 预估的剩余完成时间
         */
        void onProgressChanged(FileTransfer fileTransfer, int progress, double speed, long remainingTime);

        /**
         * 当文件传输结束后，开始计算MD5码时回调
         */
        void onStartComputeMD5();

        /**
         * 当文件传输操作和MD5码计算结束时回调
         *
         * @param fileTransfer 如果文件传输成功且MD5码校验成功，则MD5码不为null，否则MD5码为null
         */
        void onTransferFinished(FileTransfer fileTransfer);

    }

    private ServerSocket serverSocket;

    private InputStream inputStream;

    private ObjectInputStream objectInputStream;

    private FileOutputStream fileOutputStream;

    private OnReceiveProgressChangListener progressChangListener;

    public class MyBinder extends Binder {
        public FileReceiverService getService() {
            return FileReceiverService.this;
        }
    }

    public FileReceiverService() {
        super("FileReceiverService");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new MyBinder();
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null && ACTION_START_RECEIVE.equals(intent.getAction())) {
            clean();
            File file = null;
            FileTransfer fileTransfer = null;
            try {
                serverSocket = new ServerSocket();
                serverSocket.setReuseAddress(true);
                serverSocket.bind(new InetSocketAddress(Constants.PORT));
                Socket client = serverSocket.accept();
                Log.e(TAG, "客户端IP地址 : " + client.getInetAddress().getHostAddress());
                inputStream = client.getInputStream();
                objectInputStream = new ObjectInputStream(inputStream);
                fileTransfer = (FileTransfer) objectInputStream.readObject();
                Log.e(TAG, "待接收的文件: " + fileTransfer);
                if (fileTransfer == null || TextUtils.isEmpty(fileTransfer.getMd5())) {
                    return;
                }
                String name = new File(fileTransfer.getFilePath()).getName();
                //将文件存储至指定位置
                file = new File(Environment.getExternalStorageDirectory() + "/" + name);
                fileOutputStream = new FileOutputStream(file);
                byte buf[] = new byte[512];
                int len;
                //文件大小
                long fileSize = fileTransfer.getFileLength();
                //当前的传输进度
                int progress;
                //总的已接收字节数
                long total = 0;
                //缓存-当次更新进度时的时间
                long tempTime = System.currentTimeMillis();
                //缓存-当次更新进度时已接收的总字节数
                long tempTotal = 0;
                //传输速率（Kb/s）
                double speed;
                //预估的剩余完成时间（秒）
                long remainingTime;
                while ((len = inputStream.read(buf)) != -1) {
                    fileOutputStream.write(buf, 0, len);
                    total += len;
                    long time = System.currentTimeMillis() - tempTime;
                    //每一秒更新一次传输速率和传输进度
                    if (time > 1000) {
                        //当前的传输进度
                        progress = (int) (total * 100 / fileSize);
                        Logger.e(TAG, "---------------------------");
                        Logger.e(TAG, "传输进度: " + progress);
                        Logger.e(TAG, "时间变化：" + time / 1000.0);
                        Logger.e(TAG, "字节变化：" + (total - tempTotal));
                        //计算传输速率，字节转Kb，毫秒转秒   17:45:07
                        speed = ((total - tempTotal) / 1024.0 / (time / 1000.0));
                        //预估的剩余完成时间
                        remainingTime = (long) ((fileSize - total) / 1024.0 / speed);
                        Logger.e(TAG, "传输速率：" + speed);
                        Logger.e(TAG, "预估的剩余完成时间：" + remainingTime);
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
                    //因为上面在计算文件传输进度时因为小数点问题可能不会显示到100%，所以此处手动将之设为100%
                    progressChangListener.onProgressChanged(fileTransfer, 100, 0, 0);
                    //开始计算传输到本地的文件的MD5码
                    progressChangListener.onStartComputeMD5();
                }
                Log.e(TAG, "文件接收成功");
            } catch (Exception e) {
                Log.e(TAG, "文件接收 Exception: " + e.getMessage());
            } finally {
                clean();
                if (progressChangListener != null) {
                    if (fileTransfer == null || TextUtils.isEmpty(fileTransfer.getMd5())) {
                        progressChangListener.onTransferFinished(null);
                    } else {
                        if (file != null && file.exists()) {
                            FileTransfer transfer = new FileTransfer();
                            transfer.setFilePath(file.getPath());
                            transfer.setFileLength(file.length());
                            String md5 = Md5Util.getMd5(file);
                            //如果本地计算出的MD5码和文件发送端传来的值不一致，则认为传输失败
                            transfer.setMd5(fileTransfer.getMd5().equals(md5) ? md5 : null);
                            Log.e(TAG, "文件的MD5码是：" + md5);
                            if (progressChangListener != null) {
                                progressChangListener.onTransferFinished(fileTransfer);
                            }
                        } else {
                            progressChangListener.onTransferFinished(null);
                        }
                    }
                }
                //再次启动服务，等待客户端下次连接
                startActionTransfer(this);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        clean();
    }

    private void clean() {
        if (serverSocket != null) {
            try {
                serverSocket.close();
                serverSocket = null;
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
        if (objectInputStream != null) {
            try {
                objectInputStream.close();
                objectInputStream = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (fileOutputStream != null) {
            try {
                fileOutputStream.close();
                fileOutputStream = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void startActionTransfer(Context context) {
        Intent intent = new Intent(context, FileReceiverService.class);
        intent.setAction(ACTION_START_RECEIVE);
        context.startService(intent);
    }

    public void setProgressChangListener(OnReceiveProgressChangListener progressChangListener) {
        this.progressChangListener = progressChangListener;
    }

}
