package com.vision.poc.googlevisionpoc.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class VisionResponseDTO implements Serializable {
    private String name;
}
