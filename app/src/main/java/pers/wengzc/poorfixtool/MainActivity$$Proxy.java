package pers.wengzc.poorfixtool;

import android.content.Context;
import android.widget.Toast;

import java.lang.reflect.Method;

public class MainActivity$$Proxy {

    public MainActivity $$originalObject;

    public void someFun (){
        System.out.println("------- fix function --------");

        String toastStr =  $$originalObject.publicStrReturn();
        try{
            Method methodPrivateStrReturn = $$originalObject.getClass().getDeclaredMethod("privateStrReturn", new Class[0]);
            methodPrivateStrReturn.setAccessible(true);
            toastStr += methodPrivateStrReturn.invoke($$originalObject);
        }catch (Exception e) {
            e.printStackTrace();
        }
        Toast.makeText($$originalObject, toastStr, Toast.LENGTH_SHORT).show();
    }

    public static String helloString (){
        return "成功读取补丁后的静态方法返回";
    }
}
