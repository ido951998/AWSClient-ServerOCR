package com.example.myapp;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.List;

public class S3Tool {
    private final String bucket;
    private final S3Client s3Client;
    private final Region region;

    public S3Tool(String bucket, Region region){
        this.bucket = bucket;
        this.region = region;
        this.s3Client = S3Client.builder().region(region).build();
        CreateBucketRequest createBucketRequest = CreateBucketRequest.builder().bucket(bucket).createBucketConfiguration(CreateBucketConfiguration.builder().locationConstraint(region.id()).build()).build();
        s3Client.createBucket(createBucketRequest);
    }
    public void upload_file(String path, String key){
        System.out.println("Uploading object...");

        s3Client.putObject(PutObjectRequest.builder().bucket(bucket).key(key)
                .build(), RequestBody.fromFile(new File(path)));

        System.out.println("Upload complete");
        System.out.printf("%n");
    }

    public void cleanUp(List<String> keys) {
        System.out.println("Cleaning up...");
        try {
            for (String keyName:keys) {
                System.out.println("Deleting object: " + keyName);
                DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder().bucket(bucket).key(keyName).build();
                s3Client.deleteObject(deleteObjectRequest);
                System.out.println(keyName + " has been deleted.");
            }
            System.out.println("Deleting bucket: " + bucket);
            DeleteBucketRequest deleteBucketRequest = DeleteBucketRequest.builder().bucket(bucket).build();
            s3Client.deleteBucket(deleteBucketRequest);
            System.out.println(bucket +" has been deleted.");
            System.out.printf("%n");
        } catch (S3Exception e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }
        System.out.println("Cleanup complete");
        System.out.printf("%n");
        s3Client.close();
    }

    public BufferedReader download_file(String key){
        GetObjectRequest getObjectRequest = GetObjectRequest.builder().bucket(bucket).key(key).build();
        BufferedReader reader = new BufferedReader(new InputStreamReader(s3Client.getObject(getObjectRequest)));
        return reader;
    }
}
