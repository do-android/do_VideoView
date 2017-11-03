package doext.implement;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Map;

import org.json.JSONObject;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import core.DoServiceContainer;
import core.helper.DoIOHelper;
import core.helper.DoJsonHelper;
import core.helper.DoResourcesHelper;
import core.helper.DoScriptEngineHelper;
import core.helper.DoTextHelper;
import core.helper.DoUIModuleHelper;
import core.interfaces.DoActivityResultListener;
import core.interfaces.DoBaseActivityListener;
import core.interfaces.DoIBitmap;
import core.interfaces.DoIModuleTypeID;
import core.interfaces.DoIPageView;
import core.interfaces.DoIScriptEngine;
import core.interfaces.DoIUIModuleView;
import core.object.DoInvokeResult;
import core.object.DoMultitonModule;
import core.object.DoUIModule;
import doext.define.do_VideoView_IMethod;
import doext.videoview.FillScreenVideoActivity;
import doext.videoview.MyVideoPlayer;
import doext.videoview.MyVideoPlayer.OnFullScreenClickListener;
import doext.videoview.MyVideoPlayer.OnVideoPlayerPreparedListener;

/**
 * 自定义扩展UIView组件实现类，此类必须继承相应VIEW类，并实现DoIUIModuleView,do_VideoView_IMethod接口；
 * #如何调用组件自定义事件？可以通过如下方法触发事件：
 * this.model.getEventCenter().fireEvent(_messageName, jsonResult);
 * 参数解释：@_messageName字符串事件名称，@jsonResult传递事件参数对象； 获取DoInvokeResult对象方式new
 * DoInvokeResult(this.getUniqueKey());
 */
public class do_VideoView_View extends FrameLayout implements DoIUIModuleView, do_VideoView_IMethod, DoBaseActivityListener, OnFullScreenClickListener, DoActivityResultListener,
		OnVideoPlayerPreparedListener, DoIModuleTypeID {
	public static String TAG = "do_VideoView_View";
	/**
	 * 每个UIview都会引用一个具体的model实例；
	 */
	private do_VideoView_Model model;
	private View videoView;
	private Activity context;
	private MyVideoPlayer videoPlayer;
	private String playUrl;
	private int palyTime;
	public static final String DO_VIDEOVIEW_VIEW_FINISH_FILL_SCREEN_VIDEO = "do_videoview_view_finish_fill_screen_video";

	public do_VideoView_View(Context context) {
		super(context);
		this.context = (Activity) context;
	}

	/**
	 * 初始化加载view准备,_doUIModule是对应当前UIView的model实例
	 */
	@Override
	public void loadView(DoUIModule _doUIModule) throws Exception {
		this.model = (do_VideoView_Model) _doUIModule;
		int do_videoview_id = DoResourcesHelper.getIdentifier("do_videoview", "layout", this);
		videoView = View.inflate(context, do_videoview_id, this);
		int sv_id = DoResourcesHelper.getIdentifier("sv", "id", this);
		SurfaceView surfaceView = (SurfaceView) videoView.findViewById(sv_id);

		int iv_id = DoResourcesHelper.getIdentifier("iv", "id", this);
		ImageView ivFullscreen = (ImageView) videoView.findViewById(iv_id);

		int media_ll_id = DoResourcesHelper.getIdentifier("media_ll", "id", this);
		LinearLayout mediaLayout = (LinearLayout) videoView.findViewById(media_ll_id);

		int sb_id = DoResourcesHelper.getIdentifier("sb", "id", this);
		SeekBar sb = (SeekBar) videoView.findViewById(sb_id);

		int time_current_id = DoResourcesHelper.getIdentifier("time_current", "id", this);
		TextView mCurrentTime = (TextView) videoView.findViewById(time_current_id);

		int time_id = DoResourcesHelper.getIdentifier("time", "id", this);
		TextView mEndTime = (TextView) videoView.findViewById(time_id);

		videoPlayer = new MyVideoPlayer((Activity) context, this.model, surfaceView, mediaLayout, ivFullscreen, mCurrentTime, mEndTime, sb);
		((DoIPageView) DoServiceContainer.getPageViewFactory().getAppContext()).setBaseActivityListener(this);
		videoPlayer.setOnFullScreenClickListener(this);

	}

	/**
	 * 动态修改属性值时会被调用，方法返回值为true表示赋值有效，并执行onPropertiesChanged，否则不进行赋值；
	 * 
	 * @_changedValues<key,value>属性集（key名称、value值）；
	 */
	@Override
	public boolean onPropertiesChanging(Map<String, String> _changedValues) {
		return true;
	}

	/**
	 * 属性赋值成功后被调用，可以根据组件定义相关属性值修改UIView可视化操作；
	 * 
	 * @_changedValues<key,value>属性集（key名称、value值）；
	 */
	@Override
	public void onPropertiesChanged(Map<String, String> _changedValues) {
		DoUIModuleHelper.handleBasicViewProperChanged(this.model, _changedValues);
		if (_changedValues.containsKey("path")) {
			String _path = _changedValues.get("path");
			try {
				if (null == DoIOHelper.getHttpUrlPath(_path)) {
					_path = DoIOHelper.getLocalFileFullPath(this.model.getCurrentPage().getCurrentApp(), _path);
				}
				if (_path != null && !_path.equals("")) {
					playUrl = _path;
					new Thread(new Runnable() {
						@Override
						public void run() {
							Bitmap _bitmap = null;
							try {
								_bitmap = videoPlayer.createVideoThumbnail(playUrl);
							} catch (Exception e) {
								e.printStackTrace();
							}
							Message _msg = Message.obtain();
							_msg.obj = _bitmap;
							mHandler.sendMessage(_msg);
						}
					}).start();
				}
			} catch (Exception e) {
				DoServiceContainer.getLogEngine().writeError("do_VideoView_View path \n\t", e);
			}
		}
	}

	private Handler mHandler = new Handler() {
		public void handleMessage(android.os.Message msg) {
			if (msg.obj != null)
				videoPlayer.setPreviewBackground((Bitmap) msg.obj);
		};
	};

	/**
	 * 同步方法，JS脚本调用该组件对象方法时会被调用，可以根据_methodName调用相应的接口实现方法；
	 * 
	 * @_methodName 方法名称
	 * @_dictParas 参数（K,V）
	 * @_scriptEngine 当前Page JS上下文环境对象
	 * @_invokeResult 用于返回方法结果对象
	 */
	@Override
	public boolean invokeSyncMethod(String _methodName, JSONObject _dictParas, DoIScriptEngine _scriptEngine, DoInvokeResult _invokeResult) throws Exception {
		if ("play".equals(_methodName)) {
			this.play(_dictParas, _scriptEngine, _invokeResult);
			return true;
		}
		if ("pause".equals(_methodName)) { // 暂停播放
			this.pause(_dictParas, _scriptEngine, _invokeResult);
			return true;
		}
		if ("resume".equals(_methodName)) { // 继续播放
			this.resume(_dictParas, _scriptEngine, _invokeResult);
			return true;
		}
		if ("stop".equals(_methodName)) {
			this.stop(_dictParas, _scriptEngine, _invokeResult);
			return true;
		}
		if ("isPlaying".equals(_methodName)) {
			this.isPlaying(_dictParas, _scriptEngine, _invokeResult);
		}
		if ("getCurrentPosition".equals(_methodName)) {
			this.getCurrentPosition(_dictParas, _scriptEngine, _invokeResult);
		}
		if ("expand".equals(_methodName)) {
			this.expand(_dictParas, _scriptEngine, _invokeResult);
		}
		if ("setControlVisible".equals(_methodName)) {
			this.setControlVisible(_dictParas, _scriptEngine, _invokeResult);
		}
		return false;
	}

	/**
	 * 异步方法（通常都处理些耗时操作，避免UI线程阻塞），JS脚本调用该组件对象方法时会被调用， 可以根据_methodName调用相应的接口实现方法；
	 * 
	 * @throws Exception
	 * 
	 * @_methodName 方法名称
	 * @_dictParas 参数（K,V）
	 * @_scriptEngine 当前page JS上下文环境
	 * @_callbackFuncName 回调函数名 #如何执行异步方法回调？可以通过如下方法：
	 *                    _scriptEngine.callback(_callbackFuncName,
	 *                    _invokeResult);
	 *                    参数解释：@_callbackFuncName回调函数名，@_invokeResult传递回调函数参数对象；
	 *                    获取DoInvokeResult对象方式new
	 *                    DoInvokeResult(this.getUniqueKey());
	 */
	@Override
	public boolean invokeAsyncMethod(String _methodName, JSONObject _dictParas, DoIScriptEngine _scriptEngine, String _callbackFuncName) throws Exception {
		if ("getFrameAsImage".equals(_methodName)) { // 执行动画
			this.getFrameAsImage(_dictParas, _scriptEngine, _callbackFuncName);
			return true;
		}
		if ("getFrameAsBitmap".equals(_methodName)) { // 执行动画
			this.getFrameAsBitmap(_dictParas, _scriptEngine, _callbackFuncName);
			return true;
		}
		return false;
	}

	/**
	 * 释放资源处理，前端JS脚本调用closePage或执行removeui时会被调用；
	 */
	@Override
	public void onDispose() {
		videoPlayer.stopPlayback();
	}

	/**
	 * 重绘组件，构造组件时由系统框架自动调用；
	 * 或者由前端JS脚本调用组件onRedraw方法时被调用（注：通常是需要动态改变组件（X、Y、Width、Height）属性时手动调用）
	 */
	@Override
	public void onRedraw() {
		this.setLayoutParams(DoUIModuleHelper.getLayoutParams(this.model));
	}

	/**
	 * 获取当前model实例
	 */
	@Override
	public DoUIModule getModel() {
		return model;
	}

	/**
	 * 暂停播放；
	 * 
	 * @_dictParas 参数（K,V），可以通过此对象提供相关方法来获取参数值（Key：为参数名称）；
	 * @_scriptEngine 当前Page JS上下文环境对象
	 * @_invokeResult 用于返回方法结果对象
	 */
	@Override
	public void pause(JSONObject _dictParas, DoIScriptEngine _scriptEngine, DoInvokeResult _invokeResult) throws Exception {
		if (videoPlayer.isPlaying()) {
			videoPlayer.pause();
		}
		palyTime = videoPlayer.getCurrentPosition();
		_invokeResult.setResultInteger(palyTime);
	}

	/**
	 * 开始播放；
	 * 
	 * @_dictParas 参数（K,V），可以通过此对象提供相关方法来获取参数值（Key：为参数名称）；
	 * @_scriptEngine 当前Page JS上下文环境对象
	 * @_invokeResult 用于返回方法结果对象
	 */
	@Override
	public void play(JSONObject _dictParas, DoIScriptEngine _scriptEngine, DoInvokeResult _invokeResult) throws Exception {
		if (playUrl == null)
			throw new Exception("url 不能为空");
		final int pos = DoJsonHelper.getInt(_dictParas, "point", 0);
		this.post(new Runnable() {
			@Override
			public void run() {
				videoPlayer.playUrl(playUrl);
				if (!videoPlayer.isPlaying()) {
					// 按照初始位置播放
					videoPlayer.start();
					videoPlayer.seekTo(pos);
					videoView.requestFocus();
				}
			}
		});
	}

	/**
	 * 继续播放；
	 * 
	 * @_dictParas 参数（K,V），可以通过此对象提供相关方法来获取参数值（Key：为参数名称）；
	 * @_scriptEngine 当前Page JS上下文环境对象
	 * @_invokeResult 用于返回方法结果对象
	 */
	@Override
	public void resume(JSONObject _dictParas, DoIScriptEngine _scriptEngine, DoInvokeResult _invokeResult) throws Exception {
		if (!videoPlayer.isPlaying()) {
			videoPlayer.playUrl(videoPlayer.getPalyUrl());
			if (!videoPlayer.isPlaying()) {
				// 按照初始位置播放
				videoPlayer.start();
				videoPlayer.seekTo(palyTime);
				videoView.requestFocus();
			}
		}
	}

	/**
	 * 停止播放；
	 * 
	 * @_dictParas 参数（K,V），可以通过此对象提供相关方法来获取参数值（Key：为参数名称）；
	 * @_scriptEngine 当前Page JS上下文环境对象
	 * @_invokeResult 用于返回方法结果对象
	 */
	@Override
	public void stop(JSONObject _dictParas, DoIScriptEngine _scriptEngine, DoInvokeResult _invokeResult) throws Exception {
		videoPlayer.pause();
		videoPlayer.stopPlayback();
	}

	@Override
	public void onResume() {
	}

	@Override
	public void onPause() {
		palyTime = videoPlayer.getCurrentPosition();
		videoPlayer.pause();
	}

	@Override
	public void onRestart() {
	}

	@Override
	public void onStop() {
		videoPlayer.stopPlayback();
	}

	@Override
	public String getTypeID() {
		return this.model.getTypeID();
	}

	@Override
	public void isPlaying(JSONObject _dictParas, DoIScriptEngine _scriptEngine, DoInvokeResult _invokeResult) throws Exception {
		_invokeResult.setResultBoolean(videoPlayer.isPlaying());
	}

	@Override
	public void getCurrentPosition(JSONObject _dictParas, DoIScriptEngine _scriptEngine, DoInvokeResult _invokeResult) throws Exception {
		_invokeResult.setResultText(String.valueOf(videoPlayer.getCurrentPosition()));
	}

	@Override
	public void setControlVisible(JSONObject _dictParas, DoIScriptEngine _scriptEngine, DoInvokeResult _invokeResult) throws Exception {
		boolean _isVisible = DoJsonHelper.getBoolean(_dictParas, "visible", true);
		videoPlayer.setControlVisible(_isVisible);
	}

	@Override
	public void expand(JSONObject _dictParas, DoIScriptEngine _scriptEngine, DoInvokeResult _invokeResult) throws Exception {
		boolean _isFullScreen = DoJsonHelper.getBoolean(_dictParas, "isFullScreen", false);
		if (_isFullScreen) {
			fillScreen();
		} else {
			Intent intent = new Intent(DO_VIDEOVIEW_VIEW_FINISH_FILL_SCREEN_VIDEO);
			context.sendBroadcast(intent);
		}
	}

	@Override
	public void onFullScreenClick(View view) {
		fillScreen();
	}

	private void fillScreen() {
		Intent i = new Intent(context, FillScreenVideoActivity.class);
		i.putExtra("path", this.playUrl);
		i.putExtra("modelAddress", model.getUniqueKey());
		i.putExtra("point", videoPlayer.getCurrentPosition());
		i.putExtra("controlVisible", videoPlayer.isControlVisible());
		((DoIPageView) context).registActivityResultListener(this);
		context.startActivityForResult(i, 1000);
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent intent) {
		if (requestCode == 1000) {
			((DoIPageView) context).unregistActivityResultListener(this);
			if (resultCode == 2000) {
				palyTime = intent.getIntExtra("point", palyTime);
				videoPlayer.setOnVideoPlayerPreparedListener(this);
			}
		}
	}

	@Override
	public void onPrepared(MediaPlayer mediaPlayer) {
//		videoPlayer.setOnVideoPlayerPreparedListener(null); 
		if (!TextUtils.isEmpty(playUrl)) {
			videoPlayer.playUrl(playUrl);
			if (!videoPlayer.isPlaying()) {
				// 按照初始位置播放
				videoPlayer.seekTo(palyTime);
				videoPlayer.start();
			}
		}
	}

	@Override
	public void getFrameAsImage(JSONObject _dictParas, DoIScriptEngine _scriptEngine, String _callbackFuncName) throws Exception {
		String _time = DoJsonHelper.getString(_dictParas, "time", "1000");
		String _format = DoJsonHelper.getString(_dictParas, "format", "JPEG");
		int _quality = DoJsonHelper.getInt(_dictParas, "quality", 100);
		String _outPath = DoJsonHelper.getString(_dictParas, "outPath", "");
		boolean _isUseDefault = false;
		if (_quality < 0 || _quality > 100) {
			_quality = 100;
		}
		CompressFormat _cFormat = CompressFormat.JPEG;
		String _fileName = DoTextHelper.getTimestampStr() + ".jpg.do";
		if ("PNG".equalsIgnoreCase(_format)) {
			_cFormat = CompressFormat.PNG;
			_fileName = DoTextHelper.getTimestampStr() + ".png.do";
		}
		String _fillPath = "";
		try {
			_fillPath = DoIOHelper.getLocalFileFullPath(_scriptEngine.getCurrentPage().getCurrentApp(), _outPath);
		} catch (Exception e) {
			e.printStackTrace();
		}
		if (TextUtils.isEmpty(_fillPath)) {
			_isUseDefault = true;
			_fillPath = _scriptEngine.getCurrentApp().getDataFS().getRootPath() + "/temp/do_VideoView/" + _fileName;
		}

		File _outFile = new File(_fillPath);
		if (!DoIOHelper.existFile(_fillPath)) {
			DoIOHelper.createFile(_fillPath);
		}
		OutputStream _outputStream = new FileOutputStream(_outFile);
		Bitmap mBitmap = videoPlayer.createBitmap(this.model.getPropertyValue("path"), Long.parseLong(_time));
		boolean _result = mBitmap.compress(_cFormat, _quality, _outputStream);
		_outputStream.close();

		DoInvokeResult _invokeResult = new DoInvokeResult(model.getUniqueKey());

		String _resultText = "";
		if (_result) {
			if (_isUseDefault) {
				_resultText = "data://temp/do_VideoView/" + _fileName;
			} else {
				_resultText = _outPath;
			}
		}
		_invokeResult.setResultText(_resultText);
		_scriptEngine.callback(_callbackFuncName, _invokeResult);
	}

	@Override
	public void getFrameAsBitmap(JSONObject _dictParas, DoIScriptEngine _scriptEngine, String _callbackFuncName) throws Exception {
		String _address = DoJsonHelper.getString(_dictParas, "bitmap", "");
		String _time = DoJsonHelper.getString(_dictParas, "time", "1000");
		if (_address == null || _address.length() <= 0)
			throw new Exception("bitmap参数不能为空！");
		DoMultitonModule _multitonModule = DoScriptEngineHelper.parseMultitonModule(_scriptEngine, _address);
		if (_multitonModule == null)
			throw new Exception("bitmap参数无效！");
		if (_multitonModule instanceof DoIBitmap) {
			DoIBitmap _bitmap = (DoIBitmap) _multitonModule;
			Bitmap mBitmap = videoPlayer.createBitmap(this.model.getPropertyValue("path"), Long.parseLong(_time));
			_bitmap.setData(mBitmap);
			DoInvokeResult _invokeResult = new DoInvokeResult(model.getUniqueKey());
			_scriptEngine.callback(_callbackFuncName, _invokeResult);
		}
	}
}