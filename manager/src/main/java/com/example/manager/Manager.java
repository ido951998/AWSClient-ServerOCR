package com.example.manager;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.model.Message;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static j2html.TagCreator.*;

public class Manager {
    //TODO: does everything sit in the same region?

    private static final int MAX_NUMBER_OF_INSTANCES = 15;
    private static final Region r = Region.US_WEST_2;
    private static final EC2Tool ec2Tool = new EC2Tool("ami-01c80d6a89543695b");
    private static final SQSTool sqsLocalsToManager = new SQSTool("localstomanager", r, false);
    private static final SQSTool sqsWorkersToManager = new SQSTool("workerstomanager", r, true);
    private static final SQSTool sqsManagerToWorkers = new SQSTool("managertoworkers", r, true);
    private static final Map<String, Integer> projectMap = new ConcurrentHashMap<>();
    private static final Map<String, Map<String, String>> ocrMap = new ConcurrentHashMap<>();
    private static boolean terminate = false;
    private static final List<String> bus = new Vector<>();
    private static int expectedNumberOfWorkers = 0;
    private static boolean work = true;


    private static class LocalToManager implements Runnable {
        @Override
        public void run() {
            while (!terminate){
                try {
                    Thread.sleep(50);
                    checkLocalToManager();
                } catch (Exception e) {}
            }
            System.out.println("Finish LocalToManager");
        }
    }

    private static class WorkerStarter implements Runnable {
        @Override
        public void run() {
            while (work) {
                startWorkers();
            }
            System.out.println("Finish WorkerStarter");
        }
    }

    private static class WorkerToManager implements Runnable {
        @Override
        public void run() {
            while (!terminate || !projectMap.isEmpty()) {
                try{
                    Thread.sleep(10);
                }
                catch (Exception ignored){}
                checkWorkerToManager();
            }
            System.out.println("Finish WorkerToManager");
        }
    }

    private static class FinishProject implements Runnable{
        @Override
        public void run() {
            while (!terminate || !ocrMap.isEmpty()) {
                if (bus.isEmpty()){
                    try{
                        Thread.sleep(50);
                    }
                    catch (Exception e){}
                }
                else finishProject(bus.remove(0));
            }
            System.out.println("Finish FinishProject");
        }
    }

    private static void cleanUp(){
        sqsLocalsToManager.DeleteQueue();
        sqsManagerToWorkers.DeleteQueue();
        sqsWorkersToManager.DeleteQueue();
        ec2Tool.terminateWorkers();
        ec2Tool.terminateManager();
    }

    private static void startWorkers(){
        int numberToStart = expectedNumberOfWorkers - ec2Tool.ActualNumberOfWorkers();
        for (; numberToStart > 0; numberToStart--){
            System.out.println("creating");
            System.out.println("numberToStart: " + numberToStart);
            try {
                ec2Tool.create();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        try {
            System.out.println("Going to sleep");
            Thread.sleep(5000);
            System.out.println("Woke up");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static void checkLocalToManager() throws IOException {
        List<Message> messageList = sqsLocalsToManager.pollMessage();
        if (messageList.isEmpty()) return;

        for (Message message : messageList) {
            String location = message.body();
            String[] location_parsed = location.split(",");
            S3Tool s3Tool = new S3Tool(location_parsed[0], r);
            BufferedReader urls = s3Tool.download_file(location_parsed[1]);
            int n = Integer.parseInt(location_parsed[2]);

            String line;
            int numMessages = 0;
            while ((line = urls.readLine()) != null) {
                if (line.isEmpty()) continue;
                numMessages++;
                sqsManagerToWorkers.createMessage(line + "," + location_parsed[0], 0);
            }
            projectMap.put(location_parsed[0], numMessages);
            ocrMap.put(location_parsed[0], new ConcurrentHashMap<>());
            int numInstancesRunning = ec2Tool.ActualNumberOfWorkers();

            int numInstancesToStart = numMessages / n;
            if (numInstancesToStart + numInstancesRunning > MAX_NUMBER_OF_INSTANCES) {
                expectedNumberOfWorkers = MAX_NUMBER_OF_INSTANCES;
            }
            else expectedNumberOfWorkers = numInstancesRunning + numInstancesToStart;
            System.out.println(expectedNumberOfWorkers);
            terminate = location_parsed[3].equalsIgnoreCase("true");
        }
    }

    private static void checkWorkerToManager(){
        List<Message> list = sqsWorkersToManager.pollMessage();
        for (Message m : list) {
            System.out.println(m.body());
            String[] parsedMessage = m.body().split(",!,");
            System.out.println("project name: " + parsedMessage[2]);
            ocrMap.get(parsedMessage[2]).put(parsedMessage[0], parsedMessage[1]);
            projectMap.put(parsedMessage[2], projectMap.get(parsedMessage[2]) - 1);
            if (projectMap.get(parsedMessage[2]) == 0) {
                projectMap.remove(parsedMessage[2]);
                bus.add(bus.size(), parsedMessage[2]);
            }
        }
    }

    private static void finishProject(String name) {
        Map<String, String> finishedMap = ocrMap.get(name);
        String body = body(
                p("finished OCR"),
                each(finishedMap.keySet(), url ->
                        div(img().withSrc(url),
                        h2(finishedMap.get(url)))
                )
        ).render();
        FileWriter myWriter = null;
        try {
            myWriter = new FileWriter("finished.html");
            myWriter.write(body);
            myWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        S3Tool s3Tool = new S3Tool(name, r);
        s3Tool.upload_file("finished.html","finished" + name);
        SQSTool sqsManagerToLocal = new SQSTool("managerto" + name, r, false);
        sqsManagerToLocal.createMessage("finished", 0);
        ocrMap.remove(name);
    }

    public static void main(String[] args) throws InterruptedException {
        LocalToManager localToManager = new LocalToManager();
        Thread threadLocalToManager = new Thread(localToManager);
        WorkerToManager workerToManager = new WorkerToManager();
        Thread threadWorkerToManager = new Thread(workerToManager);
        FinishProject finishProject = new FinishProject();
        Thread threadFinishProject = new Thread(finishProject);
        WorkerStarter workerStarter = new WorkerStarter();
        Thread threadWorkerStarter = new Thread(workerStarter);

        threadFinishProject.start();
        threadLocalToManager.start();
        threadWorkerToManager.start();
        threadWorkerStarter.start();

        threadFinishProject.join();
        work = false;
        threadWorkerStarter.join();

        cleanUp();
    }
}
