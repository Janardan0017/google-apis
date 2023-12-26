package com.google.apis.api;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.apis.service.AuthService;
import com.google.photos.library.v1.PhotosLibrarySettings;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.security.GeneralSecurityException;

@RestController("/photos")
public class PhotosApi {

    @GetMapping
    public String getPhotos() throws GeneralSecurityException, IOException {

    }
}
