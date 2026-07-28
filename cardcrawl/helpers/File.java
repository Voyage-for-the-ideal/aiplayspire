package com.megacrit.cardcrawl.helpers;

import com.badlogic.gdx.Gdx;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.FileAttribute;
import java.util.Arrays;

public class File {
    private static final Logger logger = LogManager.getLogger(File.class.getName());
    private String filepath;
    private byte[] data;

    public File(String filepath, String data) {
        this.filepath = filepath;
        this.data = data.getBytes(StandardCharsets.UTF_8);
    }

    public String getFilepath() {
        return this.filepath;
    }

    public void save() {
        int MAX_RETRIES = 5;
        String localStoragePath = Gdx.files.getLocalStoragePath();
        Path destination = FileSystems.getDefault().getPath(localStoragePath + this.filepath, new String[0]);
        Path backup = FileSystems.getDefault().getPath(localStoragePath + this.filepath + ".backUp", new String[0]);
        Path parent = destination.getParent();
        logger.debug("Attempting to save file=" + destination);

        if (Files.exists(parent, new java.nio.file.LinkOption[0])) {
            if (Files.exists(destination, new java.nio.file.LinkOption[0])) {

                copyAndValidate(destination, backup, 5);

                deleteFile(destination);
            }
        } else {
            try {
                Files.createDirectories(parent, (FileAttribute<?>[]) new FileAttribute[0]);
            } catch (IOException e) {
                logger.info("Failed to create directory", e);
            }
        }

        boolean success = writeAndValidate(destination, this.data, 5);

        if (success) {

            logger.debug("Successfully saved file=" + destination.toString());
        }
    }

    private static void copyAndValidate(Path source, Path target, int retry) {
        byte[] sourceData = new byte[0];
        try {
            sourceData = Files.readAllBytes(source);
            Files.copy(source, target, new CopyOption[] {StandardCopyOption.REPLACE_EXISTING });
        } catch (IOException e) {
            if (retry <= 0) {
                logger.info("Failed to copy " + source
                        .toString() + " to " + target.toString() + ", but the retry expired", e);

                return;
            }
            logger.info("Failed to copy file=" + source.toString(), e);

            sleep(300);
            copyAndValidate(source, target, retry - 1);
        }
        Exception err = validateWrite(target, sourceData);
        if (err != null) {
            if (retry <= 0) {
                logger.info("Failed to copy " + source
                        .toString() + " to " + target.toString() + ", but the retry expired", err);

                return;
            }
            logger.info("Failed to copy file=" + source.toString(), err);

            sleep(300);
            copyAndValidate(source, target, retry - 1);
        }
    }

    private static void sleep(int milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            logger.info(e);
        }
    }

    private static void deleteFile(Path filepath) {
        try {
            Files.delete(filepath);
        } catch (IOException e) {
            logger.info("Failed to delete", e);
        }
    }

    private static Exception validateWrite(Path filepath, byte[] inMemoryBytes) {
        byte[] writtenBytes;
        try {
            writtenBytes = Files.readAllBytes(filepath);
        } catch (IOException e) {
            return e;
        }
        boolean valid = Arrays.equals(writtenBytes, inMemoryBytes);
        if (!valid) {
            return new FileWriteValidationError("Not valid: written=" +
                    Arrays.toString(writtenBytes) + " vs inMemory=" + Arrays.toString(inMemoryBytes));
        }

        return null;
    }

    static boolean writeAndValidate(Path filepath, byte[] data, int retry) {
        try {
            Files.write(filepath, data,
                    new OpenOption[] {StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.SYNC });

        } catch (Exception ex) {
            if (retry <= 0) {
                logger.info("Failed to write file " + filepath.toString() + ", but the retry expired.", ex);
                return false;
            }
            logger.info("Failed to validate source=" + filepath.toString() + ", retrying...", ex);

            sleep(300);
            return writeAndValidate(filepath, data, retry - 1);
        }
        Exception err = validateWrite(filepath, data);
        if (err != null) {
            if (retry <= 0) {
                logger.info("Failed to write file " + filepath.toString() + ", but the retry expired.", err);
                return false;
            }
            logger.info("Failed to validate source=" + filepath.toString() + ", retrying...", err);

            sleep(300);
            return writeAndValidate(filepath, data, retry - 1);
        }
        return true;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\helpers\File.class
 * Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

