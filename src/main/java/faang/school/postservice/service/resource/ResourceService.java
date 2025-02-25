package faang.school.postservice.service.resource;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import faang.school.postservice.dto.resource.ResourceDto;
import faang.school.postservice.exception.FileException;
import faang.school.postservice.image.ImageResizer;
import faang.school.postservice.mapper.resource.ResourceMapper;
import faang.school.postservice.model.Post;
import faang.school.postservice.model.resource.Resource;
import faang.school.postservice.model.resource.ResourceType;
import faang.school.postservice.repository.ResourceRepository;
import faang.school.postservice.validation.resource.ResourceValidator;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResourceService {

    private final AmazonS3 amazonS3Client;
    private final ResourceRepository resourceRepository;
    private final ImageResizer imageResizer;
    private final ResourceValidator resourceValidator;
    private final ResourceMapper resourceMapper;

    @Value("${services.s3.bucket-name}")
    private String bucketName;

    public Resource saveImage(MultipartFile image, Post post) {
        resourceValidator.validateImageFileSize(image);
        String folder = String.valueOf(post.getId());
        Resource resource = uploadImage(image, folder, imageResizer.getResizedImage(image));
        log.info("File {} uploaded to file storage", image.getOriginalFilename());
        resource.setPost(post);
        post.getResources().add(resource);
        return resource;
    }

    @Transactional
    public void deleteResource(Long resourceId) {
        Resource resource = resourceRepository.findById(resourceId)
                .orElseThrow(() -> new EntityNotFoundException(String.format("Resource with id %s not found", resourceId)));
        amazonS3Client.deleteObject(bucketName, resource.getKey());
        log.info("File {} deleted from file storage", resource.getKey());
        resourceRepository.delete(resource);
        log.info("File {} deleted from data base", resource.getKey());
    }

    @Transactional
    public ResourceDto attachMediaToPost(MultipartFile mediaFile, Post post) {
        resourceValidator.validateAudioOrVideoFileSize(mediaFile);
        resourceValidator.validateTypeAudioOrVideo(mediaFile);
        String folder = String.valueOf(post.getId());
        ObjectMetadata objectMetadata = getObjectMetaData(mediaFile);
        String key = generatePostKey(folder, mediaFile.getOriginalFilename());
        PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, key, getMultipartFileInputStream(mediaFile), objectMetadata);
        amazonS3Client.putObject(putObjectRequest);
        log.info("File {} attached to post", key);
        Resource resource = getResource(mediaFile, mediaFile.getSize(), key);
        resource.setPost(post);
        resourceRepository.save(resource);
        log.info("Resource {} saved to data base", resource);
        return resourceMapper.toDto(resource);
    }

    private ObjectMetadata getObjectMetaData(MultipartFile mediaFile) {
        ObjectMetadata objectMetadata = new ObjectMetadata();
        objectMetadata.setContentType(mediaFile.getContentType());
        objectMetadata.setContentLength(mediaFile.getSize());
        return objectMetadata;
    }

    private Resource uploadImage(MultipartFile file, String folder, BufferedImage image) {
        ObjectMetadata objectMetadata = new ObjectMetadata();
        objectMetadata.setContentType(file.getContentType());
        objectMetadata.setContentEncoding("utf-8");
        String key = generatePostKey(folder, file.getOriginalFilename());
        InputStream inputStream = getInputStream(image, file.getContentType());
        setContentLength(objectMetadata, inputStream);
        PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, key, inputStream, objectMetadata);
        amazonS3Client.putObject(putObjectRequest);
        return getResource(file, file.getSize(), key);
    }

    private InputStream getMultipartFileInputStream(MultipartFile multipartFile) {
        try {
            return multipartFile.getInputStream();
        } catch (IOException e) {
            log.error("FileException", e);
            throw new FileException(e.getMessage());
        }
    }

    private String generatePostKey(String folder, String fileName) {
        return String.format("%s/%s/%s", folder, System.currentTimeMillis(), fileName);
    }

    private void setContentLength(ObjectMetadata objectMetadata, InputStream inputStream) {
        try {
            objectMetadata.setContentLength(inputStream.available());
        } catch (IOException e) {
            log.error("FileException", e);
            throw new FileException(e.getMessage());
        }
    }

    private Resource getResource(MultipartFile file, Long fileSize, String key) {
        return Resource.builder()
                .key(key)
                .size(fileSize)
                .name(file.getOriginalFilename())
                .type(ResourceType.getResourceType(getContentType(file)).name())
                .build();
    }

    private String getContentType(MultipartFile file) {
        String contentType = Objects.requireNonNullElse(file.getContentType(), "");
        return contentType.split("/")[0].toLowerCase();
    }

    private InputStream getInputStream(BufferedImage image, String contentType) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, contentType.split("/")[1], baos);
        } catch (IOException e) {
            log.error("FileException", e);
            throw new FileException(e.getMessage());
        }
        return new ByteArrayInputStream(baos.toByteArray());
    }
}
