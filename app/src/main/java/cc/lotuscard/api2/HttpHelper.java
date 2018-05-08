package cc.lotuscard.api2;

import android.content.Context;
import android.util.Log;

import java.util.concurrent.TimeUnit;

import cc.lotuscard.api.ApiConstants;
import cc.lotuscard.app.AppApplication;
import cc.lotuscard.bean.HttpResponse;
import cc.lotuscard.utils.exception.ApiException;
import cc.lotuscard.utils.exception.TimeoutException;
import io.reactivex.functions.Function;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Created by Administrator on 2018/4/26 0026.
 */

public class HttpHelper {
    private static final int DEFAULT_TIMEOUT = 6000;
    private static final int TIMEOUT_STATUS = 1430;
    private static final int EXCEPTION_THRESHOLD = 1000;
    protected Retrofit retrofit;

    protected HttpHelper() {
        //手动创建一个OkHttpClient并设置超时时间
        OkHttpClient.Builder httpClientBuilder = new OkHttpClient.Builder();
        httpClientBuilder.connectTimeout(DEFAULT_TIMEOUT, TimeUnit.SECONDS);
//        initHeader(httpClientBuilder);
        retrofit = new Retrofit.Builder()
                .client(httpClientBuilder.build())
                .addConverterFactory(GsonConverterFactory.create())
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .baseUrl(ApiConstants.QUALITY_HOST)
                .build();
    }

    private void initHeader(OkHttpClient.Builder httpClientBuilder) {
        httpClientBuilder.addInterceptor(chain -> {
            Context context = AppApplication.getAppContext();
            String jwt = "";
            Request original = chain.request();
            Request.Builder requestBuilder = original.newBuilder()
                    .header("X-Authorization", jwt);
            Request request = requestBuilder.build();
            return chain.proceed(request);
        });
    }

    public class HttpResponseFunc<T> implements Function<HttpResponse<T>, T> {
        @Override
        public T apply(HttpResponse<T> httpResponse) throws Exception {
            //全局处理错误信息
            Log.e("------", httpResponse.toString());
            int status = httpResponse.getStatus();
            if (status >= EXCEPTION_THRESHOLD) {
                if (status == TIMEOUT_STATUS) {
                    throw new TimeoutException(httpResponse.getMsg());
                } else {
                    throw new ApiException(httpResponse.getMsg());
                }
            }
            if (httpResponse.getData() == null) {
                throw new ApiException("暂无数据");
            }
            return httpResponse.getData();
        }
    }
}
