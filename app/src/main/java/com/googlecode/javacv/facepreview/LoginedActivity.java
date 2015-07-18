package com.googlecode.javacv.facepreview;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.Button;


public class LoginedActivity extends Activity {

    public static boolean isReset = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_logined);
        Button reset = (Button) findViewById(R.id.resetButton);
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //File recognizerFile = new File(Launcher.getExternalFilesDir(null).getAbsolutePath() + "/recognizer.xml");
                //Launcher.recognizerFile.delete();
                //isReset = true;
                Intent intent = new Intent(LoginedActivity.this, Launcher.class);
                startActivity(intent);
                finish();
            }
        });

        Button testAgain = (Button) findViewById(R.id.testAgainButton);
        testAgain.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(LoginedActivity.this, LockScreen.class);
                startActivity(intent);
                finish();
            }
        });
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
}
