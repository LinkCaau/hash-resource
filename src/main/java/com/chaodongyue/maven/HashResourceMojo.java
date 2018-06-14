package com.chaodongyue.maven;


import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.DirectoryScanner;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.Adler32;
import java.util.zip.CRC32;
import java.util.zip.Checksum;


@Mojo(name = "hash", defaultPhase = LifecyclePhase.PREPARE_PACKAGE)
public class HashResourceMojo extends AbstractMojo {


    @Parameter(defaultValue = "${basedir}/src/main/webapp")
    private File webappDirectory;//webapp文件的路径

    @Parameter(defaultValue = "${project.build.directory}/prepareWarSource")
    private File prepareDirectory;//预备文件的路径

    @Parameter(defaultValue = "<(link|script).*(src|href).*?>")
    private String tagPattern;//匹配文件中js和css标签的正则表达式

    @Parameter
    private String[] excludeResource;//排除处理资源(如:css或js)

    @Parameter
    private String[] includeResource;//包含处理资源

    @Parameter
    private String[] includesPage;//包含处理的页面

    @Parameter
    private String[] excludesPage;//排除处理的页面

    @Parameter(defaultValue = "UTF-8")
    private String encode;

    @Parameter(defaultValue = "CRC32")
    private String algorithm;


    @Override
    public void execute() {
        copyWebapp();
        fileProcessor();
    }


    private void copyWebapp() {
        try {
            FileUtils.copyDirectory(getWebappDirectory(), getPrepareDirectory());
        } catch (IOException e) {
            getLog().error(e);
            throw new RuntimeException(e);
        }

    }

    private void fileProcessor() {
        Map<String, String> resourceMap = hashAndRenameFile();
        Pattern pattern = Pattern.compile(tagPattern);
        List<File> files = getFiles(getPrepareDirectory(), getIncludesPage(), getExcludesPage());
        int processFileCount = files.parallelStream().mapToInt(file -> {
            int count = 0;
            try {
                String source = FileUtils.readFileToString(file, encode);
                StringBuilder sb = new StringBuilder();
                while (true) {
                    Matcher matcher = pattern.matcher(source);
                    if (matcher.find()) {
                        int start = matcher.start();
                        int end = matcher.end();
                        String tag = source.substring(start, end);
                        String uri = getUri(tag);

                        String hashUri = resourceMap.get(convertRelativePath(file, uri));
                        if (hashUri != null) {
                            hashUri = uri.substring(0, uri.lastIndexOf("/") + 1) + hashUri;
                            int uriStartIndex = source.indexOf(uri);
                            sb.append(source, 0, uriStartIndex).append(hashUri).append(source, uriStartIndex + uri.length(), end);
                        } else {
                            sb.append(source, 0, end);
                        }
                        count++;
                        source = source.substring(end);
                    } else {
                        break;
                    }
                }

                if (count > 0) {
                    sb.append(source);
                    FileUtils.write(file, sb.toString(), encode);
                    getLog().debug(convertToWebPath(file) + " change " + count + " uri");
                }
            } catch (IOException e) {
                getLog().error(e);
                throw new RuntimeException(e);
            }
            return count > 0 ? 1 : 0;
        }).sum();
        getLog().info("Hash " + resourceMap.keySet().size() + " files");
        getLog().info("Process " + processFileCount + " files");
    }

    private String getUri(String tag) {
        String attribute = tag.startsWith("<link") ? "href" : "src";
        int startUirIndex = tag.indexOf(attribute) + attribute.length();
        startUirIndex = tag.indexOf("\"", startUirIndex) + 1;
        int endUirIndex = tag.indexOf("\"", startUirIndex);
        String uri = tag.substring(startUirIndex, endUirIndex).trim();

        //删除${context}EL表达式的上下文
        if (uri.startsWith("${")) {
            uri = uri.substring(uri.indexOf("}") + 1);
        }
        return uri;
    }

    private String convertRelativePath(File file, String path) throws IOException {
        String result = path;
        if (!path.startsWith("/") && !path.startsWith("http")) {
            try {
                File resourcePath = new File(file.getParent(), path);
                result = resourcePath.getCanonicalPath().replace(getPrepareDirectory().getPath(), "").replace("\\", "/");
            } catch (IOException e) {
                getLog().info(file.getName() + "," + path);
                throw e;
            }
        }
        return result;
    }


    /**
     * 将源文件转换成 hash 了的文件
     * key: 原uri,
     * value: hash 文件名称
     */
    private Map<String, String> hashAndRenameFile() {
        List<File> files = getFiles(getPrepareDirectory(), getIncludes(), getExcludeResource());
        Map<String, String> result = files.parallelStream().map(file -> {
            String[] str = new String[2];
            try (InputStream in = new FileInputStream(file)) {
                byte[] bytes = IOUtils.toByteArray(in);
                Checksum checksum = getChecksum();
                checksum.update(bytes, 0, bytes.length);
                String hexCode = Long.toHexString(checksum.getValue());
                String fileName = file.getName();
                int suffixIndex = fileName.lastIndexOf(".");
                String hashFileName = fileName.substring(0, suffixIndex) + "_" + hexCode + fileName.substring(suffixIndex);

                File outFile = new File(file.getParent(), hashFileName);
                FileUtils.copyFile(file, outFile);

                String srcPath = convertToWebPath(file);
                str[0] = srcPath;
                str[1] = hashFileName;
            } catch (IOException e) {
                getLog().error(e);
                throw new RuntimeException(e);
            }
            return str;
        }).collect(Collectors.toMap(obj -> obj[0], obj -> obj[1]));
        for (File file : files) {
            FileUtils.deleteQuietly(file);
        }
        return result;
    }

    private List<File> getFiles(File parent, String[] in, String[] ex) {
        DirectoryScanner scanner = new DirectoryScanner();
        scanner.setBasedir(parent);
        scanner.setIncludes(in);
        scanner.setExcludes(ex);
        scanner.scan();
        return Arrays.stream(scanner.getIncludedFiles()).parallel().map(fileName -> new File(parent, fileName)).collect(Collectors.toList());
    }

    /**
     * 本地目录转换成web url
     */
    private String convertToWebPath(File file) throws IOException {
        return file.getCanonicalPath().replace(prepareDirectory.getPath(), "").replace("\\", "/");
    }

    public File getWebappDirectory() {
        return webappDirectory;
    }

    public File getPrepareDirectory() {
        return prepareDirectory;
    }

    public String getTagPattern() {
        return tagPattern;
    }

    public String[] getExcludeResource() {
        return excludeResource;
    }

    public String[] getExcludesPage() {
        return excludesPage;
    }

    private String[] getIncludes() {
        if (includeResource == null || includeResource.length < 1) {
            return new String[]{"**/*.js", "**/*.css"};
        }
        return includeResource;
    }

    private String[] getIncludesPage() {
        if (includesPage == null || includesPage.length < 1) {
            return new String[]{"**/*.jsp", "**/*.html"};
        }
        return includesPage;
    }

    private Checksum getChecksum() {
        Checksum checksum;
        if (algorithm.equalsIgnoreCase("Adler32")) {
            checksum = new Adler32();
        } else if (algorithm.equalsIgnoreCase("CRC32")) {
            checksum = new CRC32();
        } else {
            throw new IllegalArgumentException("don't has algorithm :  " + algorithm);
        }
        return checksum;
    }
}
