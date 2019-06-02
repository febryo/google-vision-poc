package com.vision.poc.googlevisionpoc.service.gcloudstorage;

import com.google.cloud.storage.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
public class GCloudStorageService {

    public String upload(MultipartFile file) throws IOException {
        Storage storage = StorageOptions.getDefaultInstance().getService();
        BlobId blobId = BlobId.of("telkom-dms-poc", "doc-test.pdf");
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType("application/pdf").build();
        Blob blob = storage.create(blobInfo, file.getBytes());
        return "gs://"+blob.getBlobId().getBucket()+"/"+blob.getBlobId().getName();
    }

}
