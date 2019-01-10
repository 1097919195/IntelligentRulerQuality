package cc.lotuscard.presenter;


import com.jaydenxiao.common.baserx.RxSubscriber;
import com.jaydenxiao.common.commonutils.LogUtils;
import com.polidea.rxandroidble2.scan.ScanResult;

import org.json.JSONException;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import cc.lotuscard.app.AppConstant;
import cc.lotuscard.bean.HttpResponse;
import cc.lotuscard.bean.QualityValueLength;
import cc.lotuscard.contract.QualityContract;
import cc.lotuscard.utils.HexString;

/**
 * Created by Administrator on 2018/3/28 0028.
 */

public class QualityPresenter extends QualityContract.Presenter {
    private static final int MEASURE_DURATION = 400;
    @Override
    public void getQualityDataRequest() {
        mRxManage.add(mModel.getQualityData()
                .subscribeWith(new RxSubscriber<List<QualityValueLength>>(mContext,false) {
            @Override
            protected void _onNext(List<QualityValueLength> qualityValueLengthList) {
                mView.returnGetQualityData(qualityValueLengthList);
            }

            @Override
            protected void _onError(String message) {
                mView.showErrorTip("loadingFail");

            }
        }));
    }

    @Override
    public void getBleDeviceDataRequest() {
        mRxManage.add(mModel.getBleDeviceData()
                .filter(r -> r.getBleDevice().getName() != null)//过滤名字为空的值
                .subscribeWith(new RxSubscriber<ScanResult>(mContext, false) {
                    @Override
                    protected void _onNext(ScanResult scanResult) {
                        mView.returnGetBleDeviceData(scanResult);
                    }

                    @Override
                    protected void _onError(String message) {
//                mView.showErrorTip(message);
                    }
                }));

    }

    @Override
    public void chooseDeviceConnectRequest(String mac) {
        mRxManage.add(mModel.chooseDeviceConnect(mac)
                .doOnSubscribe(disposable->
                    mView.showLoading("chooseConnect"))
                .subscribe(services -> {
                    mView.returnChooseDeviceConnectWithSetAddress(mac);
                    mView.returnChooseDeviceConnectWithSetUuid(services);
                },e -> {mView.showErrorTip("connectFail");LogUtils.loge(e.getCause().toString());}));

    }

    @Override
    public void startMeasureRequest(UUID characteristicUUID) {
        mRxManage.add(mModel.startMeasure(characteristicUUID)
                .throttleFirst(MEASURE_DURATION, TimeUnit.MILLISECONDS)
                .subscribeWith(new RxSubscriber<byte[]>(mContext,false) {
                    @Override
                    protected void _onNext(byte[] bytes) {
                        String s = HexString.bytesToHex(bytes);
                        if (s.length() == AppConstant.STANDARD_LENGTH) {
                            int code = Integer.parseInt("8D6A", 16);
                            int length = Integer.parseInt(s.substring(0, 4), 16);
                            int angle = Integer.parseInt(s.substring(4, 8), 16);
                            int battery = Integer.parseInt(s.substring(8, 12), 16);
                            int a1 = length ^ code;
                            int a2 = angle ^ code;
                            int a3 = battery ^ code;
                            a1 += AppConstant.ADJUST_VALUE;
                            mView.returnStartMeasure(Float.valueOf(a1) / 10, Float.valueOf(a2) / 10, a3);
                        }

                    }

                    @Override
                    protected void _onError(String message) {

                    }
                }));
    }

    @Override
    public void checkBleConnectStateRequest(String mac) {
        mRxManage.add(mModel.checkBleConnectState(mac)
                .subscribe(
                        connectedState->mView.returnCheckBleConnectState(connectedState,mac)
                ));
    }

    @Override
    public void getUpLoadAfterCheckedRequest(String customer, String macAddress) {
        mRxManage.add(mModel.getUpLoadAfterChecked(customer,macAddress).subscribeWith(new RxSubscriber<HttpResponse>(mContext,true) {
            @Override
            protected void _onNext(HttpResponse httpResponse) {
                try {
                    mView.returnGetUpLoadAfterChecked(httpResponse);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            @Override
            protected void _onError(String message) {
                mView.showErrorTip(message);
            }
        }));
    }

    @Override
    public void getFuzzySearchDataRequest(String name) {
        mRxManage.add(mModel.getFuzzySearchData(name)
                .subscribe(
                        fuzzySearchData -> mView.returnGetFuzzySearchData(fuzzySearchData),
                        e->{mView.showErrorTip(e.getMessage());}
                ));
    }

    @Override
    public void getRulerNumDataRequest() {
        mRxManage.add(mModel.getRulerNumData()
                .subscribe(
                        num -> mView.returnRulerNumData(num),
                        e->{mView.showErrorTip(e.getMessage());}
                ));
    }

}
