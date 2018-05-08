package cc.lotuscard.bean;

/**
 * Created by Administrator on 2018/5/3 0003.
 */

public class QualityBleData {
    private String valueLength;
    private float actAngle;
    private float actValue;
    private boolean isSelected;
    private boolean unqualified;

    public boolean isUnqualified() {
        return unqualified;
    }

    public void setUnqualified(boolean unqualified) {
        this.unqualified = unqualified;
    }

    public boolean isSelected() {
        return isSelected;
    }

    public void setSelected(boolean selected) {
        isSelected = selected;
    }

    public String getValueLength() {
        return valueLength;
    }

    public void setValueLength(String valueLength) {
        this.valueLength = valueLength;
    }

    public float getActAngle() {
        return actAngle;
    }

    public void setActAngle(float actAngle) {
        this.actAngle = actAngle;
    }

    public float getActValue() {
        return actValue;
    }

    public void setActValue(float actValue) {
        this.actValue = actValue;
    }
}
