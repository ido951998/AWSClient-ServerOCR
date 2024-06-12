package com.example.worker;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.model.Message;

import java.util.List;

public class worker {
    private static final Region r = Region.US_WEST_2;
    private static final SQSTool sqsWorkersToManager = new SQSTool("workerstomanager", r, false);
    private static final SQSTool sqsManagerToWorkers = new SQSTool("managertoworkers", r, false);
    private static final WorkerOcr workerOcr = new WorkerOcr();

    private static void checkManagerToWorker() {
        List<Message> list = sqsManagerToWorkers.peekMessage();
        if (list.isEmpty()) return;
        System.out.println("Working on new message");

        String unparsed = list.get(0).body();
        String url = unparsed.split(",")[0];
        String projectName = unparsed.split(",")[1];
        String result, type = "";
        try {
            type = workerOcr.imageDownloader(url);
            result = workerOcr.doOcr(type);
        } catch (Exception e) {
            result = e.getMessage();
        }
        workerOcr.imageDelete(type);
        sqsWorkersToManager.createMessage(url + ",!," + result + ",!," + projectName, 0);
        sqsManagerToWorkers.deleteMessages(list);
    }

    public static void main(String[] args) {
        while (true){
            checkManagerToWorker();
            try{Thread.sleep(100);}
            catch (Exception e){}
        }
    }
}
