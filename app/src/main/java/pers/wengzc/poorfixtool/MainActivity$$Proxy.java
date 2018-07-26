package pers.wengzc.poorfixtool;

import android.content.Context;
import android.widget.Toast;

public class MainActivity$$Proxy {

    public void someFun (){
        System.out.println("------- fix function --------");
        Toast.makeText(SampleApplication.sApplication, "修改后的方法", Toast.LENGTH_SHORT).show();
    }

    public static String helloString (){
        return "成功读取补丁后的静态方法返回";
    }
}
