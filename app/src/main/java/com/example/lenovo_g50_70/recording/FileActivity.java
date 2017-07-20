package com.example.lenovo_g50_70.recording;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.LoggingPermission;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class FileActivity extends BaseActivity {

    @BindView(R.id.btn_record)
    Button btnRecord;
    @BindView(R.id.record_result)
    TextView recordResult;

    private ExecutorService mExecutorService;
    private MediaRecorder mMediaRecorder;
    private File mAudioFile;
    private Toast mToast;

    private long mStartRecordTime;
    private long mStopRecordTime;

    private Handler mHandler;

    //主线程和后台播放线程数据同步
    private volatile boolean mIsPlaying;
    private MediaPlayer mMediaPlayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file);
        ButterKnife.bind(this);

        init();
    }

    private void init() {
        //录音 JNI 函数不具备线程安全性 所以要用单线程
        mExecutorService = Executors.newSingleThreadExecutor();

        //主线程的Handler
        mHandler = new Handler(Looper.getMainLooper());

        //按下说话，释放发送语音
        btnRecord.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        //手指按下，开始录音
                        if (!hasPermission(Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                            requestPermission(1001, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO);
                        } else {
                            startRecording();
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        //手指抬起，录音停止，发送语音
                        stopRecording();
                        break;
                }
                return true;
            }
        });
    }

    /**
     * 停止录音
     */
    private void stopRecording() {
        recordResult.setText("按住说话");
        btnRecord.setBackgroundResource(R.drawable.bg_not_say);

        //停止后台任务
        mExecutorService.submit(new Runnable() {
            @Override
            public void run() {
                //停止录音
                if (!doStop()) {
                    recordFail();
                }

                //释放 MeadiaRecorder
                releaseRecorder();
            }
        });
    }

    /**
     * 开始录音
     */
    @Override
    public void startRecording() {
        recordResult.setText("正在说话...");
        btnRecord.setBackgroundResource(R.drawable.bg_saying);

        //录音是耗时操作，提交给后台处理
        mExecutorService.submit(new Runnable() {
            @Override
            public void run() {
                //释放之前录音的recorder
                releaseRecorder();

                //执行录音逻辑，如果失败提示用户
                if (!doStart()) {
                    recordFail();
                }
            }
        });
    }

    /**
     * 停止录音的逻辑
     *
     * @return
     */
    private boolean doStop() {
        try {
            //停止录音
            mMediaRecorder.stop();
            //记录停止时间，统计时长
            mStopRecordTime = System.currentTimeMillis();
            //只接受3秒以上的录音
            final int second = (int) ((mStopRecordTime - mStartRecordTime) / 1000);
            if (second > 3) {
                //在UI上显示录音时间
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        recordResult.setText("录音时长" + second + "秒");
                    }
                });
            }
        } catch (RuntimeException e) {
            e.printStackTrace();
            //捕获异常
            return false;
        }
        return true;
    }

    /**
     * 开始录音的逻辑
     *
     * @return
     */
    private boolean doStart() {
        try {
            //创建 MeadiaRecorder
            mMediaRecorder = new MediaRecorder();
            //创建录音文件
            mAudioFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/" +
                    System.currentTimeMillis() + ".m4a");
            //创建所有的父目录
            mAudioFile.getParentFile().mkdirs();
            mAudioFile.createNewFile();

            //配置 MeadiaRecorder
            //声音从麦克风采集
            mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            //录音文件保存为MP4格式
            mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            //所有安卓系统都支持的采样频率
            mMediaRecorder.setAudioSamplingRate(44100);
            //通用的AAC 编码格式
            mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            //音质比较好的频率
            mMediaRecorder.setAudioEncodingBitRate(96000);
            //设置录音文件的位置
            mMediaRecorder.setOutputFile(mAudioFile.getAbsolutePath());

            //开始录音
            mMediaRecorder.prepare();
            mMediaRecorder.start();

            //记录开始录音的时间
            mStartRecordTime = System.currentTimeMillis();
        } catch (IOException | RuntimeException e) {
            e.printStackTrace();
            //捕获异常，避免闪退
            return false;
        }

        return true;
    }

    /**
     * 释放MediaRecorder
     */
    private void releaseRecorder() {
        //检查MediaRecorder 不为 null
        if (mMediaRecorder != null) {
            mMediaRecorder.release();
            mMediaRecorder = null;
        }
    }

    /**
     * 录音失败的处理
     */
    private void recordFail() {
        mAudioFile = null;
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                showToast("录音失败");
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //销毁时，停止后台任务，避免内存泄漏
        mExecutorService.shutdownNow();
        //停止录音
        releaseRecorder();
        //停止录音播放
        stopPlay();
    }

    public void showToast(String msg) {
        if (mToast == null) {
            mToast = Toast.makeText(this, "", Toast.LENGTH_SHORT);
        }
        //直接刷新显示内容
        mToast.setText(msg);
        mToast.show();
    }

    /**
     * 播放录音
     */
    @OnClick(R.id.btn_play)
    public void play() {
        //检查当前状态，防止重复播放
        if (mAudioFile != null && !mIsPlaying) {
            //设置当前播放状态
            mIsPlaying = true;
            //提交后台任务，开始播放
            mExecutorService.submit(new Runnable() {
                @Override
                public void run() {
                    doPlay(mAudioFile);
                }
            });
        }
    }

    /**
     * 播放录音的逻辑
     *
     * @param file
     */
    private void doPlay(File file) {
        //配置播放器 MediaPlayer
        mMediaPlayer = new MediaPlayer();

        try {
            //设置声音文件
            mMediaPlayer.setDataSource(file.getAbsolutePath());
            //设置监听回调
            mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    //播放结束，释放播放器
                    stopPlay();
                }
            });
            mMediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    //提示用户
                    playFail();
                    //释放播放器
                    stopPlay();
                    //错误已经处理
                    return true;
                }
            });
            //配置音量，是否循环
            mMediaPlayer.setVolume(1, 1);
            //不循环播放
            mMediaPlayer.setLooping(false);
            //准备工作,开始播放
            mMediaPlayer.prepare();
            mMediaPlayer.start();
        } catch (IOException | RuntimeException e) {
            //捕获异常，避免闪退
            e.printStackTrace();
            //播放失败
            playFail();
            //停止播放
            stopPlay();
        }


    }

    /**
     * 播放失败，提醒用户
     */
    private void playFail() {
        //在主线程Toast提示
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                showToast("播放失败");
            }
        });
    }

    /**
     * 录音停止播放的逻辑
     */
    private void stopPlay() {
        //重置播放状态
        mIsPlaying = false;
        //释放播放器
        if (mMediaPlayer != null) {
            //重置监听器，防止内存泄漏
            mMediaPlayer.setOnCompletionListener(null);
            mMediaPlayer.setOnErrorListener(null);

            mMediaPlayer.stop();
            mMediaPlayer.reset();
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
    }
}
