package pers.wengzc.poorfix;

import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ZipUtil {

    public static void zipFile (byte[] classBytesArray, ZipOutputStream zos, String entryName){
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
