package com.example.lenovo_g50_70.recording;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Button;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends AppCompatActivity {

    @BindView(R.id.btnFileMode)
    Button btnFileMode;
    @BindView(R.id.btnStreamMode)
    Button btnStreamMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
    }

    /**
     * 文件模式按钮的点击事件
     */
    @OnClick(R.id.btnFileMode)
    public void fileMode() {
        startActivity(new Intent(this, FileActivity.class));
    }

    /**
     * 字节流模式按钮的点击事件
     */
    @OnClick(R.id.btnStreamMode)
    public void streamMode() {
        startActivity(new Intent(this, StreamActivity.class));
    }
}
