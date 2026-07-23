package com.techpulse.techradar.features.social.application;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImageInput {
    private String contentType;   // e.g. image/png
    private String dataBase64;    // raw base64 or data URL
}
