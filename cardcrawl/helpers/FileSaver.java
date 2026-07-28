package com.megacrit.cardcrawl.helpers;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.BlockingQueue;

public class FileSaver
        implements Runnable {
    private static final Logger logger = LogManager.getLogger(FileSaver.class.getName());
    private final BlockingQueue<File> queue;

    public FileSaver(BlockingQueue<File> q) {
        this.queue = q;
    }

    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                consume(this.queue.take());
            } catch (InterruptedException e) {
                logger.info("Save thread interrupted!");
                Thread.currentThread().interrupt();
            }
        }
        logger.info("Save thread will die now.");
    }

    private void consume(File file) {
        logger.debug("Dequeue: qsize=" + this.queue.size() + " file=" + file.getFilepath());
        file.save();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\helpers\FileSaver.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

