package com.example.myapp;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.model.Message;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class local {

    private static final Region region = Region.US_WEST_2;
    private static final String bucket = "local" + System.currentTimeMillis();
    private static final String key = "urls" + System.currentTimeMillis();
    private static final S3Tool s3Tool = new S3Tool(bucket, region);
    private static final EC2Tool mngr = new EC2Tool("ami-0c24c9e65e82d1ca7", region);
    private static final SQSTool sqsManagerToLocal = new SQSTool("managerto" + bucket, region, true);

    private static void finishWork(String outputPath) throws IOException {
        BufferedReader s3File = s3Tool.download_file("finished"+bucket);
        File savedFile = new File(outputPath);
        savedFile.createNewFile();

        String line;
        while ((line = s3File.readLine()) != null){
            FileWriter myWriter = new FileWriter(outputPath, true);
            System.out.println(line);
            myWriter.write(line);
            myWriter.close();
        }
    }

    private static void cleanUp(){
        List<String> list = new ArrayList<>();
        list.add("finished"+bucket);
        list.add(key);
        s3Tool.cleanUp(list);
        sqsManagerToLocal.DeleteQueue();
    }

    public static void main(String[] args) throws IOException {
        String path = args[0];
        s3Tool.upload_file(path, key);

        boolean managerExists = mngr.checkForManager();
        final SQSTool sqsLocalsToManager = new SQSTool("localstomanager", region, !managerExists);
        final SQSTool sqsManagerToLocal = new SQSTool("managerto" + bucket, region, true);
        sqsLocalsToManager.createMessage(bucket + "," + key + "," + args[2] + ","+(args.length == 4 ? "True" : "False"), 0);

        if (!managerExists) mngr.create();

        while (true){
            List<Message> l = sqsManagerToLocal.pollMessage();
            if (!l.isEmpty()){
                finishWork(args[1]);
                cleanUp();
                break;
            }
            try{Thread.sleep(50);} catch (Exception e){}
        }
    }
}
