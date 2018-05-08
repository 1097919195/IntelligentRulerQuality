package cc.lotuscard.bean;

import java.io.Serializable;

/**
 * Created by Administrator on 2018/5/3 0003.
 */

public class QualityValueLength implements Serializable {
    private String valueLength;

    public QualityValueLength() {
    }

    public QualityValueLength(String valueLength) {
        this.valueLength = valueLength;
    }

    public String getValueLength() {
        return valueLength;
    }

    public void setValueLength(String valueLength) {
        this.valueLength = valueLength;
    }
}
