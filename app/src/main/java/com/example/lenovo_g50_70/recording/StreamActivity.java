package com.example.lenovo_g50_70.recording;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class StreamActivity extends AppCompatActivity {
    @BindView(R.id.record_result)
    TextView mTextView;
    @BindView(R.id.btn_record)
    Button mButton;

    //录音状态,volatile 保证多线程内存同步,避免出问题
    private volatile boolean mIsRecording;
    private ExecutorService mExecutorService;
    private Handler mHandler;
    private File mAudioFile;
    private Toast mToast;
    private long mStartRecordTime;
    private long mStopRecordTime;

    //mByte不能太大，避免OOM
    private static final int BUFFER_SIZE = 2048;
    private byte[] mByte;
    private FileOutputStream mFileOutputStream;
    private AudioRecord mAudioRecord;

    private volatile boolean mIsPlaying;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stream);
        ButterKnife.bind(this);

        init();
    }

    /**
     * 初始化操作
     */
    private void init() {
        mByte = new byte[BUFFER_SIZE];

        mExecutorService = Executors.newSingleThreadExecutor();
        mHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mExecutorService.shutdownNow();
    }

    /**
     * 开始或停止录音
     */
    @OnClick(R.id.btn_record)
    public void start() {
        if (mIsRecording) {
            //更改UI
            mButton.setText("开始录音");
            //更改状态标识
            mIsRecording = false;
        } else {
            mButton.setText("停止录音");
            mIsRecording = true;
            //执行录音开始逻辑
            mExecutorService.submit(new Runnable() {
                @Override
                public void run() {
                    if (!startRecord()) {
                        recordFail();
                    }
                }
            });
        }
    }

    /**
     * 录音逻辑的启动是否成功
     *
     * @return
     */
    private boolean startRecord() {
        try {
            //创建录音文件
            mAudioFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/" +
                    System.currentTimeMillis() + ".pcm");
            //确保所有的父目录都存在
            mAudioFile.getParentFile().mkdirs();
            mAudioFile.createNewFile();
            //创建文件输出流
            mFileOutputStream = new FileOutputStream(mAudioFile);

            //配置 AudioRecord
            int audioSource = MediaRecorder.AudioSource.MIC;
            //所有安卓系统都支持的频率
            int sampleRate = 44100;
            //单声道输入
            int channelConfig = AudioFormat.CHANNEL_IN_MONO;
            //PCM_16,所有安卓系统都支持
            int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
            //计算AudioRecord 内部buffer最小的大小
            int minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);
            mAudioRecord = new AudioRecord(audioSource, sampleRate,
                    channelConfig, audioFormat, Math.max(minBufferSize, BUFFER_SIZE));

            //开始录音
            mAudioRecord.startRecording();

            //记录开始录音时间
            mStartRecordTime = System.currentTimeMillis();

            //循环读取数据，写道输出流中
            while (mIsRecording) {
                //只要是录音状态，就一直读取数据
                int read = mAudioRecord.read(mByte, 0, BUFFER_SIZE);
                if (read > 0) {//读取成功
                    mFileOutputStream.write(mByte, 0, read);
                } else {
                    //读取失败
                    return false;
                }
            }
            return stopRecord();
        } catch (IOException | RuntimeException e) {
            e.printStackTrace();
            return false;
        } finally {
            //退出循环,停止录音，释放资源
            if (mAudioRecord != null) {
                mAudioRecord.release();
            }
        }
    }

    /**
     * 停止录音的逻辑
     *
     * @return
     */
    private boolean stopRecord() {
        try {
            //关闭文件输出流
            mAudioRecord.stop();
            mAudioRecord.release();
            mAudioRecord = null;
            mFileOutputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        //记录结束时间，统计录音时长
        mStopRecordTime = System.currentTimeMillis();
        //大于3秒才算成功
        final int second = (int) ((mStopRecordTime - mStartRecordTime) / 1000);
        if (second > 3) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mTextView.setText("录音时长" + second + "秒");
                }
            });
        }
        return true;
    }

    /**
     * 录音失败的处理
     */
    private void recordFail() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                showToast("录音失败");

                //重置录音状态以及UI状态
                mIsRecording = false;
                mButton.setText("开始录音");
            }
        });
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
     * 录音播放
     */
    @OnClick(R.id.btn_play)
    public void play() {
        //检查播放状态，防止重复播放
        if (mAudioFile != null && mIsPlaying) {
            //设置当前为播放状态
            mIsPlaying = true;

            //在后台线程提交播放任务，防止堵塞主线程
            mExecutorService.submit(new Runnable() {
                @Override
                public void run() {
                    doPlay(mAudioFile);
                }
            });
        }
    }

    /**
     * 启动录音逻辑
     *
     * @param file
     */
    private void doPlay(File file) {
        //配置播放器
        //音乐类型，扬声器播放
        int streamType = AudioManager.STREAM_MUSIC;
        //录音采用的采样频率，所以播放时采用一样的
        int sampleRate = 44100;
        //录音时使用输入单声道，播放时使用输出单声道
        int channelConfig = AudioFormat.CHANNEL_OUT_MONO;
        //录音时采用的，播放时也使用同样的
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
        //流的模式
        int mode = AudioTrack.MODE_STREAM;
        //计算最小 buffer
        int minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat);
        //构造AudioTrack
        AudioTrack audioTrack = new AudioTrack(streamType, sampleRate, channelConfig,
                audioFormat, Math.max(minBufferSize, BUFFER_SIZE), mode);
        //开始播放
        audioTrack.play();
        //从文件流中读数据
        FileInputStream inputStream = null;
        try {
            inputStream = new FileInputStream(file);
            //循环读数据，写到播放器去播放
            int read;
            while ((read = inputStream.read(mByte)) > 0) {
                int ret = audioTrack.write(mByte, 0, read);
                //检查write返回值，错误处理
                switch (ret) {
                    case AudioTrack.ERROR_BAD_VALUE:
                    case AudioTrack.ERROR_DEAD_OBJECT:
                    case AudioTrack.ERROR_INVALID_OPERATION:
                        playFail();
                        return;
                    default:
                        break;
                }
            }
        } catch (RuntimeException | IOException e) {
            e.printStackTrace();
            //捕获异常，防止闪退
            playFail();
        } finally {
            mIsPlaying = false;
            //关闭文件输入流
            if (inputStream != null) {
                closeQuietlyInputStream(inputStream);
            }
            //释放播放器
            resetQuietly(audioTrack);
        }
    }

    private void resetQuietly(AudioTrack audioTrack) {
        try {
            audioTrack.stop();
            audioTrack.release();
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
    }

    /**
     * 静默关闭文件输入流
     */
    private void closeQuietlyInputStream(FileInputStream inputStream) {
        try {
            inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 录音播放失败
     */
    private void playFail() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                showToast("播放失败");
            }
        });
    }
}
