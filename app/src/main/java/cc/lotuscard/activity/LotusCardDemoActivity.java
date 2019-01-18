package cc.lotuscard.activity;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.IntentFilter;
import android.graphics.drawable.ColorDrawable;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.alibaba.fastjson.JSON;
import com.aspsine.irecyclerview.IRecyclerView;
import com.aspsine.irecyclerview.universaladapter.ViewHolderHelper;
import com.aspsine.irecyclerview.universaladapter.recyclerview.CommonRecycleViewAdapter;
import com.aspsine.irecyclerview.universaladapter.recyclerview.OnItemClickListener;
import com.clj.fastble.BleManager;
import com.clj.fastble.callback.BleGattCallback;
import com.clj.fastble.callback.BleNotifyCallback;
import com.clj.fastble.callback.BleScanCallback;
import com.clj.fastble.callback.BleWriteCallback;
import com.clj.fastble.data.BleDevice;
import com.clj.fastble.exception.BleException;
import com.clj.fastble.scan.BleScanRuleConfig;
import com.jakewharton.rxbinding2.widget.RxTextView;
import com.jaydenxiao.common.base.BaseActivity;
import com.jaydenxiao.common.base.BasePopupWindow;
import com.jaydenxiao.common.baserx.RxBus2;
import com.jaydenxiao.common.baserx.RxSchedulers;
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
import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import butterknife.BindView;
import cc.lotuscard.app.AppApplication;
import cc.lotuscard.app.AppConstant;
import cc.lotuscard.bean.MyBleDevice;
import cc.lotuscard.bean.HttpResponse;
import cc.lotuscard.bean.QualityBleData;
import cc.lotuscard.bean.QualityValueLength;
import cc.lotuscard.contract.QualityContract;
import cc.lotuscard.rulerQuality.R;
import cc.lotuscard.model.QualityModel;
import cc.lotuscard.presenter.QualityPresenter;
import cc.lotuscard.broadcast.StartingUpBroadcast;
import cc.lotuscard.utils.HexString;
import cc.lotuscard.utils.HexStringTwo;
import io.reactivex.Observable;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.functions.Consumer;
import io.reactivex.observers.DisposableObserver;
import io.reactivex.subjects.PublishSubject;


public class LotusCardDemoActivity extends BaseActivity<QualityPresenter,QualityModel> implements QualityContract.View{

    /*********************************** BLE *********************************/
    private List<MyBleDevice> myBleDeviceList = new ArrayList<>();//RxAndroidBle中的
    List<BleDevice> scanResultList = new ArrayList<>();
    private CommonRecycleViewAdapter<BleDevice> bleDeviceAdapter;
    private MaterialDialog scanResultDialog,cirProgressBarWithScan,cirProgressBarWithChoose;
    private List<String> rxBleDeviceAddressList = new ArrayList<>();//避免重复添加设备

    /*********************************** UI *********************************/
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
    @BindView(R.id.change_name)
    Button change_name;
    @BindView(R.id.disconnect_ble)
    Button disconnect_ble;
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
    String result = "";//格式化尺子编号
    boolean isGetRulerNum = false;
    int connectPostion = -1;

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
        //蓝牙默认扫描配置
        BleScanRuleConfig scanRuleConfig = new BleScanRuleConfig.Builder()
//                                    .setServiceUuids(serviceUuids)      // 只扫描指定的服务的设备，可选
//                                    .setDeviceName(true, names)         // 只扫描指定广播名的设备，可选
//                                    .setDeviceMac(mac)                  // 只扫描指定mac的设备，可选
//                                    .setAutoConnect(isAutoConnect)      // 连接时的autoConnect参数，可选，默认false
                .setScanTimeOut(50000)              // 扫描超时时间，可选，默认10秒
                .build();
        BleManager.getInstance().initScanRule(scanRuleConfig);

        bleDeviceAdapter = new CommonRecycleViewAdapter<BleDevice>(this,R.layout.item_bledevice, scanResultList) {
            @Override
            public void convert(ViewHolderHelper helper, BleDevice myBleDevice) {
                TextView text_name = helper.getView(R.id.text_name);
                TextView text_mac = helper.getView(R.id.text_mac);
                TextView text_rssi = helper.getView(R.id.text_rssi);
                text_name.setText(myBleDevice.getName());
                text_mac.setText(myBleDevice.getMac());
                text_rssi.setText(String.valueOf(myBleDevice.getRssi()));

                helper.itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        //连接蓝牙
//                        mPresenter.chooseDeviceConnectRequest(text_mac.getText().toString());
                        connectPostion = helper.getAdapterPosition();//记录索引等下获取对应的BleDevice
                        LogUtils.loge("connectPostion=="+connectPostion);
                        BleManager.getInstance().cancelScan();//停止扫描
                        BleManager.getInstance().connect(text_mac.getText().toString(), new BleGattCallback() {
                            @Override
                            public void onStartConnect() {
                                cirProgressBarWithChoose.show();
                            }

                            @Override
                            public void onConnectFail(BleDevice bleDevice, BleException exception) {
                                bleState.setImageResource(R.drawable.ble_disconnected);
                                cirProgressBarWithChoose.dismiss();
                                bleMacAddress.setText("");
                                ToastUtil.showShort("蓝牙连接失败");
                                AppConstant.MAC_ADDRESS = "";
                                bleBattery.setText("");
                            }

                            @Override
                            public void onConnectSuccess(BleDevice bleDevice, BluetoothGatt gatt, int status) {
                                AppConstant.MAC_ADDRESS = bleDevice.getMac();
                                bleState.setImageResource(R.drawable.ble_connected);
                                bleMacAddress.setText(AppConstant.MAC_ADDRESS);
                                cirProgressBarWithChoose.dismiss();
                                ToastUtil.showShort("连接成功");
                                BleManager.getInstance().notify(//连接后 获取通知特性
                                        bleDevice,
                                        AppConstant.UUID_SERVER,
                                        AppConstant.UUID_STRING,
                                        new BleNotifyCallback() {
                                            @Override
                                            public void onNotifySuccess() {
                                                ToastUtil.showShort("通知连接成功");
                                                // 打开通知操作成功
                                            }

                                            @Override
                                            public void onNotifyFailure(BleException exception) {
                                                bleMacAddress.setText("");
                                                ToastUtil.showShort("通知连接失败");
                                                // 打开通知操作失败
                                            }

                                            @Override
                                            public void onCharacteristicChanged(byte[] data) {
                                                // 打开通知后，设备发过来的数据将在这里出现
                                                String s = HexString.bytesToHex(data);
                                                if (s.length() == AppConstant.STANDARD_LENGTH) {
                                                    int code = Integer.parseInt("8D6A", 16);
                                                    int length = Integer.parseInt(s.substring(0, 4), 16);
                                                    int angle = Integer.parseInt(s.substring(4, 8), 16);
                                                    int battery = Integer.parseInt(s.substring(8, 12), 16);
                                                    int a1 = length ^ code;
                                                    int a2 = angle ^ code;
                                                    int a3 = battery ^ code;
                                                    a1 += AppConstant.ADJUST_VALUE;
                                                    returnStartMeasure(Float.valueOf(a1) / 10, Float.valueOf(a2) / 10, a3);
                                                }
                                            }
                                        });
                            }

                            @Override
                            public void onDisConnected(boolean isActiveDisConnected, BleDevice bleDevice, BluetoothGatt gatt, int status) {
                                bleState.setImageResource(R.drawable.ble_disconnected);
                                cirProgressBarWithChoose.dismiss();
                                bleMacAddress.setText("");
                                ToastUtil.showShort("蓝牙连接断开");
                                AppConstant.MAC_ADDRESS = "";
                                bleBattery.setText("");
                            }
                        });
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
                            cirProgressBarWithScan.show();

                            rxBleDeviceAddressList.clear();
                            myBleDeviceList.clear();
                            scanResultList.clear();
//                            mPresenter.getBleDeviceDataRequest();
                            BleManager.getInstance().scan(new BleScanCallback() {
                                @Override
                                public void onScanStarted(boolean success) {
                                    scanResultDialog.show();
                                }

                                @Override
                                public void onLeScan(BleDevice bleDevice) {
                                    super.onLeScan(bleDevice);
                                    if (bleDevice != null) {
                                        if (!rxBleDeviceAddressList.contains(bleDevice.getMac())&& bleDevice.getName()!=null && !bleDevice.getName().equals("")) {//避免重复添加设备
                                            rxBleDeviceAddressList.add(bleDevice.getMac());
                                            scanResultList.add(bleDevice);
                                            bleDeviceAdapter.notifyDataSetChanged();
                                        }

                                        if (rxBleDeviceAddressList.size() != 0 && cirProgressBarWithScan.isShowing()) {
                                            cirProgressBarWithScan.dismiss();
                                        }
                                    }
                                }

                                @Override
                                public void onScanning(BleDevice bleDevice) {
                                }

                                @Override
                                public void onScanFinished(List<BleDevice> scanResult) {
//                                    ToastUtil.showShort("本次扫描结束");
                                }
                            });

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
        pop = LayoutInflater.from(this).inflate(R.layout.pop_fuzzysearch, null);
        ircSearch = pop.findViewById(R.id.searchList);

        initRcycleAdapter();
        itemClickRemeasure();
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

        //设置分割线
//        ircWithSearch.addItemDecoration(new DividerItemDecoration(this,DividerItemDecoration.VERTICAL_LIST));//默认
        //添加自定义分割线
        DividerItemDecoration divider = new DividerItemDecoration(this,DividerItemDecoration.VERTICAL);//这里导入的包和自己封装的库不同
        divider.setDrawable(ContextCompat.getDrawable(this,R.drawable.custom_divider));
        ircWithSearch.addItemDecoration(divider);


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

        mPresenter.getRulerNumDataRequest();//初始化返回当前尺子可用的编码（每次会加1）

        //发送完指令需要断开连接使修改生效
        change_name.setOnClickListener(v -> {
            if(!AppConstant.MAC_ADDRESS.equals("")){//保证蓝牙已经连接
                mPresenter.getRulerNumDataRequest();//初始化返回当前尺子可用的编码（每次会加1）
                if (isGetRulerNum && !result.equals("")) {//确保蓝牙编号存在
                    String checkCode1 = result.substring(0, 2);//注意substring是不包括结尾的索引的
                    String checkCode2 = result.substring(2, 4);
                    //把二个十六进制转化成long相加 来获取校验码
                    long x = Long.parseLong(checkCode1, 16);
                    long y = Long.parseLong(checkCode2, 16);
                    String checkCodeResult = Long.toHexString(x+y);
                    LogUtils.loge(checkCode1+"==="+checkCode2+"==="+checkCodeResult);
                    if (checkCodeResult.length() > 2) {//确保校验码长度小于2
                        ToastUtil.showShort("校验码长度过长,写入失败");
                        mPresenter.getRulerNumDataRequest();
                        return;
                    }
                    String instructions = "A0" + result + checkCodeResult;//最终发送的指令 A0这个帧头是固定的
                    LogUtils.loge("instructions=="+instructions);

                    CompositeDisposable disposable = new CompositeDisposable();
                    PublishSubject<Boolean> disconnectTriggerSubject = PublishSubject.create();
                    byte[] bytes = HexStringTwo.hexStringToBytes(instructions);//一个字节可表示为两个十六进制数字  A0 00 01 01
                    LogUtils.loge("length: "+bytes.length);

                    writeBleName(bytes);
//                    byte[] bytes1 = Arrays.copyOfRange(bytes, 0, 1);
//                    byte[] bytes2 = Arrays.copyOfRange(bytes, 1, 2);
//                    byte[] bytes3 = Arrays.copyOfRange(bytes, 2, 3);
//                    byte[] bytes4 = Arrays.copyOfRange(bytes, 3, 4);
//                    ArrayList<byte[]> arrayList = new ArrayList<>();
//                    arrayList.add(bytes);
////                    arrayList.add(bytes1);
////                    arrayList.add(bytes2);
////                    arrayList.add(bytes3);
////                    arrayList.add(bytes4);
//
//                    DisposableObserver<Long> disposableObserver = new DisposableObserver<Long>() {
//                        @Override
//                        public void onNext(Long l) {
//                            int i = l.intValue();
//                            if (i == arrayList.size()) {
//                                disposable.clear();
//                                LogUtils.loge("complete");
//                            } else {
//                                AppApplication.getRxBleClient(AppApplication.getAppContext()).getBleDevice(AppConstant.MAC_ADDRESS)
//                                        .establishConnection(false)
//                                        .takeUntil(disconnectTriggerSubject)
//                                        .flatMapSingle(rxBleConnection -> rxBleConnection.writeCharacteristic(UUID.fromString(AppConstant.UUID_WRITE), arrayList.get(i)))
////                                    .firstElement()
//                                        .subscribe(
//                                                by ->
//                                                {
//                                                    LogUtils.loge("write=======" + HexString.bytesToHex(by));
//                                                    disconnectTriggerSubject.onNext(true);
//                                                }
//                                                , e -> LogUtils.loge(i + " times " + e.toString())
//                                        );
//                            }
//                        }
//
//                        @Override
//                        public void onError(Throwable e) {
//
//                        }
//
//                        @Override
//                        public void onComplete() {
//
//                        }
//                    };
//
//                    Observable.interval(0, 200, TimeUnit.MILLISECONDS)
//                            .compose(RxSchedulers.io_main())
//                            .subscribe(disposableObserver);
//                    disposable.add(disposableObserver);
                }else {
                    ToastUtil.showShort("蓝牙编号获取失败，请重试");
                }

            }else {
                ToastUtil.showShort("当前UUID已为空，先连接智能尺");
            }

        });

        disconnect_ble.setOnClickListener(v -> {
            if (scanResultList.size() > 0) {
                if (BleManager.getInstance().isConnected(scanResultList.get(connectPostion))) {
                BleManager.getInstance().disconnect(scanResultList.get(connectPostion));
                }
            }
        });
    }

    //蓝牙改名
    private void writeBleName(byte[] data) {
        BleManager.getInstance().write(
                scanResultList.get(connectPostion),
                AppConstant.UUID_SERVER,
                AppConstant.UUID_WRITE,
                data,
                new BleWriteCallback() {
                    @Override
                    public void onWriteSuccess(int current, int total, byte[] justWrite) {
                        ToastUtil.showShort("写入成功了，请断开蓝牙");
                        // 发送数据到设备成功（分包发送的情况下，可以通过方法中返回的参数可以查看发送进度）
                    }

                    @Override
                    public void onWriteFailure(BleException exception) {
                        ToastUtil.showShort("写入失败，请重试");
                        // 发送数据到设备失败
                    }
                });
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
        mPresenter.getQualityDataRequest();//先获取需要测量的具体数据
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
                myBleDeviceList.add(new MyBleDevice(device.getName(), device.getMacAddress(), scanResult.getRssi()));
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
        //蓝牙第一版（只有通讯）
//        LogUtils.loge("asdf"+deviceServices.getBluetoothGattServices().size());
//        for (BluetoothGattService service : deviceServices.getBluetoothGattServices()) {
//            for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
//                LogUtils.loge("asdf===="+characteristic.getUuid().toString());
//                if (isCharacteristicNotifiable(characteristic)) {
//                    AppConstant.UUID_STRING = characteristic.getUuid().toString();
//                    LogUtils.loge("asdf"+AppConstant.UUID_STRING);
//                    ToastUtil.showShort("蓝牙配对成功，等待建立通信中...");
//                    cirProgressBarWithChoose.dismiss();
//                    mPresenter.startMeasureRequest(characteristic.getUuid());
//                    if (AppConstant.MAC_ADDRESS!="") {
//                        mPresenter.checkBleConnectStateRequest(AppConstant.MAC_ADDRESS);
//                    }
//                    break;
//                }
//            }
//        }
        //蓝牙第二版（可写--改名）通过FastBle查看到特性的值
            for (BluetoothGattCharacteristic characteristic : deviceServices.getBluetoothGattServices().get(3).getCharacteristics()) {
                if ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) {
                    // TODO: 2019/1/10 0010 不同蓝牙的服务和特性都是一样的通过FastBLE直接固定传入，不获取了
                }
                if (isCharacteristicNotifiable(characteristic)) {
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

    //客户的模糊查询返回
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

    @Override
    public void returnRulerNumData(Integer data) {//65535转化的十六进制是ffff,再大就会超过4位数
        isGetRulerNum = true;
        LogUtils.loge("ruler_num"+data);
        String strHex = Integer.toHexString(data);//将其转换为十六进制
        LogUtils.loge("ruler_num_hex"+strHex);
        if (strHex.length() < 5) {
            strHex = String.format("%4s", Integer.toHexString(data)).replace(' ', '0');//确保位4位数
            result = strHex;
        }else {
            ToastUtil.showShort("服务器编号转化的字节长度过长,请删除数据库的编号重新开始");
            result = "";
        }
        LogUtils.loge("ruler_num_result"+result);
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
        if (msg.equals("蓝牙编号获取失败")) {
            isGetRulerNum = false;
            mPresenter.getRulerNumDataRequest();//编号获取失败就直接跳过重新获取
        }
    }
}
