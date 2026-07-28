package com.megacrit.cardcrawl.helpers;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class AsyncSaver {
    private static final Logger logger = LogManager.getLogger(AsyncSaver.class.getName());

    private static Thread saveThread;

    private static final BlockingQueue<File> saveQueue = new LinkedBlockingQueue<>();

    public static void save(String filepath, String data) {
        boolean enableAsyncSave = true;
        if (enableAsyncSave) {
            logger.debug("Enqueue: qsize=" + saveQueue.size() + " file=" + filepath);
            saveQueue.add(new File(filepath, data));
            ensureSaveThread();
        } else {
            logger.info("Saving synchronously");
            File saveFile = new File(filepath, data);
            saveFile.save();
        }
    }

    private static void ensureSaveThread() {
        if (saveThread == null) {
            startSaveThread();
        } else if (!saveThread.isAlive()) {
            logger.info("Save thread is dead. Starting save thread!");
            startSaveThread();
        }
    }

    private static void startSaveThread() {
        saveThread = new Thread(new FileSaver(saveQueue));
        saveThread.setName("FileSaver");
        saveThread.start();
    }

    public static void shutdownSaveThread() {
        if (saveThread != null)
            saveThread.interrupt();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\helpers\AsyncSaver
 * .class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

