package com.eme.pwd_input_view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.Nullable;
import android.text.InputType;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

import java.util.Timer;
import java.util.TimerTask;

import static android.view.View.MeasureSpec.AT_MOST;
import static android.view.View.MeasureSpec.EXACTLY;

/**
 * 自定义密码输入框
 * <p>
 * <p>
 * 整个View的宽高度计算公式为 ：
 * View宽度 = 单个密码框的宽度 * 密码位数 + 密码框间隔 * （密码位数 - 1）
 * View高度 = 密码框的高度
 * <p>
 * <p>
 * Created by dijiaoliang on 17/5/26.
 */
public class PwdInputView extends View {

    /**
     * 密码边框样式定义
     */
    public static final int BORDER_STYLE_CHEEK = 1001;//四边形边框样式
    public static final int BORDER_STYLE_UNDERLINE = 1002;//下划线样式

    private int pwdLength;//密码长度
    private long cursorFlashTime;//光标闪动间隔时间
    private int pwdPadding;//密码之间间隔
    private int pwdSize;//密码单元大小
    private int borderColor;//边框颜色
    private int borderWidth;//边框粗细或下划线粗细
    private int cursorPosition;//光标位置
    private int cursorWidth;//光标粗细
    private int cursorHeight;//光标长度
    private int cursorColor;//光标颜色
    private boolean isCursorShowing;//光标是否正在显示
    private boolean isCursorEnable;//是否开启光标
    private boolean isInputComplete;//是否输入完毕
    private int cipherTextSize;//密文文字大小
    private boolean isCipherEnable;//是否开启密文
    private static String CIPHER_TEXT = "*";//密文符号


    private int mode;//边框样式
    private Paint mPaint;//边框画笔
    private Paint cursorPaint;//焦点画笔
    private Paint cipherPaint;//文字画笔

    private String[] pwdData;//存储密码数据

    private TimerTask timerTask;//定时器任务
    private Timer timer;//定时器

    private PwdInputListener pwdInputListener;

    private InputMethodManager inputMethodManager;//软键盘管理器

    /**
     * 设置密码输入监听
     *
     * @param pwdInputListener
     */
    public void setPwdInputListener(PwdInputListener pwdInputListener) {
        this.pwdInputListener = pwdInputListener;
    }

    public PwdInputView(Context context) {
        super(context);
    }

    public PwdInputView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public PwdInputView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    private void init(AttributeSet attrs) {
        TypedArray ta = getContext().obtainStyledAttributes(attrs, R.styleable.PwdInput);
        borderColor = ta.getColor(R.styleable.PwdInput_borderColor, Color.GRAY);
        cursorColor = ta.getColor(R.styleable.PwdInput_cursorColor, Color.GRAY);
        pwdLength = ta.getInt(R.styleable.PwdInput_pwdLength, 6);
        isCursorEnable = ta.getBoolean(R.styleable.PwdInput_isCursorEnable, true);
        isCipherEnable = ta.getBoolean(R.styleable.PwdInput_isCipherEnable, true);
        pwdPadding = dp2px(getContext(), ta.getDimension(R.styleable.PwdInput_pwdPadding, 2));
        mode = ta.getInt(R.styleable.PwdInput_mode, BORDER_STYLE_CHEEK);
        ta.recycle();

        setFocusableInTouchMode(true);
        setOnKeyListener(new PwdKeyListener());
        inputMethodManager = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);


        borderWidth = dp2px(getContext(), 2);
        //光标宽度
        cursorWidth = dp2px(getContext(), 2);
        if (mPaint == null) {
            mPaint = new Paint();
            mPaint.setColor(borderColor);
            mPaint.setStyle(Paint.Style.FILL);
            mPaint.setStrokeWidth(borderWidth);
        }
        if (cursorPaint == null) {
            cursorPaint = new Paint();
            cursorPaint.setStrokeWidth(cursorWidth);
            cursorPaint.setColor(cursorColor);
            cursorPaint.setStyle(Paint.Style.FILL);
        }
        if (cipherPaint == null) {
            cipherPaint = new Paint();
            cipherPaint.setColor(Color.GRAY);
            cipherPaint.setTextAlign(Paint.Align.CENTER);
            cipherPaint.setStyle(Paint.Style.FILL);
        }
        pwdData = new String[pwdLength];
        cursorFlashTime = 500;

        timerTask = new TimerTask() {

            @Override
            public void run() {
                isCursorShowing = !isCursorShowing;
                postInvalidate();
            }
        };
        timer = new Timer();

    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        //开启定时器，显示光标   cursorTime为光标闪动的间隔时间
        timer.scheduleAtFixedRate(timerTask, 0, cursorFlashTime);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        timer.cancel();//取消定时器
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        outAttrs.inputType = InputType.TYPE_CLASS_NUMBER;//设置输入信息的类型只能是数字
        return super.onCreateInputConnection(outAttrs);
    }

    /**
     * 点击控件弹出软键盘
     *
     * @param event
     * @return
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            //弹出软键盘
            requestFocus();//焦点聚焦在控件上
            inputMethodManager.showSoftInput(this, InputMethodManager.SHOW_FORCED);
        }
        return super.onTouchEvent(event);
    }

    /**
     * 当失去系统焦点，隐藏软键盘
     *
     * @param hasWindowFocus
     */
    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (!hasWindowFocus) {
            inputMethodManager.hideSoftInputFromWindow(this.getWindowToken(), 0);
        }
    }

    /**
     * 保存状态
     *
     * @return
     */
    @Override
    protected Parcelable onSaveInstanceState() {
        Bundle bundle = new Bundle();
        bundle.putParcelable("superState", super.onSaveInstanceState());
        bundle.putStringArray("pwd", pwdData);
        bundle.putInt("cursorPosition", cursorPosition);
        return bundle;
    }

    /**
     * 恢复数据
     *
     * @param state
     */
    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (state instanceof Bundle) {
            Bundle bundle = (Bundle) state;
            pwdData = bundle.getStringArray("pwd");
            cursorPosition = bundle.getInt("cursorPosition");
            state = bundle.getParcelable("superState");
        }
        super.onRestoreInstanceState(state);
    }

    /**
     * 宽高确定
     *
     * @param widthMeasureSpec
     * @param heightMeasureSpec
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        //重写onMeasure,自定义控件的宽高等其他指标
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int width = 0;
        switch (widthMode) {
            case MeasureSpec.UNSPECIFIED:
                break;
            case EXACTLY:
                //控件的宽度指定了
                width = MeasureSpec.getSize(widthMeasureSpec);
                //这是密码单元的宽度大小就需要通过指定的控件宽度来计算
                pwdSize = (width - getPaddingLeft() - getPaddingRight() - pwdPadding * (pwdLength - 1)) / pwdLength;
                break;
            case AT_MOST:
                //没有指定大小，控件的宽度＝单个密码框的宽度 * 密码位数 + 密码框间隔 * （密码位数 - 1）
                width = pwdSize * pwdLength + pwdPadding * (pwdLength - 1);
                pwdSize = dp2px(getContext(), 20);//给每个密码单元设置一个默认的宽度
                break;
        }
        setMeasuredDimension(width, pwdSize);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        //密码文本大小
        cipherTextSize = pwdSize / 2;
        //光标长度
        cursorHeight = pwdSize / 2;
    }

    /**
     * 绘制
     *
     * @param canvas
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        switch (mode) {
            case BORDER_STYLE_CHEEK:
                //方框样式
                drawCheek(canvas);
                break;
            case BORDER_STYLE_UNDERLINE:
                //下划线样式
                drawUnderline(canvas);
                break;
        }
        //画游标
        drawCursor(canvas);
        //画密文符号
        drawCipherText(canvas);
    }

    /**
     * 画方框
     *
     * @param canvas
     */
    private void drawCheek(Canvas canvas) {
//        mPaint.setStyle(Paint.Style.STROKE);
//        mPaint.setStrokeWidth(borderWidth);
//        Rect rect = new Rect();
//        canvas.getClipBounds(rect);
//        canvas.drawRect(rect, mPaint);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setStrokeWidth(borderWidth);
        canvas.drawLine(0, 0, getWidth(), 0, mPaint);
        canvas.drawLine(0, pwdSize, getWidth(), pwdSize, mPaint);
        canvas.drawLine(0, 0, 0, pwdSize, mPaint);
        canvas.drawLine(getWidth(), 0, getWidth(), pwdSize, mPaint);
        mPaint.setStrokeWidth(borderWidth/2);
        for (int i = 1; i < pwdLength; i++) {
            //根据密码位数绘制中间分割线
            //起始点x=paddingLeft+单个密码单元大小+(密码框边距-分割线宽度)/2 * i
            //起始点y=分割线宽度
            //终止点x=起始点+分割线宽度
            //终止点y=分割线宽度+paddingTop+单个密码单元大小+paddingBottom
            canvas.drawLine(getPaddingLeft() + (pwdSize + pwdPadding) * i - pwdPadding / 2, 0, getPaddingLeft() + (pwdSize + pwdPadding) * i - pwdPadding / 2,
                    borderWidth + getPaddingTop() + pwdSize + getPaddingBottom(), mPaint);
        }
    }

    /**
     * 绘制下划线
     *
     * @param canvas
     */
    private void drawUnderline(Canvas canvas) {
        for (int i = 0; i < pwdLength; i++) {
            //根据密码位数绘制下划线
            //起始点x=paddingLeft+(单个密码单元大小+密码框边距)*i
            //起始点y=paddingTop+单个密码框大小
            //终止点x=起始点+单个密码框大小,终止点y与起始点一样不变
            canvas.drawLine(getPaddingLeft() + (pwdSize + pwdPadding) * i, getPaddingTop() + pwdSize,
                    getPaddingLeft() + (pwdSize + pwdPadding) * i + pwdSize, getPaddingTop() + pwdSize, mPaint);
        }
    }

    /**
     * 绘制光标
     *
     * @param canvas
     */
    private void drawCursor(Canvas canvas) {
        //绘制光标的前提：光标未显示&开启光标&输入位数未满&获得焦点
        if (!isCursorShowing && isCursorEnable && !isInputComplete && hasFocus()) {
            //起始点x=paddingLeft+单个密码框／2+(单个密码框大小+密码框间距)*i
            //起始点y=paddingTop+(单个密码框大小-光标大小)/2
            //终止点x=起始点x
            //终止点y=起始点y+光标高度
            canvas.drawLine(getPaddingLeft() + pwdSize / 2 + (pwdSize + pwdPadding) * cursorPosition, getPaddingTop() + (pwdSize - cursorHeight) / 2,
                    getPaddingLeft() + pwdSize / 2 + (pwdSize + pwdPadding) * cursorPosition, getPaddingTop() + (pwdSize - cursorHeight) / 2 + cursorHeight, cursorPaint);
        }
    }

    /**
     * 画密文符号
     *
     * @param canvas
     */
    private void drawCipherText(Canvas canvas) {
        //画笔设置
        cipherPaint.setTextSize(cipherTextSize);
        //文字居中的处理
        Rect rect = new Rect();
        canvas.getClipBounds(rect);
        int cHeight = rect.height();
        cipherPaint.getTextBounds(CIPHER_TEXT, 0, CIPHER_TEXT.length(), rect);
        float y = cHeight / 2f + rect.height() / 2f - rect.bottom;
        //根据输入密码的位数，进行for循环绘制
        for (int i = 0; i < pwdLength; i++) {
            if (!TextUtils.isEmpty(pwdData[i])) {
                //x=paddingLeft+单个密码框大小/2+(密码框大小+密码框间距)*i
                //y=paddingTop+文字居中所需的偏移量
                if (isCipherEnable) {
                    //开启密文
//                    canvas.drawText(CIPHER_TEXT, getPaddingLeft() + pwdSize / 2 + (pwdSize / 2 + pwdPadding) * i, getPaddingTop() + y, paint);
                    canvas.drawText(CIPHER_TEXT, getPaddingLeft() + pwdSize / 2 + (pwdSize + pwdPadding) * i, getPaddingTop() + y, cipherPaint);
                } else {
                    //不开启密文
                    canvas.drawText(pwdData[i], getPaddingLeft() + pwdSize / 2 + (pwdSize + pwdPadding) * i, getPaddingTop() + y, cipherPaint);
                }
            }
        }
    }

    /*************************  工具方法  **********************/

    /**
     * 不同单位值转换 dp/px
     *
     * @param context
     * @param dipValue
     * @return
     */
    public int dp2px(Context context, float dipValue) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dipValue * scale + 0.5f);
    }

    /**************************************************************/


    /**
     * 定义密码监听器，监听密码输入状态
     */
    public interface PwdInputListener {

        /**
         * 输入删除监听
         *
         * @param text 输入删除的字符
         */
        void pwdChange(String text);

        /**
         * 输入完成
         */
        void pwdComplete();

        /**
         * 确认键后的回调
         *
         * @param password
         * @param isComplete
         */
        void keyConfirmPress(String password, boolean isComplete);

    }

    class PwdKeyListener implements OnKeyListener {

        @Override
        public boolean onKey(View v, int keyCode, KeyEvent event) {
            int action = event.getAction();
            if (action == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_DEL) {
                    if (TextUtils.isEmpty(pwdData[0])) {
                        return true;
                    }
                    String deleteInfo = deletePwdOfOne();
                    if (pwdInputListener != null && !TextUtils.isEmpty(deleteInfo)) {
                        pwdInputListener.pwdChange(deleteInfo);
                    }
                    postInvalidate();
                    return true;
                }
                if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
                    if (isInputComplete) return true;
                    String inputInfo = addPwd(String.valueOf(keyCode - 7));
                    if (pwdInputListener != null && !TextUtils.isEmpty(inputInfo)) {
                        pwdInputListener.pwdChange(inputInfo);
                    }
                    postInvalidate();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_ENTER) {
                    //确认键
                    if (pwdInputListener != null) {
                        pwdInputListener.keyConfirmPress(getPwdInfo(), isInputComplete);
                    }
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * 回退删除一个密码
     *
     * @return
     */
    private String deletePwdOfOne() {
        String deleteInfo = null;
        if (cursorPosition > 0) {
            deleteInfo = pwdData[cursorPosition - 1];
            pwdData[cursorPosition - 1] = null;
            cursorPosition--;
        } else if (cursorPosition == 0) {
            deleteInfo = pwdData[cursorPosition];
            pwdData[cursorPosition] = null;
        }
        isInputComplete = false;
        return deleteInfo;
    }

    /**
     * 添加密码
     *
     * @param addInfo
     * @return
     */
    private String addPwd(String addInfo) {
        String addText = null;
        if (cursorPosition < pwdLength) {
            addText = addInfo;
            pwdData[cursorPosition] = addInfo;
            cursorPosition++;
            if (cursorPosition == pwdLength) {
                isInputComplete = true;
                if (pwdInputListener != null) {
                    pwdInputListener.pwdComplete();
                }
            }
        }
        return addText;
    }

    /**
     * 获取密码信息
     *
     * @return
     */
    public String getPwdInfo() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < pwdLength; i++) {
            if (!TextUtils.isEmpty(pwdData[i])) {
                builder.append(pwdData[i]);
            } else {
                break;
            }
        }
        String pwdInfo = builder.toString();
        builder.delete(0, builder.length());//清空 StringBuilder中的内容
        if (TextUtils.isEmpty(pwdInfo)) {
            return null;
        } else {
            return pwdInfo;
        }
    }
}
