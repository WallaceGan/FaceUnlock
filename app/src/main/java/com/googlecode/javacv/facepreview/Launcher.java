package com.googlecode.javacv.facepreview;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextPaint;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;

// The App's entry point: immediately starts one of two different Activitys, without a transition animation.
public class Launcher extends Activity {
	private ImageView loginImage;
	private TextView topText;
	private TextPaint tp;
	private   Button loginbtn;
	private EditText username;
	private EditText password;

	private Drawable mIconPerson;
	private Drawable mIconLock;
	File recognizerFile;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_launcher);

		topText=(TextView)findViewById(R.id.topname);
		topText.setTextColor(Color.WHITE);
		topText.setTextSize(24.0f);
		topText.setTypeface(Typeface.MONOSPACE, Typeface.BOLD_ITALIC);
		//使用TextPaint的仿“粗体”设置setFakeBoldText为true。目前还无法支持仿“斜体”方法
		tp=topText.getPaint();

		tp.setFakeBoldText(true);
		loginImage=(ImageView)findViewById(R.id.loginImage);
//		loginImage.setBackgroundDrawable(new BitmapDrawable(Util.toRoundBitmap(this, "test.jpg")));
//		loginImage.getBackground().setAlpha(0);
//		loginImage.setImageBitmap(Util.toRoundBitmap(this, "test.jpg"));

		Button signIn = (Button) findViewById(R.id.signInButton);
		recognizerFile = new File(this.getExternalFilesDir(null).getAbsolutePath() + "/recognizer.xml");
		signIn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				//File recognizerFile = new File(this.getExternalFilesDir(null).getAbsolutePath() + "/recognizer.xml");
				//Launcher.recognizerFile.delete();
				//isReset = true;
				if (recognizerFile.exists()) {
			    	Intent intent = new Intent(Launcher.this,LockScreen.class);
			    	startActivity(intent);
			    	finish();
			    } else {
			    	Intent intent = new Intent(Launcher.this, Introduction.class);
			    	startActivity(intent);
			    	finish();
			    }
			}
		});

		Button signUp = (Button) findViewById(R.id.signUpButton);
		signUp.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				//File recognizerFile = new File(this.getExternalFilesDir(null).getAbsolutePath() + "/recognizer.xml");
				if (recognizerFile.exists()) {
					recognizerFile.delete();
					System.gc();
				}
				Intent intent = new Intent(Launcher.this, Introduction.class);
				startActivity(intent);
				finish();
			}
		});

		//init();
//		if (LoginedActivity.isReset){
//			recognizerFile.delete();
//			LoginedActivity.isReset = false;
//		}

//		if (recognizerFile.exists()) {
//	    	Intent intent = new Intent(this,LockScreen.class);
//	    	startActivity(intent);
//	    	finish();
//	    } else {
//	    	Intent intent = new Intent(this, Introduction.class);
//	    	startActivity(intent);
//	    	finish();
//	    }

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