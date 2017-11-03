package doext.videoview;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import core.helper.DoResourcesHelper;
import core.helper.DoScriptEngineHelper;
import core.interfaces.DoIModuleTypeID;
import core.object.DoUIModule;
import doext.implement.do_VideoView_View;
import doext.videoview.MyVideoPlayer.OnFullScreenClickListener;
import doext.videoview.MyVideoPlayer.OnVideoPlayerPreparedListener;

public class FillScreenVideoActivity extends Activity implements DoIModuleTypeID, OnVideoPlayerPreparedListener, OnFullScreenClickListener {

	private MyVideoPlayer videoPlayer;
	private int position;
	private ProgressBar progressBar;
	private FrameLayout contentView;
	private MyBroadcastReceiver myBroadcastReceiver;
	private Context mContext;
	private DoUIModule model;
	private String playUrl;
	private Bitmap startBmp;
	private Bitmap pauseBmp;
	private boolean isStart;
	private boolean isControlVisible = true;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mContext = this;
		String modelAddress = getIntent().getStringExtra("modelAddress");
		if (!TextUtils.isEmpty(modelAddress)) {
			model = DoScriptEngineHelper.parseUIModule(DoScriptEngineHelper.getCurrentPageScriptEngine(), modelAddress);
		}

		initReceiver();
		contentView = new FrameLayout(this);
		progressBar = new ProgressBar(this);

		int do_videoview_id = DoResourcesHelper.getIdentifier("do_videoview", "layout", this);
		View videoView = View.inflate(mContext, do_videoview_id, contentView);
		int sv_id = DoResourcesHelper.getIdentifier("sv", "id", this);
		SurfaceView surfaceView = (SurfaceView) videoView.findViewById(sv_id);

		startBmp = BitmapFactory.decodeResource(getResources(), DoResourcesHelper.getIdentifier("videoview_start", "drawable", this));
		pauseBmp = BitmapFactory.decodeResource(getResources(), DoResourcesHelper.getIdentifier("videoview_pause", "drawable", this));

		int operation_id = DoResourcesHelper.getIdentifier("operation", "id", this);
		final ImageView operation = (ImageView) videoView.findViewById(operation_id);
		operation.setVisibility(View.VISIBLE);
		operation.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (videoPlayer != null) {
					if (isStart) {
						operation.setImageBitmap(pauseBmp);
						videoPlayer.show(MyVideoPlayer.sDefaultTimeout);
						int pos = videoPlayer.getCurrentPosition();
						videoPlayer.start();
						videoPlayer.seekTo(pos);
						isStart = false;
					} else {
						operation.setImageBitmap(startBmp);
						videoPlayer.pause();
						isStart = true;
					}
				}
			}
		});

		int media_ll_id = DoResourcesHelper.getIdentifier("media_ll", "id", this);
		LinearLayout mediaLayout = (LinearLayout) videoView.findViewById(media_ll_id);

		int sb_id = DoResourcesHelper.getIdentifier("sb", "id", this);
		SeekBar sb = (SeekBar) videoView.findViewById(sb_id);

		int time_current_id = DoResourcesHelper.getIdentifier("time_current", "id", this);
		TextView mCurrentTime = (TextView) videoView.findViewById(time_current_id);

		int time_id = DoResourcesHelper.getIdentifier("time", "id", this);
		TextView mEndTime = (TextView) videoView.findViewById(time_id);

		videoPlayer = new MyVideoPlayer((Activity) mContext, this.model, surfaceView, mediaLayout, null, mCurrentTime, mEndTime, sb);

		FrameLayout.LayoutParams pb_lp = new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		pb_lp.gravity = Gravity.CENTER;
		contentView.addView(progressBar, pb_lp);
		this.setContentView(contentView, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

		playUrl = getIntent().getStringExtra("path");
		position = getIntent().getIntExtra("point", 0);
		isControlVisible = getIntent().getBooleanExtra("controlVisible", true);
		videoPlayer.setControlVisible(isControlVisible);
		videoPlayer.setOnVideoPlayerPreparedListener(this);
		videoPlayer.setOnFullScreenClickListener(this);
	}

	@Override
	public void onPrepared(MediaPlayer mp) {
		progressBar.setVisibility(View.GONE);
		if (!TextUtils.isEmpty(playUrl)) {
			videoPlayer.playUrl(playUrl);
			if (!videoPlayer.isPlaying()) {
				// 按照初始位置播放
				videoPlayer.seekTo(position);
				videoPlayer.start();
			}
		}
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			close();
		}
		return super.onKeyDown(keyCode, event);
	}

	public void close() {
		if (myBroadcastReceiver != null) {
			this.unregisterReceiver(myBroadcastReceiver);
		}
		Intent _resultIntent = new Intent();
		_resultIntent.putExtra("point", videoPlayer.getCurrentPosition());
		setResult(2000, _resultIntent);
		finish();
	}

	private void initReceiver() {
		myBroadcastReceiver = new MyBroadcastReceiver();
		IntentFilter filter = new IntentFilter();
		filter.addAction(do_VideoView_View.DO_VIDEOVIEW_VIEW_FINISH_FILL_SCREEN_VIDEO);
		registerReceiver(myBroadcastReceiver, filter);
	}

	private class MyBroadcastReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent arg1) {
			if (arg1.getAction().equals(do_VideoView_View.DO_VIDEOVIEW_VIEW_FINISH_FILL_SCREEN_VIDEO)) {
				close();
			}
		}

	}

	@Override
	public String getTypeID() {
		if (model != null) {
			return this.model.getTypeID();
		}
		return "do_VideoView";
	}

	@Override
	public void onFullScreenClick(View view) {
		close();
	}
}