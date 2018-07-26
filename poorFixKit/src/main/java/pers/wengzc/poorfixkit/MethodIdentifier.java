package pers.wengzc.poorfixkit;

public class MethodIdentifier {

    public boolean mIsStatic;

    public String mClassName;

    public String mMethodName;

    public Class[] mParameterTypes;

    public MethodIdentifier(boolean mIsStatic, String mClassName, String mMethodName, Class[] mParameterTypes) {
        this.mIsStatic = mIsStatic;
        this.mClassName = mClassName;
        this.mMethodName = mMethodName;
        this.mParameterTypes = mParameterTypes;
    }

}
