/**
 * Copyright 2016 Robin Steel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package squash.deployment.lambdas.utils;

import org.apache.commons.io.FilenameUtils;

import com.amazonaws.services.lambda.runtime.LambdaLogger;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPOutputStream;

/**
 * File utilities.
 * 
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public class FileUtils {

  /**
   * GZIPs files in-place.
   * 
   * <p>Replaces files with their gzip-ed version, without altering the filename. The files can
   *    be a mix of files and folders. Any folders will be recursed into, gzip-ing contained
   *    files in-place. A list of paths to exclude from gzip-ing should also be provided.
   * 
   *    @param pathsToZip the list of paths to gzip.
   *    @param pathsToExclude the list of paths to exclude.
   *    @param logger a CloudwatchLogs logger.
   * @throws IOException 
   */
  public static void gzip(List<File> pathsToZip, List<File> pathsToExclude, LambdaLogger logger)
      throws IOException {

    for (File pathToZip : pathsToZip) {
      logger.log("Replacing file with its gzip-ed version: " + pathToZip.getAbsolutePath());
      if (pathsToExclude.contains(pathToZip)) {
        continue;
      }
      if (pathToZip.isDirectory()) {
        logger.log("File is a directory - so recursing...");
        gzip(new ArrayList<File>(Arrays.asList(pathToZip.listFiles())), pathsToExclude, logger);
        continue;
      }

      // File is a file - so gzip it
      File tempZip = File.createTempFile("temp", ".gz", new File(pathToZip.getParent()));
      Files.deleteIfExists(tempZip.toPath());

      byte[] buffer = new byte[1024];
      try {
        try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(new FileOutputStream(tempZip))) {
          try (FileInputStream fileInputStream = new FileInputStream(pathToZip)) {
            int len;
            while ((len = fileInputStream.read(buffer)) > 0) {
              gzipOutputStream.write(buffer, 0, len);
            }
          }
          gzipOutputStream.finish();
        }
      } catch (IOException e) {
        logger.log("Caught exception whilst zipping file: " + e.getMessage());
        throw e;
      }

      // Replace original file with gzip-ed file, with same name
      Files.copy(tempZip.toPath(), pathToZip.toPath(), StandardCopyOption.REPLACE_EXISTING);
      Files.deleteIfExists(tempZip.toPath());
      logger.log("Replaced file with its gzip-ed version");
    }
  }

  /**
   * GZIPs data in memory.
   * 
   * <p>Returns gzip-ed version of an array.
   * 
   *    @param dataToZip the byte array to gzip.
   *    @param logger a CloudwatchLogs logger.
   * @throws Exception 
   */
  public static byte[] gzip(byte[] dataToZip, LambdaLogger logger) throws Exception {

    logger.log("About to zip byte[].");

    try {
      try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream(dataToZip.length)) {
        try (GZIPOutputStream zipStream = new GZIPOutputStream(byteStream)) {
          zipStream.write(dataToZip);
        }
        logger.log("Zipped byte[]");
        return byteStream.toByteArray();
      }
    } catch (Exception e) {
      logger.log("Caught exception whilst zipping byte[]: " + e.getMessage());
      throw e;
    }
  }

  /**
   * Adds revving suffix to all filenames beneath a folder.
   * 
   * <p>Adds revving suffix to all filenames beneath a folder, recursing into subfolders.
   *    Only js and css files are revved.
   * 
   *    @param suffix the suffix to add to all filenames.
   *    @param startFolder the folder at root of tree within which to suffix files
   *    @param logger a CloudwatchLogs logger.
   * @throws IOException
   */
  public static void appendRevvingSuffix(String suffix, Path startFolder, LambdaLogger logger)
      throws IOException {
    Files
        .walk(startFolder, FileVisitOption.FOLLOW_LINKS)
        .filter(Files::isRegularFile)
        .forEach(path -> {
          File file = path.toFile();
          if (file.isDirectory()) {
            return;
          }
          String absolutePath = file.getAbsolutePath();
          String fileExtension = FilenameUtils.getExtension(absolutePath);
          if (!fileExtension.equals("js") && !fileExtension.equals("css")) {
            // We rev only js and css
            return;
          }
          String suffixedName = FilenameUtils.getBaseName(absolutePath) + "_" + suffix + "."
              + fileExtension;
          File suffixedFile = new File(file.getParentFile(), suffixedName);
          file.renameTo(suffixedFile);
          logger.log("Appended suffix to file: " + absolutePath + ", giving: "
              + suffixedFile.getAbsolutePath());
        });
  }
}