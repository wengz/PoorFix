package pers.wengzc.poorfixkit;

public class RunParams {

    public Object mOriginalObject;

    public Object[] mParams;

    public RunParams(Object[] params, Object orgObject) {
        this.mParams = params;
        this.mOriginalObject = orgObject;
    }
}
