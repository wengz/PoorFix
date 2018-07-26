package pers.wengzc.poorfix;

import com.android.SdkConstants;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.TransformInput;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;

import javassist.ClassPool;
import javassist.CtClass;

public class ConvertUtil {

    public static List<CtClass> toPatchClasses (Collection<TransformInput> inputs, ClassPool classPool) throws Exception {
        List<String> classNames = new ArrayList<>();
        List<CtClass> allClass = new ArrayList<>();

        for (TransformInput input : inputs){
            Collection<DirectoryInput> directoryInputs = input.getDirectoryInputs();
            for (DirectoryInput directoryInput : directoryInputs){

                File dir = directoryInput.getFile();
                String dirPath = dir.getAbsolutePath();
                classPool.insertClassPath(dirPath);
                for( File file : FileUtils.listFiles(dir, null, true) ){
                    if (file.getAbsolutePath().endsWith(SdkConstants.DOT_CLASS)){
                        String className = file.getAbsolutePath().substring(dirPath.length() + 1, file.getAbsolutePath().length() - SdkConstants.DOT_CLASS.length()).replaceAll(Matcher.quoteReplacement(File.separator), ".");
                        if (!className.contains("$$")){
                            continue;
                        }
                        if (classNames.contains(className)){
                            throw new RuntimeException("重复的类定义");
                        }
                        classNames.add(className);
                    }
                }
            }

            Collection<JarInput> jarInputs = input.getJarInputs();
            for (JarInput jarInput : jarInputs){
                File file = jarInput.getFile();
                classPool.insertClassPath(file.getAbsolutePath());
                JarFile jarFile = new JarFile(file);
                Enumeration<JarEntry> classes = jarFile.entries();
                while (classes.hasMoreElements()){
                    JarEntry libClass = classes.nextElement();
                    String className = libClass.getName();
                    if (className.endsWith(SdkConstants.DOT_CLASS)){
                        className = className.substring(0, className.length() - SdkConstants.DOT_CLASS.length()).replaceAll("/", ".");
                        if (!className.contains("$$")){
                            continue;
                        }
                        if (classNames.contains(className)){
                            throw new RuntimeException("重复的类定义>>>"+className);
                        }
                        classNames.add(className);
                    }
                }
            }
        }

        for (String className : classNames){
            allClass.add(classPool.get(className));
        }

        Collections.sort(allClass, new Comparator<CtClass>() {
            @Override
            public int compare(CtClass ct1, CtClass ct2) {
                return ct1.getName().compareTo(ct2.getName());
            }
        });
        return allClass;
    }

    public static List<CtClass> toCtClasses (Collection<TransformInput> inputs, ClassPool classPool) throws Exception {
        List<String> classNames = new ArrayList<>();
        List<CtClass> allClass = new ArrayList<>();

        for (TransformInput input : inputs){
            Collection<DirectoryInput> directoryInputs = input.getDirectoryInputs();
            for (DirectoryInput directoryInput : directoryInputs){

                File dir = directoryInput.getFile();
                String dirPath = dir.getAbsolutePath();
                classPool.insertClassPath(dirPath);
                for( File file : FileUtils.listFiles(dir, null, true) ){
                    if (file.getAbsolutePath().endsWith(SdkConstants.DOT_CLASS)){
                        String className = file.getAbsolutePath().substring(dirPath.length() + 1, file.getAbsolutePath().length() - SdkConstants.DOT_CLASS.length()).replaceAll(Matcher.quoteReplacement(File.separator), ".");
                        if (className.contains("$$")){
                            continue;
                        }
                        if (classNames.contains(className)){
                            throw new RuntimeException("重复的类定义");
                        }
                        classNames.add(className);
                    }
                }
            }

            Collection<JarInput> jarInputs = input.getJarInputs();
            for (JarInput jarInput : jarInputs){
                File file = jarInput.getFile();
                classPool.insertClassPath(file.getAbsolutePath());
                JarFile jarFile = new JarFile(file);
                Enumeration<JarEntry> classes = jarFile.entries();
                while (classes.hasMoreElements()){
                    JarEntry libClass = classes.nextElement();
                    String className = libClass.getName();
                    if (className.endsWith(SdkConstants.DOT_CLASS)){
                        className = className.substring(0, className.length() - SdkConstants.DOT_CLASS.length()).replaceAll("/", ".");
                        if (className.contains("$$")){
                            continue;
                        }
                        if (classNames.contains(className)){
                            throw new RuntimeException("重复的类定义>>>"+className);
                        }
                        classNames.add(className);
                    }
                }
            }
        }

        for (String className : classNames){
            allClass.add(classPool.get(className));
        }

        Collections.sort(allClass, new Comparator<CtClass>() {
            @Override
            public int compare(CtClass ct1, CtClass ct2) {
                return ct1.getName().compareTo(ct2.getName());
            }
        });
        return allClass;
    }

}
