package cc.lotuscard.model;


import com.jaydenxiao.common.baserx.RxSchedulers;

import com.jaydenxiao.common.commonutils.ACache;
import com.polidea.rxandroidble2.RxBleClient;
import com.polidea.rxandroidble2.RxBleConnection;
import com.polidea.rxandroidble2.RxBleDeviceServices;
import com.polidea.rxandroidble2.scan.ScanFilter;
import com.polidea.rxandroidble2.scan.ScanResult;
import com.polidea.rxandroidble2.scan.ScanSettings;


import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import cc.lotuscard.api.Api;
import cc.lotuscard.api.HostType;
import cc.lotuscard.app.AppApplication;
import cc.lotuscard.app.AppConstant;
import cc.lotuscard.bean.HttpResponse;
import cc.lotuscard.bean.QualityValueLength;
import cc.lotuscard.contract.QualityContract;

import cc.lotuscard.db.QualityValueLengthManager;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;


/**
 * Created by Administrator on 2018/3/28 0028.
 */
public class QualityModel implements QualityContract.Model {
    private RxBleClient rxBleClient = AppApplication.getRxBleClient(AppApplication.getAppContext());
    @Override
    public Observable<List<QualityValueLength>> getQualityData() {
        return Observable.create(new ObservableOnSubscribe<List<QualityValueLength>>() {
            @Override
            public void subscribe(ObservableEmitter<List<QualityValueLength>> emitter) throws Exception {
                ArrayList<QualityValueLength> newsChannelTableList = (ArrayList<QualityValueLength>) ACache.get(AppApplication.getAppContext()).getAsObject(AppConstant.QUALITY_DATA_LENGTH);
                if(newsChannelTableList==null){
                    newsChannelTableList= (ArrayList<QualityValueLength>) QualityValueLengthManager.loadNewsChannelsStatic();
                    ACache.get(AppApplication.getAppContext()).put(AppConstant.QUALITY_DATA_LENGTH,newsChannelTableList);
                }
                emitter.onNext(newsChannelTableList);
                emitter.onComplete();
            }
        }).compose(RxSchedulers.io_main());
    }

    @Override
    public Observable<ScanResult> getBleDeviceData() {
        return rxBleClient.scanBleDevices(
                new ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_BALANCED)//此段代码会导致部分设备找不打对应的RxBleDeviceServices,模式一定要对
                        .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                        .build(),
                new ScanFilter.Builder().build()
        ).compose(RxSchedulers.<ScanResult>io_main());
    }

    @Override
    public Maybe<RxBleDeviceServices> chooseDeviceConnect(String mac) {
         return rxBleClient.getBleDevice(mac)
                .establishConnection(false) //autoConnect flag布尔值：是否直接连接到远程设备（false）或在远程设备变为可用时立即自动连接
                .flatMapSingle(RxBleConnection::discoverServices)
                .firstElement() // Disconnect automatically after discovery
                .compose(RxSchedulers.<RxBleDeviceServices>io_main_maybe());
    }

    @Override
    public Observable<byte[]> startMeasure(UUID characteristicUUID) {
        return rxBleClient.getBleDevice(AppConstant.MAC_ADDRESS)
                .establishConnection(true)//这里要为true
                .flatMap(rxBleConnection -> rxBleConnection.setupNotification(characteristicUUID))
                .flatMap(notificationObservable -> notificationObservable)
                .compose(RxSchedulers.<byte[]>io_main());
    }

    @Override
    public Observable<RxBleConnection.RxBleConnectionState> checkBleConnectState(String mac) {
        return rxBleClient.getBleDevice(mac)
                .observeConnectionStateChanges()
                .compose(RxSchedulers.io_main());
    }

    @Override
    public Observable<HttpResponse> getUpLoadAfterChecked(String customer, String macAddress) {
        return Api.getDefault(HostType.QUALITY_DATA)
                .getUpLoadAfterChecked(customer,macAddress)
                .compose(RxSchedulers.io_main());
    }

    @Override
    public Observable<HttpResponse> getFuzzySearchData(String name) {
        return Api.getDefault(HostType.QUALITY_DATA)
                .getFuzzySearch(name)
                .compose(RxSchedulers.io_main());

    }

}
