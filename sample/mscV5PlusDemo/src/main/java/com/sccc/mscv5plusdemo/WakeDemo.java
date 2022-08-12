package com.sccc.mscv5plusdemo;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.RadioGroup.OnCheckedChangeListener;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;

import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.GrammarListener;
import com.iflytek.cloud.InitListener;
import com.iflytek.cloud.RecognizerListener;
import com.iflytek.cloud.RecognizerResult;
import com.iflytek.cloud.RequestListener;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechEvent;
import com.iflytek.cloud.SpeechRecognizer;
import com.iflytek.cloud.SpeechSynthesizer;
import com.iflytek.cloud.SynthesizerListener;
import com.iflytek.cloud.VoiceWakeuper;
import com.iflytek.cloud.WakeuperListener;
import com.iflytek.cloud.WakeuperResult;
import com.iflytek.cloud.util.ResourceUtil;
import com.iflytek.cloud.util.ResourceUtil.RESOURCE_TYPE;
import com.iflytek.mscv5plusdemo.R;
import com.sccc.speech.util.FucUtil;
import com.sccc.speech.util.JsonParser;
import com.sccc.speech.util.XmlParser;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

public class WakeDemo extends Activity implements OnClickListener {
    private String TAG = "ivw";
    private Toast mToast;
    private TextView textView;
    // 语音唤醒对象
    private VoiceWakeuper mIvw;
    // 唤醒结果内容
    private String resultString;

    // 设置门限值 ： 门限值越低越容易被唤醒
    private TextView tvThresh;
    private SeekBar seekbarThresh;
    private final static int MAX = 3000;
    private final static int MIN = 0;
    private int curThresh = 1450;
    private String threshStr = "门限值：";
    private String keep_alive = "1";
    private String ivwNetMode = "0";

    //语音合成对象
    private SpeechSynthesizer mTts;

    // 语音识别对象
    private SpeechRecognizer mAsr;
    // 缓存
    private SharedPreferences mSharedPreferences;
    // 本地语法文件
    private String mLocalGrammar = null;
    // 本地语法构建路径
    private String grmPath;
    // 返回结果格式，支持：xml,json
    private String mResultType = "json";

    private final String GRAMMAR_TYPE_BNF = "bnf";

    private String mEngineType = "local";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.wake_activity);

        initUi();
        // 初始化唤醒对象
        mIvw = VoiceWakeuper.createWakeuper(this, null);
        //语音合成对象
        mTts  = SpeechSynthesizer.createSynthesizer(this, null);

        //获得录音地址
        grmPath = getExternalFilesDir("msc").getAbsolutePath() + "/test";
        // 初始化识别对象
        mAsr = SpeechRecognizer.createRecognizer(this, mInitListener);
        if (mAsr == null) {
            Log.e(TAG, "masr is null");
        }
        // 初始化语法、命令词
        mLocalGrammar = FucUtil.readFile(this, "call.bnf", "utf-8");

        mSharedPreferences = getSharedPreferences(getPackageName(), MODE_PRIVATE);
    }

    private void initUi() {
        findViewById(R.id.btn_start).setOnClickListener(this);
        findViewById(R.id.btn_stop).setOnClickListener(this);
        textView = (TextView) findViewById(R.id.txt_show_msg);
        tvThresh = (TextView) findViewById(R.id.txt_thresh);
        seekbarThresh = (SeekBar) findViewById(R.id.seekBar_thresh);
        seekbarThresh.setMax(MAX - MIN);
        seekbarThresh.setProgress(curThresh);
        tvThresh.setText(threshStr + curThresh);
        seekbarThresh.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {

            @Override
            public void onStopTrackingTouch(SeekBar arg0) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar arg0) {
            }

            @Override
            public void onProgressChanged(SeekBar arg0, int arg1, boolean arg2) {
                curThresh = seekbarThresh.getProgress() + MIN;
                tvThresh.setText(threshStr + curThresh);
            }
        });

        RadioGroup group = (RadioGroup) findViewById(R.id.ivw_net_mode);
        group.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup arg0, int arg1) {
                /**
                 * 闭环优化网络模式有三种：
                 * 模式0：关闭闭环优化功能
                 *
                 * 模式1：开启闭环优化功能，允许上传优化数据。需开发者自行管理优化资源。
                 * sdk提供相应的查询和下载接口，请开发者参考API文档，具体使用请参考本示例
                 * queryResource及downloadResource方法；
                 *
                 * 模式2：开启闭环优化功能，允许上传优化数据及启动唤醒时进行资源查询下载；
                 * 本示例为方便开发者使用仅展示模式0和模式2；
                 */
                switch (arg1) {
                    case R.id.mode_close:
                        ivwNetMode = "0";
                        break;
                    case R.id.mode_open:
                        ivwNetMode = "1";
                        break;
                    default:
                        break;
                }
            }
        });
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_start:
                //非空判断，防止因空指针使程序崩溃
                mIvw = VoiceWakeuper.getWakeuper();
                if (mIvw != null) {
                    setRadioEnable(false);
                    resultString = "";
                    textView.setText(resultString);

                    // 清空参数
                    mIvw.setParameter(SpeechConstant.PARAMS, null);
                    // 唤醒门限值，根据资源携带的唤醒词个数按照“id:门限;id:门限”的格式传入
                    mIvw.setParameter(SpeechConstant.IVW_THRESHOLD, "0:" + curThresh);
                    // 设置唤醒模式
                    mIvw.setParameter(SpeechConstant.IVW_SST, "wakeup");
                    // 设置持续进行唤醒
                    mIvw.setParameter(SpeechConstant.KEEP_ALIVE, keep_alive);
                    // 设置闭环优化网络模式
                    mIvw.setParameter(SpeechConstant.IVW_NET_MODE, ivwNetMode);
                    // 设置唤醒资源路径
                    mIvw.setParameter(SpeechConstant.IVW_RES_PATH, getResource());
                    // 设置唤醒录音保存路径，保存最近一分钟的音频
                    mIvw.setParameter(SpeechConstant.IVW_AUDIO_PATH,
                            getExternalFilesDir("msc").getAbsolutePath() + "/ivw.wav");
                    mIvw.setParameter(SpeechConstant.AUDIO_FORMAT, "wav");
                    // 如有需要，设置 NOTIFY_RECORD_DATA 以实时通过 onEvent 返回录音音频流字节
                    //mIvw.setParameter( SpeechConstant.NOTIFY_RECORD_DATA, "1" );
                    // 启动唤醒
                    /*	mIvw.setParameter(SpeechConstant.AUDIO_SOURCE, "-1");*/

                    mIvw.startListening(mWakeuperListener);

                    //设置语法构建参数。
                    String mContent = new String(mLocalGrammar);
                    mAsr.setParameter(SpeechConstant.PARAMS, null);
                    // 设置文本编码格式
                    mAsr.setParameter(SpeechConstant.TEXT_ENCODING, "utf-8");
                    // 设置引擎类型
                    mAsr.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_LOCAL);
                    // 设置语法构建路径
                    mAsr.setParameter(ResourceUtil.GRM_BUILD_PATH, grmPath);
                    //使用8k音频的时候请解开注释
//					mAsr.setParameter(SpeechConstant.SAMPLE_RATE, "8000");
                    // 设置资源路径
                    mAsr.setParameter(ResourceUtil.ASR_RES_PATH, getResourcePath());
                    int ret = mAsr.buildGrammar(GRAMMAR_TYPE_BNF, mContent, grammarListener);
                    if (ret != ErrorCode.SUCCESS) {
                        showTip("语法构建失败,错误码：" + ret + ",请点击网址https://www.xfyun.cn/document/error-code查询解决方案");
                    }
                } else {
                    showTip("唤醒未初始化");
                }
                break;
            case R.id.btn_stop:
                mIvw.stopListening();
                //     mIvw.writeAudio();
                setRadioEnable(true);
                break;
            default:
                break;
        }
    }

    /**
     * 查询闭环优化唤醒资源
     * 请在闭环优化网络模式1或者模式2使用
     */
    public void queryResource() {
        int ret = mIvw.queryResource(getResource(), requestListener);
        showTip("updateResource ret:" + ret);
    }

    /**
     * 构建语法监听器。
     */
    private GrammarListener grammarListener = new GrammarListener() {
        @Override
        public void onBuildFinish(String grammarId, SpeechError error) {
            if (error == null) {
                showTip("语法构建成功：" + grammarId);
            } else {
                showTip("语法构建失败,错误码：" + error.getErrorCode());
            }
        }
    };

    /**
     * 初始化监听器。
     */
    private InitListener mInitListener = new InitListener() {

        @Override
        public void onInit(int code) {
            Log.d(TAG, "SpeechRecognizer init() code = " + code);
            if (code != ErrorCode.SUCCESS) {
                showTip("初始化失败,错误码：" + code + ",请点击网址https://www.xfyun.cn/document/error-code查询解决方案");
            }
        }
    };
    // 查询资源请求回调监听
    private RequestListener requestListener = new RequestListener() {
        @Override
        public void onEvent(int eventType, Bundle params) {
            // 以下代码用于获取查询会话id，当业务出错时将会话id提供给技术支持人员，可用于查询会话日志，定位出错原因
            //if(SpeechEvent.EVENT_SESSION_ID == eventType) {
            // 	Log.d(TAG, "sid:"+params.getString(SpeechEvent.KEY_EVENT_SESSION_ID));
            //}
        }

        @Override
        public void onCompleted(SpeechError error) {
            if (error != null) {
                Log.d(TAG, "error:" + error.getErrorCode());
                showTip(error.getPlainDescription(true));
            }
        }

        @Override
        public void onBufferReceived(byte[] buffer) {
            try {
                String resultInfo = new String(buffer, "utf-8");
                Log.d(TAG, "resultInfo:" + resultInfo);

                JSONTokener tokener = new JSONTokener(resultInfo);
                JSONObject object = new JSONObject(tokener);

                int ret = object.getInt("ret");
                if (ret == 0) {
                    String uri = object.getString("dlurl");
                    String md5 = object.getString("md5");
                    Log.d(TAG, "uri:" + uri);
                    Log.d(TAG, "md5:" + md5);
                    showTip("请求成功");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };


    /**
     * 识别监听器。
     */
    private RecognizerListener mRecognizerListener = new RecognizerListener() {

        @Override
        public void onVolumeChanged(int volume, byte[] data) {
            showTip("当前正在说话，音量大小：" + volume);
            Log.d(TAG, "返回音频数据：" + data.length);
        }

        @Override
        public void onResult(final RecognizerResult result, boolean isLast) {
            if (null != result && !TextUtils.isEmpty(result.getResultString())) {
                Log.d(TAG, "recognizer result：" + result.getResultString());
                String text = "";
                    text = JsonParser.parseGrammarResult(result.getResultString(), SpeechConstant.TYPE_LOCAL);
                // 显示
                textView.setText(text);

                // 成功识别出命令并执行后的提示语音
                String answer = "已帮您打开！";
                int code = mTts.startSpeaking(answer, null);

                //关闭识别麦克风，打开唤醒麦克风。
                mAsr.stopListening();
                mIvw.startListening(mWakeuperListener);
            } else {
                Log.d(TAG, "recognizer result : null");
            }
        }

        @Override
        public void onEndOfSpeech() {
            // 此回调表示：检测到了语音的尾端点，已经进入识别过程，不再接受语音输入
            showTip("结束说话");
        }

        @Override
        public void onBeginOfSpeech() {
            // 此回调表示：sdk内部录音机已经准备好了，用户可以开始语音输入
            showTip("开始说话");
        }

        @Override
        public void onError(SpeechError error) {
            showTip("onError Code：" + error.getErrorCode() + error.getErrorDescription() + error.getMessage());
            if(error.getErrorCode()==20005){
                //说话没有命令词时的语音提示。
                String answer = "对不起，没有有效的指令哟。";
                int code = mTts.startSpeaking(answer, null);

                //停止语音麦克风，打开识别麦克风。
                mAsr.stopListening();
                mIvw.startListening(mWakeuperListener);
            }
        }

        @Override
        public void onEvent(int eventType, int arg1, int arg2, Bundle obj) {
            // 以下代码用于获取与云端的会话id，当业务出错时将会话id提供给技术支持人员，可用于查询会话日志，定位出错原因
            // 若使用本地能力，会话id为null
            //	if (SpeechEvent.EVENT_SESSION_ID == eventType) {
            //		String sid = obj.getString(SpeechEvent.KEY_EVENT_SESSION_ID);
            //		Log.d(TAG, "session id =" + sid);
            //	}
        }

    };

    private WakeuperListener mWakeuperListener = new WakeuperListener() {

        @Override
        public void onResult(WakeuperResult result) {
            int onResult = Log.d(TAG, "onResult");

            String answer = "哎！";
            int code = mTts.startSpeaking(answer, null);

            mIvw.stopListening();

            if (!setParam()) {
                showTip("请先构建语法。");
                return;
            }

            int ret = mAsr.startListening(mRecognizerListener);
            if (ret != ErrorCode.SUCCESS) {
                showTip("识别失败,错误码: " + ret + ",请点击网址https://www.xfyun.cn/document/error-code查询解决方案");
            }

            if (!"1".equalsIgnoreCase(keep_alive)) {
                setRadioEnable(true);
            }
            try {
                String text = result.getResultString();
                JSONObject object;
                object = new JSONObject(text);
                StringBuffer buffer = new StringBuffer();
                buffer.append("【RAW】 " + text);
                buffer.append("\n");
                buffer.append("【操作类型】" + object.optString("sst"));
                buffer.append("\n");
                buffer.append("【唤醒词id】" + object.optString("id"));
                buffer.append("\n");
                buffer.append("【得分】" + object.optString("score"));
                buffer.append("\n");
                buffer.append("【前端点】" + object.optString("bos"));
                buffer.append("\n");
                buffer.append("【尾端点】" + object.optString("eos"));
                resultString = buffer.toString();
            } catch (JSONException e) {
                resultString = "结果解析出错";
                e.printStackTrace();
            }
            textView.setText(resultString);
        }

        @Override
        public void onError(SpeechError error) {
            showTip(error.getPlainDescription(true));
            setRadioEnable(true);
        }

        @Override
        public void onBeginOfSpeech() {
        }

        @Override
        public void onEvent(int eventType, int isLast, int arg2, Bundle obj) {
            switch (eventType) {
                // EVENT_RECORD_DATA 事件仅在 NOTIFY_RECORD_DATA 参数值为 真 时返回
                case SpeechEvent.EVENT_RECORD_DATA:
                    final byte[] audio = obj.getByteArray(SpeechEvent.KEY_EVENT_RECORD_DATA);
                    Log.i(TAG, "ivw audio length: " + audio.length);
                    break;
            }
        }

        @Override
        public void onVolumeChanged(int volume) {

        }
    };



    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy WakeDemo");
        // 销毁合成对象
        mIvw = VoiceWakeuper.getWakeuper();
        if (mIvw != null) {
            mIvw.destroy();
        }
    }

    private String getResource() {
        final String resPath = ResourceUtil.generateResourcePath(WakeDemo.this, RESOURCE_TYPE.assets, "ivw/" + getString(R.string.app_id) + ".jet");
        Log.d(TAG, "resPath: " + resPath);
        return resPath;
    }

    private void showTip(final String str) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mToast != null) {
                    mToast.cancel();
                }
                mToast = Toast.makeText(getApplicationContext(), str, Toast.LENGTH_SHORT);
                mToast.show();
            }
        });
    }

    private void setRadioEnable(final boolean enabled) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                findViewById(R.id.ivw_net_mode).setEnabled(enabled);
                findViewById(R.id.btn_start).setEnabled(enabled);
                findViewById(R.id.seekBar_thresh).setEnabled(enabled);
            }
        });
    }

    private String getResourcePath() {
        StringBuffer tempBuffer = new StringBuffer();
        //识别通用资源
        tempBuffer.append(ResourceUtil.generateResourcePath(this, RESOURCE_TYPE.assets, "asr/common.jet"));
        return tempBuffer.toString();
    }

    /**
     * 参数设置
     *
     * @return
     */
    public boolean setParam() {
        boolean result = false;
        // 清空参数
        mAsr.setParameter(SpeechConstant.PARAMS, null);
        // 设置识别引擎
        mAsr.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_LOCAL);
        // 设置本地识别资源
        mAsr.setParameter(ResourceUtil.ASR_RES_PATH, getResourcePath());
        // 设置语法构建路径
        mAsr.setParameter(ResourceUtil.GRM_BUILD_PATH, grmPath);
        // 设置返回结果格式
        mAsr.setParameter(SpeechConstant.RESULT_TYPE, mResultType);
        // 设置本地识别使用语法id
        mAsr.setParameter(SpeechConstant.LOCAL_GRAMMAR, "call");
        // 设置识别的门限值
        mAsr.setParameter(SpeechConstant.MIXED_THRESHOLD, "30");
        // 使用8k音频的时候请解开注释
//			mAsr.setParameter(SpeechConstant.SAMPLE_RATE, "8000");
        result = true;

        // 设置音频保存路径，保存音频格式支持pcm、wav，设置路径为sd卡请注意WRITE_EXTERNAL_STORAGE权限
        mAsr.setParameter(SpeechConstant.AUDIO_FORMAT, "wav");
        mAsr.setParameter(SpeechConstant.ASR_AUDIO_PATH,
                getExternalFilesDir("msc").getAbsolutePath() + "/asr.wav");
        return result;
    }

}