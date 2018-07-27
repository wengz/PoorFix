package pers.wengzc.poorfixtool;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.lang.reflect.Method;

import pers.wengzc.poorfixkit.MethodIdentifier;
import pers.wengzc.poorfixkit.PatchManager;
import pers.wengzc.poorfixkit.RunParams;

public class MainActivity extends AppCompatActivity {

    Button mBtnTriger;
    Button mBtnTrigerStatic;
    Button mBtnLoadPatch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        PermissionUtil.checkPermission(this);
        initView();
    }

    private void initView (){
        mBtnTriger = findViewById(R.id.btn_triger);
        mBtnLoadPatch = findViewById(R.id.btn_load_patch);
        mBtnTrigerStatic = findViewById(R.id.btn_triger_static);

        mBtnTriger.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                someFun();
            }
        });
        mBtnLoadPatch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadPatch();
            }
        });
        mBtnTrigerStatic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                useStaticMethod();
            }
        });
    }


    private void someFun (){
        Toast.makeText(this, "旧的方法", Toast.LENGTH_SHORT).show();
    }

    private void loadPatch (){
        PatchManager.loadPatch();
    }

    private void useStaticMethod (){
        Toast.makeText(this, helloString(), Toast.LENGTH_SHORT).show();
    }

    private static String helloString (){
        return "补丁前的静态返回";
    }

    private String privateStrReturn (){
        return "private String 返回";
    }

    public String publicStrReturn (){
        return "public String 返回";
    }

}
