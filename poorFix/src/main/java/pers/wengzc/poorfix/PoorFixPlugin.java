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
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.bytecode.AccessFlag;
import pers.wengzc.poorfixkit.MethodIdentifier;
import pers.wengzc.poorfixkit.PatchManager;
import pers.wengzc.poorfixkit.RunParams;

public class PoorFixPlugin extends Transform implements Plugin<Project>{

    private Project mProject;

    @Override
    public String getName() {
        return "PoorFix";
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
            File jarFile = transformOutputProvider.getContentLocation("main", getOutputTypes(), getScopes(), Format.JAR);

            if (!jarFile.getParentFile().exists()) {
                jarFile.getParentFile().mkdirs();
            }
            if (jarFile.exists()) {
                jarFile.delete();
            }

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

            List<CtClass> box = ConvertUtil.toCtClasses(inputs, classPool);
            insertCode(box, jarFile);

        }catch (Exception e){
            e.printStackTrace();
        }
    }


    private void insertCode (List<CtClass> box, File jarFile) throws Exception {
        ZipOutputStream outputStream = new JarOutputStream(new FileOutputStream(jarFile));
        for (CtClass ctClass : box){
            ctClass.setModifiers(AccessFlag.setPublic(ctClass.getModifiers()));
            String clsName = ctClass.getName();
            if ( isNeedInsertClass(clsName)
                   && !( ctClass.isInterface() || ctClass.getDeclaredMethods().length < 1 )){
                byte[] classData = transformClass(ctClass.toBytecode(), ctClass.getName().replaceAll("\\.", "/"));
                String copyOutClassFilePath  = "D:/class_view/"+clsName+".class";
                FileUtils.writeByteArrayToFile(new File(copyOutClassFilePath), classData);
                zipFile(classData, outputStream, ctClass.getName().replaceAll("\\.", "/") + ".class");
            }else{
                zipFile(ctClass.toBytecode(), outputStream, ctClass.getName().replaceAll("\\.", "/") + ".class");
            }
        }
        outputStream.close();
    }


    private boolean isNeedInsertClass( String className) {

        if (className.contains("$$Proxy") || className.contains("PermissionUtil") ){
            return false;
        }
        if (className.startsWith("pers.wengzc.poorfixtool")){
            return true;
        }
        return false;
    }


    private byte[] transformClass (byte[] bs, String className)throws Exception{
        try{
            ClassReader classReader = new ClassReader(bs);
            ClassNode classNode = new ClassNode();
            classReader.accept(classNode, ClassReader.EXPAND_FRAMES);
            Iterator<MethodNode> it = classNode.methods.iterator();
            while (it.hasNext()){
                MethodNode mnd = it.next();
                if (isQualifiedMethod(mnd)){
                    transformMethodWrapperClass(mnd, classNode);
                }
            }
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            classNode.accept(cw);
            byte[] b = cw.toByteArray();
            return b;
        }catch (Exception e){
            e.printStackTrace();
        }
        return bs;
    }

    private boolean isQualifiedMethod(MethodNode mnd) {
        int access = mnd.access;
        String name = mnd.name;
        InsnList insnList = mnd.instructions;

        if (insnList.size() <= 0){
            return false;
        }

        //类初始化函数和构造函数过滤
        if (AsmUtils.CLASS_INITIALIZER.equals(name) || AsmUtils.CONSTRUCTOR.equals(name)) {
            return false;
        }

        // synthetic 方法暂时不aop 比如AsyncTask 会生成一些同名 synthetic方法,对synthetic 以及private的方法也插入的代码，主要是针对lambda表达式
        if (((access & Opcodes.ACC_SYNTHETIC) != 0) && ((access & Opcodes.ACC_PRIVATE) == 0)) {
            return false;
        }
        if ((access & Opcodes.ACC_ABSTRACT) != 0) {
            return false;
        }
        if ((access & Opcodes.ACC_NATIVE) != 0) {
            return false;
        }
        if ((access & Opcodes.ACC_INTERFACE) != 0) {
            return false;
        }

        if ((access & Opcodes.ACC_DEPRECATED) != 0) {
            return false;
        }

        return true;
    }

    private void transformMethodWrapperClass (MethodNode mnd, ClassNode cn){
        InsnList insnList = mnd.instructions;
        String methodName = mnd.name;
        String classNameInternal = cn.name;
        String className = classNameInternal.replace("/", ".");
        boolean isStatic = isStatic(mnd.access);

        Type[] argType = Type.getArgumentTypes(mnd.desc);
        Type returnType = Type.getReturnType(mnd.desc);

        InsnList codeInsert = new InsnList();
        codeInsert.add(new TypeInsnNode(Opcodes.NEW, Type.getInternalName(MethodIdentifier.class)));
        codeInsert.add(new InsnNode(Opcodes.DUP));
        codeInsert.add(new InsnNode( isStatic ? Opcodes.ICONST_1 : Opcodes.ICONST_0 ));
        codeInsert.add(new LdcInsnNode(className));
        codeInsert.add(new LdcInsnNode(methodName));
        codeInsert.add(new IntInsnNode(Opcodes.BIPUSH, argType.length));
        codeInsert.add(new TypeInsnNode(Opcodes.ANEWARRAY, Type.getInternalName(Class.class)));

        for ( int index = 0; index < argType.length; index++ ){
            codeInsert.add(new InsnNode(Opcodes.DUP));
            codeInsert.add(new IntInsnNode(Opcodes.BIPUSH, index));
            codeInsert.add(classInfoFromArgumentType(argType[index]));
            codeInsert.add(new InsnNode(Opcodes.AASTORE));
        }

        codeInsert.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
                Type.getInternalName(MethodIdentifier.class),
                "<init>",
                "(ZLjava/lang/String;Ljava/lang/String;[Ljava/lang/Class;)V"));
        codeInsert.add(nodeFor_isPatched());
        LabelNode insertEndLabel = new LabelNode();
        codeInsert.add(new JumpInsnNode(Opcodes.IFEQ, insertEndLabel));
        codeInsert.add(new TypeInsnNode(Opcodes.NEW, Type.getInternalName(MethodIdentifier.class)));
        codeInsert.add(new InsnNode(Opcodes.DUP));
        codeInsert.add(new InsnNode( isStatic ? Opcodes.ICONST_1 : Opcodes.ICONST_0 ));
        codeInsert.add(new LdcInsnNode(className));
        codeInsert.add(new LdcInsnNode(methodName));
        codeInsert.add(new IntInsnNode(Opcodes.BIPUSH, argType.length));
        codeInsert.add(new TypeInsnNode(Opcodes.ANEWARRAY, Type.getInternalName(Class.class)));
        for ( int index = 0; index < argType.length; index++ ){
            codeInsert.add(new InsnNode(Opcodes.DUP));
            codeInsert.add(new IntInsnNode(Opcodes.BIPUSH, index));
            codeInsert.add(classInfoFromArgumentType(argType[index]));
            codeInsert.add(new InsnNode(Opcodes.AASTORE));
        }

        codeInsert.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
                Type.getInternalName(MethodIdentifier.class),
                "<init>",
                "(ZLjava/lang/String;Ljava/lang/String;[Ljava/lang/Class;)V"));
        codeInsert.add(new TypeInsnNode(Opcodes.NEW, Type.getInternalName(RunParams.class)));
        codeInsert.add(new InsnNode(Opcodes.DUP));
        codeInsert.add(new IntInsnNode(Opcodes.BIPUSH, argType.length));
        codeInsert.add(new TypeInsnNode(Opcodes.ANEWARRAY, Type.getInternalName(Object.class)));

        for ( int argIndex = 0, localVarIndex = 1; argIndex < argType.length; argIndex++ ){
            codeInsert.add(new InsnNode(Opcodes.DUP));
            codeInsert.add(new IntInsnNode(Opcodes.BIPUSH, argIndex));
            codeInsert.add(loadLocalVar(argType[argIndex], localVarIndex));
            if (isPrimaryType(argType[argIndex])){
                codeInsert.add(valueObjectFromPrimaryArgumentType(argType[argIndex]));
            }
            codeInsert.add(new InsnNode(Opcodes.AASTORE));
            localVarIndex++;
            if (isWideLocalVariable(argType[argIndex])){
                localVarIndex++;
            }
        }

        codeInsert.add( isStatic ? new InsnNode(Opcodes.ACONST_NULL): new VarInsnNode(Opcodes.ALOAD, 0));

        codeInsert.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
                Type.getInternalName(RunParams.class),
                "<init>",
                "([Ljava/lang/Object;Ljava/lang/Object;)V"));
        codeInsert.add(nodeFor_fixedReturn());
        if (!isPrimaryType(returnType)){
            codeInsert.add(new TypeInsnNode(Opcodes.CHECKCAST, returnType.getInternalName()));
        }
        codeInsert.add(returnNode(returnType));
        codeInsert.add(insertEndLabel);

        insnList.insertBefore(insnList.getFirst(), codeInsert);
    }

    private boolean isStatic(int access) {
        return (access & Opcodes.ACC_STATIC) != 0;
    }

    private AbstractInsnNode returnNode (Type returnType){
        String className = returnType.getClassName();
        if ("byte".equals(className)
                || "short".equals(className)
                || "int".equals(className)
                || "char".equals(className)
                || "boolean".equals(className)
                ){
            return new InsnNode(Opcodes.IRETURN);
        }
        if ("long".equals(className)){
            return new InsnNode(Opcodes.LRETURN);
        }
        if ("float".equals(className)){
            return new InsnNode(Opcodes.FRETURN);
        }
        if ("double".equals(className)){
            return new InsnNode(Opcodes.DRETURN);
        }
        if ("void".equals(className)){
            return new InsnNode(Opcodes.RETURN);
        }
        return new InsnNode(Opcodes.ARETURN);
    }


    private AbstractInsnNode valueObjectFromPrimaryArgumentType(Type argumentType){
        String className = argumentType.getClassName();
        if ("boolean".equals(className)){
            return new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "java/lang/Boolean",
                    "valueOf",
                    "(Z)Ljava/lang/Boolean;");
        }
        if ("byte".equals(className)){
            return new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "java/lang/Byte",
                    "valueOf",
                    "(B)Ljava/lang/Byte;");
        }
        if ("short".equals(className)){
            return new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "java/lang/Short",
                    "valueOf",
                    "(S)Ljava/lang/Short;");
        }
        if ("int".equals(className)){
            return new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "java/lang/Integer",
                    "valueOf",
                    "(I)Ljava/lang/Integer;");
        }
        if ("long".equals(className)){
            return new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "java/lang/Long",
                    "valueOf",
                    "(J)Ljava/lang/Long;");
        }
        if ("float".equals(className)){
            return new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "java/lang/Float",
                    "valueOf",
                    "(F)Ljava/lang/Float;");
        }
        if ("double".equals(className)){
            return new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "java/lang/Double",
                    "valueOf",
                    "(D)Ljava/lang/Double;");
        }
        if ("char".equals(className)){
            return new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "java/lang/Character",
                    "valueOf",
                    "(C)Ljava/lang/Character;");
        }
        return null;
    }

    private boolean isWideLocalVariable (Type argType){
        String className = argType.getClassName();
        if ("long".equals(className) || "double".equals(className)){
            return true;
        }
        return false;
    }

    private boolean isPrimaryType(Type argType){
        String className = argType.getClassName();
        if ("byte".equals(className) || "short".equals(className) || "int".equals(className) || "long".equals(className)
                || "float".equals(className) || "double".equals(className) || "char".equals(className) || "boolean".equals(className)
                || "void".equals(className)){
            return true;
        }
        return false;
    }

    private AbstractInsnNode loadLocalVar (Type argumentType, int index){
        String className = argumentType.getClassName();
        if ("byte".equals(className)
                || "short".equals(className)
                || "int".equals(className)
                || "char".equals(className)
                || "boolean".equals(className)
                ){
            return new VarInsnNode(Opcodes.ILOAD, index);
        }
        if ("long".equals(className)){
            return new VarInsnNode(Opcodes.LLOAD, index);
        }
        if ("float".equals(className)){
            return new VarInsnNode(Opcodes.FLOAD, index);
        }
        if ("double".equals(className)){
            return new VarInsnNode(Opcodes.DLOAD, index);
        }
        return new VarInsnNode(Opcodes.ALOAD, index);
    }

    private MethodInsnNode nodeFor_fixedReturn (){
        try{
            Class patchManagerClass = PatchManager.class;
            Method fixedReturnMethod = patchManagerClass.getDeclaredMethod("fixedReturn", MethodIdentifier.class, RunParams.class);
            return new MethodInsnNode(Opcodes.INVOKESTATIC,
                    Type.getInternalName(patchManagerClass),
                    fixedReturnMethod.getName(),
                    Type.getMethodDescriptor(fixedReturnMethod));
        }catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }

    private MethodInsnNode nodeFor_isPatched (){
        try{
            Class patchManagerClass = PatchManager.class;
            Method isPatchedMethod = patchManagerClass.getDeclaredMethod("isPatched", MethodIdentifier.class);

            return new MethodInsnNode(Opcodes.INVOKESTATIC,
                    Type.getInternalName(PatchManager.class),
                    isPatchedMethod.getName(),
                    Type.getMethodDescriptor(isPatchedMethod));
        }catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }


    private AbstractInsnNode classInfoFromArgumentType (Type argumentType){
        String className = argumentType.getClassName();
        if ("boolean".equals(className)){
            return new FieldInsnNode(Opcodes.GETSTATIC,
                    "java/lang/Boolean",
                    "TYPE",
                    Type.getDescriptor(Class.class));
        }
        if ("byte".equals(className)){
            return new FieldInsnNode(Opcodes.GETSTATIC,
                    "java/lang/Byte",
                    "TYPE",
                    Type.getDescriptor(Class.class));
        }
        if ("short".equals(className)){
            return new FieldInsnNode(Opcodes.GETSTATIC,
                    "java/lang/Short",
                    "TYPE",
                    Type.getDescriptor(Class.class));
        }
        if ("int".equals(className)){
            return new FieldInsnNode(Opcodes.GETSTATIC,
                    "java/lang/Integer",
                    "TYPE",
                    Type.getDescriptor(Class.class));
        }
        if ("long".equals(className)){
            return new FieldInsnNode(Opcodes.GETSTATIC,
                    "java/lang/Long",
                    "TYPE",
                    Type.getDescriptor(Class.class));
        }
        if ("float".equals(className)){
            return new FieldInsnNode(Opcodes.GETSTATIC,
                    "java/lang/Float",
                    "TYPE",
                    Type.getDescriptor(Class.class));
        }
        if ("double".equals(className)){
            return new FieldInsnNode(Opcodes.GETSTATIC,
                    "java/lang/Double",
                    "TYPE",
                    Type.getDescriptor(Class.class));
        }
        if ("char".equals(className)){
            return new FieldInsnNode(Opcodes.GETSTATIC,
                    "java/lang/Character",
                    "TYPE",
                    Type.getDescriptor(Class.class));
        }
        return new LdcInsnNode(argumentType);
    }


    private void zipFile (byte[] classBytesArray, ZipOutputStream zos, String entryName){
        try{
            ZipEntry entry = new ZipEntry(entryName);
            zos.putNextEntry(entry);
            zos.write(classBytesArray, 0, classBytesArray.length);
            zos.closeEntry();;
            zos.flush();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

}
