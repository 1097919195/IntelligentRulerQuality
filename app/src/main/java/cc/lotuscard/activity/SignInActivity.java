package cc.lotuscard.activity;

import android.content.Intent;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;

import com.jaydenxiao.common.base.BaseActivity;
import com.jaydenxiao.common.commonutils.LogUtils;
import com.jaydenxiao.common.commonutils.ToastUtil;

import butterknife.BindView;
import cc.lotuscard.rulerQuality.R;
import cc.lotuscard.widget.ClearEditText;

/**
 * Created by Administrator on 2018/5/7 0007.
 */

public class SignInActivity extends BaseActivity {
    @BindView(R.id.passWord)
    ClearEditText passWord;
    @BindView(R.id.signIn)
    Button signIn;
    @Override
    public int getLayoutId() {
        return R.layout.act_signin;
    }

    @Override
    public void initPresenter() {

    }

    @Override
    public void initView() {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);// 设置全屏
        initListener();
    }

    private void initListener() {
        signIn.setOnClickListener(v->{
            if (passWord.getText().toString().equals("123345")) {
                Intent intent = new Intent(SignInActivity.this, LotusCardDemoActivity.class);
                startActivity(intent);
            }else {
                ToastUtil.showShort("密码错误！");
            }
        });
    }
}
