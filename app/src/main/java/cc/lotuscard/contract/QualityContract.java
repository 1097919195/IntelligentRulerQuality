package cc.lotuscard.contract;

import com.jaydenxiao.common.base.BaseModel;
import com.jaydenxiao.common.base.BasePresenter;
import com.jaydenxiao.common.base.BaseView;
import com.polidea.rxandroidble2.RxBleConnection;
import com.polidea.rxandroidble2.RxBleDeviceServices;
import com.polidea.rxandroidble2.scan.ScanResult;


import org.json.JSONException;

import java.util.List;
import java.util.UUID;

import cc.lotuscard.bean.HttpResponse;
import cc.lotuscard.bean.QualityValueLength;
import io.reactivex.Maybe;
import io.reactivex.Observable;


/**
 * Created by Administrator on 2018/3/28 0028.
 */

public interface QualityContract {
    interface Model extends BaseModel {
        Observable<List<QualityValueLength>> getQualityData();

        Observable<ScanResult> getBleDeviceData();

        Maybe<RxBleDeviceServices> chooseDeviceConnect(String mac);

        Observable<byte[]> startMeasure(UUID characteristicUUID);

        Observable<RxBleConnection.RxBleConnectionState> checkBleConnectState(String mac);

        Observable<HttpResponse> getUpLoadAfterChecked(String customer, String macAddress);

        Observable<HttpResponse> getFuzzySearchData(String name);
    }

    interface View extends BaseView {
        void returnGetQualityData(List<QualityValueLength> qualityData);

        void returnGetBleDeviceData(ScanResult scanResult);

        void returnChooseDeviceConnectWithSetUuid(RxBleDeviceServices rxBleConnection);
        void returnChooseDeviceConnectWithSetAddress(String mac);

        void returnStartMeasure(Float length, Float angle, int battery);

        void returnCheckBleConnectState(RxBleConnection.RxBleConnectionState connectionState,String mac);

        void returnGetUpLoadAfterChecked(HttpResponse httpResponse) throws JSONException;

        void returnGetFuzzySearchData(HttpResponse fuzzySearchData);
    }

    abstract class Presenter extends BasePresenter<View, Model> {
        public abstract void getQualityDataRequest();

        public abstract void getBleDeviceDataRequest();

        public abstract void chooseDeviceConnectRequest(String mac);

        public abstract void startMeasureRequest(UUID characteristicUUID);

        public abstract void checkBleConnectStateRequest(String mac);

        public abstract void getUpLoadAfterCheckedRequest(String customer, String macAddress);

        public abstract void getFuzzySearchDataRequest(String name);
    }

}
