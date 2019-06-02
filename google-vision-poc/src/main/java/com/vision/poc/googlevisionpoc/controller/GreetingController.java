package com.vision.poc.googlevisionpoc.controller;

import com.vision.poc.googlevisionpoc.service.gcloudstorage.GCloudStorageService;
import com.vision.poc.googlevisionpoc.service.gcloudvision.GCloudVisionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;

@Controller
public class GreetingController {

    @Autowired
    private GCloudStorageService gCloudStorageService;

    @Autowired
    private GCloudVisionService gCloudVisionService;

    @GetMapping("/greeting")
    public String greeting(@RequestParam(name = "name", required = false, defaultValue = "World") String name, Model model) {
        model.addAttribute("name", name);
        return "greeting";
    }

    @PostMapping("/greeting")
    public String handleFileUpload(@RequestParam("file") MultipartFile file,
                                   RedirectAttributes redirectAttributes) throws Exception {
        String gcstoragePath = this.gCloudStorageService.upload(file);
        redirectAttributes.addFlashAttribute("message",
                "Successfully upload to Google Storage " + gcstoragePath + "!");
        return "redirect:/greeting";
    }


    @GetMapping("/vision")
    public String vision(RedirectAttributes redirectAttributes) throws Exception {
        String results = this.gCloudVisionService.detectDocumentsGcs("gs://telkom-dms-poc/doc-test.pdf", "gs://telkom-dms-poc/result/");
        redirectAttributes.addFlashAttribute("message",
                "Successfully Google vision this file!");
        redirectAttributes.addFlashAttribute("results",
                results);
        return "redirect:/greeting";
    }

}
