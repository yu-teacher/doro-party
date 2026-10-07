package com.doro.party.infra.storage;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * MinIO 구현. 버킷은 비공개로 만들고 공개 읽기 정책을 걸지 않는다(사진은 권한을 확인한 백엔드만 내려준다).
 * 기동 때 MinIO 가 죽어 있어도 앱은 뜨고, 첫 사용 때 버킷을 준비한다.
 */
@Slf4j
@Component
@EnableConfigurationProperties(StorageProperties.class)
public class MinioObjectStorage implements ObjectStorage {

    private final MinioClient client;
    private final String bucket;
    private volatile boolean bucketReady;

    public MinioObjectStorage(StorageProperties properties) {
        this.client = MinioClient.builder()
                .endpoint(properties.endpoint())
                .credentials(properties.accessKey(), properties.secretKey())
                .build();
        this.bucket = properties.bucket();
        prepareBucket();
    }

    private synchronized void prepareBucket() {
        if (bucketReady) {
            return;
        }
        try {
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Created private MinIO bucket: {}", bucket);
            }
            bucketReady = true;
        } catch (Exception e) {
            log.warn("MinIO bucket preparation failed (will retry on next use): {}", e.getMessage());
        }
    }

    @Override
    public void put(String key, InputStream data, long size, String contentType) {
        prepareBucket();
        try {
            client.putObject(PutObjectArgs.builder().bucket(bucket).object(key).stream(data, size, -1).contentType(contentType).build());
        } catch (Exception e) {
            throw new StorageException("오브젝트 저장 실패: " + key, e);
        }
    }

    @Override
    public InputStream open(String key) {
        prepareBucket();
        try {
            return client.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (Exception e) {
            throw new StorageException("오브젝트 열기 실패: " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        prepareBucket();
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (Exception e) {
            throw new StorageException("오브젝트 삭제 실패: " + key, e);
        }
    }
}
