package com.chaodongyue.maven;


import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.DirectoryScanner;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.Adler32;
import java.util.zip.CRC32;
import java.util.zip.Checksum;


@Mojo(name = "hash", defaultPhase = LifecyclePhase.VALIDATE)
public class HashResourceMojo extends AbstractMojo {

	@Parameter(defaultValue = "${project.build.directory}/prepareWarSource")
	private File warSourceDirectory;

	@Parameter
	private String[] excludes;

	@Parameter
	private String[] includes;

	@Parameter
	private String[] includesHtml;

	@Parameter
	private String[] excludesHtml;

	@Parameter(defaultValue = "UTF-8")
	private String encode;

	@Parameter(defaultValue = "CRC32")
	private String algorithm;

	public void execute() throws MojoExecutionException {
		Map<String, String> resourceMap = checksumResource();
		Pattern pattern = Pattern.compile("<(link|script)\\b[^<]*(src|href)=[\"'](?<uri>[\\w-\\./]+?)[\"'][^<]*>");
		List<File> files = getFiles(warSourceDirectory, getIncludesHtml(), excludesHtml);
		int processFileCount =files.parallelStream().mapToInt(file->{
			int count = 0;
			try {
				String source = FileUtils.readFileToString(file, encode);
				StringBuilder sb = new StringBuilder();
				while (true) {
					Matcher matcher = pattern.matcher(source);
					if (matcher.find()) {
						String uri = matcher.group("uri");
						File uriFile = new File(warSourceDirectory, uri);
						String absPath = convertToWebPath(uriFile);
						if (resourceMap.containsKey(absPath)) {
							String hashFile = resourceMap.get(absPath);
							int index = absPath.lastIndexOf("/");
							sb.append(source.substring(0, matcher.start("uri"))).append(absPath.substring(0, index + 1)).append(hashFile);
							count++;
						} else {
							sb.append(source.substring(0, matcher.end("uri")));
						}
						source = source.substring(matcher.end("uri"));
					} else {
						break;
					}
				}
				sb.append(source);
				if (count > 0) {
					FileUtils.write(file, sb.toString(), encode);
					getLog().info(convertToWebPath(file) + " change " + count + " uri");

				}
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			return count > 0 ? 1 : 0;
		}).sum();
		getLog().info("Hash " + resourceMap.keySet().size() + " files");
		getLog().info("Process " + processFileCount + " files");
	}

	private Map<String, String> checksumResource() {
		List<File> files = getFiles(warSourceDirectory, getIncludes(), excludes);
		return files.parallelStream().map(file -> {
			String[] str = new String[2];
			FileReader in = null;
			try {
				in = new FileReader(file);
				byte[] bytes = IOUtils.toByteArray(in);
				IOUtils.closeQuietly(in);
				Checksum checksum = getChecksum();
				checksum.update(bytes, 0, bytes.length);
				String hexCode = Long.toHexString(checksum.getValue());
				String fileName = file.getName();
				int suffixIndex = fileName.lastIndexOf(".");
				String hashFileName = fileName.substring(0, suffixIndex) + "_" + hexCode + fileName.substring(suffixIndex);

				File outFile = new File(file.getParent(), hashFileName);
				if (file.renameTo(outFile)) {
					String srcPath = convertToWebPath(file);
					str[0] = srcPath;
					str[1] = hashFileName;
				} else {
					throw new RuntimeException("File rename failed");
				}
			} catch (IOException e) {
				throw new RuntimeException(e);
			} finally {
				IOUtils.closeQuietly(in);
			}
			return str;
		}).collect(Collectors.toMap(obj -> obj[0], obj -> obj[1]));
	}

	private List<File> getFiles(File parent, String[] in, String[] ex) {
		DirectoryScanner scanner = new DirectoryScanner();
		scanner.setBasedir(parent);
		scanner.setIncludes(in);
		scanner.setExcludes(ex);
		scanner.scan();
		return Arrays.stream(scanner.getIncludedFiles()).parallel().map(fileName -> new File(parent, fileName)).collect(Collectors.toList());
	}

	private String convertToWebPath(File file) throws IOException {
		return file.getCanonicalPath().replace(warSourceDirectory.getPath(), "").replace("\\", "/");
	}

	public String[] getIncludes() {
		if (includes == null || includes.length < 1) {
			return new String[]{"**/*.js", "**/*.css"};
		}
		return includes;
	}

	public String[] getIncludesHtml() {
		if (includesHtml == null || includesHtml.length < 1) {
			return new String[]{"**/*.jsp", "**/*.html"};
		}
		return includesHtml;
	}

	private Checksum getChecksum() {
		Checksum checksum;
		if (algorithm.equalsIgnoreCase("Adler32")) {
			checksum = new Adler32();
		} else if (algorithm.equalsIgnoreCase("CRC32")) {
			checksum = new CRC32();
		} else {
			throw new IllegalArgumentException("Algorithm value is " + algorithm);
		}
		return checksum;
	}
}
