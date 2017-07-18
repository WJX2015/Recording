package com.example.lenovo_g50_70.recording;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import butterknife.BindView;
import butterknife.ButterKnife;

public class FileActivity extends AppCompatActivity {

    @BindView(R.id.btn_record)
    Button btnRecord;
    @BindView(R.id.record_result)
    TextView recordResult;

    private ExecutorService mExecutorService;

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

        //按下说话，释放发送语音
        btnRecord.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        //手指按下，开始录音
                        startRecording();
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
    private void startRecording() {
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
        return false;
    }

    /**
     * 开始录音的逻辑
     *
     * @return
     */
    private boolean doStart() {
        return false;
    }

    /**
     * 释放MediaRecorder
     */
    private void releaseRecorder() {
    }

    /**
     * 录音失败的处理
     */
    private void recordFail() {
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //销毁时，停止后台任务，避免内存泄漏
        mExecutorService.shutdownNow();
    }
}
