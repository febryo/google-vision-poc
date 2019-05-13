package com.vision.poc.googlevisionpoc.service.gcloudvision;

import com.google.api.gax.longrunning.OperationFuture;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.cloud.vision.v1.AnnotateFileResponse;
import com.google.cloud.vision.v1.AnnotateFileResponse.Builder;
import com.google.cloud.vision.v1.AnnotateImageResponse;
import com.google.cloud.vision.v1.AsyncAnnotateFileRequest;
import com.google.cloud.vision.v1.AsyncAnnotateFileResponse;
import com.google.cloud.vision.v1.AsyncBatchAnnotateFilesResponse;
import com.google.cloud.vision.v1.Feature;
import com.google.cloud.vision.v1.GcsDestination;
import com.google.cloud.vision.v1.GcsSource;
import com.google.cloud.vision.v1.ImageAnnotatorClient;
import com.google.cloud.vision.v1.InputConfig;
import com.google.cloud.vision.v1.OperationMetadata;
import com.google.cloud.vision.v1.OutputConfig;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.protobuf.util.JsonFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GCloudVisionService {

    private static final String VISION_FILE_ASYNC_BATCH_ANNOTATE = "https://vision.googleapis.com/v1/files:asyncBatchAnnotate";

    private static final String VISION_OPERATION = "https://vision.googleapis.com/v1/operations/";

    @Autowired
    private RestTemplate restTemplate;

    public String learn(String authkey) {
        String baseMessage = "{" +
                "  \"requests\":[" +
                "    {" +
                "      \"inputConfig\": {" +
                "        \"gcsSource\": {" +
                "          \"uri\": \"gs://telkom-dms-poc/doc-test.pdf\"" +
                "        }," +
                "        \"mimeType\": \"application/pdf\"" +
                "      }," +
                "      \"features\": [" +
                "        {" +
                "          \"type\": \"DOCUMENT_TEXT_DETECTION\"" +
                "        }" +
                "      ]," +
                "      \"outputConfig\": {" +
                "        \"gcsDestination\": {" +
                "          \"uri\": \"gs://telkom-dms-poc/result/\"" +
                "        }," +
                "        \"batchSize\": 1" +
                "      }" +
                "    }" +
                "  ]" +
                "}";
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.set("Authorization", "Bearer " + authkey);
        HttpEntity<String> entity = new HttpEntity(baseMessage, headers);
        String response = this.restTemplate.postForObject(VISION_FILE_ASYNC_BATCH_ANNOTATE, entity, String.class);

        if (response != null) {
            try {
                JsonParser jsonParser = new JsonParser();
                JsonObject o = jsonParser.parse(response).getAsJsonObject();
                return o.get("name").getAsString();
            } catch (Exception e) {
                throw new IllegalStateException("unable to process message : " + response);
            }

        } else {
            throw new IllegalStateException("the message failed to sent");
        }

    }

    public String detectDocumentsGcs(String gcsSourcePath, String gcsDestinationPath) throws
            Exception {
        try (ImageAnnotatorClient client = ImageAnnotatorClient.create()) {
            List<AsyncAnnotateFileRequest> requests = new ArrayList<>();

            // Set the GCS source path for the remote file.
            GcsSource gcsSource = GcsSource.newBuilder()
                    .setUri(gcsSourcePath)
                    .build();

            // Create the configuration with the specified MIME (Multipurpose Internet Mail Extensions)
            // types
            InputConfig inputConfig = InputConfig.newBuilder()
                    .setMimeType("application/pdf") // Supported MimeTypes: "application/pdf", "image/tiff"
                    .setGcsSource(gcsSource)
                    .build();

            // Set the GCS destination path for where to save the results.
            GcsDestination gcsDestination = GcsDestination.newBuilder()
                    .setUri(gcsDestinationPath)
                    .build();

            // Create the configuration for the output with the batch size.
            // The batch size sets how many pages should be grouped into each json output file.
            OutputConfig outputConfig = OutputConfig.newBuilder()
                    .setBatchSize(2)
                    .setGcsDestination(gcsDestination)
                    .build();

            // Select the Feature required by the vision API
            Feature feature = Feature.newBuilder().setType(Feature.Type.DOCUMENT_TEXT_DETECTION).build();

            // Build the OCR request
            AsyncAnnotateFileRequest request = AsyncAnnotateFileRequest.newBuilder()
                    .addFeatures(feature)
                    .setInputConfig(inputConfig)
                    .setOutputConfig(outputConfig)
                    .build();

            requests.add(request);

            // Perform the OCR request
            OperationFuture<AsyncBatchAnnotateFilesResponse, OperationMetadata> response =
                    client.asyncBatchAnnotateFilesAsync(requests);

            System.out.println("Waiting for the operation to finish.");

            // Wait for the request to finish. (The result is not used, since the API saves the result to
            // the specified location on GCS.)
            List<AsyncAnnotateFileResponse> result = response.get(180, TimeUnit.SECONDS)
                    .getResponsesList();

            // Once the request has completed and the output has been
            // written to GCS, we can list all the output files.
            Storage storage = StorageOptions.getDefaultInstance().getService();

            // Get the destination location from the gcsDestinationPath
            Pattern pattern = Pattern.compile("gs://([^/]+)/(.+)");
            Matcher matcher = pattern.matcher(gcsDestinationPath);
            StringBuilder stringBuilder = new StringBuilder();
            if (matcher.find()) {
                String bucketName = matcher.group(1);
                String prefix = matcher.group(2);

                // Get the list of objects with the given prefix from the GCS bucket
                Bucket bucket = storage.get(bucketName);
                com.google.api.gax.paging.Page<Blob> pageList = bucket.list(Storage.BlobListOption.prefix(prefix));

                Blob firstOutputFile = null;

                // List objects with the given prefix.
                System.out.println("Output files:");

                for (Blob blob : pageList.iterateAll()) {
                    System.out.println(blob.getName());
                    String jsonContents = new String(blob.getContent());
                    try {
                        Builder builder = AnnotateFileResponse.newBuilder();
                        JsonFormat.parser().merge(jsonContents, builder);
                        AnnotateFileResponse annotateFileResponse = builder.build();
                        if (annotateFileResponse.getResponsesCount() > 0) {
                            for (AnnotateImageResponse annotateImageResponse : annotateFileResponse.getResponsesList()) {
                                stringBuilder.append("<p>").append(annotateImageResponse.getFullTextAnnotation().getText()).append("</p>");
                            }
                        }

                    } catch (Exception e) {

                    }

                }
                return stringBuilder.toString();
            } else {
                return "No MATCH";
            }
        }
    }

}
