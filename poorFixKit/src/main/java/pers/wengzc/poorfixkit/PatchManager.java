package pers.wengzc.poorfixkit;

import android.os.Environment;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import dalvik.system.PathClassLoader;

public class PatchManager {

    private static ClassLoader sClassLoader;

    public static ClassLoader getClassLoader() {
        return sClassLoader;
    }

    public static void setClassLoader(ClassLoader classLoader) {
        sClassLoader = classLoader;
    }

    public static void loadPatch (){
        try{
            File rootDir = Environment.getExternalStorageDirectory();
            File patchFile = new File(rootDir, "patch.dex");
            PathClassLoader pathClassLoader = new PathClassLoader(patchFile.getAbsolutePath(), PatchManager.class.getClassLoader());
            PatchManager.setClassLoader(pathClassLoader);
        }catch (Exception e){
            e.printStackTrace();
        }
    }


    public static boolean isPatched (MethodIdentifier methodIdentifier){
        try{
            Class patchClass = sClassLoader.loadClass(methodIdentifier.mClassName+"$$Proxy");
            Method method = patchClass.getMethod(methodIdentifier.mMethodName, methodIdentifier.mParameterTypes);
            return true;
        }catch (Exception e){
            e.printStackTrace();
        }
        return false;
    }

    public static Object fixedReturn (MethodIdentifier methodIdentifier, RunParams runParams){
        try{
            Class patchClass = sClassLoader.loadClass(methodIdentifier.mClassName+"$$Proxy");
            Method method = patchClass.getMethod(methodIdentifier.mMethodName, methodIdentifier.mParameterTypes);
            if (methodIdentifier.mIsStatic){
                return method.invoke(null, runParams.mParams);
            }else{
                Object patchInstance = patchClass.newInstance();
                try{
                    Field orgObjectField = patchClass.getField("$$originalObject");
                    orgObjectField.set(patchInstance, runParams.mOriginalObject);
                }catch (Exception e){
                    e.printStackTrace();
                }
                return method.invoke(patchInstance, runParams.mParams);
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }
}
