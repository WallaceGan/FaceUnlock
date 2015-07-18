package com.googlecode.javacv.facepreview;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.googlecode.javacv.facepreview.views.FaceViewWithAnalysis;
import com.googlecode.javacv.facepreview.views.Preview;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;



public class LockScreen extends Activity implements FaceViewWithAnalysis.SuccessCallback {
    private FrameLayout layout;
    private FaceViewWithAnalysis faceView;
    private Preview mPreview;
    private Timer timer=null;
    private static Handler handler = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);        // Hide the window title.
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        try {
            layout = new FrameLayout(this);
            faceView = new FaceViewWithAnalysis(this);
            mPreview = new Preview(this, faceView);
            faceView.setSuccessCallback(this);
            layout.addView(mPreview);
            layout.addView(faceView);
            setContentView(layout);
        } catch (IOException e) {
            e.printStackTrace();
            new AlertDialog.Builder(this).setMessage(e.getMessage()).create().show();
        }
         handler = new Handler() {
             @Override
             public void handleMessage(Message msg) {
                // handler处理消息
                 switch(msg.what){
                     case 1:
//                         Toast toast = Toast.makeText(getApplicationContext(), "认证超时 " , Toast.LENGTH_LONG);
//                         toast.show();
                         AlertDialog.Builder dialog = new AlertDialog.Builder(LockScreen.this);
                         dialog.setTitle("提示");
                         dialog.setMessage("登录超时。请重新登录或者重置认证。");
                         dialog.setCancelable(false);
                         dialog.setPositiveButton("返回", new DialogInterface.OnClickListener() {
                             @Override
                             public void onClick(DialogInterface dialogInterface, int i) {
                                 Intent myIntent = new Intent(LockScreen.this, Launcher.class);
                                 startActivity(myIntent);
                                 finish();
                             }
                         });
                         dialog.show();
                         break;
                 }
                 super.handleMessage(msg);
            }
        };
        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {

                Log.i("认证中", Thread.currentThread().getName());

                // 定义一个消息传过去，20秒的延迟
                Message msg = new Message();
                msg.what = 1;
                handler.sendMessage(msg);
            }

        }, 20000, 20000);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()){
            case R.id.exit_item:
                finish();
                break;
            default:
        }
        return true;
    }
    @Override
    public void success(boolean bool) {


        int duration = Toast.LENGTH_SHORT;
//        Toast toast = Toast.makeText(getApplicationContext(), "Debug: result = " + bool, duration);
//        toast.show();

        if (bool){
            Toast toast = Toast.makeText(getApplicationContext(), "认证成功", duration);
            toast.show();
            Intent myIntent = new Intent(LockScreen.this, LoginedActivity.class);
            this.startActivity(myIntent);
            finish();
        } else{
            Toast toast = Toast.makeText(getApplicationContext(), "认证失败,请重试", duration);
            toast.show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        timer.cancel();
    }
}