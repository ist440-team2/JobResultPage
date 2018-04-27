package edu.psu.ist440.group2.jobresultformatter;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;

import j2html.tags.ContainerTag;

import static j2html.TagCreator.*;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Generates HTML containing formatted output of a decryption job including the uploaded image
 * and the output of the OCR, decryption, and translation modules.
 * 
 * @author j6r
 *
 */
public class LambdaFunctionHandler implements RequestHandler<JobItem, JobItem> {

	private static final String BUCKET_NAME = "ist440grp2-pages";
	private static final String KEY_FORMATTER = "%s--%s.html";
	private static final String URL_FORMATTER = "https://s3.amazonaws.com/%s/%s";

	@Override
	public JobItem handleRequest(JobItem item, Context context) {

		ContainerTag container = div(
				div(
						h2("Metadata"),
						p(String.format("ID: %s", item.getJobId())),
						p(String.format("Created on: %s", item.getCreatedDate())),
						p(String.format("Status: %s", item.getStatus())))
				.withClass("idSectionDiv"),
				
				div(
						h2("Note image"),
						img().withSrc(getUrl(item.getUploadedImageInfo().getBucket(), item.getUploadedImageInfo().getKey())))
				.withClass("idSectionDiv"),

				div(
						h2("OCR Text"), 
						p("Text extracted from your uploaded image"),
						p(getText(item.getOcrInfo().getBucket(), item.getOcrInfo().getKey())),
						p(a("view").withHref(getUrl(item.getOcrInfo().getBucket(), item.getOcrInfo().getKey()))))
				.withClass("idSectionDiv"),

				div(
						h2("Decrypted Text"),
						each(item.getDecryptedInfo(), dItem -> div(
								h3(String.format("%s %s", dItem.getMethod(), dItem.getSourceLanguage())),
								h4(String.format("Best decryption attempt (confidence %s)", dItem.getConfidence())),
								p(getText(dItem.getDecryptedBucket(), dItem.getDecryptedKey())),
								p(a("view").withHref(getUrl(dItem.getDecryptedBucket(), dItem.getDecryptedKey()))),
								h4("English translation"),
								p(getText(dItem.getTranslatedBucket(), dItem.getTranslatedKey())),
								p(a("view").withHref(getUrl(dItem.getTranslatedBucket(), dItem.getTranslatedKey())))
								).withClass("idDecryptSectionDiv")))
				.withClass("idSectionDiv")

		);

		savePage(BUCKET_NAME, String.format(KEY_FORMATTER, item.getUserId(), item.getJobId()), container.render());
		return item;
	}

	/**
	 * Generates a URL for the specified file
	 * 
	 * @param bucket the file's S3 bucket
	 * @param key the file's S3 key
	 * @return the URL
	 */
	public static String getUrl(String bucket, String key) {
		return String.format(URL_FORMATTER, bucket, key);
	}

	/**
	 * Returns the specified file's content as text
	 * 
	 * @param bucket the file's S3 bucket
	 * @param key the file's S3 key
	 * @return the text content, or an empty string if the file was not found
	 */
	protected String getText(String bucket, String key) {
		StringBuffer sb = new StringBuffer();
		AmazonS3 s3client = AmazonS3ClientBuilder.standard().withRegion(Regions.US_EAST_1).build();
		GetObjectRequest getRequest = new GetObjectRequest(bucket, key);
		S3Object s3obj = s3client.getObject(getRequest);

		try (S3ObjectInputStream s3in = s3obj.getObjectContent();
				BufferedReader br = new BufferedReader(new InputStreamReader(s3in))) {
			String line = "";

			while ((line = br.readLine()) != null) {
				sb.append(line);
				sb.append("\n");
			}

		} catch (IOException e) {
			e.printStackTrace();
		}

		return sb.toString();
	}
	
	/**
	 * Writes data to the specified file in S3
	 * 
	 * @param bucket target S3 bucket
	 * @param key target S3 key
	 * @param data text to write
	 */
	protected void savePage(String bucket, String key, String data) {

		byte[] dataByteArray = data.getBytes();

		ByteArrayInputStream in = new ByteArrayInputStream(dataByteArray);

		ObjectMetadata metadata = new ObjectMetadata();
		metadata.setContentType("text/plain");
		metadata.setContentLength(dataByteArray.length);

		AmazonS3 s3client = AmazonS3ClientBuilder.standard().withRegion(Regions.US_EAST_1).build();
		PutObjectRequest putRequest = new PutObjectRequest(bucket, key, in, metadata)
				.withCannedAcl(CannedAccessControlList.PublicRead);
		s3client.putObject(putRequest);
}
}
