package com.example.myapp;

import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;
import software.amazon.awssdk.regions.Region;

import java.util.Base64;
import java.util.Objects;

/**
 * Creates an EC2 instance
 */
public class EC2Tool {
    private final String amiId;
    private final Ec2Client ec2;

    public EC2Tool(String amiId, Region region){
        this.ec2 = Ec2Client.builder().region(region).build();
        this.amiId = amiId;
    }

    public boolean checkForManager(){
        DescribeInstancesRequest request = DescribeInstancesRequest.builder()
                .build();
        for (Reservation r : ec2.describeInstances(request).reservations()) {
            for (Instance i : r.instances()) {
                if ((i.state().name().name().equals("RUNNING") || i.state().name().name().equals("PENDING")) && Objects.equals(i.tags().get(0).value(), "Manager")) return true;
            }
        }
        return false;
    }

    public void create(){
        IamInstanceProfileSpecification iamInstanceProfileSpecification = IamInstanceProfileSpecification.builder().name("LabInstanceProfile").build();
        RunInstancesRequest runRequest = RunInstancesRequest.builder()
                .instanceType(InstanceType.T2_MICRO)
                .imageId(amiId)
                .iamInstanceProfile(iamInstanceProfileSpecification)
                .maxCount(1)
                .minCount(1)
                .userData(Base64.getEncoder().encodeToString(("#!/bin/bash\ncd /home/ec2-user/manager\nmvn package\nmvn exec:java -Dexec.mainClass=\"com.example.manager.Manager\"").getBytes()))
                .build();

        RunInstancesResponse response = ec2.runInstances(runRequest);

        String instanceId = response.instances().get(0).instanceId();

        Tag tag = Tag.builder()
                .key("Name")
                .value("Manager")
                .build();

        CreateTagsRequest tagRequest = CreateTagsRequest.builder()
                .resources(instanceId)
                .tags(tag)
                .build();

        try {
            ec2.createTags(tagRequest);
            System.out.printf(
                    "Successfully started EC2 instance %s based on AMI %s",
                    instanceId, amiId);

        } catch (Ec2Exception e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
        System.out.println("Done!");
    }
}
