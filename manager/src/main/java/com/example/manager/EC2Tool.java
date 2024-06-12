package com.example.manager;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;

import java.util.List;

public class EC2Tool {
    private final String amiId;
    private final Ec2Client ec2;
    private static final software.amazon.awssdk.regions.Region r = Region.US_WEST_2;
    public EC2Tool(String amiId){
        this.amiId = amiId;
        this.ec2 = Ec2Client.builder().region(r).build();
    }

    public int ActualNumberOfWorkers(){
        int counter = 0;
        DescribeInstancesRequest request = DescribeInstancesRequest.builder()
                .build();
        List<Reservation> reservations = ec2.describeInstances(request).reservations();
        for (Reservation r : reservations) {
            for (Instance i : r.instances()) {
                if ((i.state().name().name().equals("RUNNING") || i.state().name().name().equals("PENDING")) && i.tags().get(0).value().equals("Worker")) counter++;
            }
        }
        return counter;
    }

    public Instance create() throws InterruptedException {
        IamInstanceProfileSpecification iamInstanceProfileSpecification = IamInstanceProfileSpecification.builder().name("LabInstanceProfile").build();
        RunInstancesRequest runRequest = RunInstancesRequest.builder()
                .instanceType(InstanceType.T2_MICRO)
                .imageId(amiId)
                .iamInstanceProfile(iamInstanceProfileSpecification)
                .maxCount(1)
                .minCount(1)
                .build();

        RunInstancesResponse response = ec2.runInstances(runRequest);

        String instanceId = response.instances().get(0).instanceId();

        Tag tag = Tag.builder()
                .key("Name")
                .value("Worker")
                .build();

        CreateTagsRequest tagRequest = CreateTagsRequest.builder()
                .resources(instanceId)
                .tags(tag)
                .build();

        boolean tagged = false;
        while(!tagged) {
            try {
                ec2.createTags(tagRequest);
                System.out.printf(
                        "Successfully started EC2 instance %s based on AMI %s",
                        instanceId, amiId);
                tagged = true;

            } catch (Ec2Exception e) {
                Thread.sleep(50);
                System.out.println("Failed to tag, trying again");
            }
        }
        System.out.println(" Done!");
        return response.instances().get(0);
    }

    private void terminateInstance(Instance instance){
        TerminateInstancesRequest terminateInstancesRequest = TerminateInstancesRequest.builder().instanceIds(instance.instanceId()).build();
        ec2.terminateInstances(terminateInstancesRequest);
    }

    public void terminateWorkers(){
        DescribeInstancesRequest request = DescribeInstancesRequest.builder()
                .build();
        List<Reservation> reservations = ec2.describeInstances(request).reservations();
        for (Reservation r : reservations) {
            for (Instance i : r.instances()) {
                if ((i.state().name().name().equals("PENDING") || i.state().name().name().equals("RUNNING")) && i.tags().get(0).value().equals("Worker"))
                    terminateInstance(i);
            }
        }
    }

    public void terminateManager(){
        DescribeInstancesRequest request = DescribeInstancesRequest.builder()
                .build();
        List<Reservation> reservations = ec2.describeInstances(request).reservations();
        for (Reservation r : reservations) {
            for (Instance i : r.instances()) {
                if (i.state().name().name().equals("RUNNING") && i.tags().get(0).value().equals("Manager"))
                    terminateInstance(i);
            }
        }
    }
}