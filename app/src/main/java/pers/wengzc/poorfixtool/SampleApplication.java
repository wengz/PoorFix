package pers.wengzc.poorfixtool;

import android.app.Application;

public class SampleApplication extends Application{

    public static SampleApplication sApplication;

    @Override
    public void onCreate() {
        super.onCreate();

        sApplication = this;
    }
}
