import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map.Entry;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.DeleteMessageRequest;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;

public class Ec2App {
	// Retrieve message from 756project queue. Process message (input). Send 
	// processed data to outbox queue.
	// APPLICATION FOR THE EC2 INSTANCES

    public static void main(String[] args) throws Exception {

        /*
         * The ProfileCredentialsProvider will return your [default]
         * credential profile by reading from the credentials file located at
         * (/Users/zach/.aws/credentials).
         */
        AWSCredentials credentials = null;
        try {
            credentials = new ProfileCredentialsProvider("default").getCredentials();
        } catch (Exception e) {
            throw new AmazonClientException(
                    "Cannot load the credentials from the credential profiles file. " +
                    "Please make sure that your credentials file is at the correct " +
                    "location (/Users/zach/.aws/credentials), and is in valid format.",
                    e);
        }

        AmazonSQS sqs = new AmazonSQSClient(credentials);
        AmazonS3 s3 = new AmazonS3Client(credentials);
        Region usWest2 = Region.getRegion(Regions.US_WEST_2);
        sqs.setRegion(usWest2);

        System.out.println("===========================================");
        System.out.println("Processing and Sorting the Integers");
        System.out.println("===========================================\n");

        try {
            // specifying the queue
            String inboxQueue = "https://sqs.us-west-2.amazonaws.com/550715799126/756project";
            String outboxQueue = "https://sqs.us-west-2.amazonaws.com/550715799126/756Outbox";
            
            // List queues
            System.out.println("Listing all queues in your account.\n");
            for (String queueUrl : sqs.listQueues().getQueueUrls()) {
                System.out.println("  QueueUrl: " + queueUrl);
            }
            System.out.println("\n");

            // Receive messages
            System.out.println("Receiving messages from MyQueue.\n");
            
            
			while (true){
            ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest(inboxQueue);
            
            List<Message> messages = sqs.receiveMessage(receiveMessageRequest).getMessages();
            
            
            for (Message message : messages) {
                System.out.println("  Message");
                System.out.println("    MessageId:     " + message.getMessageId());
                System.out.println("    ReceiptHandle: " + message.getReceiptHandle());
                System.out.println("    MD5OfBody:     " + message.getMD5OfBody());
                System.out.println("    Body:          " + message.getBody());
                
                // beginning of my code to obtain the string, split into a list of integers
                String str = message.getBody();
                
                System.out.println("\n");
                System.out.println("Printing the original string");
                System.out.println(str);
                System.out.println("\n");
                
                // splitting the string
                System.out.println("Spliting the string into a list of ints");
                List<String> splitInt = Arrays.asList(str.split("\\s*,\\s*"));

                
                // converting list to integers
                System.out.println("Converting to a list of integers");
                List<Integer> intList = new ArrayList<Integer>();
                for(String s : splitInt) intList.add(Integer.valueOf(s));
                
                // printing and sorting the integer list
                System.out.println("\n");
                System.out.println("Printing the processed integer list, line by line");
                Collections.sort(intList);
                for (Integer s : intList) System.out.println(s);
                
                // returning list to string
                String listString = intList.toString();
                listString = listString.substring(1, listString.length()-1);
                System.out.println(listString);
                  
                
                // Sending to outbox queue
                System.out.println("\n");        
                System.out.println("Sending results to outbox queue.");               
                sqs.sendMessage(new SendMessageRequest(outboxQueue, listString));
                
                
                // waiting to receive the msg from client with messageId in the body
                ReceiveMessageRequest messageRep = new ReceiveMessageRequest(inboxQueue);                
                List<Message> messages2 = sqs.receiveMessage(messageRep).getMessages();
                for (Message message2 : messages2) {
                    System.out.println("    MessageId:     " + message2.getBody());
                String repMsgId = message2.getBody();
                String messageRecieptHandle = messages2.get(0).getReceiptHandle();
                sqs.deleteMessage(new DeleteMessageRequest(inboxQueue, messageRecieptHandle));
                
                // adding S3 record of transaction
                    System.out.println("Uploading log file to S3 bucket. \n");
                    s3.putObject(new PutObjectRequest(
                    		"756project", 
                    		getDateString(), 
                    		createS3File(message.getMessageId(), str, repMsgId, listString)));
                }
                

                // Deleting first message from inbox queue
                System.out.println("Deleting the message from the inbox queue.\n");
                String messageRecieptHandle = messages.get(0).getReceiptHandle();
                sqs.deleteMessage(new DeleteMessageRequest(inboxQueue, messageRecieptHandle));
                
                System.out.println("Done processing\n");                
                
                for (Entry<String, String> entry : message.getAttributes().entrySet()) {
                    System.out.println("  Attribute");
                    System.out.println("    Name:  " + entry.getKey());
                    System.out.println("    Value: " + entry.getValue());
                    
                }
                
            // System.out.println();
            // Send a message
            // System.out.println("Sending a message to MyQueue.\n");
            // sqs.sendMessage(new SendMessageRequest(myQueueUrl, "This is my message text."));
            }}}
            



            // Delete a queue
          //  System.out.println("Deleting the test queue.\n");
          //  sqs.deleteQueue(new DeleteQueueRequest(myQueueUrl));
         catch (AmazonServiceException ase) {
            System.out.println("Caught an AmazonServiceException, which means your request made it " +
                    "to Amazon SQS, but was rejected with an error response for some reason.");
            System.out.println("Error Message:    " + ase.getMessage());
            System.out.println("HTTP Status Code: " + ase.getStatusCode());
            System.out.println("AWS Error Code:   " + ase.getErrorCode());
            System.out.println("Error Type:       " + ase.getErrorType());
            System.out.println("Request ID:       " + ase.getRequestId());
        } catch (AmazonClientException ace) {
            System.out.println("Caught an AmazonClientException, which means the client encountered " +
                    "a serious internal problem while trying to communicate with SQS, such as not " +
                    "being able to access the network.");
            System.out.println("Error Message: " + ace.getMessage());
        }
    }
    
    private static File createS3File(
    		String reqMsgId,
    		String reqMsgbdy,
    		String repMsgId,
    		String repMsgBdy
    		) throws IOException {
        File file = File.createTempFile("aws-java-sdk-", ".txt");
        file.deleteOnExit();
       
        Writer writer = new OutputStreamWriter(new FileOutputStream(file));
        writer.write("Request Message ID: "+ reqMsgId +"\n");
        writer.write(reqMsgbdy + "\n");
        writer.write("Reply Message ID: "+ repMsgId +"\n");
        writer.write(repMsgBdy + "\n");
        writer.write(InetAddress.getLocalHost().getHostName() + "\n");
        writer.write(getDateString() + "\n");
        writer.close();

        return file;
        }
    private static String getDateString(){
        Date today = Calendar.getInstance().getTime();
        Format formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String dateString = formatter.format(today);
    	return dateString;}
}
