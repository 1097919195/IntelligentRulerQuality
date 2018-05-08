package cc.lotuscard.db;

import com.jaydenxiao.common.commonutils.LogUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import cc.lotuscard.app.AppApplication;
import cc.lotuscard.bean.QualityData;
import cc.lotuscard.bean.QualityValueLength;
import cc.lotuscard.rulerQuality.R;

/**
 * Created by Administrator on 2018/5/3 0003.
 */

public class QualityValueLengthManager {
    public static List<QualityValueLength> loadNewsChannelsStatic() {
        List<String> valueLength = Arrays.asList(AppApplication.getAppContext().getResources().getStringArray(R.array.items_quality_value));
        ArrayList<QualityValueLength> newsChannelTables=new ArrayList<>();
        for (int i = 0; i < valueLength.size(); i++) {
            QualityValueLength entity = new QualityValueLength(valueLength.get(i));
            newsChannelTables.add(entity);
        }
        return newsChannelTables;
    }
}
