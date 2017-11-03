package doext.videoview;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Locale;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnBufferingUpdateListener;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import core.DoServiceContainer;
import core.helper.DoIOHelper;
import core.object.DoInvokeResult;
import core.object.DoUIModule;

public class MyVideoPlayer implements OnBufferingUpdateListener, OnErrorListener, OnCompletionListener, MediaPlayer.OnPreparedListener, SurfaceHolder.Callback, OnClickListener {
	private int videoWidth;
	private int videoHeight;
	private MediaPlayer mMediaPlayer;
	private SurfaceHolder surfaceHolder;
	private LinearLayout mediaLayout;
	private SeekBar skbProgress;
	private TextView mEndTime, mCurrentTime;
	private ImageView imageViewFullscreen;
	private StringBuilder mFormatBuilder;
	private Formatter mFormatter;
	private boolean mShowing = true;
	private SurfaceView surfaceView;
	public static final int sDefaultTimeout = 3000;
	private static final int SHOW_PROGRESS = 2;
	private static final int FADE_OUT = 1;
	private boolean mDragging;
	private int mCurrentBufferPercentage = 0;
	private boolean isCallPalyMethod;
	private int mSeekWhenPrepared; // recording the seek position while
									// preparing

	// all possible internal states
	private static final int STATE_ERROR = -1;
	private static final int STATE_IDLE = 0;
	private static final int STATE_PREPARING = 1;
	private static final int STATE_PREPARED = 2;
	private static final int STATE_PLAYING = 3;
	private static final int STATE_PAUSED = 4;
	private static final int STATE_PLAYBACK_COMPLETED = 5;

	// mCurrentState is a VideoView object's current state.
	// mTargetState is the state that a method caller intends to reach.
	// For instance, regardless the VideoView object's current state,
	// calling pause() intends to bring the object to a target state
	// of STATE_PAUSED.
	private int mCurrentState = STATE_IDLE;

	private Activity mActivity;
	private DoUIModule model;
	private String playUrl;
	private boolean controlVisible = true;

	public MyVideoPlayer(Activity activity, DoUIModule model, SurfaceView surfaceView, LinearLayout mediaLinerLayout, ImageView ivFullscreen, TextView time_current, TextView time,
			SeekBar mediacontroller_progress) {
		this.mActivity = activity;
		this.model = model;
		surfaceHolder = surfaceView.getHolder();
		surfaceHolder.addCallback(this);
		surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		this.surfaceView = surfaceView;
		this.mediaLayout = mediaLinerLayout;
		this.skbProgress = mediacontroller_progress;
		this.mCurrentTime = time_current;
		this.mEndTime = time;
		this.imageViewFullscreen = ivFullscreen;
		if (!controlVisible) {
			if (this.imageViewFullscreen != null) {
				this.imageViewFullscreen.setVisibility(View.GONE);
			}
			this.mediaLayout.setVisibility(View.GONE);
		}
		mFormatBuilder = new StringBuilder();
		mFormatter = new Formatter(mFormatBuilder, Locale.getDefault());
		this.skbProgress.setOnSeekBarChangeListener(new SeekBarChangeEvent());
		surfaceView.setOnClickListener(this);
		if (this.imageViewFullscreen != null) {
			this.imageViewFullscreen.setOnClickListener(this);
		}
		mCurrentState = STATE_IDLE;

	}

	@SuppressLint("NewApi")
	public void setPreviewBackground(Bitmap bmp) {
		Drawable drawable = new BitmapDrawable(bmp);
		surfaceView.setBackgroundDrawable(drawable);
	}

	// *****************************************************

	private void initMediaPlayer() {
		mMediaPlayer = new MediaPlayer();
		mMediaPlayer.setDisplay(surfaceHolder);
		mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
		mMediaPlayer.setOnBufferingUpdateListener(this);
		mMediaPlayer.setOnPreparedListener(this);
		mMediaPlayer.setOnCompletionListener(this);
		mMediaPlayer.setOnErrorListener(this);
		if (mVideoPlayerPreparedListener != null) {
			mVideoPlayerPreparedListener.onPrepared(mMediaPlayer);
		}
		this.skbProgress.setMax(1000);
	}

	@SuppressLint("NewApi")
	public void playUrl(String videoUrl) {
		try {
			if (surfaceView.getBackground() != null) {
				surfaceView.setBackgroundDrawable(null);
			}

			this.playUrl = videoUrl;
			isCallPalyMethod = true;
			if (mMediaPlayer == null) {
				initMediaPlayer();
			}
			mMediaPlayer.reset();
			if (null != DoIOHelper.getHttpUrlPath(videoUrl)) {
				mMediaPlayer.setDataSource(videoUrl);
			} else {
				if (DoIOHelper.isAssets(videoUrl)) {
					AssetFileDescriptor _mFileDescriptor = mActivity.getAssets().openFd(DoIOHelper.getAssetsRelPath(videoUrl));
					mMediaPlayer.setDataSource(_mFileDescriptor.getFileDescriptor(), _mFileDescriptor.getStartOffset(), _mFileDescriptor.getLength());
				} else {
					File _mFile = new File(videoUrl);
					if (_mFile.exists()) {
						FileInputStream is = new FileInputStream(new File(videoUrl));
						FileDescriptor _mFileDescriptor = is.getFD();
						mMediaPlayer.setDataSource(_mFileDescriptor);
						is.close();
					} else {
						throw new Exception(videoUrl + "文件不存在！");
					}
				}
			}
			mMediaPlayer.prepareAsync();// prepare之后自动播放
//			mediaPlayer.start();
			if (controlVisible) {
				mediaLayout.setVisibility(View.VISIBLE);
				if (imageViewFullscreen != null) {
					imageViewFullscreen.setVisibility(View.VISIBLE);
				}
			}
			mCurrentState = STATE_PREPARING;
		} catch (Exception e) {
			mCurrentState = STATE_ERROR;
			e.printStackTrace();
		}
	}

	public String getPalyUrl() {
		return this.playUrl;
	}

	public void pause() {
		if (isInPlaybackState()) {
			if (mMediaPlayer.isPlaying()) {
				mMediaPlayer.pause();
				mCurrentState = STATE_PAUSED;
			}
		}
	}

	public void stopPlayback() {
		if (mMediaPlayer != null) {
			mMediaPlayer.stop();
			mMediaPlayer.release();
			mMediaPlayer = null;
			mCurrentState = STATE_IDLE;
		}
	}

	private void release(boolean cleartargetstate) {
		if (mMediaPlayer != null) {
			mMediaPlayer.reset();
			mMediaPlayer.release();
			mMediaPlayer = null;
//            mPendingSubtitleTracks.clear();
			mCurrentState = STATE_IDLE;
			if (cleartargetstate) {
//                mTargetState  = STATE_IDLE;
			}
		}
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		Log.e("mediaPlayer", "surface changed");
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		initMediaPlayer();
		Log.e("mediaPlayer", "surface created");
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		release(true);
		Log.e("mediaPlayer", "surface destroyed");
	}

	public void setOnVideoPlayerPreparedListener(OnVideoPlayerPreparedListener listener) {
		this.mVideoPlayerPreparedListener = listener;
	}

	public void setOnFullScreenClickListener(OnFullScreenClickListener listener) {
		this.mFullScreenClickListener = listener;
	}

	@Override
	/**
	 * 通过onPrepared播放
	 */
	public void onPrepared(MediaPlayer mediaPlayer) {
		mCurrentState = STATE_PREPARED;
		videoWidth = mediaPlayer.getVideoWidth();
		videoHeight = mediaPlayer.getVideoHeight();
		int seekToPosition = mSeekWhenPrepared; // mSeekWhenPrepared may be
												// changed after seekTo() call
		if (seekToPosition != 0) {
			seekTo(seekToPosition);
		}
		if (videoHeight != 0 && videoWidth != 0) {
			surfaceHolder.setFixedSize(videoWidth, videoHeight);
			start();
		}
		show(sDefaultTimeout);
		Log.e("mediaPlayer", "onPrepared");
	}

	public void start() {
		if (isInPlaybackState()) {
			mMediaPlayer.start();
			mCurrentState = STATE_PLAYING;
		}
//        mTargetState = STATE_PLAYING;
	}

	@Override
	public void onCompletion(MediaPlayer mp) {
		mCurrentState = STATE_PLAYBACK_COMPLETED;
		model.getEventCenter().fireEvent("finished", new DoInvokeResult(model.getUniqueKey()));
	}

	@Override
	public boolean onError(MediaPlayer mp, int what, int extra) {
		mCurrentState = STATE_ERROR;
		model.getEventCenter().fireEvent("error", new DoInvokeResult(model.getUniqueKey()));
		return true;
	}

	@Override
	public void onBufferingUpdate(MediaPlayer mediaPlayer, int bufferingProgress) {
		mCurrentBufferPercentage = bufferingProgress;
	}

	public void show(int timeout) {
		if (!mShowing) {
			setProgress();
			if (controlVisible) {
				mediaLayout.setVisibility(View.VISIBLE);
				if (imageViewFullscreen != null) {
					imageViewFullscreen.setVisibility(View.VISIBLE);
				}
			}
			mShowing = true;
		}
		mHandler.sendEmptyMessage(SHOW_PROGRESS);
		Message msg = mHandler.obtainMessage(FADE_OUT);
		if (timeout != 0) {
			mHandler.removeMessages(FADE_OUT);
			mHandler.sendMessageDelayed(msg, timeout);
		}
	}

	public void hide() {
		if (mShowing) {
			mediaLayout.setVisibility(View.INVISIBLE);
			if (imageViewFullscreen != null) {
				imageViewFullscreen.setVisibility(View.INVISIBLE);
			}
			mHandler.removeMessages(SHOW_PROGRESS);
			mShowing = false;
		}
	}

	@SuppressLint("HandlerLeak")
	private Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			int pos;
			switch (msg.what) {
			case FADE_OUT:
				hide();
				break;
			case SHOW_PROGRESS:
				pos = setProgress();
				if (!mDragging && mShowing && isPlaying()) {
					msg = mHandler.obtainMessage(SHOW_PROGRESS);
					sendMessageDelayed(msg, 1000 - (pos % 1000));
				}
				break;
			}
		}
	};

	private class SeekBarChangeEvent implements SeekBar.OnSeekBarChangeListener {
		@Override
		public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
			if (!fromUser) {
				return;
			}
			long duration = getDuration();
			long newposition = (duration * progress) / 1000L;
			seekTo((int) newposition);
			if (mCurrentTime != null)
				mCurrentTime.setText(stringForTime((int) newposition));
		}

		@Override
		public void onStartTrackingTouch(SeekBar seekBar) {
			show(3600000);
			mDragging = true;
			mHandler.removeMessages(SHOW_PROGRESS);
		}

		@Override
		public void onStopTrackingTouch(SeekBar seekBar) {
			mDragging = false;
			setProgress();
//            updatePausePlay();
			show(sDefaultTimeout);
			mHandler.sendEmptyMessage(SHOW_PROGRESS);
		}
	}

	private int setProgress() {
		if (mMediaPlayer == null || mDragging || !isCallPalyMethod) {
			return 0;
		}
		int position = getCurrentPosition();
		int duration = getDuration();
		if (skbProgress != null) {
			if (duration > 0) {
				// use long to avoid overflow
				long pos = 1000L * position / duration;
				skbProgress.setProgress((int) pos);
			}
			skbProgress.setSecondaryProgress(mCurrentBufferPercentage * 10);
		}

		if (mEndTime != null)
			mEndTime.setText(stringForTime(duration));
		if (mCurrentTime != null)
			mCurrentTime.setText(stringForTime(position));

		return position;
	}

	@Override
	public void onClick(View v) {
		if (v.getId() == surfaceView.getId()) {
			if (mShowing) {
				hide();
			} else {
				show(sDefaultTimeout);
			}
		} else if (v.getId() == imageViewFullscreen.getId()) {
			if (mFullScreenClickListener != null) {
				mFullScreenClickListener.onFullScreenClick(v);
			}
		}
	}

	private OnVideoPlayerPreparedListener mVideoPlayerPreparedListener;

	public interface OnVideoPlayerPreparedListener {
		public void onPrepared(MediaPlayer mediaPlayer);
	}

	private OnFullScreenClickListener mFullScreenClickListener;

	public interface OnFullScreenClickListener {
		public void onFullScreenClick(View view);
	}

	private String stringForTime(int timeMs) {
		int totalSeconds = timeMs / 1000;

		int seconds = totalSeconds % 60;
		int minutes = (totalSeconds / 60) % 60;
		int hours = totalSeconds / 3600;

		mFormatBuilder.setLength(0);
		if (hours > 0) {
			return mFormatter.format("%d:%02d:%02d", hours, minutes, seconds).toString();
		} else {
			return mFormatter.format("%02d:%02d", minutes, seconds).toString();
		}
	}

	public int getDuration() {
		if (isInPlaybackState()) {
			return mMediaPlayer.getDuration();
		}

		return -1;
	}

	public int getCurrentPosition() {
		if (isInPlaybackState()) {
			return mMediaPlayer.getCurrentPosition();
		}
		return 0;
	}

	public void seekTo(int msec) {
		if (isInPlaybackState()) {
			mMediaPlayer.seekTo(msec);
			mSeekWhenPrepared = 0;
		} else {
			mSeekWhenPrepared = msec;
		}
	}

	public boolean isPlaying() {
		return isInPlaybackState() && mMediaPlayer.isPlaying();
	}

	public void setControlVisible(boolean visible) {
		this.controlVisible = visible;
		if (this.controlVisible) {
			show(sDefaultTimeout);
		} else {
			hide();
		}
	}

	public boolean isControlVisible() {
		return this.controlVisible;
	}

	private boolean isInPlaybackState() {
		return (mMediaPlayer != null && mCurrentState != STATE_ERROR && mCurrentState != STATE_IDLE && mCurrentState != STATE_PREPARING);
	}
	//如果是网络视频 不支持截取
	public Bitmap createVideoThumbnail(String filePath) throws Exception {
		Bitmap bitmap = null;
		if (null == DoIOHelper.getHttpUrlPath(filePath)) {
			MediaMetadataRetriever retriever = new MediaMetadataRetriever();
			try {
				retriever.setDataSource(filePath);
				byte[] _data = retriever.getEmbeddedPicture();
				if (_data != null) {
					bitmap = BitmapFactory.decodeByteArray(_data, 0, _data.length);
				} else {
					// 第一个参数是微秒 x1000的话 所取的是 1毫秒的那一帧
					bitmap = retriever.getFrameAtTime(1000, MediaMetadataRetriever.OPTION_CLOSEST);
				}
			} catch (Exception ex) {
				DoServiceContainer.getLogEngine().writeError("setPath", ex);
			}
			return bitmap;
		} else {
			return bitmap;
		}
	}

	public Bitmap createBitmap(String filePath, long time) throws Exception {
		Bitmap bitmap = null;
		MediaMetadataRetriever retriever = new MediaMetadataRetriever();
		try {
			if (null == DoIOHelper.getHttpUrlPath(filePath)) {
				filePath = DoIOHelper.getLocalFileFullPath(this.model.getCurrentPage().getCurrentApp(), filePath);
				retriever.setDataSource(filePath);
			} else {
				retriever.setDataSource(filePath, new HashMap<String, String>());
			}

			byte[] _data = retriever.getEmbeddedPicture();
			if (_data != null) {
				bitmap = BitmapFactory.decodeByteArray(_data, 0, _data.length);
			} else {
				// 第一个参数是微秒 x1000的话 所取的是 1毫秒的那一帧
				// 如果 获取时间大于视频总时间或者 小于0 默认取第一帧
				bitmap = retriever.getFrameAtTime(time * 1000, MediaMetadataRetriever.OPTION_CLOSEST);
			}
		} catch (Exception ex) {
			DoServiceContainer.getLogEngine().writeError("createBitmap", ex);
		}
		return bitmap;
	}
}