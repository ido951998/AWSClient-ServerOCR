package com.example.worker;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.util.List;

public class SQSTool {
    private final String QUEUE_NAME;
    private final SqsClient sqs;
    private String queueUrl;

    public SQSTool(String queueName, Region region, boolean create) {
        QUEUE_NAME = queueName;
        sqs = SqsClient.builder().region(region).build();
        setup(create);
    }

    public int getNumMessages() {
        GetQueueAttributesRequest request = GetQueueAttributesRequest.builder()
                .queueUrl(queueUrl)
                .attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES)
                .build();
        return Integer.parseInt(sqs.getQueueAttributes(request).attributes().get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES));
    }

    public void setup(boolean create){
        if (create) {
            try {
                CreateQueueRequest request = CreateQueueRequest.builder()
                        .queueName(QUEUE_NAME)
                        .build();
                sqs.createQueue(request);
            } catch (QueueNameExistsException e) {
                throw e;
            }
        }
        GetQueueUrlRequest getQueueRequest = GetQueueUrlRequest.builder()
                .queueName(QUEUE_NAME)
                .build();
        queueUrl = sqs.getQueueUrl(getQueueRequest).queueUrl();
    }

    public void createMessage(String message, int delay) {
        SendMessageRequest send_msg_request = SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(message)
                .delaySeconds(delay)
                .build();
        sqs.sendMessage(send_msg_request);
    }

    public List<Message> peekMessage() {
        // receive messages from the queue
        ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .build();
        List<Message> messages = sqs.receiveMessage(receiveRequest).messages();
        return messages;
    }

    public List<Message> pollMessage() {
    // receive messages from the queue
        ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                .maxNumberOfMessages(1)
                .queueUrl(queueUrl)
                .build();
        List<Message> messages = sqs.receiveMessage(receiveRequest).messages();

        // delete messages from the queue
        for (Message m : messages) {
            DeleteMessageRequest deleteRequest = DeleteMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .receiptHandle(m.receiptHandle())
                    .build();
            sqs.deleteMessage(deleteRequest);
        }
        return messages;
    }

    public void deleteMessages(List<Message> messages){
        for (Message m : messages) {
            DeleteMessageRequest deleteRequest = DeleteMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .receiptHandle(m.receiptHandle())
                    .build();
            sqs.deleteMessage(deleteRequest);
        }
    }
}