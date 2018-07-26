package pers.wengzc.poorfix;

import com.android.build.api.transform.Format;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.BaseExtension;
import com.android.build.gradle.internal.pipeline.TransformManager;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipOutputStream;

import javassist.ClassPool;
import javassist.CtClass;

public class PatchGeneratePlugin extends Transform implements Plugin<Project>{

    private Project mProject;

    @Override
    public String getName() {
        return "PatchGenerate";
    }

    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS;
    }

    @Override
    public Set<? super QualifiedContent.Scope> getScopes() {
        return TransformManager.SCOPE_FULL_PROJECT;
    }

    @Override
    public boolean isIncremental() {
        return false;
    }

    @Override
    public void apply(Project project) {
        this.mProject = project;
        BaseExtension androidExtension = (BaseExtension) project.getExtensions().getByName("android");
        androidExtension.registerTransform(this);
    }

    @Override
    public void transform(TransformInvocation transformInvocation) throws TransformException, InterruptedException, IOException {
        try{
            Collection<TransformInput> inputs = transformInvocation.getInputs();
            TransformOutputProvider transformOutputProvider = transformInvocation.getOutputProvider();
            transformOutputProvider.deleteAll();
            File outDir = transformOutputProvider.getContentLocation("main", getOutputTypes(), getScopes(), Format.DIRECTORY);
            ClassPool classPool = new ClassPool();
            BaseExtension androidExtension = (BaseExtension) mProject.getExtensions().getByName("android");
            List<File> bootClassPath = androidExtension.getBootClasspath();
            for (File file : bootClassPath){
                try{
                    classPool.appendClassPath(file.getAbsolutePath());
                }catch (Exception e){
                    e.printStackTrace();
                }
            }

            List<CtClass> box = ConvertUtil.toPatchClasses(inputs, classPool);
            generatePatch(box);
            copyJarToGeneratePath();
            jarToDex();
            throw new RuntimeException("补丁包生成成功");
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private void jarToDex () throws Exception {
        String command = "java -jar " + Config.Patch_Generate_path + Config.Tool_Jar_Dex_Name +
                " --dex --output=" + Config.Patch_Generate_path + Config.Patch_Dex_Name +
                " " + Config.Patch_Generate_path + Config.Patch_Jar_Name;
        executeCommand(command);
    }

    private void copyJarToGeneratePath () throws Exception {
        File targetDir = new File(Config.Patch_Generate_path);
        if (!targetDir.exists()){
            targetDir.mkdirs();
        }
        InputStream inputStream = this.getClass().getResourceAsStream("/libs/dx.jar");
        File targetDirFile = new File(targetDir, "dx.jar");
        FileUtils.copyInputStreamToFile(inputStream, targetDirFile);
    }

    private void executeCommand (String command) throws  Exception {
        Process pc = Runtime.getRuntime().exec(command);
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(pc.getInputStream()));
        StringBuilder stringBuilder = new StringBuilder();
        String line =  null;
        while ( ( line = bufferedReader.readLine() ) != null){
            stringBuilder.append(line+"\n");
        }
        System.out.println(stringBuilder.toString());
    }

    private void generatePatch ( List<CtClass> box ) throws Exception {
        File jarFile = new File(Config.Patch_Generate_path + Config.Patch_Jar_Name);
        if (!jarFile.getParentFile().exists()){
            jarFile.getParentFile().mkdirs();
        }
        if (jarFile.exists()){
            jarFile.delete();
        }

        ZipOutputStream outputStream = new JarOutputStream(new FileOutputStream(jarFile));
        for (CtClass ctClass : box){
            String className = ctClass.getName();
            if (className.contains("$$")){
                ZipUtil.zipFile(ctClass.toBytecode(), outputStream, className.replaceAll("\\.","/")+".class");
            }
        }
        outputStream.close();
    }

}
