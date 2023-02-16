package com.onevizion.maven.plugin.dbschema;

import com.onevizion.maven.plugin.dbschema.vo.CopyFilesConfig;
import com.onevizion.maven.plugin.dbschema.vo.ReformatFilesConfig;
import com.onevizion.maven.plugin.dbschema.vo.DeleteFilesConfig;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOCase;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.apache.commons.lang3.StringEscapeUtils;

import java.io.*;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class SiteUtils {

    public static final String SITE_TEMPLATE_DIR = "site-template/";

    public static void reformatFiles(ReformatFilesConfig config) throws Exception {
        File dir = new File(config.getInputDirectory());

        for (String include : config.getIncludes()) {
            include = (include == null || include.isEmpty()) ? "*" : include;
            String regex = StringEscapeUtils.unescapeHtml4(config.getRegexp());
            regex = StringEscapeUtils.escapeJava(regex);

            String replacement = config.getReplacement();
            if (replacement == null || replacement.isEmpty()) {
                replacement = "";
            } else {
                replacement = StringEscapeUtils.unescapeHtml4(config.getReplacement());
            }

            FileFilter filter = new WildcardFileFilter(include, IOCase.INSENSITIVE);
            File[] filteredFiles = dir.listFiles(filter);
            if (filteredFiles == null || filteredFiles.length == 0) {
                throw new Exception(include + " files not found in " + config.getInputDirectory());
            } else {
                for (File file : filteredFiles) {
                    String content = FileUtils.readFileToString(file);
                    Pattern pattern = Pattern.compile(regex, Pattern.DOTALL);
                    content = pattern.matcher(content).replaceAll(replacement);
                    FileUtils.writeStringToFile(file, content);
                }
            }
        }
    }

    public static void copyFiles(CopyFilesConfig config) throws Exception {
        File srcDir = new File(config.getSrcDir());
        File destDir = new File(config.getDestDir());
        for (String include : config.getIncludes()) {
            include = (include == null || include.isEmpty()) ? "*" : include;
            FileFilter filter = new WildcardFileFilter(include, IOCase.INSENSITIVE);
            File[] filteredFiles = srcDir.listFiles(filter);
            if (filteredFiles == null || filteredFiles.length == 0) {
                throw new Exception(include + " files not found in " + config.getSrcDir());
            } else {
                for (File file : filteredFiles) {
                    FileUtils.copyFileToDirectory(file, destDir);
                }
            }
        }
    }

    public static void deleteFiles(DeleteFilesConfig config) throws Exception {
        File srcDir = new File(config.getInputDirectory());
        if (config.getIncludes() == null) {
            FileUtils.deleteDirectory(srcDir);
            return;
        }
        for (String include : config.getIncludes()) {
            include = (include == null || include.isEmpty()) ? "*" : include;
            FileFilter filter = new WildcardFileFilter(include, IOCase.INSENSITIVE);
            File[] filteredFiles = srcDir.listFiles(filter);
            if (filteredFiles == null || filteredFiles.length == 0) {
                throw new Exception(include + " files not found in " + config.getInputDirectory());
            } else {
                for (File file : filteredFiles) {
                    FileUtils.forceDeleteOnExit(file);
                }
            }
        }
    }

    public static void unZipSiteTemplate(InputStream stream, File destDir) throws IOException {
        byte[] buffer = new byte[1024];
        if (!destDir.exists()) {
            destDir.mkdir();
        }

        boolean isExist = false;
        ZipInputStream zis = new ZipInputStream(stream);
        ZipEntry ze;
        while ((ze = zis.getNextEntry()) != null) {

            String fileName = ze.getName();
            if (!fileName.matches(SITE_TEMPLATE_DIR + ".+")) {
                continue;
            }
            isExist = true;
            fileName = fileName.replaceFirst(SITE_TEMPLATE_DIR, "");
            File newFile = new File(destDir + File.separator + fileName);

            new File(newFile.getParent()).mkdirs();
            if (!ze.isDirectory()) {
                FileOutputStream fos = new FileOutputStream(newFile);
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    fos.write(buffer, 0, len);
                }
                fos.close();
            }
        }

        zis.closeEntry();
        zis.close();

        if (!isExist) {
            throw new IOException(SITE_TEMPLATE_DIR + " doesn't exist in the jar file");
        }
    }
}