package com.googlecode.javacv.facepreview;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.TextView;

public class CompletedAuthorization extends Activity {

	private TextView mTextView;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		super.onCreate(savedInstanceState);
		setContentView(R.layout.completed_authorization);

		mTextView = (TextView)findViewById(R.id.beginButton);
		mTextView.setEnabled(false);
		mTextView.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent intent = new Intent(CompletedAuthorization.this, LockScreen.class);
				intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
				CompletedAuthorization.this.startActivity(intent);
				finish();
			}
		});
		loadFacePredictor();
	}

	// Construct a FacePredictor. When done, allow the next button to be pressed.
	private void loadFacePredictor() {
		new AsyncTask<Void, Void, Void>() {
			@Override
			protected Void doInBackground(Void... params) {
				FacePredictorFactory.createFacePredictor(CompletedAuthorization.this);
				return null;
			}
			@Override
			protected void onPostExecute(Void result) {
				mTextView.setEnabled(true);
			}
		}.execute();
	}

}