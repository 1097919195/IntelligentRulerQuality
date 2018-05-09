package cc.lotuscard.activity;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.IntentFilter;
import android.graphics.drawable.ColorDrawable;
import android.support.annotation.NonNull;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.TextView;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.alibaba.fastjson.JSON;
import com.aspsine.irecyclerview.IRecyclerView;
import com.aspsine.irecyclerview.universaladapter.ViewHolderHelper;
import com.aspsine.irecyclerview.universaladapter.recyclerview.CommonRecycleViewAdapter;
import com.aspsine.irecyclerview.universaladapter.recyclerview.OnItemClickListener;
import com.jakewharton.rxbinding2.widget.RxTextView;
import com.jaydenxiao.common.base.BaseActivity;
import com.jaydenxiao.common.base.BasePopupWindow;
import com.jaydenxiao.common.baserx.RxBus2;
import com.jaydenxiao.common.commonutils.LogUtils;
import com.jaydenxiao.common.commonutils.ToastUtil;
import com.polidea.rxandroidble2.RxBleClient;
import com.polidea.rxandroidble2.RxBleConnection;
import com.polidea.rxandroidble2.RxBleDevice;
import com.polidea.rxandroidble2.RxBleDeviceServices;
import com.polidea.rxandroidble2.scan.ScanResult;
import com.tbruyelle.rxpermissions2.RxPermissions;


import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import butterknife.BindView;
import cc.lotuscard.app.AppApplication;
import cc.lotuscard.app.AppConstant;
import cc.lotuscard.bean.BleDevice;
import cc.lotuscard.bean.HttpResponse;
import cc.lotuscard.bean.QualityBleData;
import cc.lotuscard.bean.QualityValueLength;
import cc.lotuscard.contract.QualityContract;
import cc.lotuscard.rulerQuality.R;
import cc.lotuscard.model.QualityModel;
import cc.lotuscard.presenter.QualityPresenter;
import cc.lotuscard.broadcast.StartingUpBroadcast;
import io.reactivex.functions.Consumer;


public class LotusCardDemoActivity extends BaseActivity<QualityPresenter,QualityModel> implements QualityContract.View{

    /*********************************** BLE *********************************/
    private List<BleDevice> bleDeviceList = new ArrayList<>();
    private CommonRecycleViewAdapter<BleDevice> bleDeviceAdapter;
    private MaterialDialog scanResultDialog,cirProgressBarWithScan,cirProgressBarWithChoose;
    private List<String> rxBleDeviceAddressList = new ArrayList<>();

    /*********************************** UI *********************************/
    private StartingUpBroadcast startingUpBroadcast;
    @BindView(R.id.bleState)
    ImageView bleState;
    @BindView(R.id.bleMacAddress)
    TextView bleMacAddress;
    @BindView(R.id.bleBattery)
    TextView bleBattery;
    @BindView(R.id.customer)
    EditText customer;
    @BindView(R.id.irc_quality_data)
    IRecyclerView irc;
    @BindView(R.id.macCounts)
    TextView macCounts;
    private CommonRecycleViewAdapter<QualityBleData> adapter;
    List<QualityBleData> qualityBleDataList = new ArrayList<>();

    List<QualityValueLength> qualityValueLengths = new ArrayList<>();
    int unMeasuredCounts=0;
    int measuredCounts=0;
    int itemPostion = 0;
    int itemPostionAgo = 0;
    boolean remuasure = false;
    List<Integer> canRemeasureData = new ArrayList<>();
    List<Integer> unqualifiedData = new ArrayList<>();


    @BindView(R.id.ircWithSearch)
    IRecyclerView ircWithSearch;
    CommonRecycleViewAdapter<String> searchAdapter;
    BasePopupWindow popupWindow;
    View pop;
    IRecyclerView ircSearch;
    List<String> searchName = new ArrayList<>();
    boolean stopSearch = false;

    @Override
    protected void onResume() {
        super.onResume();

        configureBleList();
        initBleStateListener();
    }

    private void initBleStateListener() {
        bleState.setOnClickListener(v ->  {
                scanAndConnectBle();
        });
    }

    private void configureBleList() {
        bleDeviceAdapter = new CommonRecycleViewAdapter<BleDevice>(this,R.layout.item_bledevice, bleDeviceList) {
            @Override
            public void convert(ViewHolderHelper helper, BleDevice bleDevice) {
                TextView text_name = helper.getView(R.id.text_name);
                TextView text_mac = helper.getView(R.id.text_mac);
                TextView text_rssi = helper.getView(R.id.text_rssi);
                text_name.setText(bleDevice.getName());
                text_mac.setText(bleDevice.getAddress());
                text_rssi.setText(String.valueOf(bleDevice.getRssi()));

                helper.itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        //连接蓝牙
                        mPresenter.chooseDeviceConnectRequest(text_mac.getText().toString());
                        if (scanResultDialog != null) {
                            scanResultDialog.dismiss();
                        }
                    }
                });

            }
        };

        scanResultDialog = new MaterialDialog.Builder(this)
                .title(R.string.choose_device_prompt)
                .content("已检测到的蓝牙设备...")
                .backgroundColor(getResources().getColor(R.color.white))
                .titleColor(getResources().getColor(R.color.scan_result_list_title))
                .adapter(bleDeviceAdapter, null)
                .dividerColor(getResources().getColor(R.color.white))
                .build();

        cirProgressBarWithScan = new MaterialDialog.Builder(this)
                .progress(true, 100)
                .content("扫描附近蓝牙...")
                .backgroundColor(getResources().getColor(R.color.white))
                .build();

        cirProgressBarWithChoose = new MaterialDialog.Builder(this)
                .progress(true, 100)
                .content("配对中...")
                .backgroundColor(getResources().getColor(R.color.white))
                .build();
    }

    private void scanAndConnectBle() {
        BluetoothAdapter defaultAdapter = BluetoothAdapter.getDefaultAdapter();
        //先判断蓝牙是否打开
        if (!defaultAdapter.isEnabled()) {
            new MaterialDialog.Builder(this)
                    .content(getString(R.string.can_open_ble))
                    .positiveText(getString(R.string.open))
                    .negativeText(getString(R.string.cancel))
                    .backgroundColor(getResources().getColor(R.color.white))
                    .contentColor(getResources().getColor(R.color.primary))
                    .onPositive((dialog, which) -> defaultAdapter.enable())
                    .show();
        } else {
            RxPermissions rxPermissions = new RxPermissions(this);
            rxPermissions.requestEach(Manifest.permission.ACCESS_COARSE_LOCATION)
                    .subscribe(permission -> { // will emit 2 Permission objects
                        if (permission.granted) {
                            // FIXME: 2018/4/10 0010 需检测当前位置有没有开启
                            cirProgressBarWithScan.show();
                            Timer timer = new Timer();
                            timer = new Timer(true);
                            timer.schedule(new TimerTask() {
                                @Override
                                public void run() {
                                    if (cirProgressBarWithScan.isShowing()){
                                        cirProgressBarWithScan.dismiss();
                                        RxBus2.getInstance().post(AppConstant.NO_BLE_FIND,true);
                                    }
                                }
                            }, 6000);
                            rxBleDeviceAddressList.clear();
                            bleDeviceList.clear();
                            mPresenter.getBleDeviceDataRequest();

                        } else if (permission.shouldShowRequestPermissionRationale) {
                            // Denied permission without ask never again
                        } else {
                            ToastUtil.showShort(getString(R.string.unauthorized_location));
                        }
                    });
        }
    }

    @Override
    public int getLayoutId() {
        return R.layout.act_main;
    }

    @Override
    public void initPresenter() {
        mPresenter.setVM(this,mModel);
    }

    @Override
    public void initView() {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);// 设置全屏
        StartingUpBroadcastRecive();
        pop = LayoutInflater.from(this).inflate(R.layout.pop_fuzzysearch, null);
        ircSearch = pop.findViewById(R.id.searchList);

        initRcycleAdapter();
        itemClickRemeasure();
        initRxBus2FindBle();
        initListener();
        initSearchAdapter();
    }

    private void initSearchAdapter() {
        searchAdapter= new CommonRecycleViewAdapter<String>(this,R.layout.item_customer,searchName) {
            @Override
            public void convert(ViewHolderHelper helper, String names) {
                TextView customer = helper.getView(R.id.customerName);
                customer.setText(names);
            }
        };

        //popupwindow
        ircSearch.setAdapter(searchAdapter);
        ircSearch.setLayoutManager(new StaggeredGridLayoutManager(1,StaggeredGridLayoutManager.VERTICAL));
        //framlayout
        ircWithSearch.setAdapter(searchAdapter);
        ircWithSearch.setLayoutManager(new StaggeredGridLayoutManager(1,StaggeredGridLayoutManager.VERTICAL));


        searchAdapter.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(ViewGroup parent, View view, Object o, int position) {
                ircWithSearch.setVisibility(View.GONE);
                customer.setText(searchName.get(position));
                stopSearch = true;
            }

            @Override
            public boolean onItemLongClick(ViewGroup parent, View view, Object o, int position) {
                return false;
            }
        });

    }

    private void initListener() {
        RxTextView.textChanges(customer)
                .debounce( 600 , TimeUnit.MILLISECONDS )
                .subscribe(new Consumer<Object>() {
                    @Override
                    public void accept(Object o) throws Exception {
                        if (!TextUtils.isEmpty(customer.getEditableText()) && !stopSearch) {
                            mPresenter.getFuzzySearchDataRequest(customer.getEditableText().toString());
                        }
                        ircWithSearch.post(() -> {
                            if (ircWithSearch.getVisibility() == View.VISIBLE) {
                                ircWithSearch.setVisibility(View.GONE);
                            }
                        });
                    }
                });

        customer.setOnClickListener(v->{stopSearch = false;});
    }

    private void itemClickRemeasure() {
        adapter.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(ViewGroup parent, View view, Object o, int position) {
                if (canRemeasureData.size() > position) {
                    itemPostion = position;
                    remuasure = true;
                    qualityBleDataList.get(itemPostionAgo).setSelected(false);
                    if (unMeasuredCounts != 0) {
                        qualityBleDataList.get(measuredCounts-unMeasuredCounts).setSelected(false);
                    }
                    qualityBleDataList.get(itemPostion).setSelected(true);
                    adapter.notifyDataSetChanged();
                    itemPostionAgo = itemPostion;
                }else {
                    ToastUtil.showShort("请先按顺序完成第一次测量");
                }

                // FIXME: 2018/5/7 0007
//                View viewHolder = irc.getChildAt(position);
//                LogUtils.loge(viewHolder.toString());
//                if (null != irc.getChildViewHolder(viewHolder)){
//                    ViewHolderHelper viewHolderHelper = (ViewHolderHelper) irc.getChildViewHolder(viewHolder);
//                    viewHolderHelper.setBackgroundColor(R.id.ll_container, getResources().getColor(R.color.red));
//                    adapter.notifyDataSetChanged();
//                }
            }

            @Override
            public boolean onItemLongClick(ViewGroup parent, View view, Object o, int position) {
                return false;
            }
        });
    }

    private void initRcycleAdapter() {
        mPresenter.getQualityDataRequest();
        adapter = new CommonRecycleViewAdapter<QualityBleData>(this, R.layout.item_quality, qualityBleDataList) {
            @Override
            public void convert(ViewHolderHelper helper, QualityBleData qualityBleData) {
                TextView valueLength = helper.getView(R.id.valueLength);
                TextView actValue = helper.getView(R.id.actValue);
                TextView actAngle = helper.getView(R.id.actAngle);

                valueLength.setText(qualityBleData.getValueLength());
                actValue.setText(String.valueOf(qualityBleData.getActValue()));
                actAngle.setText(String.valueOf(qualityBleData.getActAngle()));

                if (qualityBleData.isUnqualified()) {
                    //不及格的
                    actValue.setTextColor(getResources().getColor(R.color.battery_color));
                }else {
                    actValue.setTextColor(getResources().getColor(R.color.black));
                }

                if (qualityBleData.isSelected()) {
                    //选中的样式
                    helper.setBackgroundColor(R.id.valueLength, getResources().getColor(R.color.item_selector));
                    helper.setBackgroundColor(R.id.actValue, getResources().getColor(R.color.item_selector));
                    helper.setBackgroundColor(R.id.actAngle, getResources().getColor(R.color.item_selector));
                } else {
                    //未选中的样式
                    helper.setBackgroundColor(R.id.valueLength,getResources().getColor(R.color.white));
                    helper.setBackgroundColor(R.id.actValue,getResources().getColor(R.color.white));
                    helper.setBackgroundColor(R.id.actAngle,getResources().getColor(R.color.white));
                }

                if (qualityBleData.getActValue() == 0) {
                    actValue.setTextColor(getResources().getColor(R.color.black));
                }
            }
        };
        View view = View.inflate(this, R.layout.list_bottom_button, null);
        view.findViewById(R.id.btnClear).setOnClickListener(v -> {
            new MaterialDialog.Builder(this)
                    .title("确定清除所有数据吗")
                    .onPositive(new MaterialDialog.SingleButtonCallback() {
                        @Override
                        public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                            qualityBleDataList.clear();
                            for (int i=0;i<qualityValueLengths.size();i++) {
                                qualityBleDataList.add(new QualityBleData());
                                qualityBleDataList.get(i).setValueLength(qualityValueLengths.get(i).getValueLength());
                            }
                            unMeasuredCounts = qualityValueLengths.size();
                            measuredCounts = qualityValueLengths.size();
                            qualityBleDataList.get(0).setSelected(true);
                            adapter.notifyDataSetChanged();
                            customer.setText("");
                        }
                    })
                    .positiveText(getResources().getString(R.string.sure))
                    .negativeColor(getResources().getColor(R.color.ff0000))
                    .negativeText("点错了")
                    .show();

        });
        view.findViewById(R.id.btnSave).setOnClickListener(v -> {
            if (customer.getEditableText().toString().trim().length()>=1) {
                if (unMeasuredCounts == 0 && AppConstant.MAC_ADDRESS!="") {
                    mPresenter.getUpLoadAfterCheckedRequest(customer.getEditableText().toString().trim(),AppConstant.MAC_ADDRESS);
                }else {
                    ToastUtil.showShort("请先测量完毕！");
                }
            }else {
                ToastUtil.showShort("您还没有填写对应的客户呐！");
            }

        });
        irc.addFooterView(view);

        irc.setAdapter(adapter);
        irc.setLayoutManager(new StaggeredGridLayoutManager(1,StaggeredGridLayoutManager.VERTICAL));
    }

    private void initRxBus2FindBle() {
        //监听是否发现附近蓝牙
        mRxManager.on(AppConstant.NO_BLE_FIND, new Consumer<Boolean>() {
            @Override
            public void accept(Boolean isChecked) throws Exception {
                if (isChecked) {
                    ToastUtil.showShort("附近没有可见设备！请重试");
                }
            }
        });
    }

    public void StartingUpBroadcastRecive() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("android.intent.action.BOOT_COMPLETED");
        startingUpBroadcast = new StartingUpBroadcast();
        registerReceiver(startingUpBroadcast, intentFilter);
    }

    //获取需要测试的长度
    @Override
    public void returnGetQualityData(List<QualityValueLength> qualityValueLength) {
        if (qualityValueLength != null) {
            qualityValueLengths = qualityValueLength;
            for (int i=0;i<qualityValueLength.size();i++) {
                qualityBleDataList.add(new QualityBleData());
                qualityBleDataList.get(i).setValueLength(qualityValueLength.get(i).getValueLength());
            }
            unMeasuredCounts = qualityValueLength.size();
            measuredCounts = qualityValueLength.size();
            qualityBleDataList.get(0).setSelected(true);
        }
    }

    //获取附近的蓝牙设备
    @Override
    public void returnGetBleDeviceData(ScanResult scanResult) {
        if (scanResult != null) {
            RxBleDevice device = scanResult.getBleDevice();
            if (!rxBleDeviceAddressList.contains(device.getMacAddress())) {//避免重复添加设备
                rxBleDeviceAddressList.add(device.getMacAddress());
                bleDeviceList.add(new BleDevice(device.getName(), device.getMacAddress(), scanResult.getRssi()));
                bleDeviceAdapter.notifyDataSetChanged();
            }

            if (rxBleDeviceAddressList.size() != 0 && cirProgressBarWithScan.isShowing()) {
                cirProgressBarWithScan.dismiss();
                scanResultDialog.show();
            }
        }
    }

    //获取到UUID并且建立通信
    @Override
    public void returnChooseDeviceConnectWithSetUuid(RxBleDeviceServices deviceServices) {
        for (BluetoothGattService service : deviceServices.getBluetoothGattServices()) {
            for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                if (isCharacteristicNotifiable(characteristic)) {
                    AppConstant.UUID_STRING = characteristic.getUuid().toString();
                    ToastUtil.showShort("蓝牙配对成功，等待建立通信中...");
                    cirProgressBarWithChoose.dismiss();
                    mPresenter.startMeasureRequest(characteristic.getUuid());
                    if (AppConstant.MAC_ADDRESS!="") {
                        mPresenter.checkBleConnectStateRequest(AppConstant.MAC_ADDRESS);
                    }
                    break;
                }
            }
        }
    }
    private boolean isCharacteristicNotifiable(BluetoothGattCharacteristic characteristic) {
        return (characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0;
    }

    @Override
    public void returnChooseDeviceConnectWithSetAddress(String mac) {
        AppConstant.MAC_ADDRESS = mac;
    }

    //测量内容
    @Override
    public void returnStartMeasure(Float length, Float angle, int battery) {
        bleBattery.setText(battery + "%");

        if (remuasure) {
            qualityBleDataList.get(itemPostion).setActValue(length);
            qualityBleDataList.get(itemPostion).setActAngle(angle);
            qualityBleDataList.get(itemPostion).setSelected(false);

            if (unMeasuredCounts != 0) {
                qualityBleDataList.get(measuredCounts - unMeasuredCounts).setSelected(true);
                remuasure = false;
            } else {
                remuasure = false;
            }
            adapter.notifyDataSetChanged();
        } else {
            if (unMeasuredCounts != 0) {
                assignValue(length, angle);
            } else {
                ToastUtil.showShort(getString(R.string.measure_completed));
            }
        }

        //对应不达标的显示状态
        unqualifiedData.clear();
        for (int i = 0; i < qualityBleDataList.size(); i++) {
            if (qualityBleDataList.get(i).getActValue() - Float.valueOf(qualityBleDataList.get(i).getValueLength()) > 0.3 || qualityBleDataList.get(i).getActValue() - Float.valueOf(qualityBleDataList.get(i).getValueLength()) < -0.3) {
                unqualifiedData.add(i);
            }else {
                qualityBleDataList.get(i).setUnqualified(false);
            }
        }

        for (Integer i:unqualifiedData) {
            qualityBleDataList.get(i).setUnqualified(true);
            adapter.notifyDataSetChanged();
        }
    }

    private void assignValue(Float length, Float angle) {
        try {
            if (unMeasuredCounts != 1) {//这个操作只有蓝牙按下后才会触发，所以unMeasuredCounts不能为1
                qualityBleDataList.get(measuredCounts+1-unMeasuredCounts).setSelected(true);
            }
            qualityBleDataList.get(measuredCounts-unMeasuredCounts).setSelected(false);
            qualityBleDataList.get(measuredCounts - unMeasuredCounts).setActValue(length);
            qualityBleDataList.get(measuredCounts - unMeasuredCounts).setActAngle(angle);
            canRemeasureData.add(measuredCounts-unMeasuredCounts);
            if (unMeasuredCounts != 0) {
                unMeasuredCounts = unMeasuredCounts - 1;
            }
            adapter.notifyDataSetChanged();
        } catch (Exception e) {

        }

    }

    //监听蓝牙状态
    @Override
    public void returnCheckBleConnectState(RxBleConnection.RxBleConnectionState connectionState,String mac) {
        RxBleClient rxBleClient = AppApplication.getRxBleClient(this);

        RxBleDevice rxBleDevice = rxBleClient.getBleDevice(mac);
        RxBleConnection.RxBleConnectionState bleStatus = rxBleDevice.getConnectionState();
        if (bleStatus == connectionState.DISCONNECTED) {
            bleState.setImageResource(R.drawable.ble_disconnected);
            bleMacAddress.setText("");
            bleBattery.setText("");
        }
        if (bleStatus == connectionState.CONNECTED) {
            bleState.setImageResource(R.drawable.ble_connected);
            bleMacAddress.setText(AppConstant.MAC_ADDRESS);
        }
    }

    //上传服务器返回
    @Override
    public void returnGetUpLoadAfterChecked(HttpResponse httpResponse){
        if (httpResponse.getSuccess()){
            //设置录入总数
            try {
                JSONObject jsonObject = new JSONObject(httpResponse.getData().toString());
                LogUtils.loge("录入总数"+jsonObject.getString("count"));
                float f = Float.parseFloat(jsonObject.getString("count"));
                int i = (int) f;
                macCounts.setText(String.valueOf(i));
            } catch (JSONException e) {
                e.printStackTrace();
            }

            ToastUtil.showShort(httpResponse.getMsg());
            qualityBleDataList.clear();
            for (int i=0;i<qualityValueLengths.size();i++) {
                qualityBleDataList.add(new QualityBleData());
                qualityBleDataList.get(i).setValueLength(qualityValueLengths.get(i).getValueLength());
            }
            unMeasuredCounts = qualityValueLengths.size();
            measuredCounts = qualityValueLengths.size();
            qualityBleDataList.get(0).setSelected(true);
            adapter.notifyDataSetChanged();
            customer.setText("");
        }else {
            ToastUtil.showShort("该mac已经存在，不可重复添加");
        }
    }

    @Override
    public void returnGetFuzzySearchData(HttpResponse fuzzySearchData) {
        searchName.clear();
        if (fuzzySearchData != null) {
            try {
                String s = JSON.toJSONString(fuzzySearchData.getData());
                JSONArray jsonArray = new JSONArray(s);
                LogUtils.loge("jsonArray==="+jsonArray);
                for (int i=0; i<jsonArray.length();i++) {
                    JSONObject ob  = jsonArray.getJSONObject(i);
                    String name= ob.getString("name");
                    LogUtils.loge("name==" +name);
                    searchName.add(name);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
            if (searchName.size() > 0) {
                searchAdapter.notifyDataSetChanged();
                ircWithSearch.setVisibility(View.VISIBLE);
//                showPopupWindow();
            }
        }
    }

    private void showPopupWindow() {
        popupWindow = new BasePopupWindow(this);
        popupWindow.setWidth(ViewGroup.LayoutParams.MATCH_PARENT);
        popupWindow.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
//        popupWindow.setHeight(240);
        popupWindow.setContentView(pop);
        popupWindow.setOutsideTouchable(true);
        popupWindow.setFocusable(false);//不然底部不可编辑
        popupWindow.setBackgroundDrawable(new ColorDrawable(0x00000000));
        popupWindow.showAsDropDown(customer, Gravity.BOTTOM, 0, 0);
    }

    @Override
    public void showLoading(String title) {
        if (title=="chooseConnect") {
            cirProgressBarWithChoose.show();
        }

    }

    @Override
    public void stopLoading() {

    }

    @Override
    public void showErrorTip(String msg) {
        ToastUtil.showShort(msg);
        //蓝牙连接失败
        if(msg=="connectFail"){
            cirProgressBarWithChoose.dismiss();
            bleState.setImageResource(R.drawable.ble_disconnected);
            AppConstant.UUID_STRING= "";
            AppConstant.MAC_ADDRESS= "";
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (startingUpBroadcast != null) {
            unregisterReceiver(startingUpBroadcast);
        }
    }
}
